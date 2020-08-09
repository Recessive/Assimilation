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
    protected List<Player> captains = new ArrayList<>();
    protected List<Player> privates = new ArrayList<>();
    protected List<Player> drones = new ArrayList<>();
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
        privates.add(ply);
    }

    public void rank(CustomPlayer ply, int rank){
        switch(ply.assimRank){
            case 1: drones.remove(ply.player); break;
            case 2: privates.remove(ply.player); break;
            case 3: captains.remove(ply.player); break;
        }
        switch(rank){
            case 1: drones.add(ply.player); break;
            case 2: privates.add(ply.player); break;
            case 3: captains.add(ply.player); break;
        }

        ply.assimRank = rank;
    }
}
