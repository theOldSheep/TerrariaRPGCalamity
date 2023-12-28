package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.noise.PerlinOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.worldgen.Interpolate;
import terraria.worldgen.Interpolate.InterpolatePoint;

import java.util.*;

public class OverworldChunkGenerator extends ChunkGenerator {
    static long seed = TerrariaHelper.worldSeed;
    public static final int OCTAVES = 6, OCTAVES_CAVE = 4,
            NEARBY_BIOME_SAMPLE_RADIUS = 25, NEARBY_BIOME_SAMPLE_STEPSIZE = 1,
            LAND_HEIGHT = 100, RIVER_DEPTH = 25, LAKE_DEPTH = 30, PLATEAU_HEIGHT = 20, SEA_LEVEL = 90, LAVA_LEVEL = -150,
            Y_OFFSET_OVERWORLD = 0, HEIGHT_SAMPLING_DIAMETER;
    private static final double FREQUENCY = 0.05221649073, HEIGHT_SAMPLE_FACTOR_SUM;
    private static final double[][] HEIGHT_INFLUENCE_FACTOR;
    // initialize the mapping in which nearby heights influences the current block
    static {
        HEIGHT_SAMPLING_DIAMETER = 1 + NEARBY_BIOME_SAMPLE_RADIUS * 2;
        double[][] influence_factors = new double[HEIGHT_SAMPLING_DIAMETER][HEIGHT_SAMPLING_DIAMETER];
        double furthestDistance = NEARBY_BIOME_SAMPLE_RADIUS * Math.sqrt(2);
        for (int i = 0; i <= NEARBY_BIOME_SAMPLE_RADIUS; i ++)
            for (int j = 0; j <= NEARBY_BIOME_SAMPLE_RADIUS; j ++) {
                double currDist = Math.sqrt(i * i + j * j);
                double currFactor = Math.cos( (Math.PI / 2) * currDist / furthestDistance);
                currFactor = 1;
                influence_factors[NEARBY_BIOME_SAMPLE_RADIUS + i][NEARBY_BIOME_SAMPLE_RADIUS + j] = currFactor;
                influence_factors[NEARBY_BIOME_SAMPLE_RADIUS + i][NEARBY_BIOME_SAMPLE_RADIUS - j] = currFactor;
                influence_factors[NEARBY_BIOME_SAMPLE_RADIUS - i][NEARBY_BIOME_SAMPLE_RADIUS + j] = currFactor;
                influence_factors[NEARBY_BIOME_SAMPLE_RADIUS - i][NEARBY_BIOME_SAMPLE_RADIUS - j] = currFactor;
            }
        HEIGHT_INFLUENCE_FACTOR = influence_factors;
        double sum = 0;
        for (double[] factors : HEIGHT_INFLUENCE_FACTOR)
            for (double currFactor : factors)
                sum += currFactor;
        HEIGHT_SAMPLE_FACTOR_SUM = sum;
    }
    public static PerlinOctaveGenerator terrainScalingGenerator, terrainDetailGenerator,
                                        plateauHeightGenerator, landscapeVariationGenerator,
                                        astralInfectionGenerator,
                                        riverGenerator, lakeGenerator,
                                        stoneVeinGenerator;
    public static Interpolate astralInfectionHeightProvider,
                                variationHeightProvider, plateauHeightProvider,
                                riverRatioProvider, lakeRatioProvider;
    static OverworldChunkGenerator instance = new OverworldChunkGenerator();
    static List<BlockPopulator> populators;
    static OverworldCaveGenerator caveGen;
    private OverworldChunkGenerator() {
        super();
        // terrain noise functions
        Random rdm = new Random(seed);
        astralInfectionGenerator = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES);
        astralInfectionGenerator.setScale(0.0005);
        astralInfectionGenerator.setScale(0.005);
        terrainScalingGenerator = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES);
        terrainScalingGenerator.setScale(0.015);
        terrainDetailGenerator = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES);
        terrainDetailGenerator.setScale(0.025);

        plateauHeightGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 2);
        plateauHeightGenerator.setScale(0.0025);
        landscapeVariationGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 2);
        landscapeVariationGenerator.setScale(0.001);

        riverGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        riverGenerator.setScale(0.0005);
        lakeGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        lakeGenerator.setScale(0.0025);

        stoneVeinGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        stoneVeinGenerator.setScale(0.05);
        // constants
        seed = TerrariaHelper.worldSeed;
        // interpolates
        astralInfectionHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.35 , PLATEAU_HEIGHT),
                InterpolatePoint.create(-0.3  ,   0),
                InterpolatePoint.create( 0.3  ,   0),
                InterpolatePoint.create( 0.35 , PLATEAU_HEIGHT)
        }, "astral_infection_heightmap");
        plateauHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(0.5     , LAND_HEIGHT),
                InterpolatePoint.create(0.55    , LAND_HEIGHT + 25),
                InterpolatePoint.create(0.6     , LAND_HEIGHT + 5),
                InterpolatePoint.create(0.65    , LAND_HEIGHT + 15),
                InterpolatePoint.create(0.7     , LAND_HEIGHT + 35),
                InterpolatePoint.create(0.75    , LAND_HEIGHT + 20),
                InterpolatePoint.create(0.8     , LAND_HEIGHT),
                InterpolatePoint.create(0.85    , LAND_HEIGHT + 5),
                InterpolatePoint.create(0.9     , LAND_HEIGHT + 30),
        }, "plateau_height_heightmap");
        variationHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.35     , 0),
                InterpolatePoint.create(-0.25     , 10),
                InterpolatePoint.create(-0.1     , 25),
                InterpolatePoint.create(0     , 5),
                InterpolatePoint.create(0.1     , 30),
                InterpolatePoint.create(0.15     , 15),
                InterpolatePoint.create(0.25     , 0),
                InterpolatePoint.create(0.35     , 40),
                InterpolatePoint.create(0.4     , 10),
                InterpolatePoint.create(0.45      , 50),
                InterpolatePoint.create(0.6      , 50),
                InterpolatePoint.create(0.7      , 30),
                InterpolatePoint.create(0.75       , 20),
                InterpolatePoint.create(0.8      , 75),
        }, "variations_heightmap");
        plateauHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.65    , PLATEAU_HEIGHT),
                InterpolatePoint.create(-0.55    , 0),
                InterpolatePoint.create(0.55     , 0),
                InterpolatePoint.create(0.65     , PLATEAU_HEIGHT)
        }, "plateau_heightmap");
        riverRatioProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.075  , 0),
                InterpolatePoint.create(-0.06  , 0.2),
                InterpolatePoint.create(-0.04  , 0.5),
                InterpolatePoint.create(0      , 1),
                InterpolatePoint.create(0.04   , 0.5),
                InterpolatePoint.create(0.06   , 0.2),
                InterpolatePoint.create(0.075   , 0),
        }, "river_ratio_map");
        lakeRatioProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.7  , 1),
                InterpolatePoint.create(-0.6   , 0.5),
                InterpolatePoint.create(-0.5   , 0),
                InterpolatePoint.create(0.5   , 0),
                InterpolatePoint.create(0.6   , 0.5),
                InterpolatePoint.create(0.7  , 1),
        }, "lake_ratio_map");
        // block populators
        caveGen = new OverworldCaveGenerator(Y_OFFSET_OVERWORLD, seed, OCTAVES_CAVE);
        populators = new ArrayList<>();
        populators.add(new OverworldBlockGenericPopulator());
        populators.add(new OrePopulator(Y_OFFSET_OVERWORLD));
        populators.add(new FoilagePopulator());
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
    /*
     * helper functions
     */

    // chunk block material details
    public static void generateTopSoil(ChunkData chunk, boolean[][][] stoneVeinFlag, int i, int height, int j, int blockX, int blockZ, Biome biome, int yOffset) {
        // although it is named as such, this actually generates stone layers too.
        double topSoilThicknessRandomizer = stoneVeinGenerator.noise(blockX, blockZ, 0.5, 0.5, false);
        double topSoilThickness;
            Material matTopSoil, matSoil, matStone;
            // setup biome block info
            switch (biome) {
                case DEEP_OCEAN: // abyss
                    matTopSoil = Material.SAND;
                    matSoil = Material.SAND;
                    matStone = Material.PRISMARINE;
                    break;
                case TAIGA_COLD: // tundra
                case MUTATED_TAIGA_COLD: // tundra
                    matTopSoil = Material.SNOW_BLOCK;
                    matSoil = Material.SNOW_BLOCK;
                    matStone = Material.PACKED_ICE;
                    break;
                case DESERT: // desert
                case OCEAN: // ocean
                case BEACHES: // beach
                case FROZEN_OCEAN: // sulphurous ocean
                case COLD_BEACH: // sulphurous beach
                    matTopSoil = Material.SAND;
                    matSoil = Material.SAND;
                    matStone = Material.SANDSTONE;
                    break;
                case MUTATED_DESERT: // sunken sea
                    matTopSoil = Material.STAINED_CLAY;
                    matSoil = Material.WATER;
                    matStone = Material.STAINED_CLAY;
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
                boolean isStoneVein = stoneVeinFlag[i][y][j];
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
    /*
     * init terrain helpers
     */

    // compute the desired terrain height at a specific column
    static double getTerrainHeight(Biome currBiome, int currX, int currZ) {
        double result;
        double terrainScalingFactor = terrainScalingGenerator.noise(currX, currZ, 0.5, 0.5, true);
        double riverNoise = riverGenerator.noise(currX, currZ, 0.5, 0.5, false);
        double lakeNoise = lakeGenerator.noise(currX, currZ, 0.5, 0.5, false);
        terrainScalingFactor = terrainScalingFactor * 0.75 + 0.5;
        switch (currBiome) {
            case FROZEN_OCEAN:              // sulphurous ocean
                return 0;
            case BEACHES:                   // beach
            case COLD_BEACH:                // sulphurous beach
                return LAND_HEIGHT;
            case OCEAN:                     // ocean
                return SEA_LEVEL - 30;
            case MESA:                      // astral infection
                double astralNoise = astralInfectionGenerator.noise(currX, currZ, 0.5, 0.5, false);
                result = astralInfectionHeightProvider.getY(astralNoise);
                return LAND_HEIGHT + result * terrainScalingFactor;
            case JUNGLE:                    // jungle
                return SEA_LEVEL;
            default:
                // first: plains, mountains, rolling hills, plateau etc.
                double plateauHeightNoise = plateauHeightGenerator.noise(currX, currZ, 0.5, 0.5, false);
                double variationNoise = landscapeVariationGenerator.noise(currX, currZ, 0.5, 0.5, false);
                double heightOffset = 0;
                // handle special landscapes: the rolling hills/mountains/plateau etc.
                double heightOffsetPlateau = plateauHeightProvider.getY(plateauHeightNoise);
                double heightOffsetVariation = variationHeightProvider.getY(variationNoise);
                heightOffset += heightOffsetPlateau;
                heightOffset += heightOffsetVariation;
                // account for rivers and lakes
                double riverRatio = riverRatioProvider.getY(riverNoise),
                        lakeRatio = lakeRatioProvider.getY(lakeNoise);
                double waterDepthMulti = currBiome == Biome.DESERT ? 0.4 : 1;
                if (riverRatio > 1e-5)
                    heightOffset = (heightOffset * Math.max(0, 1 - riverRatio)) - (riverRatio * RIVER_DEPTH * waterDepthMulti);
                if (lakeRatio > 1e-5)
                    heightOffset = (heightOffset * Math.max(0, 1 - lakeRatio)) - (lakeRatio * LAKE_DEPTH * waterDepthMulti);
                // then, we can add a bit of variation to the terrain
                if (heightOffset > 0) heightOffset *= terrainScalingFactor; // rivers won't be too shallow in this way
                // compute and return the final result
                result = LAND_HEIGHT + heightOffset;
                return result;
        }
    }
    // set up the "desired" block height and special cave ratio at a specific column of a chunk.
    private static void setupHeightAndCaveRatioAtLocation(int currX, int currZ, int i, int j,
                                              int[][] heightMap, double[][] caveMultiMap,
                                              HashMap<Biome, Double> nearbyBiomeMap) {
        //
        // setup height according to nearby biomes.
        //
        double height = 0;
        // debug: this will raise an alert when the biome sampler runs faulty
        double totalBiomes = 0;
        for (Biome bom : nearbyBiomeMap.keySet()) {
            totalBiomes += nearbyBiomeMap.get(bom);
            height += getTerrainHeight(bom, currX, currZ) * nearbyBiomeMap.get(bom);
        }
        if ( Math.abs(totalBiomes - HEIGHT_SAMPLE_FACTOR_SUM) > 1e-5)
            Bukkit.getLogger().info("ERROR WHILE GENERATING HEIGHT MAP: SAMPLE BIOME COUNT NOT MATCHING?? " +
                    i + ", " + j + ", " + totalBiomes + ", expected " + HEIGHT_SAMPLE_FACTOR_SUM);
        height /= HEIGHT_SAMPLE_FACTOR_SUM;
        double spawnMulti = (double)(Math.abs(currX) + Math.abs(currZ)) / 500; // make sure no absurd landscape occurs around the spawn point
        if (spawnMulti < 1)
            height = LAND_HEIGHT * (1 - spawnMulti) + height * spawnMulti;
        if (height > 50) {
            double randomHeightMulti = Math.min(Math.max(4, (height - SEA_LEVEL) * 2), 10);
            height += randomHeightMulti * terrainDetailGenerator.noise(currX, currZ, 0.5, 0.5, true);
        }
        // save height into heightmap
        heightMap[i][j] = (int) Math.ceil(height);

        //
        // setup cave multi according to nearby biomes
        //
        double caveMulti = 0;
        for (Biome bom : nearbyBiomeMap.keySet()) {
            caveMulti += OverworldCaveGenerator.getCavernNoiseMulti(bom) * nearbyBiomeMap.get(bom);
        }
        caveMulti /= HEIGHT_SAMPLE_FACTOR_SUM;
        // save cave multi into caveMultiMap
        caveMultiMap[i][j] = caveMulti;
    }
    // generates the height and cave multiplier mapping of a chunk
    public static void generateMaps(int blockXStart, int blockZStart, int[][] heightMap, double[][] caveMultiMap) {
        double height, caveMulti;
        int currX, currZ;

        // setup height info according to nearby biomes at both offset 0.
        // Then use sliding window technique to derive the height everywhere.
        HashMap<Biome, Double> nearbyBiomeMap = new HashMap<>();
        for (int sampleOffsetX = NEARBY_BIOME_SAMPLE_RADIUS * -1; sampleOffsetX <= NEARBY_BIOME_SAMPLE_RADIUS; sampleOffsetX++) {
            int currSampleX = blockXStart + sampleOffsetX * NEARBY_BIOME_SAMPLE_STEPSIZE;
            for (int sampleOffsetZ = NEARBY_BIOME_SAMPLE_RADIUS * -1; sampleOffsetZ <= NEARBY_BIOME_SAMPLE_RADIUS; sampleOffsetZ++) {
                int currSampleZ = blockZStart + sampleOffsetZ * NEARBY_BIOME_SAMPLE_STEPSIZE;
                Biome currBiome = OverworldBiomeGenerator.getBiome(seed, currSampleX, currSampleZ);
                double updatedBiomeIntensity = nearbyBiomeMap.getOrDefault(currBiome, 0d) +
                        HEIGHT_INFLUENCE_FACTOR[sampleOffsetX + NEARBY_BIOME_SAMPLE_RADIUS][sampleOffsetZ + NEARBY_BIOME_SAMPLE_RADIUS];
                nearbyBiomeMap.put(currBiome, updatedBiomeIntensity);
            }
        }
        HashMap<Biome, Double> nearbyBiomeMapBackup = (HashMap<Biome, Double>) nearbyBiomeMap.clone();

        // loop through all blocks.
        for (int i = 0; i < 16; i ++) {
            currX = blockXStart + i;
            nearbyBiomeMap = (HashMap<Biome, Double>) nearbyBiomeMapBackup.clone();
            for (int j = 0; j < 16; j++) {
                currZ = blockZStart + j;

                setupHeightAndCaveRatioAtLocation(currX, currZ, i, j, heightMap, caveMultiMap, nearbyBiomeMap);

                // sliding window technique
                if (j + 1 < 16) {
                    int currSample_dropZ = currZ - NEARBY_BIOME_SAMPLE_RADIUS * NEARBY_BIOME_SAMPLE_STEPSIZE;
                    int currSample_addZ = currZ + (NEARBY_BIOME_SAMPLE_RADIUS + 1) * NEARBY_BIOME_SAMPLE_STEPSIZE;
                    for (int sampleOffset = NEARBY_BIOME_SAMPLE_RADIUS * -1; sampleOffset <= NEARBY_BIOME_SAMPLE_RADIUS; sampleOffset++) {
                        int currSampleX = currX + sampleOffset * NEARBY_BIOME_SAMPLE_STEPSIZE;
                        double influence_factor = HEIGHT_INFLUENCE_FACTOR[0][sampleOffset + NEARBY_BIOME_SAMPLE_RADIUS];

                        Biome currBiome_drop = OverworldBiomeGenerator.getBiome(seed, currSampleX, currSample_dropZ);
                        double updatedIntensity_drop = nearbyBiomeMap.getOrDefault(currBiome_drop, 0d) - influence_factor;
                        nearbyBiomeMap.put(currBiome_drop, updatedIntensity_drop);

                        Biome currBiome_add =  OverworldBiomeGenerator.getBiome(seed, currSampleX, currSample_addZ);
                        double updatedIntensity_add = nearbyBiomeMap.getOrDefault(currBiome_add, 0d) + influence_factor;
                        nearbyBiomeMap.put(currBiome_add,  updatedIntensity_add);
                    }
                }
            }
            // sliding window technique.
            if (i + 1 < 16) {
                int currSample_dropX = currX - NEARBY_BIOME_SAMPLE_RADIUS * NEARBY_BIOME_SAMPLE_STEPSIZE;
                int currSample_addX  = currX + (NEARBY_BIOME_SAMPLE_RADIUS + 1) * NEARBY_BIOME_SAMPLE_STEPSIZE;
                for (int sampleOffset = NEARBY_BIOME_SAMPLE_RADIUS * -1; sampleOffset <= NEARBY_BIOME_SAMPLE_RADIUS; sampleOffset++) {
                    int currSampleZ = blockZStart + sampleOffset * NEARBY_BIOME_SAMPLE_STEPSIZE;
                    double influence_factor = HEIGHT_INFLUENCE_FACTOR[sampleOffset + NEARBY_BIOME_SAMPLE_RADIUS][0];

                    Biome currBiome_drop = OverworldBiomeGenerator.getBiome(seed, currSample_dropX, currSampleZ);
                    double updatedIntensity_drop = nearbyBiomeMapBackup.getOrDefault(currBiome_drop, 0d) - influence_factor;
                    nearbyBiomeMapBackup.put(currBiome_drop, updatedIntensity_drop);

                    Biome currBiome_add = OverworldBiomeGenerator.getBiome(seed, currSample_addX, currSampleZ);
                    double updatedIntensity_add = nearbyBiomeMapBackup.getOrDefault(currBiome_add, 0d) + influence_factor;
                    nearbyBiomeMapBackup.put(currBiome_add,  updatedIntensity_add);
                }
            }
        }
    }
    /*
     * init terrain (rough + detail)
     */

    // rough init of terrain (stone, air, bedrock)
    public static void initializeTerrain(ChunkData chunk, int blockXStart, int blockZStart, BiomeGrid biome, int yOffset, int[][] heightMap) {
        // this function creates the raw terrain of the chunk, consisting of only air, water or stone.
        int currX, currZ;

        // loop through all blocks.
        for (int i = 0; i < 16; i ++) {
            currX = blockXStart + i;
            for (int j = 0; j < 16; j++) {
                currZ = blockZStart + j;

                // loop through y to set blocks
                for (int y_coord = 0; y_coord < 256; y_coord ++) {
                    int effectualY = y_coord + yOffset;
                    if (y_coord == 0)
                        chunk.setBlock(i, y_coord, j, Material.BEDROCK);
                    else if (y_coord == 255 && yOffset < 0)
                        chunk.setBlock(i, y_coord, j, Material.BEDROCK);
                    else if (effectualY < heightMap[i][j])
                        chunk.setBlock(i, y_coord, j, Material.STONE);
                    else
                        chunk.setBlock(i, y_coord, j, Material.AIR);
                }
            }
        }
    }
    /*
     * helper function for set stone flags
     */
    // checks if dirt and stone should swap here
    protected static boolean checkStoneNoise(double blockX, double effectualY, double blockZ) {
        return stoneVeinGenerator.noise(blockX, effectualY, blockZ, 0.5, 0.5, false) > 0.5;
    }
    protected static boolean isDetailedCheckNeeded(boolean[][][] greaterGrid, int indexX, int indexY, int indexZ) {
        // checks the nearby 2*2 greater grid and tells if they are the same.
        int greaterIdxX = indexX >> 1;
        int greaterIdxY = indexY >> 1;
        int greaterIdxZ = indexZ >> 1;
        boolean expected = greaterGrid[greaterIdxX][greaterIdxY][greaterIdxZ];
        // for x,y,z index & 1:   0 when index % 2 = 0;  1 when index % 2 = 1.
        // to demonstrate this algorithm, an analogy in 2d would be ideal:
        // o stands for grid info in greaterGrid
        // * means the grid needs to loop through all surrounding, as x,y&1=1
        // - means the grid needs to loop through the two horizontal, as x&1=1 and y&1=0
        // | means the grid needs to loop through the two vertical,   as x&1=0 and y&1=1
        // the actual scenario is in 3D so that it is a bit different, but the idea is the same.
        // o  -  o
        // |  *  |
        // o  -  o
        for (int i = greaterIdxX; i <= greaterIdxX + (indexX & 1); i ++) {
            for (int j = greaterIdxY; j <= greaterIdxY + (indexY & 1); j ++) {
                for (int k = greaterIdxZ; k <= greaterIdxZ + (indexZ & 1); k ++) {
                    // if the grid is different from expected, return true and flag for further calculation
                    if (greaterGrid[i][j][k] != expected) return true;
                }
            }
        }
        // everything seems to match, returns false and no extra calculation is needed.
        return false;
    }
    public static boolean[][][] setupStoneFlags(int xStart, int zStart, int yOffset, int[][] heightMap) {
        // setup 4*4 grid
        boolean[][][] fourGrid = new boolean[5][65][5];
        for (int i = 0; i < 5; i++) {
            int actualXOffset = i << 2;
            for (int j = 0; j < 65; j++) {
                int actualY = (j << 2) + yOffset;
                for (int k = 0; k < 5; k++) {
                    int actualZOffset = k << 2;
                    // if the block is above max height. Checks i and k for out of bound.
                    if (i < 4 && k < 4 && actualY > heightMap[actualXOffset][actualZOffset])
                        fourGrid[i][j][k] = false;
                    // otherwise, we calculate the noise and validate
                    else fourGrid[i][j][k] = checkStoneNoise(xStart + actualXOffset, actualY, zStart + actualZOffset);
                }
            }
        }
        // setup 2*2 grid
        boolean[][][] twoGrid = new boolean[9][129][9];
        for (int i = 0; i < 9; i++) {
            int actualXOffset = i << 1;
            for (int j = 0; j < 129; j++) {
                int actualY = (j << 1) + yOffset;
                for (int k = 0; k < 9; k++) {
                    int actualZOffset = k << 1;
                    // if the block is above max height. Checks i and k for out of bound.
                    if (i < 8 && k < 8 && actualY > heightMap[actualXOffset][actualZOffset]) {
                        twoGrid[i][j][k] = false;
                        continue;
                    }
                    if (isDetailedCheckNeeded(fourGrid, i, j, k)) {
                        // if further calculation is needed, we calculate the noise and validate
                        twoGrid[i][j][k] = checkStoneNoise(xStart + actualXOffset, actualY, zStart + actualZOffset);
                    } else {
                        // otherwise, let it inherit the 4*4 grid stone flag
                        twoGrid[i][j][k] = fourGrid[i >> 1][j >> 1][k >> 1];
                    }
                }
            }
        }
        // setup result grid
        boolean[][][] result = new boolean[16][256][16];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 256; j++) {
                int actualY = j + yOffset;
                for (int k = 0; k < 16; k++) {
                    // if the block is above max height.
                    if (actualY > heightMap[i][k]) {
                        result[i][j][k] = false;
                        continue;
                    }
                    if (isDetailedCheckNeeded(twoGrid, i, j, k)) {
                        // if further calculation is needed, we calculate the noise and validate
                        result[i][j][k] = checkStoneNoise(xStart + i, actualY, zStart + k);
                    } else {
                        // otherwise, let it inherit the 2*2 grid stone flag
                        result[i][j][k] = twoGrid[i >> 1][j >> 1][k >> 1];
                    }
                }
            }
        }
        return result;
    }
    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        // setup biome
        tweakBiome(x, z, biome, Y_OFFSET_OVERWORLD);
        // init info maps
        int[][] heightMap = new int[16][16];
        double[][] caveMultiMap = new double[16][16];
        generateMaps(x << 4, z << 4, heightMap, caveMultiMap);
        // init terrain
        ChunkData chunk = createChunkData(world);
        initializeTerrain(chunk, x << 4, z << 4, biome, Y_OFFSET_OVERWORLD, heightMap);
        boolean[][][] stoneFlags = setupStoneFlags(x << 4, z << 4, Y_OFFSET_OVERWORLD, heightMap);
        // tweak terrain
        caveGen.populate(world, chunk, biome, heightMap, x, z, caveMultiMap);
//        caveGen.populate_no_optimization(chunk, biome, heightMap, x, z, caveMultiMap);
        for (int i = 0; i < 16; i ++)
            for (int j = 0; j < 16; j ++)
                generateTopSoil(chunk, stoneFlags, i, heightMap[i][j], j, (x << 4) + i, (z << 4) + j, biome.getBiome(i, j), Y_OFFSET_OVERWORLD);
        return chunk;
    }
    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return populators;
    }
}