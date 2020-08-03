package example;

import arc.Events;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.type.ItemStack;
import mindustry.world.Tile;

import java.util.HashMap;

import static mindustry.Vars.state;
import static mindustry.Vars.world;

public class Cell {
    protected int x;
    protected int y;
    protected Team owner = null;
    protected HashMap<Team, Integer> captureProgress = new HashMap<>();
    protected HashMap<Tuple<Float, Float>, Integer> builds = new HashMap<>();

    public Cell(int x, int y){
        this.x = x;
        this.y = y;
    }

    public void makeNexus(){
        clearSpace(Blocks.coreNucleus.size);
        Tile coreTile = world.tile(x, y);
        coreTile.setNet(Blocks.coreNucleus, owner, 0);
        for(ItemStack stack : state.rules.loadout){
            Call.transferItemTo(stack.item, stack.amount, coreTile.drawx(), coreTile.drawy(), coreTile);
        }
    }

    public void makeShard(){
        clearSpace(Blocks.coreShard.size);
        Tile coreTile = world.tile(x, y);
        coreTile.setNet(Blocks.coreShard, owner, 0);
    }

    public void clearSpace(int size){
        for(int xi = -size/2; xi < size/2; xi++){
            for(int yi = -size/2; yi < size/2; yi ++){
                world.tile(x+xi, y+yi).link().removeNet();
            }
        }
    }

    public boolean contains(short x, short y){
        return Math.sqrt(Math.pow(this.x - x, 2) + Math.pow(this.y - y, 2)) < Assimilation.cellRadius - 3;
    }

    public void updateCapture(Tile tile, boolean breaking){
        Team tileTeam = tile.getTeam();
        int progress = 0;
        if(captureProgress.containsKey(tile.getTeam())){
            progress = captureProgress.get(tile.getTeam());
        }
        Tuple<Float, Float> pos = new Tuple<>(tile.worldx(), tile.worldy());

        if(breaking){
            if(builds.containsKey(pos)) progress -= builds.remove(pos);
            else return;
        }else{
            progress += tile.block().health;
            builds.put(pos, tile.block().health);
        }
        if(progress > Assimilation.cellRequirement && owner == null){
            owner = tile.getTeam();
            captureProgress.clear();
            Events.fire(new CellCaptureEvent(this));
        }else{
            captureProgress.put(tileTeam, progress);
        }

    }

    public void clearCell(){
        for(Tuple key : builds.keySet()){
            Float x = (Float) key.get(0);
            Float y = (Float) key.get(1);
            Tile tile = world.tileWorld(x, y);
            if(tile.entity != null) Time.run(Mathf.random(60f), tile.entity::kill);
        }
        owner = null;
        builds.clear();
        captureProgress.clear();
    }

    public static class CellCaptureEvent{
        public final Cell cell;

        public CellCaptureEvent(Cell cell){
            this.cell = cell;
        }
    }
}
