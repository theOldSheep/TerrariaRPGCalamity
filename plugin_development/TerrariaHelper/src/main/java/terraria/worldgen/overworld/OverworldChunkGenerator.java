package terraria.worldgen.overworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.noise.PerlinOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.Interpolate;
import terraria.worldgen.Interpolate.InterpolatePoint;

import java.util.*;

public class OverworldChunkGenerator extends ChunkGenerator {
    // optimization & logging variables
    public static final boolean LOG_TIMING = TerrariaHelper.settingConfig.getBoolean("worldGen.opt.chunkPrepDurationLog", false);
    static long[] testGenDurTotal = {0, 0, 0, 0};
    static long testGenAmount = 0;

    // world generator parameters
    public static final int OCTAVES_CAVE = TerrariaHelper.settingConfig.getInt("worldGen.params.caveOctaves", 4);
    public static final int NEARBY_BIOME_SAMPLE_RADIUS = TerrariaHelper.settingConfig.getInt("worldGen.params.nearbyBiomeSampleRadius", 25);
    public static final int LAND_HEIGHT = TerrariaHelper.settingConfig.getInt("worldGen.params.landHeight", 100);
    public static final int SEA_LEVEL = TerrariaHelper.settingConfig.getInt("worldGen.params.seaLevel", 90);
    public static final int RIVER_DEPTH = TerrariaHelper.settingConfig.getInt("worldGen.params.riverDepth", 25);
    public static final int LAKE_DEPTH = TerrariaHelper.settingConfig.getInt("worldGen.params.lakeDepth", 30);
    public static final int PLATEAU_HEIGHT = TerrariaHelper.settingConfig.getInt("worldGen.params.plateauHeight", 40);
    public static final int LAVA_LEVEL = -150;
    public static final int Y_OFFSET_OVERWORLD = 0;
    public static final int HEIGHT_SAMPLING_DIAMETER;
    private static final double
            HEIGHT_SAMPLE_FACTOR_SUM;
    private static final double[][] HEIGHT_INFLUENCE_FACTOR;
    // initialize the mapping in which nearby heights influences the current block
    static {
        HEIGHT_SAMPLING_DIAMETER = 1 + NEARBY_BIOME_SAMPLE_RADIUS * 2;
        double[][] influence_factors = new double[HEIGHT_SAMPLING_DIAMETER][HEIGHT_SAMPLING_DIAMETER];
//        double furthestDistance = NEARBY_BIOME_SAMPLE_RADIUS * Math.sqrt(2);
        for (int i = 0; i <= NEARBY_BIOME_SAMPLE_RADIUS; i ++)
            for (int j = 0; j <= NEARBY_BIOME_SAMPLE_RADIUS; j ++) {
                double currFactor;
//                double currDist = Math.sqrt(i * i + j * j);
//                currFactor = Math.cos( (Math.PI / 2) * currDist / furthestDistance);
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
    public static PerlinOctaveGenerator riverGenerator, lakeGenerator,
            stoneVeinGenerator;
    public static Interpolate terrainHeightProvider, jungleHeightProvider, astralHeightProvider, desertHeightProvider, oceanHeightProvider,
            erosionRatioProvider, oceanErosionHeightProvider,
            riverRatioProvider, lakeRatioProvider;
    static OverworldChunkGenerator instance = new OverworldChunkGenerator();
    static List<BlockPopulator> populators;
    static OverworldCaveGenerator CAVE_GENERATOR_OVERWORLD;
    private OverworldChunkGenerator() {
        super();
        // terrain noise functions
        Random rdm = new Random(TerrariaHelper.WORLD_SEED);

        riverGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        riverGenerator.setScale(0.0005);
        lakeGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        lakeGenerator.setScale(0.005);

        stoneVeinGenerator = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        stoneVeinGenerator.setScale(0.05);
        // constants
        // interpolates
        terrainHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-1       , LAND_HEIGHT + 10),
                InterpolatePoint.create(-0.4     , LAND_HEIGHT),
                InterpolatePoint.create(-0.3     , LAND_HEIGHT + 15),
                InterpolatePoint.create(-0.15    , LAND_HEIGHT + 40),
                InterpolatePoint.create(0        , LAND_HEIGHT + 25),
                InterpolatePoint.create(0.15     , LAND_HEIGHT + 10),
                InterpolatePoint.create(0.25     , LAND_HEIGHT),
                InterpolatePoint.create(0.5      , LAND_HEIGHT + 25),
                InterpolatePoint.create(0.6      , LAND_HEIGHT + 40),
                InterpolatePoint.create(0.7      , LAND_HEIGHT + 55),
                InterpolatePoint.create(1        , LAND_HEIGHT + 60),
        }, "terrain_heightmap");
        jungleHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-1       , SEA_LEVEL - 10),
                InterpolatePoint.create(-0.6     , LAND_HEIGHT),
                InterpolatePoint.create(-0.4     , SEA_LEVEL - 5),
                InterpolatePoint.create(-0.2     , LAND_HEIGHT),
                InterpolatePoint.create(0        , LAND_HEIGHT + 10),
                InterpolatePoint.create(0.2      , LAND_HEIGHT),
                InterpolatePoint.create(0.4      , SEA_LEVEL - 5),
                InterpolatePoint.create(0.6      , LAND_HEIGHT),
                InterpolatePoint.create(1        , SEA_LEVEL - 10),
        }, "jungle_heightmap");
        astralHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-1       , LAND_HEIGHT + PLATEAU_HEIGHT),
                InterpolatePoint.create(-0.35    , LAND_HEIGHT + PLATEAU_HEIGHT - 10),
                InterpolatePoint.create(-0.25    , LAND_HEIGHT + 10),
                InterpolatePoint.create(0        , LAND_HEIGHT),
                InterpolatePoint.create(0.25     , LAND_HEIGHT + 10),
                InterpolatePoint.create(0.35     , LAND_HEIGHT + PLATEAU_HEIGHT - 10),
                InterpolatePoint.create(1        , LAND_HEIGHT + PLATEAU_HEIGHT),
        }, "astral_heightmap");
        desertHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-1       , LAND_HEIGHT),
                InterpolatePoint.create(-0.6     , LAND_HEIGHT + 40),
                InterpolatePoint.create(-0.2     , LAND_HEIGHT),
                InterpolatePoint.create(0        , LAND_HEIGHT + 30),
                InterpolatePoint.create(0.35     , LAND_HEIGHT),
                InterpolatePoint.create(0.5      , LAND_HEIGHT + 25),
                InterpolatePoint.create(1        , LAND_HEIGHT + 55),
        }, "desert_heightmap");
        oceanHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.75  ,   SEA_LEVEL - 25),
                InterpolatePoint.create(-0.65  ,   SEA_LEVEL - 20),
                InterpolatePoint.create(-0.575 ,   SEA_LEVEL - 10),
                InterpolatePoint.create(-0.535 ,   SEA_LEVEL + 2),
                InterpolatePoint.create(-0.5   ,      LAND_HEIGHT),
                InterpolatePoint.create(0.5    ,      LAND_HEIGHT),
                InterpolatePoint.create(0.55   ,   SEA_LEVEL + 2),
                InterpolatePoint.create(0.625  ,   0.5),
        }, "ocean_heightmap");

        erosionRatioProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-1       , 0.25),
                InterpolatePoint.create(-0.6     , 1),
                InterpolatePoint.create(0        , 1),
                InterpolatePoint.create(0.6      , 0),
                InterpolatePoint.create(1        , 0.6),
        }, "erosion_ratio_map");
        oceanErosionHeightProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-1       , 50),
                InterpolatePoint.create(-0.5     , 30),
                InterpolatePoint.create(0        , 10),
                InterpolatePoint.create(0.5      , 0),
                InterpolatePoint.create(1        , -10),
        }, "erosion_ocean_height_map");
        riverRatioProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-0.075 , 0),
                InterpolatePoint.create(-0.05  , 0.1),
                InterpolatePoint.create(-0.035 , 0.4),
                InterpolatePoint.create(-0.025 , 0.5),
                InterpolatePoint.create(0      , 1),
                InterpolatePoint.create(0.025  , 0.5),
                InterpolatePoint.create(0.035  , 0.4),
                InterpolatePoint.create(0.05   , 0.1),
                InterpolatePoint.create(0.075  , 0),
        }, "river_ratio_map");
        lakeRatioProvider = new Interpolate(new InterpolatePoint[]{
                InterpolatePoint.create(-1    , 1),
                InterpolatePoint.create(-0.6  , 0.6),
                InterpolatePoint.create(-0.3  , 0),
                InterpolatePoint.create(0.3   , 0),
                InterpolatePoint.create(0.6   , 0.6),
                InterpolatePoint.create(1     , 1),
        }, "lake_ratio_map");
        // block populators
        CAVE_GENERATOR_OVERWORLD = new OverworldCaveGenerator(Y_OFFSET_OVERWORLD, TerrariaHelper.WORLD_SEED, OCTAVES_CAVE);
        populators = new ArrayList<>();
        populators.add(new OverworldBlockGenericPopulator());
        populators.add(new OrePopulator(Y_OFFSET_OVERWORLD));
        populators.add(new FoliagePopulator());
        populators.add(new StructurePopulator(true));
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
        double topSoilThicknessRandomizer = stoneVeinGenerator.noise(blockX, blockZ, 2, 0.5, false);
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
    static double getTerrainHeight(Biome biome, int currX, int currZ) {
        // special biome setup
        Interpolate heightProvider = null;
        OverworldBiomeGenerator.BiomeFeature features = OverworldBiomeGenerator.getBiomeFeature(currX, currZ);

        double result = 1;

        switch (biome) {
            case OCEAN:
            case FROZEN_OCEAN: {
                double continentalnessFeature = features.features[OverworldBiomeGenerator.BiomeFeature.CONTINENTALNESS];
                double erosionFeature = features.features[OverworldBiomeGenerator.BiomeFeature.EROSION];

                double oceanHeight = oceanHeightProvider.getY(continentalnessFeature);
                double randomOffset = oceanErosionHeightProvider.getY(erosionFeature);
                // for abyss near the bottom, this random terrain offset should gradually disappear.
                if (oceanHeight > 50)
                    result = oceanHeight + randomOffset;
                else if (oceanHeight > 0)
                    result = oceanHeight + randomOffset * (oceanHeight / 50);
                else
                    result = oceanHeight;
                break;
            }
            case JUNGLE:
                heightProvider = jungleHeightProvider;
                break;
            case MESA:
                heightProvider = astralHeightProvider;
                break;
            case DESERT:
                heightProvider = desertHeightProvider;
                break;
            default:
                heightProvider = terrainHeightProvider;
        }

        if (heightProvider != null) {
            result = heightProvider.getY(
                    features.features[OverworldBiomeGenerator.BiomeFeature.TERRAIN_H]);
        }

        double riverNoise = riverGenerator.noise(currX, currZ, 2, 0.5, false);
        double lakeNoise = lakeGenerator.noise(currX, currZ, 2, 0.5, false);

        double heightOffset = result - LAND_HEIGHT;
        double terrainScalingFactor = erosionRatioProvider.getY(
                features.features[OverworldBiomeGenerator.BiomeFeature.EROSION] );
        // account for rivers and lakes
        double riverRatio = riverRatioProvider.getY(riverNoise),
                lakeRatio = lakeRatioProvider.getY(lakeNoise);
        if (riverRatio > 1e-5)
            heightOffset = interpolateWaterBodyHeightOffset(heightOffset, riverRatio, RIVER_DEPTH);
        if (lakeRatio > 1e-5)
            heightOffset = interpolateWaterBodyHeightOffset(heightOffset, lakeRatio, LAKE_DEPTH);
        // water bodies won't be too shallow in this way
        if (heightOffset > 0 && result > SEA_LEVEL)
            heightOffset *= terrainScalingFactor;
        result = LAND_HEIGHT + heightOffset;

        return result;
    }
    // set up the "desired" block height and special cave ratio at a specific column of a chunk.
    private static void setupHeightAndCaveRatioAtLocation(int currX, int currZ, int i, int j,
                                                          int[][] heightMap, double[][] caveMultiMap,
                                                          HashMap<Biome, Double> nearbyBiomeMap, OverworldCaveGenerator caveGen) {
        // calculate each nearby biome's contribution to the current land height
        HashMap<Biome, Double> biomeWeights = new HashMap<>();
        double totalWeight = 0;
        for (Map.Entry<Biome, Double> pair : nearbyBiomeMap.entrySet()) {
            double valSqr = pair.getValue() * pair.getValue();
            biomeWeights.put(pair.getKey(), valSqr);
            totalWeight += valSqr;
        }
        // save normalized height sum
        double totalHeight = 0;
        for (Biome b : nearbyBiomeMap.keySet()) {
            double weightCurr = biomeWeights.get(b) / totalWeight;
            totalHeight += getTerrainHeight(b, currX, currZ) * weightCurr;
        }
        // terrain near the spawning point will be around LAND_HEIGHT; use the double value to prevent overflow.
        double distToSpawn = Math.sqrt((double) currX * (double) currX + (double) currZ * (double) currZ);
        if (distToSpawn < OverworldBiomeGenerator.SPAWN_LOC_PROTECTION_RADIUS) {
            distToSpawn /= OverworldBiomeGenerator.SPAWN_LOC_PROTECTION_RADIUS;
            totalHeight *= distToSpawn;
            totalHeight += (1 - distToSpawn) * LAND_HEIGHT;
        }
        heightMap[i][j] = (int) Math.round( totalHeight );

        //
        // setup cave multi according to nearby biomes
        //
        double caveMulti = 0;
        for (Biome bom : nearbyBiomeMap.keySet()) {
            caveMulti += caveGen.getCavernNoiseMulti(bom) * nearbyBiomeMap.get(bom);
        }
        caveMulti /= HEIGHT_SAMPLE_FACTOR_SUM;
        // save cave multi into caveMultiMap
        caveMultiMap[i][j] = caveMulti;
    }
    // updates the biome information
    private static void tweakBiome(Biome[][] biomesMemoization, BiomeGrid biome, int yOffset) {
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 16; j++) {
                Biome biomeToSet = biomesMemoization[NEARBY_BIOME_SAMPLE_RADIUS + i][NEARBY_BIOME_SAMPLE_RADIUS + j];
                if (yOffset >= 0)
                    biome.setBiome(i, j, biomeToSet);
                else
                    biome.setBiome(i, j, OverworldBiomeGenerator.getUndergroundEquivalent(biomeToSet));
            }
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
        // create memoization 2D array for the nearby biomes
        int memoiSize = 16 + NEARBY_BIOME_SAMPLE_RADIUS * 2;
        Biome[][] biomesMemoization = new Biome[memoiSize][memoiSize];
        for (int i = 0; i < memoiSize; i ++)
            for (int j = 0; j < memoiSize; j ++) {
                biomesMemoization[i][j] = OverworldBiomeGenerator.getBiome(
                        blockXStart - NEARBY_BIOME_SAMPLE_RADIUS + i, blockZStart - NEARBY_BIOME_SAMPLE_RADIUS + j);
            }
        // update the biome grid
        tweakBiome(biomesMemoization, biomeGrid, yOffset);
        // record timing info
        testGenDurTotal[0] += System.nanoTime() - timing;

        timing = System.nanoTime();
        int currX, currZ;

        // setup height info according to nearby biomes at both offset 0.
        // then use sliding window technique to derive the height everywhere.
        HashMap<Biome, Double> nearbyBiomeMap = new HashMap<>();
        for (int sampleIdxX = 0; sampleIdxX <= NEARBY_BIOME_SAMPLE_RADIUS * 2; sampleIdxX++) {
            for (int sampleIdxZ = 0; sampleIdxZ <= NEARBY_BIOME_SAMPLE_RADIUS * 2; sampleIdxZ++) {
                Biome currBiome = biomesMemoization[sampleIdxX][sampleIdxZ];
                double updatedBiomeIntensity = nearbyBiomeMap.getOrDefault(currBiome, 0d) +
                        HEIGHT_INFLUENCE_FACTOR[sampleIdxX][sampleIdxZ];
                nearbyBiomeMap.put(currBiome, updatedBiomeIntensity);
            }
        }

        // loop through all blocks in a zigzag fashion with the sliding window technique.
        boolean increasingZ = true;
        for (int i = 0; i < 16; i ++) {
            currX = blockXStart + i;
            for (int j = 0; j < 16; j++) {
                // account for shift direction
                int actualJ = increasingZ ? j : 15 - j;
                currZ = blockZStart + actualJ;

                // use actualJ to pass in the correct index corresponding to currZ
                setupHeightAndCaveRatioAtLocation(currX, currZ, i, actualJ, heightMap, caveMultiMap, nearbyBiomeMap, caveGen);

                // sliding window technique
                if (j + 1 < 16) {
                    slidingWindowStep(nearbyBiomeMap, biomesMemoization, i, actualJ, false, increasingZ);
                }
            }
            // sliding window technique.
            if (i + 1 < 16) {
                int zOffset = increasingZ ? 15 : 0;
                slidingWindowStep(nearbyBiomeMap, biomesMemoization, i, zOffset, true, increasingZ);
            }
            // reverse the direction to traverse
            increasingZ = !increasingZ;
        }
        // record timing info
        testGenDurTotal[1] += System.nanoTime() - timing;
    }
    /** helper function; handles a single sliding window step
     * @param nearbyBiomeMap biome map
     * @param biomesMemoization memoized nearby biomes
     * @param x the x position (0-15)
     * @param z the z position (0-15)
     * @param isXDirection true if the sliding window is moving in the x direction
     * @param increasingZ true if the sliding window is moving in the increasing z direction
     */
    private static void slidingWindowStep(HashMap<Biome, Double> nearbyBiomeMap, Biome[][] biomesMemoization,
                                          int x, int z, boolean isXDirection, boolean increasingZ) {
        // note that x + NEARBY_BIOME_SAMPLE_RADIUS is the actual index in memoization
        // same for z
        int indAddX, indAddZ;
        int indDropX, indDropZ;
        int xStep, zStep;
        // sliding window in the x direction
        if (isXDirection) {
            // add one to include the information just beyond the current scope
            // (x + NEARBY_BIOME_SAMPLE_RADIUS) + NEARBY_BIOME_SAMPLE_RADIUS + 1
            indAddX = x + NEARBY_BIOME_SAMPLE_RADIUS * 2 + 1;
            // (x + NEARBY_BIOME_SAMPLE_RADIUS) - NEARBY_BIOME_SAMPLE_RADIUS
            indDropX = x;
            // (z + NEARBY_BIOME_SAMPLE_RADIUS) - NEARBY_BIOME_SAMPLE_RADIUS
            indAddZ = z;
            indDropZ = z;
            // step in the z direction
            xStep = 0;
            zStep = 1;
        }
        // sliding in the z direction; note this might be increasing or decreasing.
        else {
            // (x + NEARBY_BIOME_SAMPLE_RADIUS) - NEARBY_BIOME_SAMPLE_RADIUS
            indAddX = x;
            indDropX = x;
            // add one to include the information just beyond the current scope
            indAddZ = z + NEARBY_BIOME_SAMPLE_RADIUS + (NEARBY_BIOME_SAMPLE_RADIUS + 1) * (increasingZ ? 1 : -1);
            indDropZ = z + NEARBY_BIOME_SAMPLE_RADIUS - (NEARBY_BIOME_SAMPLE_RADIUS) * (increasingZ ? 1 : -1);
            // step in the x direction
            xStep = 1;
            zStep = 0;
        }
        // loop to add and remove the two strips for transition
        for (int sampleIdx = 0; sampleIdx <= NEARBY_BIOME_SAMPLE_RADIUS * 2; sampleIdx++) {
            double influence_factor = HEIGHT_INFLUENCE_FACTOR[sampleIdx][0];

            Biome currBiome_drop = biomesMemoization[indDropX][indDropZ];
            double updatedIntensity_drop = nearbyBiomeMap.getOrDefault(currBiome_drop, 0d) - influence_factor;
            nearbyBiomeMap.put(currBiome_drop, updatedIntensity_drop);

            Biome currBiome_add =  biomesMemoization[indAddX][indAddZ];
            double updatedIntensity_add = nearbyBiomeMap.getOrDefault(currBiome_add, 0d) + influence_factor;
            nearbyBiomeMap.put(currBiome_add,  updatedIntensity_add);

            // update indexes
            indAddX += xStep;
            indDropX += xStep;
            indAddZ += zStep;
            indDropZ += zStep;
        }
    }

    // generates the height and cave multiplier mapping of a chunk
    public static void generateMaps(int blockXStart, int blockZStart, int[][] heightMap, double[][] caveMultiMap,
                                    OverworldCaveGenerator caveGen, BiomeGrid biomeGrid, int yOffset) {
        long timing = System.nanoTime();
        // create memoization 2D array for the nearby biomes
        int memoiSize = 16 + NEARBY_BIOME_SAMPLE_RADIUS * 2;
        Biome[][] biomesMemoization = new Biome[memoiSize][memoiSize];
        for (int i = 0; i < memoiSize; i ++)
            for (int j = 0; j < memoiSize; j ++) {
                biomesMemoization[i][j] = OverworldBiomeGenerator.getBiome(
                        blockXStart - NEARBY_BIOME_SAMPLE_RADIUS + i, blockZStart - NEARBY_BIOME_SAMPLE_RADIUS + j);
            }
        // update the biome grid
        tweakBiome(biomesMemoization, biomeGrid, yOffset);
        // record timing info
        testGenDurTotal[0] += System.nanoTime() - timing;

        timing = System.nanoTime();
        int currX, currZ;

        // setup height info according to nearby biomes at both offset 0.
        // Then use sliding window technique to derive the height everywhere.
        HashMap<Biome, Double> nearbyBiomeMapBackup = new HashMap<>();
        for (int sampleIdxX = 0; sampleIdxX <= NEARBY_BIOME_SAMPLE_RADIUS * 2; sampleIdxX++) {
            for (int sampleIdxZ = 0; sampleIdxZ <= NEARBY_BIOME_SAMPLE_RADIUS * 2; sampleIdxZ++) {
                Biome currBiome = biomesMemoization[sampleIdxX][sampleIdxZ];
                double updatedBiomeIntensity = nearbyBiomeMapBackup.getOrDefault(currBiome, 0d) +
                        HEIGHT_INFLUENCE_FACTOR[sampleIdxX][sampleIdxZ];
                nearbyBiomeMapBackup.put(currBiome, updatedBiomeIntensity);
            }
        }

        // loop through all blocks.
        HashMap<Biome, Double> nearbyBiomeMap;
        for (int i = 0; i < 16; i ++) {
            currX = blockXStart + i;
            nearbyBiomeMap = (HashMap<Biome, Double>) nearbyBiomeMapBackup.clone();
            for (int j = 0; j < 16; j++) {
                currZ = blockZStart + j;

                setupHeightAndCaveRatioAtLocation(currX, currZ, i, j, heightMap, caveMultiMap, nearbyBiomeMap, caveGen);

                // sliding window technique
                if (j + 1 < 16) {
                    int currSample_dropZ = j;
                    int currSample_addZ = j + NEARBY_BIOME_SAMPLE_RADIUS * 2 + 1;
                    for (int sampleIdx = 0; sampleIdx <= NEARBY_BIOME_SAMPLE_RADIUS * 2; sampleIdx++) {
                        double influence_factor = HEIGHT_INFLUENCE_FACTOR[0][sampleIdx];

                        Biome currBiome_drop = biomesMemoization[sampleIdx + i][currSample_dropZ];
                        double updatedIntensity_drop = nearbyBiomeMap.getOrDefault(currBiome_drop, 0d) - influence_factor;
                        nearbyBiomeMap.put(currBiome_drop, updatedIntensity_drop);

                        Biome currBiome_add =  biomesMemoization[sampleIdx + i][currSample_addZ];
                        double updatedIntensity_add = nearbyBiomeMap.getOrDefault(currBiome_add, 0d) + influence_factor;
                        nearbyBiomeMap.put(currBiome_add,  updatedIntensity_add);
                    }
                }
            }
            // sliding window technique.
            if (i + 1 < 16) {
                int currSample_dropX = i;
                int currSample_addX  = i + NEARBY_BIOME_SAMPLE_RADIUS * 2 + 1;
                for (int sampleIdx = 0; sampleIdx <= NEARBY_BIOME_SAMPLE_RADIUS * 2; sampleIdx++) {
                    double influence_factor = HEIGHT_INFLUENCE_FACTOR[sampleIdx][0];

                    Biome currBiome_drop = biomesMemoization[currSample_dropX][sampleIdx];
                    double updatedIntensity_drop = nearbyBiomeMapBackup.getOrDefault(currBiome_drop, 0d) - influence_factor;
                    nearbyBiomeMapBackup.put(currBiome_drop, updatedIntensity_drop);

                    Biome currBiome_add =  biomesMemoization[currSample_addX][sampleIdx];
                    double updatedIntensity_add = nearbyBiomeMapBackup.getOrDefault(currBiome_add, 0d) + influence_factor;
                    nearbyBiomeMapBackup.put(currBiome_add,  updatedIntensity_add);
                }
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
        return stoneVeinGenerator.noise(blockX, effectualY, blockZ, 2, 0.5, false) > 0.5;
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
            TerrariaHelper.LOGGER.info("区块基础生成（生物群系，高度，土壤，岩石）时长：");
            TerrariaHelper.LOGGER.info("区块基础生成-载入生物群系 平均使用时间（单位：纳秒）: " + (testGenDurTotal[0] / testGenAmount) );
            TerrariaHelper.LOGGER.info("区块基础生成-地形高度 平均使用时间（单位：纳秒）: " + (testGenDurTotal[1] / testGenAmount) );
            TerrariaHelper.LOGGER.info("区块基础生成-粗略地形与岩石 平均使用时间（单位：纳秒）: " + (testGenDurTotal[2] / testGenAmount) );
            TerrariaHelper.LOGGER.info("区块基础生成-土壤 平均使用时间（单位：纳秒）: " + (testGenDurTotal[3] / testGenAmount) );
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