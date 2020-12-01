package main;

import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LichEvent extends AssimilationEvent{

    private HashMap<Team, AssimilationTeam> teams;

    public LichEvent(float delay, float timeFrame, Assimilation game, HashMap<Team, AssimilationTeam> teams) {
        super(delay, timeFrame, game);
        this.teams = teams;
    }

    @Override
    public void execute() {
        game.eventActive = true;
        Time.runTask(delay, () -> {
            Call.sendMessage("[gold]Zenith [accent]event has begun! It ends in [scarlet]" + timeFrame/60 + "[accent] seconds!");
            UnitType lich = UnitTypes.zenith;
            List<Unit> units = new ArrayList<>();
            for(Team t : teams.keySet()){
                Cell spawnCell = teams.get(t).homeCell;
                Unit baseUnit = lich.create(t);
                baseUnit.set(spawnCell.x*8, spawnCell.y*8);
                baseUnit.add();
                units.add(baseUnit);
            }

            Time.runTask(timeFrame, () -> {
                game.eventActive = false;
                Call.sendMessage("[gold]Zenith [accent]event is over!");
                for(Unit unit : units){
                    unit.kill();
                }
            });
        });

    }
}
