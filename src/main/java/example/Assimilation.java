package example;

import arc.*;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Array;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.GameState;
import mindustry.entities.type.*;
import mindustry.game.EventType;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.plugin.Plugin;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.*;

import static mindustry.Vars.*;

public class Assimilation extends Plugin{

    public int teamCount = 0;

    private final Rules rules = new Rules();
    public static final int cellRadius = 37;
    public static final int cellRequirement = 15000;

    private List<Cell> cells = new ArrayList<>();
    private List<Cell> freeCells = new ArrayList<>();

    private Array<Array<ItemStack>> loadouts = new Array<>(4);

    private HashMap<String, CustomPlayer> players = new HashMap<>();
    private HashMap<Team, AssimilationTeam> teams = new HashMap<>();

    private BuildRecorder recorder = new BuildRecorder();

    //register event handlers and create variables in the constructor
    public void init(){

        rules.canGameOver = false;
        rules.playerDamageMultiplier = 0;
        rules.enemyCoreBuildRadius = 28 * 8;
        rules.loadout = ItemStack.list(Items.copper, 1000, Items.lead, 1000, Items.graphite, 200, Items.metaglass, 200, Items.silicon, 200);
        rules.bannedBlocks.addAll(Blocks.hail, Blocks.ripple);
        rules.buildSpeedMultiplier = 2;


        netServer.admins.addActionFilter((action) -> {
            Tuple<CustomPlayer, Block> build = recorder.getBuild(action.tile.x, action.tile.y);
            if (build != null && action.player != null
                    && ((CustomPlayer) build.get(0)).assimRank > players.get(action.player.uuid).assimRank
                    && players.get(action.player.uuid).assimRank < 3) {
                return false;
            }
            return true;
        });

        Events.on(EventType.BlockBuildEndEvent.class, event ->{

            // A nice simple way of recording all block places and the players who placed them.
            if(event.breaking){
                recorder.removeBuild(event.tile.x, event.tile.y);
            }else{
                recorder.addBuild(event.tile.x, event.tile.y, players.get(event.player.uuid), event.tile.block());
            }

            for(Cell cell : cells){
                if(cell.contains(event.tile.x, event.tile.y)){
                    cell.updateCapture(event.tile.link(), event.breaking);
                    break;
                }
            }
        });

        Events.on(Cell.CellCaptureEvent.class, event ->{
            event.cell.makeShard();
            freeCells.remove(event.cell);
            boolean allCapped = true;
            for(Cell cell : cells){
                if(cell.owner != event.cell.owner){
                    allCapped = false;
                    break;
                }
            }

            if(allCapped) {
                endgame(teams.get(event.cell.owner).name);
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event ->{
            Cell eventCell = null; // The following statements should only use a cell if the event happened in one, so this shouldn't throw an error
            for(Cell cell : cells){
                if(cell.contains(event.tile.x, event.tile.y)){
                    eventCell = cell;
                }
            }

            recorder.removeBuild(event.tile.x, event.tile.y);

            // Check for team elimination (when nucleus is destroyed)
            if(event.tile.block() == Blocks.coreNucleus){
                Team oldTeam = event.tile.getTeam();
                Team newTeam = event.tile.entity.lastHit;
                Call.sendMessage("[accent]" + teams.get(newTeam).name + "[accent]'s team has [scarlet]A S S I M I L A T E D [accent]" + teams.get(oldTeam).name + "[accent]'s team!");
                for(Player ply : teams.get(oldTeam).players){
                    CustomPlayer cPly = players.get(ply.uuid);
                    ply.setTeam(newTeam);
                    players.get(ply.uuid).lastTeam = newTeam;
                    teams.get(newTeam).addPlayer(ply);
                    cPly.assimRank = 2;
                    ply.kill();
                }

                teams.remove(oldTeam);
                eventCell.owner = null;
                killTiles(oldTeam);

            }

            // Check for cell destruction and clear the cell
            if(event.tile.block() == Blocks.coreShard){
                eventCell.clearCell();
                freeCells.add(eventCell);
            }

            if(eventCell != null && eventCell.owner == null && !(event.tile.block() instanceof CoreBlock)){
                eventCell.updateCapture(event.tile.link(), true);
            }


        });

        Events.on(EventType.PlayerJoin.class, event ->{
            if(players.containsKey(event.player.uuid) && teams.containsKey(players.get(event.player.uuid).lastTeam)){
                event.player.setTeam(players.get(event.player.uuid).player.getTeam());
                teams.get(event.player.getTeam()).addPlayer(event.player);
                return;
            }
            // Get new team
            teamCount ++;
            event.player.setTeam(Team.all()[teamCount+6]);

            // Create custom team and add it to the teams hash map
            AssimilationTeam cTeam = new AssimilationTeam(event.player);
            teams.put(event.player.getTeam(), cTeam);
            // Add player to the custom team
            cTeam.addPlayer(event.player);

            // Determine next free cell randomly
            Collections.shuffle(freeCells);
            Cell cell = freeCells.remove(0);
            cTeam.capturedCells.add(cell);
            cell.owner = event.player.getTeam();

            // Get custom player object and add player
            CustomPlayer ply = new CustomPlayer(event.player, 0);
            players.put(event.player.uuid, ply);
            cell.makeNexus();
        });

    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("assimilation", "Begin hosting the Assimilation gamemode.", args ->{
            if(!Vars.state.is(GameState.State.menu)){
                Log.err("Stop the server first.");
                return;
            }

            Log.info("Generating map...");
            ArenaGenerator generator = new ArenaGenerator(cellRadius);
            world.loadGenerator(generator);
            Log.info("Map generated.");

            // Create cells objects

            for(Tuple<Integer, Integer> cell : generator.getCells()){
                Cell c = new Cell((int) cell.get(0), (int) cell.get(1));
                cells.add(c);
                freeCells.add(c);
            }

            state.rules = rules.copy();
            logic.play();
            netServer.openServer();

        });
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){

        // Register the re-rank command
        handler.<Player>register("rerank", "<player> <rank>", "Re-rank a player on your team to 1: Drone, 2: Private or 3: Captain", (args, player) -> {
            if(players.get(player.uuid).assimRank != 4){
                player.sendMessage("You can only re-rank players if you are a Commander!\n");
                return;
            }
            boolean wasID = true;
            Player other;
            try {
                other = Vars.playerGroup.getByID(Integer.parseInt(args[0]));
            }catch (NumberFormatException e){
                other = null;
            }

            if(other == null){
                wasID = false;
                other = Vars.playerGroup.find(p -> p.name.equalsIgnoreCase(args[0]));
                if(other == null){
                    String s = "[accent]No player by name [white]" + args[0] + "[accent] or id [white]" + args[0] + "[accent].\n";
                    s += "You are able to rerank the following players:";
                    for(Player ply: Vars.playerGroup){
                        if(player.getTeam() == players.get(ply.uuid).lastTeam){
                            s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
                        }
                    }
                    player.sendMessage(s);
                    return;
                }
            }
            if(other == player){
                player.sendMessage("[accent]Can not re-rank yourself!");
                return;
            }
            if(other.getTeam() != player.getTeam()){
                player.sendMessage("[accent]Can not re-rank players outside of your team!");
                return;
            }

            int newRank;
            try{
                newRank = Integer.parseInt(args[1]);
            }catch (NumberFormatException e){
                player.sendMessage("[accent]Rank must be a number from [scarlet]1 to 3[accent].");
                return;
            }

            teams.get(player.getTeam()).rank(players.get(other.uuid), newRank);
            String s1 = "[accent]Successfully re-ranked " + (wasID ? "ID: " : "Player: ") + "[white]" + args[0] + "[accent] to rank: [white]";
            String s2 = "[accent]" + player.name + " has re-ranked you to ";
            String append = "";
            switch(newRank){
                case 1: append = "Drone"; break;
                case 2: append = "Private"; break;
                case 3: append = "Captain"; break;
            }
            s1 += append;
            s2 += append;
            player.sendMessage(s1);
            other.sendMessage(s2);
        });
    }

    void killTiles(Team team){
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile.entity != null && tile.getTeam() == team){
                    Time.run(Mathf.random(60f * 6), tile.entity::kill);
                }
            }
        }
    }

    void endgame(String winner){
        Call.onInfoMessage(winner + "[accent]'s team has conquered the planet! Loading the next world...");
        Time.runTask(60f * 10f, () -> {
            for(Player player : playerGroup.all()) {
                Call.onConnect(player.con, "aamindustry.play.ai", 6567);
            }
            // I shouldn't need this, all players should be gone since I connected them to hub
            // netServer.kickAll(KickReason.serverRestarting);
            Time.runTask(5f, () -> System.exit(2));
        });
    }
}
