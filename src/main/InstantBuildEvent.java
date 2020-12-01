package main;

import arc.util.Time;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.type.UnitType;

import java.util.ArrayList;
import java.util.List;

public class InstantBuildEvent extends AssimilationEvent{

    private Rules rules;

    public InstantBuildEvent(float delay, float timeFrame, Assimilation game, Rules rules){
        super(delay, timeFrame, game);
        this.rules = rules;
    }

    @Override
    public void execute() {
        game.eventActive = true;
        Time.runTask(delay, () -> {
            Call.sendMessage("[gold]Instant build [accent]event has begun! It ends in [scarlet]" + timeFrame / 60 + "[accent] seconds!");

            rules.buildSpeedMultiplier = 100;
            Vars.state.rules = rules.copy();
            Call.setRules(rules);

            Time.runTask(timeFrame, () -> {
                game.eventActive = false;
                Call.sendMessage("[gold]Instant build [accent]event is over!");
                rules.buildSpeedMultiplier = 2;
                Vars.state.rules = rules.copy();
                Call.setRules(rules);
            });
        });
    }
}
