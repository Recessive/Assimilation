package example;

import mindustry.entities.type.Player;
import mindustry.game.Team;

public class CustomPlayer{

    public int assimRank;
    public Team lastTeam;
    protected Player player;


    public CustomPlayer(Player player, int assimRank){
        this.player = player;
        this.assimRank = assimRank;
        this.lastTeam = player.getTeam();
    }

}
