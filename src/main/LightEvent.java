package main;

import arc.graphics.Color;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Call;

import java.util.HashMap;

public class LightEvent extends AssimilationEvent{

    private Rules rules;

    public LightEvent(float delay, float timeFrame, Assimilation game, Rules rules){
        super(delay, timeFrame, game);
        this.rules = rules;
    }

    @Override
    public void execute() {
        game.eventActive = true;
        Time.runTask(delay, () -> {
            Call.sendMessage("[gold]Spooky [accent]event has begun! It ends in [scarlet]" + timeFrame / 60 + "[accent] seconds!");

            rules.lighting = true;
            rules.ambientLight = new Color(0, 0, 0, 0.75f);
            Vars.state.rules = rules.copy();
            Call.setRules(rules);

            Time.runTask(timeFrame, () -> {
                game.eventActive = false;
                rules.lighting = false;
                Call.sendMessage("[gold]Spooky [accent]event is over!");
                Vars.state.rules = rules.copy();
                Call.setRules(rules);
            });
        });
    }
}
