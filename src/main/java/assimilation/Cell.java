package assimilation;

import arc.Events;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.type.ItemStack;
import mindustry.world.Pos;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.HashMap;

import static mindustry.Vars.state;
import static mindustry.Vars.world;

public class Cell {
    protected int x;
    protected int y;
    public BuildRecorder recorder;
    protected Team owner = null;
    protected Tile myCore;
    public HashMap<String, CustomPlayer> players;
    protected HashMap<Team, Integer> captureProgress = new HashMap<>();
    protected HashMap<Tuple<Float, Float>, Integer> builds = new HashMap<>();

    private Schematic cellSpawn = Schematics.readBase64("bXNjaAB4nE2QywqDMBAAd5NNomA/xUtv/ZoiVqjgA7TS3299TeshTnQcNkouuYoNVd/IpZrntr+/q65rHlcnxes5Tu3Sl+sTKepxasphqbtmmUXkJufl1kVZflsPGRSgCCUoO0gpK1GlrJSVslJWyrqXt032P+x2c0d5bRhvz1kcs3i+8MziKXvKhmd4hmd4AS/gBbyAF/EiXsSLeAkv8dvTfs6NPGRQgCKU6J3l7DzilxRykIcMClCEErQXP30oExg=");
    private Schematic spawn = Schematics.readBase64("bXNjaAB4nD2SYW7DIAyFbRJCIN2PHiRSr7ITTChDUyVKqjTptNsPB3iNFD4F+z37qTTRB1Of/COQieEd4tdtpGlZn8+wzb8+Rrru992n+/GYlzW9w9+60fW1Rr/NT59CnDP9BLos6xbmdCwxHC/q/LaQeS1+38NG45Hi6r8zDY+Q5CT6pPbr5MUgBSovuemp1WjQADKgEaIWog40gS6VuFar8xHg/K25MdwYbgw3rm7S6ergXJTbFmedgp6CnoKegp7C9AoqHU5dO4TbbY+zL5uwznQ685BJMmBlMp3zdVLXMuihopGZhoou+Sn51jo0OtokfabSYTI13+GcmlluW++AXoMdx5q4UMvPoK5lISmfeyohDRpABjRWlREqFioWKhYqFioWKhYqFioOCbm6r5AClT8wZ2oeDh4OHg4eDh6uTNgJOdAEKhP8A1h6KiA=");

    public Cell(int x, int y, BuildRecorder recorder, HashMap<String, CustomPlayer> players, Schematic cellSpawn){
        this.x = x;
        this.y = y;
        this.recorder = recorder;
        this.players = players;
        this.cellSpawn = cellSpawn;
    }

    public void makeNexus(CustomPlayer ply, boolean initSpawn){
        if(initSpawn){
            placeSchem(x, y, cellSpawn, true, ply);
        }else{
            placeSchem(x, y, spawn, true, ply);
        }


        /*clearSpace(Blocks.coreNucleus.size);
        Tile coreTile = world.tile(x, y);
        coreTile.setNet(Blocks.coreNucleus, owner, 0);
        for(ItemStack stack : state.rules.loadout){
            Call.transferItemTo(stack.item, stack.amount, coreTile.drawx(), coreTile.drawy(), coreTile);
        }*/
    }

    public void makeShard(){
        clearSpace(Blocks.coreShard.size);
        Tile coreTile = world.tile(x, y);
        coreTile.link().removeNet();
        coreTile.setNet(Blocks.coreShard, owner, 0);
        myCore = coreTile;
    }

    public void clearSpace(int size){
        for(int xi = -size/2; xi < size/2+1; xi++){
            for(int yi = -size/2; yi < size/2+1; yi ++){
                world.tile(x+xi, y+yi).link().removeNet();
            }
        }
    }

    public boolean contains(short x, short y){
        return Math.sqrt(Math.pow(this.x - x, 2) + Math.pow(this.y - y, 2)) < Assimilation.cellRadius - 3;
    }

    public void updateCapture(Tile tile, boolean breaking, Player ply){
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
            int total = 0;
            for(ItemStack stack : tile.block().requirements){
                total += stack.amount * stack.item.cost;
            }
            progress += total;
            builds.put(pos, total);
        }

        if(progress > Assimilation.cellRequirement && owner == null){
            if(ply != null && ply.getTeam() != owner) {
                Call.setHudText(ply.con, "[accent]Play time: [scarlet]" + players.get(ply.uuid).playTime + "[accent] mins.\n       [gold]Captured!");
            }
            owner = tile.getTeam();
            captureProgress.clear();
            Events.fire(new CellCaptureEvent(this));
        }else{
            float percentage = ((float) progress / (float) Assimilation.cellRequirement)*100;
            if(ply != null && ply.getTeam() != owner) Call.setHudText(ply.con,"[accent]Play time: [scarlet]" + players.get(ply.uuid).playTime + "[accent] mins.\n  [scarlet]" + Math.round(percentage * 100.0) / 100.0 + "% [accent]captured");;

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

    public void purgeCell(){
        // Don't use
        int newX;
        int newY;
        for(int xi = -Assimilation.cellRadius; xi < x + Assimilation.cellRadius; xi ++){
            for(int yi = -Assimilation.cellRadius; yi < y + Assimilation.cellRadius; yi ++){
                newX = x + xi;
                newY = y + yi;
                if(Math.sqrt(Math.pow(x - newX, 2) + Math.pow(y - newY, 2)) < Assimilation.cellRadius-4){
                    world.tile(newX, newY).removeNet();
                }
            }
        }
    }

    void placeSchem(int x, int y, Schematic schem, boolean addItem, CustomPlayer ply){
        final Schematic.Stile[] coreTile = {schem.tiles.find(s -> s.block instanceof CoreBlock)};
        if(coreTile[0] == null) throw new IllegalArgumentException("Schematic has no core tile. Exiting.");
        int ox = x - coreTile[0].x, oy = y - coreTile[0].y;
        schem.tiles.each(st -> {
            Tile tile = world.tile(st.x + ox,st.y + oy);
            if(tile == null) return;

            if(tile.link().block() != Blocks.air){
                tile.link().removeNet();
            }

            tile.setNet(st.block, owner, st.rotation);

            Tuple<Float, Float> pos = new Tuple<>(tile.worldx(), tile.worldy());
            int total = 0;
            for(ItemStack stack : tile.block().requirements){
                total += stack.amount * stack.item.cost;
            }
            builds.put(pos, total);

            if(ply != null){
                if(tile.block() instanceof CoreBlock){
                   myCore = tile;
                }else{
                    recorder.addBuild(tile.x, tile.y, ply, tile.block());
                }
            }

            if(st.block.posConfig){
                tile.configureAny(Pos.get(tile.x - st.x + Pos.x(st.config), tile.y - st.y + Pos.y(st.config)));
            }else{
                tile.configureAny(st.config);
            }
            if(tile.block() instanceof CoreBlock && addItem){
                for(ItemStack stack : state.rules.loadout){
                    Call.transferItemTo(stack.item, stack.amount, tile.drawx(), tile.drawy(), tile);
                }
            }
        });
    }

    public static class CellCaptureEvent{
        public final Cell cell;

        public CellCaptureEvent(Cell cell){
            this.cell = cell;
        }
    }
}
