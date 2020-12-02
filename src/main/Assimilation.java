package main;

import arc.*;
import arc.func.Boolf;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.*;
import arc.util.serialization.Base64Coder;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.GameState;
import mindustry.entities.bullet.BulletType;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;

import static mindustry.Vars.*;

public class Assimilation extends Plugin {

    private Preferences prefs;
    private Random rand = new Random(System.currentTimeMillis());

    public int teamCount = 0;

    private final Rules rules = new Rules();
    public static final int cellRadius = 37;
    public static final int cellRequirement = 1500;
    private Schematic cellSpawn;


    public boolean eventActive;
    public boolean zombieActive;


    private double counter = 0f;

    private List<Cell> cells = new ArrayList<>();
    private List<Cell> freeCells = new ArrayList<>();
    private List<Cell> priorityCells = new ArrayList<>();

    private Seq<Seq<ItemStack>> loadouts = new Seq<>(4);

    private final static int minuteTime = 60 * 60, damageMultiplyTime = 60 * 60 * 30;
    private final static int timerMinute = 0, timerDamageMultiply = 1;
    private Interval interval = new Interval(10);

    private float multiplier = 1;


    private HashMap<String, CustomPlayer> players = new HashMap<>();
    private HashMap<Team, AssimilationTeam> teams = new HashMap<>();

    private BuildRecorder recorder = new BuildRecorder();

    private DBInterface playerDataDB = new DBInterface("player_data");
    private DBInterface playerConfigDB = new DBInterface("player_config");



    private StringHandler stringHandler = new StringHandler();

