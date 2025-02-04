package terraria.worldgen.overworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.noise.SimplexOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.worldgen.WorldGenHelper;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverworldCaveGenerator {
    int yOffset;
    SimplexOctaveGenerator cheeseCaveGenerator, spaghettiGeneratorOne, spaghettiGeneratorTwo,
            sunkenSeaAbyssCaveGenerator;


    int testInfoIndex;
    long[] testCaveDetailDurTotal = {0, 0},
                testCaveSetupDurTotal = {0, 0}, testCaveGenAmount = {0, 0};
    static final boolean CAVE_GEN_TIME_LOG = TerrariaHelper.optimizationConfig.getBoolean("worldGen.opt.caveGenDurationLog", false);
    static final int
            CAVE_ROUGH_SKETCH_DIAMETER = Math.max( TerrariaHelper.optimizationConfig.getInt("worldGen.opt.caveSketchSize", 3), 0),
            CAVE_ROUGH_SKETCH_THREADS = Math.max( TerrariaHelper.optimizationConfig.getInt("worldGen.opt.caveSketchThreads", 3), 1),
            CAVE_DETAIL_THREADS = Math.max( TerrariaHelper.optimizationConfig.getInt("worldGen.opt.caveDetailThreads", 8), 1);


    static ExecutorService poolRoughSketch = Executors.newFixedThreadPool(CAVE_ROUGH_SKETCH_THREADS);
    static ExecutorService poolFineDetail = Executors.newFixedThreadPool(CAVE_DETAIL_THREADS);

    public OverworldCaveGenerator(int yOffset, long seed, int OCTAVES) {
        this.yOffset = yOffset;
        this.testInfoIndex = this.yOffset == OverworldChunkGenerator.Y_OFFSET_OVERWORLD ? 0 : 1;

        Random rdm = new Random(seed);
        rdm.nextInt();
        cheeseCaveGenerator = new SimplexOctaveGenerator(rdm.nextLong(), OCTAVES);
        cheeseCaveGenerator.setScale(0.005);
        cheeseCaveGenerator.setYScale(cheeseCaveGenerator.getYScale() * 5 / 3);
        spaghettiGeneratorOne = new SimplexOctaveGenerator(rdm.nextLong(), OCTAVES);
        spaghettiGeneratorOne.setScale(0.005);
        spaghettiGeneratorOne.setYScale(spaghettiGeneratorOne.getYScale() * 5 / 3);
        spaghettiGeneratorTwo = new SimplexOctaveGenerator(rdm.nextLong(), OCTAVES);
        spaghettiGeneratorTwo.setScale(0.005);
        spaghettiGeneratorTwo.setYScale(spaghettiGeneratorTwo.getYScale() * 5 / 3);
        sunkenSeaAbyssCaveGenerator = new SimplexOctaveGenerator(rdm.nextLong(), OCTAVES);
        sunkenSeaAbyssCaveGenerator.setScale(0.01);
        sunkenSeaAbyssCaveGenerator.setYScale(sunkenSeaAbyssCaveGenerator.getYScale() * 3);

    }
    public double getCavernNoiseMulti(Biome biome) {
        switch (biome) {
            // caves for these biomes will be customized
            case DESERT:            // sunken sea/desert
            case MUTATED_DESERT:    // sunken sea/desert
                return yOffset == OverworldChunkGenerator.Y_OFFSET_OVERWORLD ? 1 : 0;
            case COLD_BEACH:        // sulphurous beach
            case FROZEN_OCEAN:      // sulphurous ocean
            case DEEP_OCEAN:        // abyss
                return 0;
            default:
                return 1;
        }
    }
    // gets the "air" material for the biome
    private static Material getAirMaterial(Biome biome, double caveMulti) {
        switch (biome) {
            // caves for these biomes will be filled with water
            case MUTATED_DESERT:    // sunken sea
            case COLD_BEACH:        // sulphurous beach
            case FROZEN_OCEAN:      // sulphurous ocean
            case DEEP_OCEAN:        // abyss
                return caveMulti < 0.5 ? Material.STATIONARY_WATER : Material.AIR;
            default:
                return Material.AIR;
        }
    }
    private double[] getCavernNoise(Biome biome, int surfaceHeight, int currX, int effectualY, int currZ, double noiseMulti) {
        double[] result = new double[]{-1, -1, -1};
        // way above the terrain height? Don't even bother generating!
        if (effectualY > surfaceHeight + CAVE_ROUGH_SKETCH_DIAMETER)
            return result;
        // near surface
        if (effectualY > 30) {
            boolean hasRiver = surfaceHeight - yOffset - 2 < OverworldChunkGenerator.SEA_LEVEL;
            double caveNoiseOffset = ((double) (effectualY - 30)) / 20;
            if (caveNoiseOffset < 2) {
                result[0] = cheeseCaveGenerator.noise(currX, effectualY, currZ, 2, 0.5, false) - caveNoiseOffset;
            }
            switch (biome) {
                case FOREST:        // forest
                case JUNGLE:        // jungle
                case DESERT:        // desert
                case TAIGA_COLD:    // tundra
                case ICE_FLATS:     // hallow
                case MESA:          // astral infection
                    // only these biomes may have surface spaghetti caves!
                    result[1] = spaghettiGeneratorOne.noise(currX, effectualY, currZ, 2, 0.5, false);
                    result[2] = spaghettiGeneratorTwo.noise(currX, effectualY, currZ, 2, 0.5, false);
                    if (hasRiver) {
                        if (result[1] < 0)
                            result[1] -= caveNoiseOffset;
                        else
                            result[1] += caveNoiseOffset;
                        if (result[2] < 0)
                            result[2] -= caveNoiseOffset;
                        else
                            result[2] += caveNoiseOffset;
                    }
            }
        }
        // not near surface
        else {
            // sunken sea & abyss
            if (yOffset != OverworldChunkGenerator.Y_OFFSET_OVERWORLD) {
                switch (biome) {
                    // sunken sea
                    case DESERT:
                    case MUTATED_DESERT:
                        if (noiseMulti < 1e-5) {
                            result[0] = sunkenSeaAbyssCaveGenerator.noise(currX, effectualY, currZ, 2, 0.5, false);
                            double amplify = 0.4;
                            result[0] += amplify;
                        }
                        return result;
                    // abyss
                    case COLD_BEACH:
                    case FROZEN_OCEAN:
                    case DEEP_OCEAN:
                        if (noiseMulti < 1e-5) {
                            result[0] = sunkenSeaAbyssCaveGenerator.noise(currX, effectualY, currZ, 2, 0.5, false);
                            double amplify = 0.4;
                            // make sure the 3rd level is pretty empty (handled by modifying amplify)
                            if (effectualY < -165) {
                                amplify = 1.75;
                                // ocean floor: -230(25) - -250(5)
                                if (effectualY < -230)
                                    amplify = (effectualY + 230) / 11.5d;
                                // ceiling of 3rd level: -185(75) - -165(95)
                                else if (effectualY > -185)
                                    amplify = Math.max( (- effectualY - 185) / 11.5d , amplify);
                            }

                            result[0] += amplify;
                        }
                        return result;
                }
            }
            // normal cave noise
            result[0] = cheeseCaveGenerator.noise(currX, effectualY, currZ, 2, 0.5, false);
            result[1] = spaghettiGeneratorOne.noise(currX, effectualY, currZ, 2, 0.5, false);
            result[2] = spaghettiGeneratorTwo.noise(currX, effectualY, currZ, 2, 0.5, false);
        }
        double complementNoise = 1 - noiseMulti;
        // equivalent to + (-1) * complementNoise
        result[0] = result[0] * noiseMulti - complementNoise;
        result[1] = result[1] * noiseMulti - complementNoise;
        result[2] = result[2] * noiseMulti - complementNoise;
        return result;
    }
    private boolean validateCaveEstimate(double[] noise) {
        double cheeseThreshold = 0.6;
        double spaghettiThreshold = 0.15;
        return (noise[0] > cheeseThreshold) || (
                    (Math.abs(noise[1]) < spaghettiThreshold) &&
                    (Math.abs(noise[2]) < spaghettiThreshold));
    }
    private boolean validateCave(double[] noise) {
        double cheeseThreshold = 0.6;
        double spaghettiThreshold = 0.15;
        return (noise[0] > cheeseThreshold) || (
                    (Math.abs(noise[1]) < spaghettiThreshold) &&
                    (Math.abs(noise[2]) < spaghettiThreshold));
    }
    private byte hasNearbyCaveEstimate(Boolean[][][] caveEstimates, int estimateX, int estimateY, int estimateZ) {
        boolean allCaves = true, allSolid = true;
        for (int i = estimateX - 1; i <= estimateX + 1; i ++)
            for (int j = estimateY - 1; j <= estimateY + 1; j ++)
                for (int k = estimateZ - 1; k <= estimateZ + 1; k ++)
                    if (!caveEstimates[i][j][k]) allCaves = false;
                    else allSolid = false;
        if (allCaves) return 1;
        if (allSolid) return -1;
        return 0;
    }

    public void populate(World wld, ChunkGenerator.ChunkData chunk, ChunkGenerator.BiomeGrid biome, int[][] heightMap, int x, int z, double[][] caveMultiMap) {
        // print time benchmarking info
        if (CAVE_GEN_TIME_LOG && testCaveGenAmount[testInfoIndex] % 10 == 9) {
            TerrariaHelper.LOGGER.info("洞穴所属世界：" + testInfoIndex + "（0为地表，1为地下）");
            TerrariaHelper.LOGGER.info("洞穴估算平均使用时间（单位：纳秒）: " + (testCaveSetupDurTotal[testInfoIndex] / testCaveGenAmount[testInfoIndex]) );
            TerrariaHelper.LOGGER.info("洞穴细化计算平均使用时间（单位：纳秒，不含估算时长）: " + (testCaveDetailDurTotal[testInfoIndex] / testCaveGenAmount[testInfoIndex]) );
        }
        int chunkX = x << 4, chunkZ = z << 4;
        // setup cave estimates
        long timing = System.nanoTime();
        int estimationWidth = Math.floorDiv(16, CAVE_ROUGH_SKETCH_DIAMETER) + 2,
                estimationHeight = Math.floorDiv(256, CAVE_ROUGH_SKETCH_DIAMETER) + 2;
        if (16 % CAVE_ROUGH_SKETCH_DIAMETER != 0)
            estimationWidth ++;
        if (256 % CAVE_ROUGH_SKETCH_DIAMETER != 0)
            estimationHeight ++;

        // create estimates
        Boolean[][][] caveEstimates = new Boolean[estimationWidth][estimationHeight][estimationWidth];
        try {
            WorldGenHelper.fill3DArray(caveEstimates, poolRoughSketch, CAVE_ROUGH_SKETCH_THREADS, (info) -> {
                int i = info[0];
                int y_coord = info[1];
                int j = info[2];

                // setup x info
                int xBlockOffset = (i - 1) * CAVE_ROUGH_SKETCH_DIAMETER;
                int currX = chunkX + xBlockOffset;
                // prevent out of bound
                if (xBlockOffset < 0) xBlockOffset = 0;
                else if (xBlockOffset >= 16) xBlockOffset = 15;
                // setup z info
                int zBlockOffset = (j - 1) * CAVE_ROUGH_SKETCH_DIAMETER;
                int currZ = chunkZ + zBlockOffset;
                // prevent out of bound
                if (zBlockOffset < 0) zBlockOffset = 0;
                else if (zBlockOffset >= 16) zBlockOffset = 15;

                Biome columnBiome = OverworldBiomeGenerator.getBiome(currX, currZ);
                int effectualY = ((y_coord - 1) * CAVE_ROUGH_SKETCH_DIAMETER) + yOffset;
                return validateCaveEstimate(getCavernNoise(
                        columnBiome, heightMap[i][j], currX, effectualY, currZ, caveMultiMap[xBlockOffset][zBlockOffset]));
            });
        }
        catch (Exception e) {
            TerrariaHelper.LOGGER.info("利用线程估算洞穴信息时以下错误出现。本区块改为以单线程模式生成。");
            e.printStackTrace();

            for (int i = 0; i < estimationWidth; i ++) {
                int xBlockOffset = (i - 1) * CAVE_ROUGH_SKETCH_DIAMETER;
                int currX = chunkX + xBlockOffset;
                // prevent out of bound
                if (xBlockOffset < 0) xBlockOffset = 0;
                else if (xBlockOffset >= 16) xBlockOffset = 15;
                for (int j = 0; j < estimationWidth; j ++) {
                    int zBlockOffset = (j - 1) * CAVE_ROUGH_SKETCH_DIAMETER;
                    int currZ = chunkZ + zBlockOffset;
                    // prevent out of bound
                    if (zBlockOffset < 0) zBlockOffset = 0;
                    else if (zBlockOffset >= 16) zBlockOffset = 15;
                    Biome columnBiome = OverworldBiomeGenerator.getBiome(currX, currZ);
                    for (int y_coord = 0; y_coord < estimationHeight; y_coord ++) {
                        int effectualY = ((y_coord - 1) * CAVE_ROUGH_SKETCH_DIAMETER) + yOffset;
                        caveEstimates[i][y_coord][j] = validateCaveEstimate(getCavernNoise(
                                columnBiome, heightMap[i][j], currX, effectualY, currZ, caveMultiMap[xBlockOffset][zBlockOffset]));
                    }
                }
            }
        }
        if (CAVE_GEN_TIME_LOG){
            testCaveSetupDurTotal[testInfoIndex] += (System.nanoTime() - timing);
            timing = System.nanoTime();
        }

        // setup actual blocks
        try {
            Boolean[][][] temp = new Boolean[16][255][16];
            WorldGenHelper.fill3DArray(temp, poolFineDetail, CAVE_DETAIL_THREADS, (info) -> {
                int i = info[0];
                int y_coord = info[1];
                // DO NOT BREAK BEDROCK!
                if (y_coord == 0)
                    return false;
                int j = info[2];

                int currX = chunkX + i;
                int estimateX = 1 + Math.floorDiv(i, CAVE_ROUGH_SKETCH_DIAMETER);

                int currZ = chunkZ + j;
                int estimateZ = 1 + Math.floorDiv(j, CAVE_ROUGH_SKETCH_DIAMETER);

                int effectualY = y_coord + yOffset;
                int estimateY = 1 + Math.floorDiv(y_coord, CAVE_ROUGH_SKETCH_DIAMETER);
                // check if the nearby estimates contains cave
                byte shouldCheckCave = hasNearbyCaveEstimate(caveEstimates, estimateX, estimateY, estimateZ);
                boolean result = false;
                if (shouldCheckCave == 0) {
                    if ((i%CAVE_ROUGH_SKETCH_DIAMETER)==0 && (y_coord%CAVE_ROUGH_SKETCH_DIAMETER)==0 && (j%CAVE_ROUGH_SKETCH_DIAMETER)==0) {
                        // if the cave is in estimate already
                        result = caveEstimates[estimateX][estimateY][estimateZ];
                    } else {
                        // setup cave noise
                        double[] noise = getCavernNoise(
                                biome.getBiome(i, j), heightMap[i][j], currX, effectualY, currZ, caveMultiMap[i][j]);
                        // validate cave noise
                        result = validateCave(noise);
                    }
                } else if (shouldCheckCave == 1) result = true;
                if (result)
                    chunk.setBlock(i, y_coord, j, getAirMaterial(biome.getBiome(i, j), caveMultiMap[i][j]));
                return result;
            });
        }
        catch (Exception e) {
            TerrariaHelper.LOGGER.info("利用线程精确计算洞穴信息时以下错误出现。本区块改为以单线程模式生成。");
            e.printStackTrace();

            for (int i = 0; i < 16; i ++) {
                int currX = chunkX + i;
                int estimateX = 1 + Math.floorDiv(i, CAVE_ROUGH_SKETCH_DIAMETER);
                for (int j = 0; j < 16; j ++) {
                    int currZ = chunkZ + j;
                    int estimateZ = 1 + Math.floorDiv(j, CAVE_ROUGH_SKETCH_DIAMETER);
                    // loop through y to set blocks
                    for (int y_coord = 1; y_coord < 255; y_coord ++) {
                        int effectualY = y_coord + yOffset;
                        int estimateY = 1 + Math.floorDiv(y_coord, CAVE_ROUGH_SKETCH_DIAMETER);
                        Material currBlock = chunk.getType(i, y_coord, j);
                        if (!currBlock.isSolid()) break;
                        // check if the nearby estimates contains cave

                        byte shouldCheckCave = hasNearbyCaveEstimate(caveEstimates, estimateX, estimateY, estimateZ);
                        boolean isCave = false;
                        if (shouldCheckCave == 0) {
                            if ((i%CAVE_ROUGH_SKETCH_DIAMETER)==0 && (y_coord%CAVE_ROUGH_SKETCH_DIAMETER)==0 && (j%CAVE_ROUGH_SKETCH_DIAMETER)==0) {
                                // if the cave is in estimate already
                                isCave = caveEstimates[estimateX][estimateY][estimateZ];
                            } else {
                                // setup cave noise
                                double[] noise = getCavernNoise(
                                        biome.getBiome(i, j), heightMap[i][j], currX, effectualY, currZ, caveMultiMap[i][j]);
                                // validate cave noise
                                isCave = validateCave(noise);
                            }
                        } else if (shouldCheckCave == 1) isCave = true;
                        if (isCave)
                            chunk.setBlock(i, y_coord, j, getAirMaterial(biome.getBiome(i, j), caveMultiMap[i][j]));
                    }
                }
            }
        }
        if (CAVE_GEN_TIME_LOG) {
            testCaveDetailDurTotal[testInfoIndex] += (System.nanoTime() - timing);
            testCaveGenAmount[testInfoIndex] ++;
        }
    }
    public void populate_no_optimization(ChunkGenerator.ChunkData chunk, ChunkGenerator.BiomeGrid biome, int[][] heightMap, int x, int z, double[][] caveMultiMap) {
        int chunkX = x << 4, chunkZ = z << 4;
        // setup actual blocks
        for (int i = 0; i < 16; i ++) {
            int currX = chunkX + i;
            for (int j = 0; j < 16; j ++) {
                int currZ = chunkZ + j;
                // loop through y to set blocks
                for (int y_coord = 1; y_coord < 255; y_coord ++) {
                    int effectualY = y_coord + yOffset;
                    Material currBlock = chunk.getType(i, y_coord, j);
                    if (!currBlock.isSolid()) break;

                    long timing = System.nanoTime();
                    // setup two types of cave noise
                    double[] noise = getCavernNoise(
                            biome.getBiome(i, j), heightMap[i][j], currX, effectualY, currZ, caveMultiMap[i][j]);
                    // cheese cave noise should be decreased above y=30, and completely gone above y=50
                    boolean isCave = validateCave(noise);
                    if (CAVE_GEN_TIME_LOG){
                        testCaveDetailDurTotal[testInfoIndex] += (System.nanoTime() - timing);
                    }
                    if (isCave) {
                        chunk.setBlock(i, y_coord, j, getAirMaterial(biome.getBiome(i, j), caveMultiMap[i][j]));
                    }
                }
            }
        }
    }
}
