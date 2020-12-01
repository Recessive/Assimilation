package main;

import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Call;

public class ZombieEvent extends AssimilationEvent{

    public ZombieEvent(float delay, float timeFrame, Assimilation game) {
        super(delay, timeFrame, game);
    }

    @Override
    public void execute() {
        game.eventActive = true;
        Time.runTask(delay, () -> {
            Call.sendMessage("[gold]Zombie [accent]event has begun! It ends in [scarlet]" + timeFrame / 60 + "[accent] seconds!");
            game.zombieActive = true;

            Time.runTask(timeFrame, () -> {
                game.eventActive = false;
                Call.sendMessage("[gold]Zombie [accent]event is over!");
                game.zombieActive = false;
            });
        });
    }
}
