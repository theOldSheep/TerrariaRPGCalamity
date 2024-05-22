package terraria.worldgen.overworld.cavern;

import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.overworld.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CavernChunkGenerator extends ChunkGenerator {
    // overworld bedrock covers up y=1, skip that block; otherwise abyss will have an empty layer.
    static final int yOffset = -WorldHelper.CAVERN_Y_BELOW_BEDROCK;
    static CavernChunkGenerator instance = new CavernChunkGenerator();
    ArrayList<BlockPopulator> populators;
    OverworldCaveGenerator CAVE_GENERATOR_CAVERN;
    public static CavernChunkGenerator getInstance() {
        return instance;
    }

    public CavernChunkGenerator() {
        super();
        // init populator
        CAVE_GENERATOR_CAVERN = new OverworldCaveGenerator(yOffset, TerrariaHelper.WORLD_SEED, OverworldChunkGenerator.OCTAVES_CAVE);
        populators = new ArrayList<>();
        populators.add(new OverworldBlockGenericPopulator());
        populators.add(new OrePopulator(yOffset));
        populators.add(new StructurePopulator(false));
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        // setup biome
        OverworldChunkGenerator.tweakBiome(x, z, biome, yOffset);
        // init info maps
        int[][] heightMap = new int[16][16];
        double[][] caveMultiMap = new double[16][16];
        OverworldChunkGenerator.generateMaps(x << 4, z << 4, heightMap, caveMultiMap, CAVE_GENERATOR_CAVERN);
        // init terrain
        ChunkData chunk = createChunkData(world);
        OverworldChunkGenerator.initializeTerrain(chunk, x * 16, z * 16, biome, yOffset, heightMap);
        boolean[][][] stoneFlags = OverworldChunkGenerator.setupStoneFlags(x << 4, z << 4, yOffset, heightMap);
        // tweak terrain
        CAVE_GENERATOR_CAVERN.populate(world, chunk, biome, heightMap, x, z, caveMultiMap);
//        CAVE_GENERATOR_CAVERN.populate_no_optimization(chunk, biome, heightMap, x, z, caveMultiMap);
        for (int i = 0; i < 16; i ++)
            for (int j = 0; j < 16; j ++)
                OverworldChunkGenerator.generateTopSoil(chunk, stoneFlags, i, heightMap[i][j], j, (x << 4) + i, (z << 4) + j, biome.getBiome(i, j), yOffset);
        return chunk;
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return populators;
    }
}