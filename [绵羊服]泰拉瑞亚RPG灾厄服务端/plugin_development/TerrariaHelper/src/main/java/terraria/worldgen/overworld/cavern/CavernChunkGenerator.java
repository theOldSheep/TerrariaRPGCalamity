package terraria.worldgen.overworld.cavern;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import terraria.TerrariaHelper;
import terraria.worldgen.overworld.OrePopulator;
import terraria.worldgen.overworld.OverworldBlockGenericPopulator;
import terraria.worldgen.overworld.OverworldCaveGenerator;
import terraria.worldgen.overworld.OverworldChunkGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CavernChunkGenerator extends ChunkGenerator {
    static final int yOffset = -253;
    static CavernChunkGenerator instance = new CavernChunkGenerator();
    ArrayList<BlockPopulator> populators;
    OverworldCaveGenerator caveGen;
    public static CavernChunkGenerator getInstance() {
        return instance;
    }
    public long tweak_terrain = 0, tweak_terrain_time = 0;

    public CavernChunkGenerator() {
        super();
        // init populator
        caveGen = new OverworldCaveGenerator(yOffset, TerrariaHelper.worldSeed, OverworldChunkGenerator.OCTAVES_CAVE);
        populators = new ArrayList<>();
        populators.add(new OverworldBlockGenericPopulator());
        populators.add(new OrePopulator(yOffset));
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        long nanosec = System.nanoTime();
        // setup biome
        OverworldChunkGenerator.tweakBiome(x, z, biome, yOffset);

        Bukkit.broadcastMessage("setup biome: " + (System.nanoTime() - nanosec));
        nanosec = System.nanoTime();

        // init info maps
        int[][] heightMap = new int[16][16];
        double[][] caveMultiMap = new double[16][16];
        OverworldChunkGenerator.generateMaps(x << 4, z << 4, heightMap, caveMultiMap);

        Bukkit.broadcastMessage("setup info maps: " + (System.nanoTime() - nanosec));
        nanosec = System.nanoTime();

        // init terrain
        ChunkData chunk = createChunkData(world);
        OverworldChunkGenerator.initializeTerrain(chunk, x * 16, z * 16, biome, yOffset, heightMap);
        boolean[][][] stoneFlags = OverworldChunkGenerator.setupStoneFlags(x << 4, z << 4, yOffset, heightMap);

        Bukkit.broadcastMessage("init terrain: " + (System.nanoTime() - nanosec));
        nanosec = System.nanoTime();

        // tweak terrain
        caveGen.populate(world, chunk, biome, heightMap, x, z, caveMultiMap);
//        caveGen.populate_no_optimization(chunk, biome, heightMap, x, z, caveMultiMap);

        tweak_terrain += System.nanoTime() - nanosec;
        tweak_terrain_time ++;
        Bukkit.broadcastMessage("tweak terrain (average): " + tweak_terrain / tweak_terrain_time);
        nanosec = System.nanoTime();

        for (int i = 0; i < 16; i ++)
            for (int j = 0; j < 16; j ++)
                OverworldChunkGenerator.generateTopSoil(chunk, stoneFlags, i, heightMap[i][j], j, (x << 4) + i, (z << 4) + j, biome.getBiome(i, j), yOffset);

        Bukkit.broadcastMessage("generate top soil: " + (System.nanoTime() - nanosec));
        nanosec = System.nanoTime();

        return chunk;
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return populators;
    }
}