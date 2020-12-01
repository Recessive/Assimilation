package main;

import arc.math.Angles;
import arc.math.geom.Bresenham2;
import arc.math.geom.Geometry;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.Log;
import arc.util.Structs;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.maps.Map;
import mindustry.maps.filters.GenerateFilter;
import mindustry.maps.filters.OreFilter;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.environment.Floor;

import java.util.ArrayList;
import java.util.List;

import static mindustry.Vars.maps;
import static mindustry.Vars.world;

public class ArenaGenerator{

    public static final int size = 516;
    private float genNumber;
    private int cellRadius;
    private int spacing;

    public ArenaGenerator(int cellRadius, float genNumber) {
        this.cellRadius = cellRadius;
        this.genNumber = genNumber;
        spacing = 78;
    }

    public void generate(Tiles tiles) {

        GenerateFilter.GenerateInput in = new GenerateFilter.GenerateInput();

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                tiles.set(x, y, new Tile(x, y, Blocks.darksand.id, Blocks.air.id, Blocks.duneWall.id));
            }
        }

        List<Tuple<Integer, Integer>> cells = getCells();




        for(Tuple<Integer, Integer> cell : cells){
            int x = (int) cell.get(0);
            int y = (int) cell.get(1);
            Geometry.circle(x, y, size, size, cellRadius*2, (cx, cy) ->{
                if(Math.sqrt(Math.pow(cx - x, 2) + Math.pow(cy - y, 2)) < cellRadius-4){
                    Tile tile = tiles.get(cx, cy);
                    tile.setBlock(Blocks.air);
                    if(Math.abs(cx - x) < 3 && Math.abs(cy - y) < 3){
                        tile.setFloor((Floor) Blocks.sand);
                    }
                }


            });

            if(genNumber < 0.25){
                continue;
            }

            int gapWidth = 3;
            if(genNumber < 0.5 && genNumber >= 0.25){
                gapWidth = 12;
            }
            int finalGapWidth = gapWidth;
            Angles.circle(3, 360f / 3 / 2f - 90, f -> {
                Tmp.v1.trnsExact(f, spacing + 12);
                if(Structs.inBounds(x + (int)Tmp.v1.x, y + (int)Tmp.v1.y, size, size)){
                    Tmp.v1.trnsExact(f, spacing / 2 + 7);
                    Bresenham2.line(x, y, x + (int)Tmp.v1.x, y + (int)Tmp.v1.y, (cx, cy) -> {
                        Geometry.circle(cx, cy, size, size, finalGapWidth, (c2x, c2y) -> {
                            if(!(Math.abs(c2x - x) < (3) && Math.abs(c2y - y) < (3))){
                                tiles.get(c2x, c2y).setBlock(Blocks.air);
                            }
                        });
                    });
                }
            });

        }



        Vars.state.map = new Map(StringMap.of("name", "Arena", "author", "Recessive"));
    }

    public static void defaultOres(Tiles tiles){
        GenerateFilter.GenerateInput in = new GenerateFilter.GenerateInput();
        Seq<GenerateFilter> ores = new Seq<>();
        maps.addDefaultOres(ores);
        ores.each(o -> ((OreFilter) o).threshold -= 0.05f);
        ores.insert(0, new OreFilter() {{
            ore = Blocks.oreScrap;
            scl += 2 / 2.1F;
        }});

        in.floor = (Floor) Blocks.darksand;
        in.block = Blocks.duneWall ;
        in.width = in.height = size;

        for (int x = 0; x < tiles.width; x++) {
            for (int y = 0; y < tiles.height; y++) {
                in.overlay = Blocks.air;
                in.x = x;
                in.y = y;
                if(tiles.get(x,y).floor().isLiquid) continue;

                for (GenerateFilter f : ores) {
                    f.apply(in);
                }
                tiles.get(x,y).setOverlay(in.overlay);
            }
        }
    }

    public List<Tuple<Integer, Integer>> getCells(){
        List<Tuple<Integer, Integer>> cells = new ArrayList<>();

        double h = Math.sqrt(3) * spacing/2;
        //base horizontal spacing=1.5w
        //offset = 3/4w
        for(int x = 0; x < size / spacing - 2; x++){
            for(int y = 0; y < size / (h/2) - 2; y++){
                int cx = (int)(x * spacing*1.5 + (y%2)* spacing*3.0/4) + spacing/2;
                int cy = (int)(y * h / 2) + spacing/2;
                cells.add(new Tuple<Integer, Integer>(cx, cy));
            }
        }
        return cells;
    }

}
