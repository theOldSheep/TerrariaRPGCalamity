package terraria.worldgen.overworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import terraria.TerrariaHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.Interpolate;
import terraria.worldgen.WorldGenHelper;

import java.util.*;

import static terraria.worldgen.overworld.OverworldBiomeGenerator.BiomeFeature.HUMIDITY;
import static terraria.worldgen.overworld.OverworldNoiseProviders.*;

public class OverworldChunkGenerator extends ChunkGenerator {
    // optimization & logging variables
    public static final boolean LOG_TIMING = TerrariaHelper.optimizationConfig.getBoolean("worldGen.opt.chunkPrepDurationLog", false);
    public static final double BIOME_CACHE_LOAD_FACTOR = TerrariaHelper.optimizationConfig.getDouble("worldGen.opt.biomeCacheLoadFactor", 0.5);
    public static final int BIOME_CACHE_LIMIT = TerrariaHelper.optimizationConfig.getInt("worldGen.opt.biomeCacheLimit", 500000);
    static long[] testStoneDurTotal = {0, 0}, testStoneSetupDurTotal = {0, 0}, testStoneGenAmount = {0, 0};
    static long[] testGenDurTotal = {0, 0, 0, 0};
    static long testGenAmount = 0;

    // world generator parameters
    public static final double BIOME_INTERPOLATE = TerrariaHelper.optimizationConfig.getDouble("worldGen.params.biomeInterpolate");
    public static final double SCALE_TERRAIN = TerrariaHelper.optimizationConfig.getDouble("worldGen.params.terrainFreq");
    public static final int OCTAVES_TERRAIN = TerrariaHelper.optimizationConfig.getInt("worldGen.params.terrainOctaves", 4);
    public static final int OCTAVES_CAVE = TerrariaHelper.optimizationConfig.getInt("worldGen.params.caveOctaves", 5);
    public static final int OCTAVES_STONE = TerrariaHelper.optimizationConfig.getInt("worldGen.params.stoneOctaves", 1);
    public static final int OCTAVES_RIVER = TerrariaHelper.optimizationConfig.getInt("worldGen.params.riverOctaves", 4);
    public static final int LAND_HEIGHT = TerrariaHelper.optimizationConfig.getInt("worldGen.params.landHeight", 100);
    public static final int SEA_LEVEL = TerrariaHelper.optimizationConfig.getInt("worldGen.params.seaLevel", 90);
    public static final int RIVER_DEPTH = TerrariaHelper.optimizationConfig.getInt("worldGen.params.riverDepth", 25);
    public static final int LAKE_DEPTH = TerrariaHelper.optimizationConfig.getInt("worldGen.params.lakeDepth", 15);
    public static final int ROLLING_HILLS_HEIGHT = TerrariaHelper.optimizationConfig.getInt("worldGen.params.rollingHillsHeight", 40);
    public static final int MOUNTAIN_HEIGHT = TerrariaHelper.optimizationConfig.getInt("worldGen.params.mountainHeight", 80);
    public static final int STONE_VEIN_ROUGH_SKETCH_DIAMETER = Math.max( TerrariaHelper.optimizationConfig.getInt("worldGen.opt.stoneVeinSketchSize", 3), 1);
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
    public static void generateTopSoil(ChunkData chunk, Boolean[][][] stoneVeinFlag, int height, int soilHeight, int i, int j, Biome biome, int yOffset) {
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
        double soilLayerHeight = height - soilHeight;
        // setup soil/stone layers
        for (int y = Math.min(height - yOffset, WorldHelper.CAVERN_Y_BELOW_BEDROCK); y > 0; y--) {
            if (! chunk.getType(i, y, j).isSolid()) continue;
            Material matToSet;
            int effectualY = y + yOffset;
            boolean isStoneVein = stoneVeinFlag[i][y][j];
            // soil layer
            if (effectualY > soilLayerHeight) {
                if (isStoneVein)
                    matToSet = matStone;
                else
                    matToSet = matSoil;
            }
            // stone layer
            else {
                if (isStoneVein && effectualY <= 50)
                    matToSet = matSoil;
                else
                    matToSet = matStone;
            }
            // place the block
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

        // merge biome types with the same generation (keys merged into one representative)
        HashMap<WorldHelper.BiomeType, Double> providerWts = new HashMap<>();
        for (WorldHelper.BiomeType biomeType : biomeSignificances.keySet()) {
            switch (biomeType) {
                // these biomes use their own provider
                case SULPHUROUS_OCEAN:
                case OCEAN:
                case JUNGLE:
                case ASTRAL_INFECTION:
                case DESERT:
                    providerWts.put(biomeType, biomeSignificances.get(biomeType));
                    break;
                // other biomes use the normal provider
                default: {
                    providerWts.put(biomeType, providerWts.getOrDefault(biomeType, 0d) + biomeSignificances.get(biomeType));
                }
            }
        }

        // calculate all distinct providers' height with weighted sum
        for (WorldHelper.BiomeType biomeType : providerWts.keySet()) {
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
    private static void setupRatioMapsAtLocation(int currX, int currZ, int i, int j,
                                                 int[][] heightMap, int[][] soilMap, double[][] caveMultiMap,
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

        // soil height, dependent on the biome and current height
        double soilHeight;
        switch (biomeFeature.evaluatedBiome) {
            case SULPHUROUS_OCEAN:
            case OCEAN:
            case JUNGLE:
                soilHeight = Math.min(30, totalHeight) - 0.5;
                break;
            case ASTRAL_INFECTION:
                soilHeight = SOIL_HEIGHT_ASTRAL.getY(totalHeight);
                break;
            case DESERT:
                soilHeight = SOIL_HEIGHT_DESERT.getY(totalHeight);
                break;
            default: {
                soilHeight = SOIL_HEIGHT_NORMAL.getY(totalHeight);
                soilHeight += SOIL_HEIGHT_HUM_NORMAL.getY(biomeFeature.features[HUMIDITY]);
            }
        }
        soilMap[i][j] = Math.max((int) soilHeight, 0);

        // cave: compare the two dominating driving power to prevent multiple biomes diluting the result
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
    public static void generateMaps(int blockXStart, int blockZStart, int[][] heightMap, int[][] soilMap, double[][] caveMultiMap,
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
                setupRatioMapsAtLocation(currX, currZ, i, j, heightMap, soilMap, caveMultiMap, caveGen);
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
    protected static boolean checkStoneNoise(double blockX, double effectualY, double blockZ, int maxHeight) {
        if (effectualY > maxHeight) return false;
        return STONE_VEIN_NOISE.noise(blockX, effectualY, blockZ, 2, 0.5, false) > 0.5;
    }

    // thread-optimized
    public static Boolean[][][] setupStoneFlags(int xStart, int zStart, int yOffset, int[][] heightMap) {
        int testInfoIndex = yOffset == Y_OFFSET_OVERWORLD ? 0 : 1;
        // print time benchmarking info
        if (LOG_TIMING && testStoneGenAmount[testInfoIndex] % 20 == 19) {
            TerrariaHelper.LOGGER.info("泥土/石头镶嵌所属世界：" + testInfoIndex + "（0为地表，1为地下）");
            TerrariaHelper.LOGGER.info("泥土/石头镶嵌估算平均使用时间（单位：纳秒）: " + (testStoneSetupDurTotal[testInfoIndex] / testStoneGenAmount[testInfoIndex]) );
            TerrariaHelper.LOGGER.info("泥土/石头镶嵌总体平均使用时间（单位：纳秒，含估算时长）: " + (testStoneDurTotal[testInfoIndex] / testStoneGenAmount[testInfoIndex]) );
        }

        // setup cave estimates
        long timing = System.nanoTime();

        Boolean[][][] caveFlags;
        try {
            // create estimates
            caveFlags = WorldGenHelper.getChunkFlag(STONE_VEIN_ROUGH_SKETCH_DIAMETER,
                    (info) -> checkStoneNoise(xStart + info[0], yOffset + info[1], zStart + info[2], heightMap[info[0]][info[2]]),
                    LOG_TIMING, testStoneSetupDurTotal, testInfoIndex);
        }
        catch (Exception e) {
            TerrariaHelper.LOGGER.warning("利用线程计算泥土/石头镶嵌时以下错误出现。本区块改为以单线程模式生成。");
            e.printStackTrace();

            caveFlags = setupStoneFlagsSingleThread(xStart, zStart, yOffset, heightMap);
        }
        if (LOG_TIMING) {
            testStoneDurTotal[testInfoIndex] += (System.nanoTime() - timing);
            testStoneGenAmount[testInfoIndex] ++;
        }
        return caveFlags;
    }

    // single-threaded, brute force as a fallback for rare exception scenarios
    public static Boolean[][][] setupStoneFlagsSingleThread(int xStart, int zStart, int yOffset, int[][] heightMap) {
        // setup result grid
        Boolean[][][] result = new Boolean[16][256][16];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 256; j++) {
                int actualY = j + yOffset;
                for (int k = 0; k < 16; k++) {
                    result[i][j][k] = checkStoneNoise(xStart + i, actualY, zStart + k, heightMap[i][k]);
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
            TerrariaHelper.LOGGER.info("地表区块基础生成-地形+土壤高度 平均使用时间（单位：纳秒）: " + (testGenDurTotal[1] / testGenAmount) );
            TerrariaHelper.LOGGER.info("地表区块基础生成-粗略地形与岩石 平均使用时间（单位：纳秒）: " + (testGenDurTotal[2] / testGenAmount) );
            TerrariaHelper.LOGGER.info("地表区块基础生成-土壤 平均使用时间（单位：纳秒）: " + (testGenDurTotal[3] / testGenAmount) );
        }

        long timing;
        // init info maps; the memoization would also set up the biome.
        int[][] heightMap = new int[16][16];
        int[][] soilMap = new int[16][16];
        double[][] caveMultiMap = new double[16][16];
        generateMaps(x << 4, z << 4, heightMap, soilMap, caveMultiMap, CAVE_GENERATOR_OVERWORLD, biome, Y_OFFSET_OVERWORLD);


        timing = System.nanoTime();
        // init terrain (height & stone)
        ChunkData chunk = createChunkData(world);
        initializeTerrain(chunk, x << 4, z << 4, biome, Y_OFFSET_OVERWORLD, heightMap);
        Boolean[][][] stoneFlags = setupStoneFlags(x << 4, z << 4, Y_OFFSET_OVERWORLD, heightMap);
        // record timing info
        testGenDurTotal[2] += System.nanoTime() - timing;

        // generate cave
        CAVE_GENERATOR_OVERWORLD.populate(world, chunk, biome, heightMap, x << 4, z << 4, caveMultiMap);

        timing = System.nanoTime();
        // finally place soil
        for (int i = 0; i < 16; i ++)
            for (int j = 0; j < 16; j ++)
                generateTopSoil(chunk, stoneFlags, heightMap[i][j], soilMap[i][j], i, j, biome.getBiome(i, j), Y_OFFSET_OVERWORLD);
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