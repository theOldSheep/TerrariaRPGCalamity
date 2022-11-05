package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.noise.PerlinOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.worldgen.Interpolate;
import terraria.worldgen.Interpolate.InterpolatePoint;

import java.util.*;

public class OverworldChunkGenerator extends ChunkGenerator {
    static long seed = TerrariaHelper.worldSeed;
    public static int OCTAVES = 6,
            NEARBY_BIOME_SAMPLE_RADIUS, NEARBY_BIOME_SAMPLE_STEPSIZE,
            LAND_HEIGHT, RIVER_DEPTH, LAKE_DEPTH, PLATEAU_HEIGHT, SEA_LEVEL, LAVA_LEVEL;
    static int yOffset_overworld = 0;
    static double FREQUENCY;
    public static PerlinOctaveGenerator terrainGenerator, terrainGeneratorTwo, terrainDetailGenerator,
                                        riverGenerator, lakeGenerator,
                                        stoneVeinGenerator;
    public static Interpolate astralInfectionHeightProvider, genericHeightProvider,
                                riverRatioProvider, lakeRatioProvider;
    static OverworldChunkGenerator instance = new OverworldChunkGenerator();
    static List<BlockPopulator> populators;
    static OverworldCaveGenerator caveGen;
    private OverworldChunkGenerator() {
        super();
        // terrain noise functions
        Random rdm = new Random(seed);
        terrainGenerator = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES);
        terrainGenerator.setScale(0.0005);
        terrainGeneratorTwo = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES);
        terrainGeneratorTwo.setScale(0.015);
        terrainDetailGenerator = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES);
        terrainDetailGenerator.setScale(0.025);
        riverGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        riverGenerator.setScale(0.0005);
        lakeGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        lakeGenerator.setScale(0.005);
        stoneVeinGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        stoneVeinGenerator.setScale(0.05);
        // constants
        seed = TerrariaHelper.worldSeed;
        FREQUENCY = 0.05221649073;
        NEARBY_BIOME_SAMPLE_RADIUS = 25;
        NEARBY_BIOME_SAMPLE_STEPSIZE = 1;
        SEA_LEVEL = 90;
        RIVER_DEPTH = 25;
        LAKE_DEPTH = 30;
        PLATEAU_HEIGHT = 50;
        LAND_HEIGHT = 100;
        LAVA_LEVEL = -150;
        // interpolates
        astralInfectionHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.5 , LAND_HEIGHT + PLATEAU_HEIGHT),
                InterpolatePoint.create(-0.45, LAND_HEIGHT),
                InterpolatePoint.create( 0.45, LAND_HEIGHT),
                InterpolatePoint.create( 0.5 , LAND_HEIGHT + PLATEAU_HEIGHT)
        }, "astral_infection_heightmap");
        genericHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.85  , LAND_HEIGHT + PLATEAU_HEIGHT),
                InterpolatePoint.create(-0.7   , LAND_HEIGHT),
                InterpolatePoint.create(-0.6   , LAND_HEIGHT + 50),
                InterpolatePoint.create(-0.5   , LAND_HEIGHT + 10),
                InterpolatePoint.create(-0.4   , LAND_HEIGHT + 100),
                InterpolatePoint.create(-0.3   , LAND_HEIGHT),
                InterpolatePoint.create(0.15   , LAND_HEIGHT),
                InterpolatePoint.create(0.3    , LAND_HEIGHT + 15),
                InterpolatePoint.create(0.4    , LAND_HEIGHT + 60),
                InterpolatePoint.create(0.5    , LAND_HEIGHT + 25),
                InterpolatePoint.create(0.6    , LAND_HEIGHT + 40),
                InterpolatePoint.create(0.7    , LAND_HEIGHT + 10),
                InterpolatePoint.create(0.8    , LAND_HEIGHT),
                InterpolatePoint.create(0.85   , LAND_HEIGHT + PLATEAU_HEIGHT)
        }, "generic_heightmap");
        riverRatioProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.05  , 0),
                InterpolatePoint.create(0      , 1),
                InterpolatePoint.create(0.05   , 0),
        }, "river_ratio_map");
        lakeRatioProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.95  , 1),
                InterpolatePoint.create(-0.9   , 0.8),
                InterpolatePoint.create(-0.75   , 0),
        }, "lake_ratio_map");
        // block populators
        caveGen = new OverworldCaveGenerator(yOffset_overworld, seed, OCTAVES);
        populators = new ArrayList<>();
        populators.add(new OverworldBlockGenericPopulator());
        populators.add(new OrePopulator(yOffset_overworld));
        populators.add(new TreePopulator());
    }
    static double getTerrainHeight(Biome currBiome, double noise, double noiseTwo, double riverNoise, double lakeNoise) {
        double result;
        switch (currBiome) {
            case FROZEN_OCEAN:              // sulphurous ocean
                return 0;
            case BEACHES:                   // beach
            case COLD_BEACH:                // sulphurous beach
                return LAND_HEIGHT;
            case OCEAN:                     // ocean
                return SEA_LEVEL - 30;
            case MESA:                      // astral infection
                result = astralInfectionHeightProvider.getY(noise);
                return LAND_HEIGHT + (result - LAND_HEIGHT) * noiseTwo;
            case JUNGLE:                    // jungle
                return SEA_LEVEL;
            default:
                double heightOffset = genericHeightProvider.getY(noise) - LAND_HEIGHT;
                double riverRatio = riverRatioProvider.getY(riverNoise),
                        lakeRatio = lakeRatioProvider.getY(lakeNoise);
                double waterDepthMulti = currBiome == Biome.DESERT ? 0.4 : 1;
                heightOffset = Math.max(0, 1 - riverRatio) * heightOffset - RIVER_DEPTH * riverRatio * waterDepthMulti;
                heightOffset = Math.max(0, 1 - lakeRatio) * heightOffset - LAKE_DEPTH * lakeRatio * waterDepthMulti;
                // we can then manipulate the height offset
                if (heightOffset > 0) heightOffset *= noiseTwo; // rivers won't be too shallow
                result = LAND_HEIGHT + heightOffset;
                return result;
        }
    }
    public static void tweakBiome(int x, int z, BiomeGrid biome, int yOffset) {
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 16; j++) {
                int blockX = x * 16 + i, blockZ = z * 16 + j;
                Biome biomeToSet = OverworldBiomeGenerator.getBiome(seed, blockX, blockZ);
                if (yOffset >= 0)
                    biome.setBiome(i, j, biomeToSet);
                else
                    biome.setBiome(i, j, OverworldBiomeGenerator.getUndergroundEquivalent(biomeToSet));
            }
    }
    public static OverworldChunkGenerator getInstance() {
        return instance;
    }
    // helper functions
    // chunk block material details
    public static void generateTopSoil(ChunkData chunk, int i, int height, int j, int blockX, int blockZ, Biome biome, int yOffset) {
        // although it is named as such, this actually generates stone layers too.
        double topSoilThicknessRandomizer = stoneVeinGenerator.noise(blockX, blockZ, 0.5, 0.5, false);
        double topSoilThickness;
            Material matTopSoil, matSoil, matStone;
            // setup biome block info
            switch (biome) {
                case DEEP_OCEAN: // abyss
                    matTopSoil = Material.SAND;
                    matSoil = Material.SAND;
                    matStone = Material.STAINED_CLAY;
                    break;
                case TAIGA_COLD: // tundra
                case MUTATED_TAIGA_COLD: // tundra
                    matTopSoil = Material.SNOW_BLOCK;
                    matSoil = Material.SNOW_BLOCK;
                    matStone = Material.PACKED_ICE;
                    break;
                case DESERT: // desert
                case MUTATED_DESERT: // desert
                case OCEAN: // ocean
                case BEACHES: // beach
                case FROZEN_OCEAN: // sulphurous ocean
                case COLD_BEACH: // sulphurous beach
                    matTopSoil = Material.SAND;
                    matSoil = Material.SAND;
                    matStone = Material.SANDSTONE;
                    break;
                case MUSHROOM_ISLAND: // corruption
                case MUSHROOM_ISLAND_SHORE: // corruption
                    matTopSoil = Material.MYCEL;
                    matSoil = Material.DIRT;
                    matStone = Material.STAINED_CLAY;
                    break;
                case ICE_FLATS: // hallow
                case MUTATED_ICE_FLATS: // hallow
                case MESA: // astral infection
                case MUTATED_MESA: // astral infection
                    matTopSoil = Material.DIRT;
                    matSoil = Material.DIRT;
                    matStone = Material.STAINED_CLAY;
                    break;
                case JUNGLE: // astral infection
                case MUTATED_JUNGLE: // astral infection
                    matTopSoil = Material.GRASS;
                    matSoil = Material.DIRT;
                    matStone = Material.DIRT;
                    break;
                default: // forest
                    matTopSoil = Material.GRASS;
                    matSoil = Material.DIRT;
                    matStone = Material.STONE;
            }
            double soilLayerHeight = 50 + topSoilThicknessRandomizer * 10;
            // setup soil/stone layers
            for (int y = Math.min(height - yOffset, 254); y > 0; y--) {
                if (! chunk.getType(i, y, j).isSolid()) continue;
                Material matToSet;
                int effectualY = y + yOffset;
                boolean isStoneVein = stoneVeinGenerator.noise(blockX, effectualY, blockZ, 0.5, 0.5, false) > 0.5;
                if (effectualY > soilLayerHeight) {
                    if (isStoneVein)
                        matToSet = matStone;
                    else
                        matToSet = matSoil;
                } else {
                    if (isStoneVein)
                        matToSet = matSoil;
                    else
                        matToSet = matStone;
                }
                if (matToSet == matSoil) {
                    // the block below is air and this block has gravity
                    if (chunk.getType(i, y - 1, j) == Material.AIR && matToSet.hasGravity())
                        matToSet = matStone;
                    // the block above is air then set a grass instead of dirt
                    else if (chunk.getType(i, y + 1, j) == Material.AIR)
                        matToSet = matTopSoil;
                }
                chunk.setBlock(i, y, j, matToSet);
            }

        if (yOffset == 0 && height - 1 < SEA_LEVEL) {
            // for surface only
            for (int y = SEA_LEVEL; y > 0; y--) {
                if (chunk.getType(i, y, j) == Material.AIR) {
                    chunk.setBlock(i, y, j, Material.WATER);
                } else break;
            }
        }
    }
    // init terrain (rough + detail)
    public static int[][] initializeTerrain(ChunkData chunk, int blockXStart, int blockZStart, BiomeGrid biome, int yOffset) {
        // this function creates the raw terrain of the chunk, consisting of only air, water or stone.
        int[][] heightMap = new int[16][16];
        double height;
        double biomesSampled = (1 + NEARBY_BIOME_SAMPLE_RADIUS * 2) * (1 + NEARBY_BIOME_SAMPLE_RADIUS * 2);
        int currX, currZ;

        HashMap<Biome, Integer> nearbyBiomeMap = new HashMap<>();
        for (int sampleOffsetX = NEARBY_BIOME_SAMPLE_RADIUS * -1; sampleOffsetX <= NEARBY_BIOME_SAMPLE_RADIUS; sampleOffsetX++) {
            int currSampleX = blockXStart + sampleOffsetX * NEARBY_BIOME_SAMPLE_STEPSIZE;
            for (int sampleOffsetZ = NEARBY_BIOME_SAMPLE_RADIUS * -1; sampleOffsetZ <= NEARBY_BIOME_SAMPLE_RADIUS; sampleOffsetZ++) {
                int currSampleZ = blockZStart + sampleOffsetZ * NEARBY_BIOME_SAMPLE_STEPSIZE;
                Biome currBiome = OverworldBiomeGenerator.getBiome(seed, currSampleX, currSampleZ);
                nearbyBiomeMap.put(currBiome, nearbyBiomeMap.getOrDefault(currBiome, 0) + 1);
            }
        }
        HashMap<Biome, Integer> nearbyBiomeMapBackup = (HashMap<Biome, Integer>) nearbyBiomeMap.clone();

        // loop through all blocks.
        for (int i = 0; i < 16; i ++) {
            currX = blockXStart + i;
            nearbyBiomeMap = (HashMap<Biome, Integer>) nearbyBiomeMapBackup.clone();
            for (int j = 0; j < 16; j++) {
                currZ = blockZStart + j;

                // setup height info according to nearby biomes.
                height = 0;
                double terrainNoise = terrainGenerator.noise(currX, currZ, 0.5, 0.5, false);
                double terrainNoiseTwo = terrainGeneratorTwo.noise(currX, currZ, 0.5, 0.5, true);
                double riverNoise = riverGenerator.noise(currX, currZ, 0.5, 0.5, false);
                double lakeNoise = lakeGenerator.noise(currX, currZ, 0.5, 0.5, false);
                terrainNoiseTwo = terrainNoiseTwo * 0.75 + 0.5;
                int totalBiomes = 0;
                for (Biome bom : nearbyBiomeMap.keySet()) {
                    totalBiomes += nearbyBiomeMap.get(bom);
                    height += getTerrainHeight(bom, terrainNoise, terrainNoiseTwo, riverNoise, lakeNoise) * nearbyBiomeMap.get(bom);
                }
                if (totalBiomes != biomesSampled) Bukkit.getLogger().info("SAMPLE BIOME COUNT NOT MATCHING?? " + i + ", " + j + ", " + totalBiomes);
                height /= biomesSampled;
                double spawnMulti = (double)(Math.abs(currX) + Math.abs(currZ)) / 1000; // make sure no absurd landscape occurs around the spawn point
                if (spawnMulti < 1)
                    height = LAND_HEIGHT * (1 - spawnMulti) + height * spawnMulti;
                if (height > 50) {
                    double randomHeightMulti = Math.min(Math.max(2, (height - SEA_LEVEL) * 2), 10);
                    height += randomHeightMulti * terrainDetailGenerator.noise(currX, currZ, 0.5, 0.5, true);
                }

                // loop through y to set blocks
                for (int y_coord = 0; y_coord < 256; y_coord ++) {
                    int effectualY = y_coord + yOffset;
                    if (y_coord == 0)
                        chunk.setBlock(i, y_coord, j, Material.BEDROCK);
                    else if (y_coord == 255 && yOffset < 0)
                        chunk.setBlock(i, y_coord, j, Material.BEDROCK);
                    else if (effectualY < height)
                        chunk.setBlock(i, y_coord, j, Material.STONE);
                    else
                        chunk.setBlock(i, y_coord, j, Material.AIR);
                }
                heightMap[i][j] = (int) Math.ceil(height);

                // sliding window technique
                if (j + 1 < 16) {
                    int currSample_dropZ = currZ - NEARBY_BIOME_SAMPLE_RADIUS * NEARBY_BIOME_SAMPLE_STEPSIZE;
                    int currSample_addZ = currZ + (NEARBY_BIOME_SAMPLE_RADIUS + 1) * NEARBY_BIOME_SAMPLE_STEPSIZE;
                    for (int sampleOffset = NEARBY_BIOME_SAMPLE_RADIUS * -1; sampleOffset <= NEARBY_BIOME_SAMPLE_RADIUS; sampleOffset++) {
                        int currSampleX = currX + sampleOffset * NEARBY_BIOME_SAMPLE_STEPSIZE;
                        Biome currBiome_drop = OverworldBiomeGenerator.getBiome(seed, currSampleX, currSample_dropZ);
                        nearbyBiomeMap.put(currBiome_drop, nearbyBiomeMap.getOrDefault(currBiome_drop, 0) - 1);
                        Biome currBiome_add =  OverworldBiomeGenerator.getBiome(seed, currSampleX, currSample_addZ);
                        nearbyBiomeMap.put(currBiome_add,  nearbyBiomeMap.getOrDefault(currBiome_add,  0) + 1);
                    }
                }
            }
            // sliding window technique.
            if (i + 1 < 16) {
                int currSample_dropX = currX - NEARBY_BIOME_SAMPLE_RADIUS * NEARBY_BIOME_SAMPLE_STEPSIZE;
                int currSample_addX  = currX + (NEARBY_BIOME_SAMPLE_RADIUS + 1) * NEARBY_BIOME_SAMPLE_STEPSIZE;
                // setup height info according to nearby biomes at both offset 0. Then use sliding window technique to derive the height everywhere.
                for (int sampleOffset = NEARBY_BIOME_SAMPLE_RADIUS * -1; sampleOffset <= NEARBY_BIOME_SAMPLE_RADIUS; sampleOffset++) {
                    int currSampleZ = blockZStart + sampleOffset * NEARBY_BIOME_SAMPLE_STEPSIZE;
                    Biome currBiome_drop = OverworldBiomeGenerator.getBiome(seed, currSample_dropX, currSampleZ);
                    nearbyBiomeMapBackup.put(currBiome_drop, nearbyBiomeMapBackup.getOrDefault(currBiome_drop, 0) - 1);
                    Biome currBiome_add = OverworldBiomeGenerator.getBiome(seed, currSample_addX, currSampleZ);
                    nearbyBiomeMapBackup.put(currBiome_add,  nearbyBiomeMapBackup.getOrDefault(currBiome_add,  0) + 1);
                }
            }
        }
        return heightMap;
    }
    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        // setup biome
        tweakBiome(x, z, biome, yOffset_overworld);
        // init terrain
        ChunkData chunk = createChunkData(world);
        int[][] heightMap = initializeTerrain(chunk, x << 4, z << 4, biome, yOffset_overworld);
        // tweak terrain
        caveGen.populate(world, chunk, biome, heightMap, x, z);
//        caveGen.populate_no_estimate(chunk, biome, heightMap, x, z);
        for (int i = 0; i < 16; i ++)
            for (int j = 0; j < 16; j ++)
                generateTopSoil(chunk, i, heightMap[i][j], j, (x << 4) + i, (z << 4) + j, biome.getBiome(i, j), yOffset_overworld);
        return chunk;
    }
    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return populators;
    }
}