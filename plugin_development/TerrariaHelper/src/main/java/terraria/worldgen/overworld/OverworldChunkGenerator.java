package terraria.worldgen.overworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.Interpolate;

import java.util.*;

import static terraria.worldgen.overworld.OverworldNoiseProviders.*;

public class OverworldChunkGenerator extends ChunkGenerator {
    // optimization & logging variables
    public static final boolean LOG_TIMING = TerrariaHelper.optimizationConfig.getBoolean("worldGen.opt.chunkPrepDurationLog", false);
    public static final double BIOME_CACHE_LOAD_FACTOR = TerrariaHelper.optimizationConfig.getDouble("worldGen.opt.biomeCacheLoadFactor", 0.5);
    public static final int BIOME_CACHE_LIMIT = TerrariaHelper.optimizationConfig.getInt("worldGen.opt.biomeCacheLimit", 500000);
    static long[] testGenDurTotal = {0, 0, 0, 0};
    static long testGenAmount = 0;

    // world generator parameters
    public static final double BIOME_INTERPOLATE = TerrariaHelper.optimizationConfig.getDouble("worldGen.params.biomeInterpolate");
    public static final double SCALE_TERRAIN = TerrariaHelper.optimizationConfig.getDouble("worldGen.params.terrainFreq");
    public static final int OCTAVES_TERRAIN = TerrariaHelper.optimizationConfig.getInt("worldGen.params.terrainOctaves", 8);
    public static final int OCTAVES_CAVE = TerrariaHelper.optimizationConfig.getInt("worldGen.params.caveOctaves", 4);
    public static final int OCTAVES_STONE = TerrariaHelper.optimizationConfig.getInt("worldGen.params.stoneOctaves", 2);
    public static final int OCTAVES_RIVER = TerrariaHelper.optimizationConfig.getInt("worldGen.params.riverOctaves", 1);
    public static final int LAND_HEIGHT = TerrariaHelper.optimizationConfig.getInt("worldGen.params.landHeight", 100);
    public static final int SEA_LEVEL = TerrariaHelper.optimizationConfig.getInt("worldGen.params.seaLevel", 90);
    public static final int RIVER_DEPTH = TerrariaHelper.optimizationConfig.getInt("worldGen.params.riverDepth", 25);
    public static final int LAKE_DEPTH = TerrariaHelper.optimizationConfig.getInt("worldGen.params.lakeDepth", 30);
    public static final int ROLLING_HILLS_HEIGHT = TerrariaHelper.optimizationConfig.getInt("worldGen.params.rollingHillsHeight", 60);
    public static final int MOUNTAIN_HEIGHT = TerrariaHelper.optimizationConfig.getInt("worldGen.params.mountainHeight", 100);
    public static final int Y_OFFSET_OVERWORLD = 0;

    // initialize the mapping in which nearby heights influences the current block
    static OverworldChunkGenerator instance = new OverworldChunkGenerator();
    static List<BlockPopulator> populators;
    static OverworldCaveGenerator CAVE_GENERATOR_OVERWORLD;
    private OverworldChunkGenerator() {
        super();

        // block populators
        CAVE_GENERATOR_OVERWORLD = new OverworldCaveGenerator(Y_OFFSET_OVERWORLD, TerrariaHelper.WORLD_SEED, OCTAVES_CAVE);
        populators = new ArrayList<>();
        populators.add(new OverworldBlockGenericPopulator());
        populators.add(new OrePopulator(Y_OFFSET_OVERWORLD));
        populators.add(new FoliagePopulator());
        populators.add(new StructurePopulatorBiomeCenter(true));
        populators.add(new StructurePopulatorLoot(true));
        populators.add(new RailPopulator());
    }
    public static OverworldChunkGenerator getInstance() {
        return instance;
    }
    /*
     * helper functions
     */

