package assimilation;

import arc.*;
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
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import org.sqlite.core.DB;

import java.util.*;

import static mindustry.Vars.*;

public class Assimilation extends Plugin{

    public int teamCount = 0;

    private final Rules rules = new Rules();
    public static final int cellRadius = 37;
    public static final int cellRequirement = 1500;

    private double counter = 0f;

    private List<Cell> cells = new ArrayList<>();
    private List<Cell> freeCells = new ArrayList<>();

    private Array<Array<ItemStack>> loadouts = new Array<>(4);

    private HashMap<String, CustomPlayer> players = new HashMap<>();
    private HashMap<Team, AssimilationTeam> teams = new HashMap<>();

    private BuildRecorder recorder = new BuildRecorder();

    private DBInterface playerDataDB = new DBInterface("player_data");

    //register event handlers and create variables in the constructor
    public void init(){
        playerDataDB.connect("data/server_data.db");

        initRules();

        netServer.admins.addActionFilter((action) -> {
            Tuple<CustomPlayer, Block> build = recorder.getBuild(action.tile.x, action.tile.y);
            if(action.player != null && players.get(action.player.uuid).assimRank == 0) return false;

            if (build != null && action.player != null && build.get(0) != null
                    && ((CustomPlayer) build.get(0)).assimRank > players.get(action.player.uuid).assimRank
                    && players.get(action.player.uuid).assimRank < 3) {
                return false;
            }
            return true;
        });
        Events.on(EventType.Trigger.class, event ->{
            counter += Time.delta();
            if(Math.round(counter) % (60*60) == 0){
                for(Player player : playerGroup.all()){
                    players.get(player.uuid).playTime += 1;
                    Call.setHudTextReliable(player.con, "[accent]Play time: [scarlet]" + players.get(player.uuid).playTime + "[accent] mins.");
                }
            }
        });

        Events.on(EventType.BlockBuildEndEvent.class, event ->{

            // A nice simple way of recording all block places and the players who placed them.
            if(event.breaking){
                recorder.removeBuild(event.tile.x, event.tile.y);
            }else if(event.tile.block() != null && event.player != null){
                recorder.addBuild(event.tile.x, event.tile.y, players.get(event.player.uuid), event.tile.block());
            }

            for(Cell cell : cells){
                if(cell.contains(event.tile.x, event.tile.y)){
                    cell.updateCapture(event.tile.link(), event.breaking, event.player);
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
            if(event.tile.block() == Blocks.coreNucleus && event.tile.getTeam() != Team.crux){
                AssimilationTeam oldTeam = teams.get(event.tile.getTeam());
                Team temp_newTeam = event.tile.entity.lastHit;
                teams.remove(oldTeam.team);
                if(temp_newTeam == null || !teams.containsKey(temp_newTeam) || teams.get(temp_newTeam) == null){
                    Call.sendMessage(oldTeam.name + "[accent]'s team has died to mysterious means... Distributing players evenly");
                    for(Player ply : oldTeam.players){
                        autoBalance(ply);
                    }
                    return;
                }
                AssimilationTeam newTeam = teams.get(temp_newTeam);

                Call.sendMessage("[accent]" + newTeam.name + "[accent]'s team has [scarlet]A S S I M I L A T E D [accent]" + oldTeam.name + "[accent]'s team!");
                for(Player ply : oldTeam.players){
                    Log.info("Switching uuid: " + ply.uuid + " to new team...");
                    addPlayerTeam(ply, newTeam);
                }


                eventCell.owner = null;
                killTiles(oldTeam.team);

                if(teams.keySet().size() == 1 && Team.crux.cores().size == 0){
                    endgame(teams.get(teams.keySet().toArray()[0]).name);
                }

            }

            // Check for cell destruction and clear the cell
            if(event.tile.block() == Blocks.coreShard){
                eventCell.clearCell();
            }

            if(event.tile.block() == Blocks.coreNucleus && event.tile.getTeam() == Team.crux){
                eventCell.clearCell();
                freeCells.remove(eventCell);
                if(teams.keySet().size() == 1 && Team.crux.cores().size == 1){
                    endgame(teams.get(teams.keySet().toArray()[0]).name);
                }
            }

            if(eventCell != null && eventCell.owner == null && !(event.tile.block() instanceof CoreBlock)){
                eventCell.updateCapture(event.tile.link(), true, null);
            }

        });

        Events.on(EventType.PlayerJoin.class, event ->{
            // Databasing stuff first:
            if(!playerDataDB.hasRow(event.player.uuid)){
                playerDataDB.addRow(event.player.uuid);
            }
            playerDataDB.loadRow(event.player.uuid);

            if(players.containsKey(event.player.uuid) && teams.containsKey(players.get(event.player.uuid).lastTeam)){
                event.player.setTeam(players.get(event.player.uuid).player.getTeam());
                return;
            }

            CustomPlayer ply = new CustomPlayer(event.player, 0, (int) playerDataDB.entries.get(event.player.uuid).get("playtime"));
            players.put(event.player.uuid, ply);
            Call.setHudTextReliable(event.player.con, "[accent]Play time: [scarlet]" + players.get(event.player.uuid).playTime + "[accent] mins.");

            // In the event there are no free cells
            if(freeCells.size() == 0){
                autoBalance(event.player);
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
            ply.lastTeam = event.player.getTeam();

            // Determine next free cell randomly
            Collections.shuffle(freeCells);
            Cell cell = freeCells.remove(0);
            cTeam.capturedCells.add(cell);
            cTeam.homeCell = cell;
            cell.owner = event.player.getTeam();

            // Get custom player object and add player

            cell.makeNexus(players.get(event.player.uuid), false);

        });

        Events.on(EventType.UnitDestroyEvent.class, event ->{
            if(event.unit instanceof Player && players.get(((Player) event.unit).uuid).assimRank == 0){
                ((Player) event.unit).mech = Mechs.alpha;
            }
        });

        Events.on(EventType.PlayerSpawn.class, event ->{
            if(players.get(event.player.uuid).assimRank == 0){
                event.player.mech = Mechs.alpha;
            }else if(event.player.mech == Mechs.alpha){
                event.player.mech = Mechs.dart;
                event.player.sendMessage("Only drones can become an Alpha");
            }
        });

        Events.on(EventType.PlayerLeave.class, event ->{
            savePlayerData(event.player);
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

            state.rules = rules.copy();
            logic.play();


            for(Tuple<Integer, Integer> cell : generator.getCells()){
                Cell c = new Cell((int) cell.get(0), (int) cell.get(1), recorder, players);
                c.owner = Team.crux;
                c.makeNexus(null, true);
                cells.add(c);
                freeCells.add(c);
            }

            netServer.openServer();

        });
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {

        // Register the re-rank command
        handler.<Player>register("rerank", "[player/id] [rank]", "Re-rank a player on your team to [scarlet]0[white]: Bot, [scarlet]1[white]: Drone, [scarlet]2[white]: Private or [scarlet]3[white]: Captain", (args, player) -> {
            if (args.length == 0) {
                String s = "[accent]Use [orange]/rerank [player/id] [rank][accent] to re-rank a player to [scarlet]0[accent]: Bot, [scarlet]1[accent]: Drone, [scarlet]2[accent]: Private or [scarlet]3[accent]: Captain\n\n";
                s += "You are able to rerank the following players:";
                for (Player ply : Vars.playerGroup) {
                    if (ply != player && player.getTeam() == players.get(ply.uuid).lastTeam) {
                        s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
                    }
                }
                player.sendMessage(s);
                return;
            }
            if (args.length == 1) {
                player.sendMessage("[accent]/rerank expects 2 arguments, [white][player/id] [rank]");
                return;
            }

            if (players.get(player.uuid).assimRank != 4) {
                player.sendMessage("You can only re-rank players if you are a Commander!\n");
                return;
            }
            boolean wasID = true;
            Player other;
            try {
                other = Vars.playerGroup.getByID(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                other = null;
            }

            if (other == null) {
                wasID = false;
                other = Vars.playerGroup.find(p -> p.name.equalsIgnoreCase(args[0]));
                if (other == null) {
                    String s = "[accent]No player by name [white]" + args[0] + "[accent] or id [white]" + args[0] + "[accent].\n";
                    s += "You are able to rerank the following players:";
                    for (Player ply : Vars.playerGroup) {
                        if (ply != player && player.getTeam() == players.get(ply.uuid).lastTeam) {
                            s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
                        }
                    }
                    player.sendMessage(s);
                    return;
                }
            }
            if (other == player) {
                player.sendMessage("[accent]Can not re-rank yourself!");
                return;
            }
            if (other.getTeam() != player.getTeam()) {
                player.sendMessage("[accent]Can not re-rank players outside of your team!");
                return;
            }

            int newRank;
            try {
                newRank = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("[accent]Rank must be a number from [scarlet]0 to 3[accent].");
                return;
            }
            if (newRank < 0 || newRank > 3) {
                player.sendMessage("[accent]Rank must be a number from [scarlet]0 to 3[accent].");
                return;
            }

            teams.get(player.getTeam()).rank(players.get(other.uuid), newRank);
            other.kill();
            String s1 = "[accent]Successfully re-ranked " + (wasID ? "ID: " : "Player: ") + "[white]" + args[0] + "[accent] to rank: [white]";
            String s2 = "[accent]" + player.name + "[accent] has re-ranked you to [white]";
            String append = "";
            switch (newRank) {
                case 0:
                    append = "Bot";
                    break;
                case 1:
                    append = "Drone";
                    break;
                case 2:
                    append = "Private";
                    break;
                case 3:
                    append = "Captain";
                    break;
            }
            s1 += append;
            s2 += append;
            player.sendMessage(s1);
            other.sendMessage(s2);
        });

        handler.<Player>register("members", "List all the members in your team", (args, player) -> {
            String s = "[accent]Members:";
            for (Player ply : teams.get(players.get(player.uuid).lastTeam).players) {
                s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
            }
            player.sendMessage(s);
        });

        handler.<Player>register("kill", "Destroy yourself", (args, player) ->{
            player.kill();
        });

    }

    void initRules(){

        // This ensures only the alpha mech has any attack damage
        Weapon useless = new Weapon("prettyShitNGL"){{
            length = 1.5f;
            reload = 14f;
            shots = 0; // get rekt
            alternate = true;
            ejectEffect = Fx.shellEjectSmall;
            bullet = Bullets.standardMechSmall;
        }};

        //Mechs.dart.weapon = useless;
        Mechs.delta.weapon = useless;
        Mechs.glaive.weapon = useless;
        Mechs.javelin.weapon = useless;
        Mechs.omega.weapon = useless;
        Mechs.tau.weapon = useless;
        Mechs.trident.weapon = useless;

        rules.canGameOver = false;
        rules.playerDamageMultiplier = 100;
        rules.playerHealthMultiplier = 1;
        rules.enemyCoreBuildRadius = (cellRadius-2) * 8;
        rules.loadout = ItemStack.list(Items.copper, 2000, Items.lead, 1000, Items.graphite, 200, Items.metaglass, 200, Items.silicon, 400);
        rules.bannedBlocks.addAll(Blocks.hail, Blocks.ripple);
        rules.buildSpeedMultiplier = 2;
    }

    void autoBalance(Player player){
        // Add player to the team with least players
        AssimilationTeam minTeam = null;
        int min = 5000;
        for(AssimilationTeam team : teams.values()){
            if(team.players.size() < min){
                min = team.players.size();
                minTeam = team;
            }
        }
        addPlayerTeam(player, minTeam);
    }

    void addPlayerTeam(Player player, AssimilationTeam newTeam){
        CustomPlayer cPly = players.get(player.uuid);
        player.setTeam(newTeam.team);
        players.get(player.uuid).lastTeam = newTeam.team;
        newTeam.addPlayer(player);
        if(cPly.assimRank > 2){
            cPly.assimRank = 2;
        }
        player.kill();
        players.get(player.uuid).assimRank = 2;

        newTeam.addPlayer(player);
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
        String winPlayer = teams.get(teams.keySet().toArray()[0]).commander.uuid;
        CustomPlayer ply = players.get(winPlayer);
        HashMap<String, Object> entry = playerDataDB.entries.get(winPlayer);
        entry.put("monthWins", (int) entry.get("monthWins") + 1);
        entry.put("allWins", (int) entry.get("allWins") + 1);
        Time.runTask(60f * 10f, () -> {
            for(Player player : playerGroup.all()) {
                savePlayerData(player);
                Call.onConnect(player.con, "aamindustry.play.ai", 6567);
            }
            // I shouldn't need this, all players should be gone since I connected them to hub
            // netServer.kickAll(KickReason.serverRestarting);
            Time.runTask(5f, () -> System.exit(2));
        });
    }

    void savePlayerData(Player player){
        CustomPlayer ply = players.get(player.uuid);
        playerDataDB.entries.get(player.uuid).put("playtime", ply.playTime);
        playerDataDB.saveRow(player.uuid);
    }
}
