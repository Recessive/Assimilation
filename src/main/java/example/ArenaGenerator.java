package example;

import arc.struct.Array;
import arc.struct.StringMap;
import mindustry.content.Blocks;
import mindustry.maps.Map;
import mindustry.maps.filters.GenerateFilter;
import mindustry.maps.filters.OreFilter;
import mindustry.maps.generators.Generator;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.Floor;

import static mindustry.Vars.maps;
import static mindustry.Vars.world;

public class ArenaGenerator extends Generator {

    public static final int size = 601;

    public ArenaGenerator() {
        super(size, size);
    }

    @Override
    public void generate(Tile[][] tiles) {

        GenerateFilter.GenerateInput in = new GenerateFilter.GenerateInput();

        System.out.println(width);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = new Tile(x, y, Blocks.darksand.id, Blocks.air.id, Blocks.air.id);
            }
        }

        defaultOres(tiles);


        world.setMap(new Map(StringMap.of("name", "Arena", "author", "Recessive")));
    }

    public static void defaultOres(Tile[][] tiles){
        GenerateFilter.GenerateInput in = new GenerateFilter.GenerateInput();
        Array<GenerateFilter> ores = new Array<>();
        maps.addDefaultOres(ores);
        ores.each(o -> ((OreFilter) o).threshold -= 0.05f);
        ores.insert(0, new OreFilter() {{
            ore = Blocks.oreScrap;
            scl += 2 / 2.1F;
        }});

        in.floor = (Floor) Blocks.darksand;
        in.block = Blocks.duneRocks ;
        in.width = size;
        in.height = size;

        for (int x = 0; x < tiles.length; x++) {
            for (int y = 0; y < tiles[x].length; y++) {
                in.ore = Blocks.air;
                in.x = x;
                in.y = y;

                if(tiles[x][y].floor().isLiquid) continue;

                for (GenerateFilter f : ores) {
                    f.apply(in);
                }
                tiles[x][y].setOverlay(in.ore);
            }
        }
    }
}
