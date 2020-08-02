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
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static mindustry.Vars.*;

public class Assimilation extends Plugin{

    public int teamCount = 0;

    private final Rules rules = new Rules();
    public static final int cellRadius = 30;
    public static final int cellRequirement = 1500;

    private List<Cell> cells = new ArrayList<>();
    private List<Cell> freeCells = new ArrayList<>();

    private Array<Array<ItemStack>> loadouts = new Array<>(4);

    private HashMap<String, CustomPlayer> players = new HashMap<>();
    private HashMap<Team, AssimilationTeam> teams = new HashMap<>();

    //register event handlers and create variables in the constructor
    public void init(){

        rules.canGameOver = false;
        rules.playerDamageMultiplier = 5;
        rules.enemyCoreBuildRadius = 28 * 8;
        rules.respawnTime = 0;
        rules.loadout = ItemStack.list(Items.copper, 1000, Items.lead, 1000, Items.graphite, 200, Items.metaglass, 200, Items.silicon, 200);


        Events.on(EventType.BlockBuildEndEvent.class, event ->{
            for(Cell cell : cells){
                if(cell.contains(event.tile.x, event.tile.y)){
                    cell.updateCapture(event.tile.link(), event.breaking);
                    break;
                }
            }
        });

        Events.on(Cell.CellCaptureEvent.class, event ->{
            event.cell.makeShard();
            boolean allCapped = true;
            for(Cell cell : cells){
                if(cell.owner != event.cell.owner){
                    allCapped = false;
                    break;
                }
            }

            if(allCapped) {
                endgame();
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event ->{
            Cell eventCell = null; // The following statements should only use a cell if the event happened in one, so this shouldn't throw an error
            for(Cell cell : cells){
                if(cell.contains(event.tile.x, event.tile.y)){
                    eventCell = cell;
                }
            }


            if(event.tile.block() == Blocks.coreNucleus){
                Team oldTeam = event.tile.getTeam();
                Team newTeam = event.tile.entity.lastHit;
                Call.sendMessage("[accent]" + teams.get(newTeam).name + "[accent] H A S  [scarlet]A S S I M I L A T E D [accent]" + teams.get(oldTeam).name + "!");
                for(Player ply : teams.get(oldTeam).players){
                    ply.setTeam(newTeam);
                    players.get(ply.uuid).lastTeam = newTeam;
                    teams.get(newTeam).addPlayer(ply);
                    ply.kill();
                }

                teams.remove(oldTeam);
                eventCell.owner = null;
                killTiles(oldTeam);

            }

            if(event.tile.block() == Blocks.coreShard){
                eventCell.clearCell();
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

        //register a simple reply command
        handler.<Player>register("reply", "<text...>", "A simple ping command that echoes a player's text.", (args, player) -> {
            player.sendMessage("You said: [accent] " + args[0]);
        });

        //register a whisper command which can be used to send other players messages
        handler.<Player>register("whisper", "<player> <text...>", "Whisper text to another player.", (args, player) -> {
            //find player by name
            Player other = Vars.playerGroup.find(p -> p.name.equalsIgnoreCase(args[0]));

            //give error message with scarlet-colored text if player isn't found
            if(other == null){
                player.sendMessage("[scarlet]No player by that name found!");
                return;
            }

            //send the other player a message, using [lightgray] for gray text color and [] to reset color
            other.sendMessage("[lightgray](whisper) " + player.name + ":[] " + args[1]);
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

    void endgame(){
        Call.sendMessage("Game over");
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