    //register event handlers and create variables in the constructor
    public void init(){

        playerDataDB.connect("data/server_data.db");
        playerConfigDB.connect(playerDataDB.conn);

        initRules();

        Rank defaultRank = new Rank("", 0);
        Rank donatorOne = new Rank("[#4d004d]{[sky]Donator[#4d004d]} [white]", 1);
        Rank donatorTwo = new Rank("[#4d004d]{[sky]Donator[gold]+[#4d004d]} [sky] ", 2);
        Rank youtuber = new Rank("[#4d004d]{[scarlet]You[gray]tuber} [sky]", 0);

        netServer.admins.addActionFilter((action) -> {
            if(action.type == Administration.ActionType.control || action.type == Administration.ActionType.command) return false;
            if(action.tile == null) return true;
            Tuple<CustomPlayer, Block> build = recorder.getBuild(action.tile.x, action.tile.y);
            if(action.player != null && players.get(action.player.uuid()).assimRank == 0) return false;

            if (build != null && action.player != null && build.get(0) != null
                    && ((CustomPlayer) build.get(0)).assimRank > players.get(action.player.uuid()).assimRank
                    && players.get(action.player.uuid()).assimRank < 3) {
                return false;
            }
            if (action.player != null && teams.containsKey(action.player.team()) && players.containsKey(action.player.uuid())
            && players.get(action.player.uuid()).assimRank != 4 && teams.get(action.player.team()).codered){
                return false;
            }

            if (action.player != null
            && action.block != null && action.block == Blocks.commandCenter && players.get(action.player.uuid()).assimRank < 2){
                return false;
            }

            return true;
        });


        Events.on(EventType.Trigger.class, event ->{

            if(interval.get(timerDamageMultiply, damageMultiplyTime)){
                multiplier *= 1.2;
                state.rules.unitDamageMultiplier *= 1.2;
                //state.rules.unitHealthMultiplier *= 1.2;
                Call.sendMessage("[accent]Units now deal [scarlet]20%[accent] more damage");
            }
        });

        Events.on(EventType.BlockBuildEndEvent.class, event ->{

            // A nice simple way of recording all block places and the players who placed them.
            if(event.breaking){
                recorder.removeBuild(event.tile.x, event.tile.y);
            }else if(event.tile.block() != null && event.unit.getPlayer() != null){
                recorder.addBuild(event.tile.x, event.tile.y, players.get(event.unit.getPlayer().uuid()), event.tile.block());
            }

            for(Cell cell : cells){
                if(cell.contains(event.tile.x, event.tile.y)){
                    cell.updateCapture(event.tile, event.breaking, event.unit.getPlayer());
                    break;
                }
            }
        });

        Events.on(Cell.CellCaptureEvent.class, event ->{
            event.cell.makeShard();
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
            if(event.tile.block() == Blocks.coreNucleus && event.tile.team() != Team.crux){
                eventCell.owner = null;
                if(teams.containsKey(event.tile.team())) assimilate(event.tile.team(), event.tile.build.lastHit);
            }

            // Check for cell destruction and clear the cell
            if(event.tile.block() == Blocks.coreShard){
                eventCell.clearCell();
            }

            if(event.tile.block() == Blocks.coreNucleus && event.tile.team() == Team.crux){
                if(event.tile.build.lastHit != null && teams.containsKey(event.tile.build.lastHit)) {
                    for (Player ply : teams.get(event.tile.build.lastHit).players) {
                        if (players.get(ply.uuid()).connected) {
                            int addXp = 10 * (ply.donateLevel + 1);
                            ply.sendMessage("[accent]+[scarlet]" + addXp + "xp[accent] for clearing a crux a cell");
                            playerDataDB.safePut(ply.uuid(),"xp", (int) playerDataDB.safeGet(ply.uuid(),"xp") + addXp);
                        }
                    }
                }
                eventCell.clearCell();
                freeCells.remove(eventCell);
                priorityCells.remove(eventCell);
                if(teams.keySet().size() == 1 && Team.crux.cores().size == 1){
                    endgame(teams.get(teams.keySet().toArray()[0]).name);
                }
            }

            if(eventCell != null && eventCell.owner == null && !(event.tile.block() instanceof CoreBlock)){
                eventCell.updateCapture(event.tile, true, null);
            }

        });

        Events.on(EventType.PlayerJoinSecondary.class, event ->{
            // Databasing stuff first:
            if(!playerDataDB.hasRow(event.player.uuid())){
                Log.info("New player, adding to local tables...");
                playerDataDB.addRow(event.player.uuid());
                playerConfigDB.addRow(event.player.uuid());
            }

            playerDataDB.loadRow(event.player.uuid());
            playerConfigDB.loadRow(event.player.uuid());

            if((int) playerConfigDB.safeGet(event.player.uuid(),"defaultRank") == 0){
                playerConfigDB.safePut(event.player.uuid(),"defaultRank", 1);
            }

            // Determine rank

            event.player.name = stringHandler.determineRank((int) playerDataDB.safeGet(event.player.uuid(),"xp")) + " " + event.player.name;
            
            playerDataDB.safePut(event.player.uuid(),"latestName", event.player.name);


            event.player.sendMessage(leaderboard(5));



            CustomPlayer ply;

            if(!players.containsKey(event.player.uuid())){
                ply = new CustomPlayer(event.player, 0);
                ply.eventCalls = event.player.donateLevel; // CHANGE THIS | why tho?
                players.put(event.player.uuid(), ply);
            }else{
                ply = players.get(event.player.uuid());
            }
            ply.connected = true;

            if(teams.containsKey(players.get(event.player.uuid()).lastTeam)){
                event.player.team(players.get(event.player.uuid()).player.team());

                AssimilationTeam oldTeam = teams.get(players.get(event.player.uuid()).lastTeam);

                // Remove old player object and replace with new one
                oldTeam.players.removeIf(player -> player.uuid().equals(event.player.uuid()));
                oldTeam.players.add(event.player);

                if(oldTeam.commander.uuid().equals(event.player.uuid())){
                    oldTeam.commander = event.player;
                }
                return;
            }
            // In the event there are no free cells
            if(priorityCells.size() == 0 && freeCells.size() == 0){
                autoBalance(event.player);

                return;
            }



            // Get new team
            teamCount ++;
            event.player.team(Team.all[teamCount+6]);

            // Create custom team and add it to the teams hash map
            AssimilationTeam cTeam = new AssimilationTeam(event.player, (int) playerConfigDB.safeGet(event.player.uuid(),"defaultRank"));
            teams.put(event.player.team(), cTeam);
            // Add player to the custom team
            cTeam.addPlayer(event.player);
            ply.lastTeam = event.player.team();


            // Determine next free cell randomly
            Cell cell;
            if(priorityCells.size() != 0){
                Collections.shuffle(priorityCells);
                cell = priorityCells.remove(0);
            }else{
                Collections.shuffle(freeCells);
                cell = freeCells.remove(0);
            }

            cTeam.capturedCells.add(cell);
            cTeam.homeCell = cell;
            cell.owner = event.player.team();

            cell.makeNexus(players.get(event.player.uuid()), false);

        });

        Events.on(EventType.UnitDestroyEvent.class, event ->{

            if(event.unit.getPlayer() == null && zombieActive && event.unit.team() != Team.crux && !event.unit.type().name.equals("zenith") && !event.unit.type().name.equals("crawler")){
                UnitType unit = Vars.content.units().find(unitType -> unitType.name.equals(event.unit.type().name));
                Unit baseUnit = unit.create(Team.crux);
                baseUnit.set(event.unit.x, event.unit.y);
                baseUnit.add();
            }


        });

        /*Events.on(EventType.PlayerSpawn.class, event ->{
            if(players.get(event.player.uuid()).assimRank == 0){
                event.player.mech = Mechs.alpha;
            }else if(event.player.mech == Mechs.alpha){
                event.player.mech = Mechs.dart;
                event.player.sendMessage("Only bots can become an Alpha");
            }
        });*/

        Events.on(EventType.PlayerLeave.class, event ->{
            savePlayerData(event.player.uuid());
            players.get(event.player.uuid()).connected = false;
        });


        Events.on(EventType.CustomEvent.class, event ->{
           if(event.value instanceof String[] && ((String[]) event.value)[0].equals("newName")){
               String[] val = (String[]) event.value;
               Player ply = players.get(val[1]).player;
               ply.name = stringHandler.determineRank((int) playerDataDB.safeGet(val[1],"xp")) + " " + ply.name;
           }
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
            float generation = rand.nextFloat();
            ArenaGenerator generator = new ArenaGenerator(cellRadius, generation);
            if(generation < 0.25f){
                cellSpawn = Schematics.readBase64("bXNjaAB4nE2RWw6CMBBFp3TaGRK34gJcjSGkH75KohK3ryA9ykc5tKe3DVd66YNoHW5F+lcZLsdxupdO/DzX8XmaquyWiX2dx2uZHyJykPZ0yxAYfp8RUihBGTLINwokB6LCN3mlCCmUoAwZ5P+XXV/ddsqSoawaq21HZEds9/qQMdc8xVM8xVO8hJfwEl7Cy3gZL+NlPMMzPKMFowWjBaMFowWjBSPZ+QFOC04LTgtOC04LTgtOC74lvgFcRBAe");
                rules.loadout = ItemStack.list(Items.copper, 2000, Items.lead, 1000, Items.graphite, 1000, Items.metaglass, 200, Items.silicon, 1500, Items.titanium, 500);
            }

            world.loadGenerator(516, 516, generator::generate);
            world.beginMapLoad();
            ArenaGenerator.defaultOres(world.tiles);
            world.endMapLoad();
            Log.info("Map generated.");

            // Create cells objects

            state.rules = rules.copy();
            logic.play();

            int cellLimit = 1000;


            for(Tuple<Integer, Integer> cell : generator.getCells()){
                Cell c = new Cell((int) cell.get(0), (int) cell.get(1), recorder, players, cellSpawn);
                c.owner = Team.crux;
                c.makeNexus(null, true);
                cells.add(c);
                if((c.x == 39 || c.x == 448 || c.y == 39 || c.y == 478) && !(c.x == 39 && c.y == 39) && !(c.x == 448 && c.y == 478)){
                    priorityCells.add(c);
                }else{
                    freeCells.add(c);
                }
                cellLimit -= 1;
                if(cellLimit <= 0){
                    break;
                }
            }

            checkExpiration();

            netServer.openServer();

        });

        handler.register("setxp", "<uuid> <xp>", "Set the xp of a player", args -> {
            int newXp;
            try{
                newXp = Integer.parseInt(args[1]);
            }catch(NumberFormatException e){
                Log.info("Invalid xp input '" + args[1] + "'");
                return;
            }

            if(!playerDataDB.entries.containsKey(args[0])){
                playerDataDB.loadRow(args[0]);
                playerDataDB.safePut(args[0],"xp", newXp);
                playerDataDB.saveRow(args[0]);
            }else{
                playerDataDB.safePut(args[0],"xp", newXp);
            }
            Log.info("Set uuid " + args[0] + " to have xp of " + args[1]);

        });

        handler.register("reset_ranks", "Sets all xp to 0.", args ->{
            rankReset();
            Log.info("Ranks reset.");
        });

        handler.register("reset_wins", "Sets all monthWins to 0.", args ->{
            winsReset();
            Log.info("Monthly wins reset.");
        });

    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {

        Function<String[], Consumer<Player>> rerankCommand = args -> player -> {
            if (args.length == 0) {
                String s = "[accent]Use [orange]/rerank [player/id] [rank][accent] to re-rank a player to [scarlet]0[accent]: Bot, [scarlet]1[accent]: Drone, [scarlet]2[accent]: Private or [scarlet]3[accent]: Captain\n\n";
                s += "You are able to rerank the following players:";
                for (Player ply : Groups.player) {
                    if (ply != player && player.team() == players.get(ply.uuid()).lastTeam) {
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

            if (players.get(player.uuid()).assimRank != 4) {
                player.sendMessage("[accent]You can only re-rank players if you are a Commander!\n");
                return;
            }
            boolean wasID = true;
            Player other;
            try {
                other = Groups.player.getByID(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                other = null;
            }

            if (other == null) {
                wasID = false;
                other = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                if (other == null) {
                    String s = "[accent]No player by name [white]" + args[0] + "[accent] or id [white]" + args[0] + "[accent].\n";
                    s += "You are able to rerank the following players:";
                    for (Player ply : Groups.player) {
                        if (ply != player && player.team() == players.get(ply.uuid()).lastTeam) {
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
            if (other.team() != player.team()) {
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

            teams.get(player.team()).rank(players.get(other.uuid()), newRank);
            other.clearUnit();
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
        };

        // Register the re-rank command
        handler.<Player>register("rerank", "[player/id] [rank]", "Re-rank a player on your team to [scarlet]0[white]: Bot, [scarlet]1[white]: Drone, [scarlet]2[white]: Private or [scarlet]3[white]: Captain", (args, player) -> {
            rerankCommand.apply(args).accept(player);
        });

        handler.<Player>register("rr", "[player/id] [rank]", "Alias for rerank", (args, player) -> {
            rerankCommand.apply(args).accept(player);
        });

        handler.<Player>register("members", "List all the members in your team and their rank", (args, player) -> {
            String commander = "[accent]Commander:";
            String captains = "[accent]Captains:";
            String privates = "[accent]Privates:";
            String drones = "[accent]Drones:";
            String bots = "[accent]Bots:";
            for (Player ply : teams.get(players.get(player.uuid()).lastTeam).players) {
                CustomPlayer cPly = players.get(ply.uuid());
                switch(cPly.assimRank){
                    case 0: bots += "\n [gold]- [accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id; break;
                    case 1: drones += "\n [gold]- [accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id; break;
                    case 2: privates += "\n [gold]- [accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id; break;
                    case 3: captains += "\n [gold]- [accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id; break;
                    case 4: commander += "\n [gold]- [accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id; break;
                }
            }
            player.sendMessage(commander + "\n" + captains + "\n" + privates + "\n" + drones + "\n" + bots);
        });

        handler.<Player>register("drank", "[rank]", "Set the default rank players have when assimilated into your team", (args, player) ->{
            if (args.length == 0) {
                player.sendMessage("[accent]This command expects [scarlet]1[accent] argument of a number from [scarlet]1 [accent]to [scarlet]3[accent]:\n [gold]- [scarlet]1[accent]: Drone\n [gold]- [scarlet]2[accent]: Private\n [gold]- [scarlet]3[accent]: Captain");
                return;
            }
            int dRank;
            try {
                dRank = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("[accent]This command expects [scarlet]1[accent] argument of a number from [scarlet]1 [accent]to [scarlet]3[accent]:\n [gold]- [scarlet]1[accent]: Drone\n [gold]- [scarlet]2[accent]: Private\n [gold]- [scarlet]3[accent]: Captain");
                return;
            }

            if(dRank < 1 || dRank > 3){
                player.sendMessage("[accent]This command expects [scarlet]1[accent] argument of a number from [scarlet]1 [accent]to [scarlet]3[accent]:\n [gold]- [scarlet]1[accent]: Drone\n [gold]- [scarlet]2[accent]: Private\n [gold]- [scarlet]3[accent]: Captain");
                return;
            }

            playerConfigDB.safePut(player.uuid(),"defaultRank", dRank);
            if(players.get(player.uuid()).assimRank == 4) teams.get(player.team()).defaultRank = dRank;
            player.sendMessage("[accent]Successfully updated default rank to [scarlet]" + dRank);

        });

        handler.<Player>register("c", "Lock building for all except commander", (args, player) ->{
            AssimilationTeam team = teams.get(player.team());
            if (players.get(player.uuid()).assimRank != 4) {
                player.sendMessage("[accent]You can only call a code red if you are a Commander!");
                return;
            }
            if (team.codered){
                player.sendMessage("[accent]Team is already in code red!");
                return;
            }
            if (!team.coderedAllowed){
                player.sendMessage("[accent]You can only call a code red once every 2 minutes!");
                return;
            }

            team.codered = true;
            team.coderedAllowed = false;

            Time.runTask(60 * 60, () -> {
                team.codered = false;
                player.sendMessage("[accent]Code red over.");
            });


            Time.runTask(60 * 60 * 2, () -> {
                team.coderedAllowed = true;
            });

            player.sendMessage("[accent]Code red called. Everyone but you on your team is build locked for the next minute.");

            for(Player ply : team.players){
                if(players.get(ply.uuid()).connected && ply != player){
                    ply.sendMessage(player.name + "[accent] called a code red. You cannot build for the next minute.");
                }
            }


        });

        /*handler.<Player>register("respawn", "Respawn (for when you cant when you join)", (args, player) ->{
            if(!player.dead){
                player.sendMessage("[accent]Can only use /respawn if you are dead!");
                return;
            }
            player.kill();
            player.beginRespawning((SpawnerTrait) teams.get(player.team()).homeCell.myCore.entity);
        });*/

        handler.<Player>register("xp", "Show your xp", (args, player) ->{
            int xp = (int) playerDataDB.safeGet(player.uuid(),"xp");
            String nextRank = stringHandler.determineRank(xp+15000);
            player.sendMessage("[scarlet]" + xp + "[accent] xp\nReach [scarlet]" + (xp/15000+1)*15000 + "[accent] xp to reach " + nextRank + "[accent] rank.");
        });

        handler.<Player>register("wins", "Show your wins", (args, player) ->{
            int wins = (int) playerDataDB.safeGet(player.uuid(),"monthWins");
            player.sendMessage("[scarlet]" + wins + "[accent] wins.");
        });

        handler.<Player>register("leaderboard", "Displays leaderboard", (args, player) ->{
            player.sendMessage(leaderboard(5));
        });

        handler.<Player>register("multiplier", "Displays unit damage/health multiplier", (args, player) ->{
            player.sendMessage("[accent]Multiplier is [scarlet]" + Math.round(multiplier * 100) / 100f + "[accent]x");
        });

        handler.<Player>register("info", "Display info about the gamemode", (args, player) -> {
            player.sendMessage("[#4d004d]{[purple]AA[#4d004d]}[red]A S S I M I L A T I O N[accent] is essentially FFA (hex pvp) but" +
                    " when you kill someone, they join your team.\n\n" +
                    "Use the [scarlet]/rerank [accent]command to rerank players on your team to:\n" +
                    "[gold]0[accent]: Bot (spawns as an alpha mech, cant build or break, only shoot)\n" +
                    "[gold]1[accent]: Drone (can only break things placed by drones)\n" +
                    "[gold]2[accent]: Private (can only break things placed by drones or privates)\n" +
                    "[gold]3[accent]: Captain (can break everything)\n\n" +
                    "You can use [scarlet]/drank [accent] to set the default rank when someone joins your team.");
        });

        handler.<Player>register("join", "[player/id]", "[sky]Assimilate into another players team (donator only)", (args, player) -> {


            AssimilationTeam thisTeam = teams.get(player.team());

            if (args.length == 0) {
                String s = "[accent]Use [orange]/join [player/id][accent] to join a players team.\n";
                s += "You are able to join the following players:";
                for (Player ply : Groups.player) {
                    if(teams.get(ply.team()) != thisTeam) {
                        s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id + ", Team: " + teams.get(ply.team()).team.name;
                    }
                }
                player.sendMessage(s);
                return;
            }

            if(player.donateLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }

            Player other;
            try {
                other = Groups.player.getByID(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                other = null;
            }

            if (other == null) {
                other = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                if (other == null) {
                    String s = "[accent]No player by name [white]" + args[0] + "[accent] or id [white]" + args[0] + "[accent].\n";
                    s += "You are able to join the following players:";
                    for (Player ply : Groups.player) {
                        if(teams.get(ply.team()) != thisTeam) {
                            s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id + ", Team: " + teams.get(ply.team()).team.name;
                        }
                    }
                    player.sendMessage(s);
                    return;
                }
            }

            if(other.team().id == player.team().id){
                player.sendMessage(other.name + "[accent] is on the same team as you!");
                return;
            }

            if(players.get(player.uuid()).joinsLeft < 1){
                player.sendMessage("You have joined the maximum number of teams this round!");
                return;
            }

            if(players.get(player.uuid()).assimRank == 4){
                assimilate(player.team(), other.team());
            }else{
                teams.get(player.team()).players.remove(player);
                addPlayerTeam(player, teams.get(other.team()));
            }

            players.get(player.uuid()).joinsLeft -= 1;

            player.sendMessage("[accent]Adding you to [white]" + other.name + "[accent]'s team...");


        });

        handler.<Player>register("ev", "[event]", "[sky]Start an event (donator only)", (args, player) -> {

            if(args.length == 0){
                player.sendMessage("[accent]You can call the following events:\n\n" +
                        "[gold]1 [accent]- Spooky\n" +
                        "[gold]2 [accent]- Instant build\n" +
                        "[gold]3 [accent]- Zombies");
                return;
            }
            if(player.donateLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }
            if(players.get(player.uuid()).eventCalls < 1){
                player.sendMessage("[accent]You have run out of event calls!");
                return;
            }

            if(eventActive){
                player.sendMessage("[accent]There is currently an event active!");
                return;
            }

            switch(args[0]){
                case "1":
                    Call.sendMessage(player.name + "[accent] has called a [gold]Spooky[accent] event! It begins in [scarlet]30 [accent]seconds!");
                    AssimilationEvent lightEvent = new LightEvent(60f * 30f, 60f * 300f, this, rules);
                    players.get(player.uuid()).eventCalls -=1;
                    lightEvent.execute();
                    player.sendMessage("[accent]You now have [scarlet]" + players.get(player.uuid()).eventCalls + "[accent] event calls left");
                    break;
                case "2":
                    Call.sendMessage(player.name + "[accent] has called an [gold]Instant build[accent] event! It begins in [scarlet]30 [accent]seconds!");
                    AssimilationEvent buildEvent = new InstantBuildEvent(60f * 30f, 60f * 300f, this, rules);
                    players.get(player.uuid()).eventCalls -=1;
                    buildEvent.execute();
                    player.sendMessage("[accent]You now have [scarlet]" + players.get(player.uuid()).eventCalls + "[accent] event calls left");
                    break;
                case "3":
                    Call.sendMessage(player.name + "[accent] has called a [gold]Zombie[accent] event! It begins in [scarlet]30 [accent]seconds!");
                    AssimilationEvent zombieEvent = new ZombieEvent(60f * 30f, 60f * 120f, this);
                    players.get(player.uuid()).eventCalls -=1;
                    zombieEvent.execute();
                    player.sendMessage("[accent]You now have [scarlet]" + players.get(player.uuid()).eventCalls + "[accent] event calls left");
                    break;
                default:
                    Call.sendMessage("[accent]No such event!");
                    break;

            }
        });

    }

    void initRules(){

        cellSpawn = Schematics.readBase64("bXNjaAB4nE2QywqDMBAAd5NNomA/xUtv/ZoiVqjgA7TS3299TeshTnQcNkouuYoNVd/IpZrntr+/q65rHlcnxes5Tu3Sl+sTKepxasphqbtmmUXkJufl1kVZflsPGRSgCCUoO0gpK1GlrJSVslJWyrqXt032P+x2c0d5bRhvz1kcs3i+8MziKXvKhmd4hmd4AS/gBbyAF/EiXsSLeAkv8dvTfs6NPGRQgCKU6J3l7DzilxRykIcMClCEErQXP30oExg=");

        UnitTypes.alpha.weapons = new Seq<>();
        UnitTypes.beta.weapons = new Seq<>();
        UnitTypes.gamma.weapons = new Seq<>();

        // Modify lancer laser
        Block lancer = Vars.content.blocks().find(new Boolf<Block>() {
            @Override
            public boolean get(Block block) {
                return block.name.equals("lancer");
            }
        });

        BulletType newLancerLaser = AssimilationData.getLLaser();

        ((PowerTurret)(lancer)).shootType = newLancerLaser;

        for(Block b : content.blocks()){
            b.health *= 10;
        }

        rules.canGameOver = false;
        rules.unitDamageMultiplier = 10;
        rules.enemyCoreBuildRadius = (cellRadius-2) * 8;
        rules.loadout = ItemStack.list(Items.copper, 2000, Items.lead, 1000, Items.graphite, 200, Items.metaglass, 200, Items.silicon, 400);
        rules.bannedBlocks.addAll(Blocks.hail, Blocks.ripple, Blocks.phaseWall, Blocks.phaseWallLarge);
        rules.buildSpeedMultiplier = 2;
    }

    void assimilate(Team oldTeam, Team newTeam){
        AssimilationTeam oldAssimilationTeam = teams.get(oldTeam);
        oldAssimilationTeam.alive = false;
        teams.remove(oldTeam);
        if(newTeam == null || !teams.containsKey(newTeam) || teams.get(newTeam) == null){
            Call.sendMessage(oldTeam.name + "[accent]'s team has died to mysterious means... Distributing players evenly");
            for(Player ply : oldAssimilationTeam.players){
                autoBalance(ply);
            }
            return;
        }
        AssimilationTeam newAssimilationTeam = teams.get(newTeam);

        Call.sendMessage("[accent]" + newAssimilationTeam.name + "[accent]'s team has [scarlet]A S S I M I L A T E D [accent]" + oldAssimilationTeam.name + "[accent]'s team!");
        // Add XP to players in the team that did the assimilating
        for(Player ply : newAssimilationTeam.players){
            if(players.get(ply.uuid()).connected) {
                int addXp = 100*(ply.donateLevel+1);
                ply.sendMessage("[accent]+[scarlet]" + addXp + "xp[accent] for assimilating " + oldAssimilationTeam.name + "[accent]'s team!");
                playerDataDB.safePut(ply.uuid(),"xp", (int) playerDataDB.safeGet(ply.uuid(),"xp") + addXp);
            }
        }

        for(Player ply : oldAssimilationTeam.players){
            if(players.get(newAssimilationTeam.commander.uuid()).connected){
                int addXp = 100*(newAssimilationTeam.commander.donateLevel+1);
                newAssimilationTeam.commander.sendMessage("[accent]+[scarlet]"+ addXp+ "xp[accent] for assimilating " + ply.name);
                playerDataDB.safePut(newAssimilationTeam.commander.uuid(),"xp", (int) playerDataDB.safeGet(newAssimilationTeam.commander.uuid(),"xp") + addXp);
            }

            Log.info("Switching uuid: " + ply.uuid() + " to team " + newTeam.name);
            addPlayerTeam(ply, newAssimilationTeam);
        }





        killTiles(oldTeam);
        killUnits(oldTeam);

        if(teams.keySet().size() == 1 && Team.crux.cores().size == 0){
            endgame(teams.get(teams.keySet().toArray()[0]).name);
        }
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
        CustomPlayer cPly = players.get(player.uuid());
        player.team(newTeam.team);
        players.get(player.uuid()).lastTeam = newTeam.team;
        newTeam.addPlayer(player);
        int dRank = newTeam.defaultRank;
        if(cPly.assimRank > dRank){
            cPly.assimRank = dRank;
        }

    }

    void killTiles(Team team){
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile.build != null && tile.team() == team){
                    Time.run(Mathf.random(60f * 6), tile.build::kill);
                }
            }
        }
    }

    void killUnits(Team team){
        for(Unit u : Groups.unit){
            if(u.team == team){
                u.kill();
            }
        }
    }

    String leaderboard(int limit){
        ResultSet rs = playerDataDB.customQuery("select * from player_data order by monthWins desc limit " + limit);
        String s = "[accent]Leaderboard:\n";
        try{
            int i = 0;
            while(rs.next()){
                i ++;
                s += "\n[gold]" + (i) + "[white]:" + rs.getString("latestName") + "[accent]: [gold]" + rs.getString("monthWins") + "[accent] wins";
            }
            rs.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return s;
    }

    void endgame(String winner){
        Call.infoMessage(winner + "[accent]'s team has conquered the planet! Loading the next world...");
        String winPlayer = teams.get(teams.keySet().toArray()[0]).commander.uuid();
        CustomPlayer ply = players.get(winPlayer);
        if(!playerDataDB.entries.containsKey(winPlayer)){
            playerDataDB.loadRow(winPlayer);
            playerConfigDB.loadRow(winPlayer);
        }
        playerDataDB.safePut(winPlayer, "monthWins", (int) playerDataDB.safeGet(winPlayer, "monthWins") + 1);
        playerDataDB.safePut(winPlayer, "allWins", (int) playerDataDB.safeGet(winPlayer, "allWins") + 1);
        playerDataDB.safePut(winPlayer, "xp", (int) playerDataDB.safeGet(winPlayer, "xp") + 500);
        for(Player player: Groups.player){
            if(player.uuid().equals(winPlayer)){
                player.sendMessage("[accent]+[scarlet]500xp[accent] for winning");
            }
        }


        Time.runTask(60f * 10f, () -> {

            for(Player player : Groups.player) {
                Call.connect(player.con, "aamindustry.play.ai", 6567);
            }

            // in case any was missed (give a delay so players all leave)
            Time.runTask(60f * 1, () -> {
                for(Object uuid: playerDataDB.entries.keySet().toArray().clone()){
                    savePlayerData((String) uuid);
                }
            });


            // I shouldn't need this, all players should be gone since I connected them to hub
            // netServer.kickAll(KickReason.serverRestarting);
            Log.info("Game ended successfully.");
            Time.runTask(60f*2, () -> System.exit(2));
        });


    }

    void savePlayerData(String uuid){
        if(!playerDataDB.entries.containsKey(uuid)){
            if(players.containsKey(uuid)){
                Log.info(uuid + " data already saved!");
            }else{
                Log.info(uuid + " does not exist in player object or data!");
            }

            return;
        }
        Log.info("Saving " + uuid + " data...");
        CustomPlayer ply = players.get(uuid);
        playerDataDB.saveRow(uuid);
        playerConfigDB.saveRow(uuid);
    }

    // All long term stuff here:

    void checkExpiration(){
        prefs = Preferences.userRoot().node(this.getClass().getName());


        int prevMonth = prefs.getInt("month", 1);
        int currMonth = Calendar.getInstance().get(Calendar.MONTH);

        if(prevMonth != currMonth){
            rankReset();
            winsReset();
            Log.info("New month, ranks and monthly wins are reset automatically...");
        }
        prefs.putInt("month", currMonth);
    }

    void rankReset(){
        // Reset ranks
        playerDataDB.setColumn("xp", 0);

        for(Object uuid: playerDataDB.entries.keySet().toArray()){
            playerDataDB.safePut((String) uuid,"xp", 0);
        }
    }

    void winsReset(){
        playerDataDB.setColumn("monthWins", 0);

        for(Object uuid: playerDataDB.entries.keySet().toArray()){
            playerDataDB.safePut((String) uuid,"monthWins", 0);
        }
    }
}
