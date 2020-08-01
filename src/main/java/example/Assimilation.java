package example;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.GameState;
import mindustry.entities.type.*;
import mindustry.game.EventType;
import mindustry.game.Rules;
import mindustry.gen.*;
import mindustry.plugin.Plugin;

import static mindustry.Vars.*;

public class Assimilation extends Plugin{

    private final Rules rules = new Rules();

    //register event handlers and create variables in the constructor
    public Assimilation(){

        rules.canGameOver = false;

        Events.on(EventType.BlockDestroyEvent.class, event ->{
            System.out.println(event.tile.entity.lastHit + " team has killed " + event.tile.getTeam() + "!");
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
            ArenaGenerator generator = new ArenaGenerator();
            world.loadGenerator(generator);
            Log.info("Map generated.");

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
}
