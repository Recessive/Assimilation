package assimilation;

import mindustry.entities.type.Player;
import mindustry.game.Team;

public class CustomPlayer{

    public int assimRank;
    public Team lastTeam;
    protected Player player;
    public boolean connected;
    public int eventCalls = 0;
    public int joinsLeft = 1;


    public CustomPlayer(Player player, int assimRank){
        this.player = player;
        this.assimRank = assimRank;
        this.lastTeam = player.getTeam();
        this.assimRank = 4;
        this.connected = true;
    }

}
