package example;

import arc.graphics.Color;
import mindustry.entities.type.Player;
import mindustry.game.Team;

import java.util.ArrayList;
import java.util.List;

public class AssimilationTeam{
    protected Team team;
    protected String name;
    protected Player commander;
    public Boolean alive = true;
    protected List<Player> players = new ArrayList<>();
    public List<Cell> capturedCells = new ArrayList<>();

    public AssimilationTeam(Player player) {
        this.team = player.getTeam();
        this.name = player.name;
        this.commander = player;
    }

    public void addPlayer(Player ply){
        players.add(ply);
    }
}
