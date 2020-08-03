package example;

import mindustry.world.Block;
import mindustry.world.Tile;

import java.util.HashMap;

public class BuildRecorder {

    protected HashMap<Tuple<Short, Short>, Tuple<CustomPlayer, Block>> placed = new HashMap<>();

    public void addBuild(Short x, Short y, CustomPlayer ply, Block block){
        Tuple<Short, Short> point = new Tuple<>(x, y);
        Tuple<CustomPlayer, Block> build = new Tuple<>(ply, block);

        placed.put(point, build);
    }

    public Tuple<CustomPlayer, Block> getBuild(Short x, Short y){
        Tuple<Short, Short> point = new Tuple<>(x, y);
        return placed.getOrDefault(point, null);
    }

    public void removeBuild(Short x, Short y){
        Tuple<Short, Short> point = new Tuple<>(x, y);
        if(placed.containsKey(point)){
            placed.remove(point);
        }
    }

}