    // chunk block material details
    public static void generateTopSoil(ChunkData chunk, Boolean[][][] stoneVeinFlag, int i, int height, int j, int blockX, int blockZ, Biome biome, int yOffset) {
        // although it is named as such, this actually generates stone layers too.
        double topSoilThicknessRandomizer = STONE_VEIN_NOISE.noise(blockX, blockZ, 2, 0.5, false);
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
                matSoil = Material.STAINED_CLAY;
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
        for (int y = Math.min(height - yOffset, WorldHelper.CAVERN_Y_BELOW_BEDROCK); y > 0; y--) {
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
                // the block below is air and this block has gravity; set the stone thickness higher, so it looks more appealing.
                if (matToSet.hasGravity()) {
                    for (int check = 1; check <= 3; check++)
                        if (chunk.getType(i, y - check, j) == Material.AIR) {
                            matToSet = matStone;
                            break;
                        }
                }
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

    static double interpolateWaterBodyHeightOffset(double heightOffset, double multiplier, double targetedWaterBodyDepth) {
        // target depth: 25 etc. heightOffset: -25 etc. flip the sign.
        targetedWaterBodyDepth = -targetedWaterBodyDepth;
        double waterOffset = (heightOffset * Math.max(0, 1 - multiplier)) + (multiplier * targetedWaterBodyDepth);

        return Math.min(heightOffset, waterOffset);
    }
    // compute the desired terrain height at a specific column
    static double getTerrainHeight(OverworldBiomeGenerator.BiomeFeature biomeFeature, HashMap<WorldHelper.BiomeType, Double> biomeSignificances, int currX, int currZ) {
        double combinedResult = 0;
        // river noise could boost up the erosion later on (NOT continentalness)
        double riverNoise = RIVER_NOISE.noise(currX, currZ, 2, 0.5, false);
        // elevation deviation: scale 0.5~0 (land) to 0 ~ 1, and 0.5~1 (ocean) to 0 ~ 1
        double elevDeviation = biomeFeature.features[OverworldBiomeGenerator.BiomeFeature.CONTINENTALNESS];
        elevDeviation = Math.abs(elevDeviation);
        if (elevDeviation > 0.5) {
            elevDeviation = elevDeviation * 2 - 1;
        } else {
            elevDeviation = 1 - elevDeviation * 2;
        }
        // erosion
        double erosion = biomeFeature.features[OverworldBiomeGenerator.BiomeFeature.EROSION];
        erosion -= RIVER_ERODE_PROVIDER.getY(riverNoise);

        for (WorldHelper.BiomeType biomeType : biomeSignificances.keySet()) {
            // biome height provider setup
            Interpolate[] idxProvider = NORMAL_INTERPOLATE_COORDINATOR;
            Interpolate[][] heightProviderMat;
            double result = 0;

            switch (biomeType) {
                case SULPHUROUS_OCEAN: {
                    idxProvider = SULPHUROUS_OCEAN_INTERPOLATE_COORDINATOR;
                    heightProviderMat = SULPHUROUS_OCEAN_INTERPOLATE;
                    break;
                }
                case OCEAN:
                    idxProvider = OCEAN_INTERPOLATE_COORDINATOR;
                    heightProviderMat = OCEAN_INTERPOLATE;
                    break;
                case JUNGLE:
                    heightProviderMat = JUNGLE_INTERPOLATE;
                    break;
                case ASTRAL_INFECTION:
                    heightProviderMat = ASTRAL_INTERPOLATE;
                    break;
                case DESERT:
                    heightProviderMat = DESERT_INTERPOLATE;
                    break;
                default: {
                    heightProviderMat = NORMAL_INTERPOLATE;
                }
            }

            // calculate relevant providers' weighted avg. height
            double total = 0d;
            double featTerrH = biomeFeature.features[OverworldBiomeGenerator.BiomeFeature.TERRAIN_H];
            // calculate the near providers' relevance
            double elevVal = idxProvider[0].getY(elevDeviation);
            double erosVal = idxProvider[1].getY(erosion);
            int elevStart, elevEnd, erosStart, erosEnd;
            elevStart = (int) Math.floor(elevVal + 1e-9);
            elevEnd = (int) Math.ceil(elevVal - 1e-9);
            erosStart = (int) Math.floor(erosVal + 1e-9);
            erosEnd = (int) Math.ceil(erosVal - 1e-9);
            // combine near providers' heights
            for (int i = elevStart; i <= elevEnd; i++) {
                for (int j = erosStart; j <= erosEnd; j++) {
                    double relevance = (1 - Math.abs(elevVal - i)) * (1 - Math.abs(erosVal - j));
                    if (relevance > 1e-5) {
                        result += heightProviderMat[i][j].getY(featTerrH) * relevance;
                        total += relevance;
                    }
                }
            }
            result /= total;
            combinedResult += result * biomeSignificances.get(biomeType);
        }

        // account for rivers
        double heightOffset = combinedResult - LAND_HEIGHT;
        double riverRatio = RIVER_RATIO_PROVIDER.getY(riverNoise);
        if (riverRatio > 1e-5)
            heightOffset = interpolateWaterBodyHeightOffset(heightOffset, riverRatio, RIVER_DEPTH);
        combinedResult = LAND_HEIGHT + heightOffset;

        return combinedResult;
    }
    // set up the "desired" block height and special cave ratio at a specific column of a chunk.
    private static void setupHeightAndCaveRatioAtLocation(int currX, int currZ, int i, int j,
                                                          int[][] heightMap, double[][] caveMultiMap,
                                                          OverworldCaveGenerator caveGen) {
        OverworldBiomeGenerator.BiomeFeature biomeFeature = OverworldBiomeGenerator.getBiomeFeature(currX, currZ);
        HashMap<WorldHelper.BiomeType, Double> biomeSignificances = biomeFeature.getBiomesWeight();
        // calculate each nearby biome's contribution to the current land height
        double totalHeight = getTerrainHeight(biomeFeature, biomeSignificances, currX, currZ);

        // terrain near the spawning point will be around LAND_HEIGHT; use the double value to prevent overflow.
        double distToSpawn = Math.sqrt((double) currX * (double) currX + (double) currZ * (double) currZ);
        if (distToSpawn < OverworldBiomeGenerator.SPAWN_LOC_PROTECTION_RADIUS) {
            distToSpawn /= OverworldBiomeGenerator.SPAWN_LOC_PROTECTION_RADIUS;
            // height factor is quadratic - aggressively flatten terrain near spawning point
            distToSpawn *= distToSpawn;
            totalHeight *= distToSpawn;
            totalHeight += (1 - distToSpawn) * LAND_HEIGHT;
        }
        heightMap[i][j] = (int) Math.round( totalHeight );

        // compare the two dominating driving power to prevent multiple biomes diluting the result
        double noCaveFactor = 0, caveFactor = 0;
        for (WorldHelper.BiomeType biomeType : biomeSignificances.keySet()) {
            if (caveGen.generateCaveForBiomeType(biomeType)) {
                caveFactor = Math.max(caveFactor, biomeSignificances.get(biomeType));
            } else {
                noCaveFactor = Math.max(noCaveFactor, biomeSignificances.get(biomeType));
            }
        }
        double caveMulti = caveFactor / (caveFactor + noCaveFactor);
        // cave multi: scale 0~0.9 to 0~1.0 to leave margins around special biomes (internally)
        caveMulti = Math.min(caveMulti / 0.9, 1);
        // save cave multi into caveMultiMap
        caveMultiMap[i][j] = caveMulti;
    }
    /** tweaks the biome then generates the height and cave multiplier mapping of a chunk
     * @param blockXStart the starting x of the chunk
     * @param blockZStart the starting z of the chunk
     * @param heightMap the height map to initialize
     * @param caveMultiMap the cave multiplier map to initialize
     * @param caveGen the cave generator to use
     * @param biomeGrid the biome grid to tweak
     * @param yOffset the y offset used to tweak the biome
     */
    public static void generateMapsAlternative(int blockXStart, int blockZStart, int[][] heightMap, double[][] caveMultiMap,
                                               OverworldCaveGenerator caveGen, BiomeGrid biomeGrid, int yOffset) {
        long timing = System.nanoTime();
        int currX, currZ;
        // update the biome grid
        for (int i = 0; i < 16; i ++) {
            currX = blockXStart + i;
            for (int j = 0; j < 16; j++) {
                currZ = blockZStart + j;
                OverworldBiomeGenerator.BiomeFeature biomeFeature = OverworldBiomeGenerator.getBiomeFeature(currX, currZ);
                Biome vanillaBiome = OverworldBiomeGenerator.getBiomeFromType(biomeFeature.evaluatedBiome);
                if (yOffset >= 0)
                    biomeGrid.setBiome(i, j, vanillaBiome);
                else
                    biomeGrid.setBiome(i, j, OverworldBiomeGenerator.getUndergroundEquivalent(vanillaBiome));
            }
        }
        // record timing info
        testGenDurTotal[0] += System.nanoTime() - timing;

        // setup height and cave ratio
        timing = System.nanoTime();
        for (int i = 0; i < 16; i ++) {
            currX = blockXStart + i;
            for (int j = 0; j < 16; j++) {
                currZ = blockZStart + j;
                setupHeightAndCaveRatioAtLocation(currX, currZ, i, j, heightMap, caveMultiMap, caveGen);
            }
        }
        // record timing info
        testGenDurTotal[1] += System.nanoTime() - timing;
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
        return STONE_VEIN_NOISE.noise(blockX, effectualY, blockZ, 2, 0.5, false) > 0.5;
    }
    protected static boolean isDetailedCheckNeeded(Boolean[][][] greaterGrid, int indexX, int indexY, int indexZ) {
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
    // NOTE: the attempt to utilize thread pool to optimize this has failed. Don't bother trying again.
    // This has to do with the noise function having a single octave.
    public static Boolean[][][] setupStoneFlags(int xStart, int zStart, int yOffset, int[][] heightMap) {
        // setup 4*4 grid
        Boolean[][][] fourGrid = new Boolean[5][65][5];
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
                    else
                        fourGrid[i][j][k] = checkStoneNoise(xStart + actualXOffset, actualY, zStart + actualZOffset);
                }
            }
        }
        // setup 2*2 grid
        Boolean[][][] twoGrid = new Boolean[9][129][9];
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
        Boolean[][][] result = new Boolean[16][256][16];
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
        // print time benchmarking info
        if (LOG_TIMING && testGenAmount % 20 == 19) {
            TerrariaHelper.LOGGER.info("地表区块基础生成（生物群系，高度，土壤，岩石）时长：");
            TerrariaHelper.LOGGER.info("地表区块基础生成-载入生物群系 平均使用时间（单位：纳秒）: " + (testGenDurTotal[0] / testGenAmount) );
            TerrariaHelper.LOGGER.info("地表区块基础生成-地形高度 平均使用时间（单位：纳秒）: " + (testGenDurTotal[1] / testGenAmount) );
            TerrariaHelper.LOGGER.info("地表区块基础生成-粗略地形与岩石 平均使用时间（单位：纳秒）: " + (testGenDurTotal[2] / testGenAmount) );
            TerrariaHelper.LOGGER.info("地表区块基础生成-土壤 平均使用时间（单位：纳秒）: " + (testGenDurTotal[3] / testGenAmount) );
        }

        long timing;
        // init info maps; the memoization would also set up the biome.
        int[][] heightMap = new int[16][16];
        double[][] caveMultiMap = new double[16][16];
//        generateMaps(x << 4, z << 4, heightMap, caveMultiMap, CAVE_GENERATOR_OVERWORLD, biome, Y_OFFSET_OVERWORLD);
        generateMapsAlternative(x << 4, z << 4, heightMap, caveMultiMap, CAVE_GENERATOR_OVERWORLD, biome, Y_OFFSET_OVERWORLD);


        timing = System.nanoTime();
        // init terrain (height & stone)
        ChunkData chunk = createChunkData(world);
        initializeTerrain(chunk, x << 4, z << 4, biome, Y_OFFSET_OVERWORLD, heightMap);
        Boolean[][][] stoneFlags = setupStoneFlags(x << 4, z << 4, Y_OFFSET_OVERWORLD, heightMap);
        // record timing info
        testGenDurTotal[2] += System.nanoTime() - timing;

        // generate cave
        CAVE_GENERATOR_OVERWORLD.populate(world, chunk, biome, heightMap, x, z, caveMultiMap);
//        CAVE_GENERATOR_OVERWORLD.populate_no_optimization(chunk, biome, heightMap, x, z, caveMultiMap);

        timing = System.nanoTime();
        // finally place topsoil
        for (int i = 0; i < 16; i ++)
            for (int j = 0; j < 16; j ++)
                generateTopSoil(chunk, stoneFlags, i, heightMap[i][j], j, (x << 4) + i, (z << 4) + j, biome.getBiome(i, j), Y_OFFSET_OVERWORLD);
        // record timing info
        testGenDurTotal[3] += System.nanoTime() - timing;
        testGenAmount++;

        return chunk;
    }
    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return populators;
    }
}