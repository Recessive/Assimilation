package assimilation;

import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Call;

public class ZombieEvent extends AssimilationEvent{

    private Assimilation game;

    public ZombieEvent(float delay, float timeFrame, Assimilation game) {
        super(delay, timeFrame);
        this.game = game;
    }

    @Override
    public void execute() {
        Time.runTask(delay, () -> {
            Call.sendMessage("[gold]Zombie [accent]event has begun! It ends in [scarlet]" + timeFrame / 60 + "[accent] seconds!");
            game.zombieActive = true;

            Time.runTask(timeFrame, () -> {
                Call.sendMessage("[gold]Zombie [accent]event is over!");
                game.zombieActive = false;
            });
        });
    }
}
