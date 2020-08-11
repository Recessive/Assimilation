package assimilation;

import mindustry.entities.type.Player;
import mindustry.game.Team;

import java.util.ArrayList;
import java.util.List;

public class AssimilationTeam{
    protected Team team;
    protected String name;
    protected Player commander;
    protected int defaultRank;
    public Boolean alive = true;
    protected List<Player> players = new ArrayList<>();
    public List<Cell> capturedCells = new ArrayList<>();
    public Cell homeCell;

    public AssimilationTeam(Player player, int defaultRank) {
        this.team = player.getTeam();
        this.name = player.name;
        this.commander = player;
        this.defaultRank = defaultRank;
    }

    public void addPlayer(Player ply){
        players.add(ply);
    }

    public void rank(CustomPlayer ply, int rank){
        ply.assimRank = rank;
    }
}
