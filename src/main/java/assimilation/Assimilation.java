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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.prefs.Preferences;

import static mindustry.Vars.*;

public class Assimilation extends Plugin{

    private Preferences prefs;

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
    private DBInterface playerConfigDB = new DBInterface("player_config");

    private StringHandler stringHandler = new StringHandler();

    //register event handlers and create variables in the constructor
    public void init(){
        playerDataDB.connect("data/server_data.db");
        playerConfigDB.connect(playerDataDB.conn);

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
                // Add XP to players in the team that did the assimilating
                for(Player ply : newTeam.players){
                    ply.sendMessage("[accent]+[scarlet]100xp[accent] for assimilating " + oldTeam.name + "[accent]'s team!");
                    playerDataDB.entries.get(ply.uuid).put("xp", (int) playerDataDB.entries.get(ply.uuid).get("xp") + 100);
                }

                for(Player ply : oldTeam.players){
                    newTeam.commander.sendMessage("[accent]+[scarlet]100xp[accent] for assimilating " + ply.name);
                    playerDataDB.entries.get(newTeam.commander.uuid).put("xp", (int) playerDataDB.entries.get(newTeam.commander.uuid).get("xp") + 100);

                    Log.info("Switching uuid: " + ply.uuid + " to team " + newTeam.name);
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

                for(Player ply : teams.get(event.tile.entity.lastHit).players){
                    ply.sendMessage("[accent]+[scarlet]10xp[accent] for clearing a crux a cell");
                    playerDataDB.entries.get(ply.uuid).put("xp", (int) playerDataDB.entries.get(ply.uuid).get("xp") + 10);
                }
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

        Events.on(EventType.PlayerConnect.class, event->{
            for(String swear : stringHandler.badNames){
                if(Strings.stripColors(event.player.name.toLowerCase()).contains(swear) && !event.player.uuid.equals("rJ2w2dsR3gQAAAAAfJfvXA==")){
                    event.player.name = event.player.name.replaceAll("(?i)" + swear, "");
                }
            }
        });

        Events.on(EventType.PlayerJoin.class, event ->{
            // Databasing stuff first:
            if(!playerDataDB.hasRow(event.player.uuid)){
                Log.info("New player, adding to tables...");
                playerDataDB.addRow(event.player.uuid);
                playerConfigDB.addRow(event.player.uuid);
            }
            playerDataDB.loadRow(event.player.uuid);
            playerConfigDB.loadRow(event.player.uuid);

            // Determine rank and save name to database
            event.player.name = stringHandler.determineRank((int) playerDataDB.entries.get(event.player.uuid).get("xp")) + " " + event.player.name;
            playerDataDB.entries.get(event.player.uuid).put("latestName", event.player.name);

            CustomPlayer ply;

            if(!players.containsKey(event.player.uuid)){
                ply = new CustomPlayer(event.player, 0, (int) playerDataDB.entries.get(event.player.uuid).get("playtime"));
                players.put(event.player.uuid, ply);
            }else{
                ply = players.get(event.player.uuid);
            }
            Call.setHudTextReliable(event.player.con, "[accent]Play time: [scarlet]" + players.get(event.player.uuid).playTime + "[accent] mins.");
            if(teams.containsKey(players.get(event.player.uuid).lastTeam)){
                event.player.setTeam(players.get(event.player.uuid).player.getTeam());

                return;
            }
            // In the event there are no free cells
            if(freeCells.size() == 0){
                autoBalance(event.player);

                return;
            }



            // Get new team
            teamCount ++;
            event.player.setTeam(Team.all()[teamCount+6]);

            // Create custom team and add it to the teams hash map
            AssimilationTeam cTeam = new AssimilationTeam(event.player, (int) playerConfigDB.entries.get(event.player.uuid).get("defaultRank"));
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

            cell.makeNexus(players.get(event.player.uuid), false);

            event.player.sendMessage(leaderboard(5));

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
                event.player.sendMessage("Only bots can become an Alpha");
            }
        });

        Events.on(EventType.PlayerLeave.class, event ->{
            savePlayerData(event.player.uuid);
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

            int cellLimit = 1000;


            for(Tuple<Integer, Integer> cell : generator.getCells()){
                Cell c = new Cell((int) cell.get(0), (int) cell.get(1), recorder, players);
                c.owner = Team.crux;
                c.makeNexus(null, true);
                cells.add(c);
                freeCells.add(c);
                cellLimit -= 1;
                if(cellLimit <= 0){
                    break;
                }
            }

            prefs = Preferences.userRoot().node(this.getClass().getName());
            int prevMonth = prefs.getInt("month", 1);
            int currMonth = Calendar.getInstance().get(Calendar.MONTH);

            if(prevMonth != currMonth){
                rankReset();
                winsReset();
                Log.info("New month, ranks and monthly wins are reset automatically...");
            }
            prefs.putInt("month", currMonth);
            netServer.openServer();

        });

        handler.register("setplaytime", "<uuid> <playtime>", "Set the play time of a player", args -> {
            int newTime;
            try{
                newTime = Integer.parseInt(args[1]);
            }catch(NumberFormatException e){
                Log.info("Invalid playtime input '" + args[1] + "'");
                return;
            }

            if(!playerDataDB.entries.containsKey(args[0])){
                playerDataDB.loadRow(args[0]);
                playerDataDB.entries.get(args[0]).put("playtime", newTime);
                playerDataDB.saveRow(args[0]);
            }else{
                Player player = players.get(args[0]).player;
                players.get(args[0]).playTime = newTime;
                Call.setHudTextReliable(player.con, "[accent]Play time: [scarlet]" + players.get(player.uuid).playTime + "[accent] mins.");
            }
            Log.info("Set uuid " + args[0] + " to have play time of " + args[1] + " minutes");

        });

        handler.register("setxp", "<uuid> <playtime>", "Set the xp of a player", args -> {
            int newXp;
            try{
                newXp = Integer.parseInt(args[1]);
            }catch(NumberFormatException e){
                Log.info("Invalid xp input '" + args[1] + "'");
                return;
            }

            if(!playerDataDB.entries.containsKey(args[0])){
                playerDataDB.loadRow(args[0]);
                playerDataDB.entries.get(args[0]).put("xp", newXp);
                playerDataDB.saveRow(args[0]);
            }else{
                playerDataDB.entries.get(args[0]).put("xp", newXp);
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


        handler.register("crash", "<name/uuid>", "Crashes the name/uuid", args ->{
            for(Player player : playerGroup.all()){
                if(player.uuid.equals(args[0]) || Strings.stripColors(player.name).equals(args[0])){
                    player.sendMessage(null);
                    Log.info("Done.");
                    return;
                }
            }
            Log.info("Player not found!");
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

        handler.<Player>register("members", "List all the members in your team and their rank", (args, player) -> {
            String commander = "[accent]Commander:";
            String captains = "[accent]Captains:";
            String privates = "[accent]Privates:";
            String drones = "[accent]Drones:";
            String bots = "[accent]Bots:";
            for (Player ply : teams.get(players.get(player.uuid).lastTeam).players) {
                CustomPlayer cPly = players.get(ply.uuid);
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
                player.sendMessage("[accent]This command expects [scarlet]1[accent] argument of a number from [scarlet]0 [accent]to [scarlet]3[accent]:\n [gold]- [scarlet]0[accent]: Bot\n [gold]- [scarlet]1[accent]: Drone\n [gold]- [scarlet]2[accent]: Private\n [gold]- [scarlet]3[accent]: Captain");
                return;
            }
            int dRank;
            try {
                dRank = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("[accent]This command expects [scarlet]1[accent] argument of a number from [scarlet]0 [accent]to [scarlet]3[accent]:\n [gold]- [scarlet]0[accent]: Bot\n [gold]- [scarlet]1[accent]: Drone\n [gold]- [scarlet]2[accent]: Private\n [gold]- [scarlet]3[accent]: Captain");
                return;
            }

            if(dRank < 0 || dRank > 3){
                player.sendMessage("[accent]This command expects [scarlet]1[accent] argument of a number from [scarlet]0 [accent]to [scarlet]3[accent]:\n [gold]- [scarlet]0[accent]: Bot\n [gold]- [scarlet]1[accent]: Drone\n [gold]- [scarlet]2[accent]: Private\n [gold]- [scarlet]3[accent]: Captain");
                return;
            }

            playerConfigDB.entries.get(player.uuid).put("defaultRank", dRank);
            if(players.get(player.uuid).assimRank == 4) teams.get(player.getTeam()).defaultRank = dRank;
            player.sendMessage("[accent]Successfully updated default rank to [scarlet]" + dRank);

        });

        handler.<Player>register("xp", "Show your xp", (args, player) ->{
            player.sendMessage("[scarlet]xp[accent]: " + playerDataDB.entries.get(player.uuid).get("xp"));
        });

        handler.<Player>register("leaderboard", "Displays leaderboard", (args, player) ->{
            player.sendMessage(leaderboard(5));
        });

        handler.<Player>register("kill", "Destroy yourself", (args, player) ->{
            player.kill();
        });

        handler.<Player>register("hub", "Connect to the AA hub server", (args, player) -> {
            Call.onConnect(player.con, "aamindustry.play.ai", 6567);
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

        Mechs.dart.weapon = useless;
        Mechs.delta.weapon = useless;
        Mechs.glaive.weapon = useless;
        Mechs.javelin.weapon = useless;
        Mechs.omega.weapon = useless;
        Mechs.tau.weapon = useless;
        Mechs.trident.weapon = useless;

        rules.canGameOver = false;
        rules.playerDamageMultiplier = 1;
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
        int dRank = newTeam.defaultRank;
        if(cPly.assimRank > dRank){
            cPly.assimRank = dRank;
        }
        player.kill();

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

    String leaderboard(int limit){
        ResultSet rs = playerDataDB.customQuery("select * from player_data order by allWins limit " + limit);
        String s = "[accent]Leaderboard:\n";
        try{
            int i = 0;
            while(rs.next()){
                i ++;
                s += "\n[gold]" + (i) + "[white]:" + rs.getString("latestName") + "[accent]: [gold]" + rs.getString("monthWins") + "[accent] wins";
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return s;
    }

    void endgame(String winner){
        Call.onInfoMessage(winner + "[accent]'s team has conquered the planet! Loading the next world...");
        String winPlayer = teams.get(teams.keySet().toArray()[0]).commander.uuid;
        CustomPlayer ply = players.get(winPlayer);
        if(!playerDataDB.entries.containsKey(winPlayer)){
            playerDataDB.loadRow(winPlayer);
            playerConfigDB.loadRow(winPlayer);
        }
        HashMap<String, Object> entry = playerDataDB.entries.get(winPlayer);
        entry.put("monthWins", (int) entry.get("monthWins") + 1);
        entry.put("allWins", (int) entry.get("allWins") + 1);
        entry.put("xp", (int) entry.get("xp") + 500);
        for(Player player: playerGroup.all()){
            if(player.uuid.equals(winPlayer)){
                player.sendMessage("[accent]+[scarlet]500xp[accent] for winning");
            }
        }


        Time.runTask(60f * 10f, () -> {

            for(Player player : playerGroup.all()) {
                Call.onConnect(player.con, "aamindustry.play.ai", 6567);
            }

            // in case any was missed
            for(Object uuid: playerDataDB.entries.keySet().toArray().clone()){
                savePlayerData((String) uuid);
            }

            // I shouldn't need this, all players should be gone since I connected them to hub
            // netServer.kickAll(KickReason.serverRestarting);
            Log.info("Game ended successfully.");
            Time.runTask(5f, () -> System.exit(2));
        });


    }

    void savePlayerData(String uuid){
        CustomPlayer ply = players.get(uuid);
        playerDataDB.entries.get(uuid).put("playtime", ply.playTime);
        playerDataDB.saveRow(uuid);
        playerConfigDB.saveRow(uuid);
    }

    // All long term stuff here:

    void rankReset(){
        // Reset ranks
        playerDataDB.setColumn("xp", 0);

        for(Object uuid: playerDataDB.entries.keySet().toArray()){
            playerDataDB.entries.get(uuid).put("xp", 0);
        }
    }

    void winsReset(){
        playerDataDB.setColumn("monthWins", 0);

        for(Object uuid: playerDataDB.entries.keySet().toArray()){
            playerDataDB.entries.get(uuid).put("monthWins", 0);
        }
    }
}