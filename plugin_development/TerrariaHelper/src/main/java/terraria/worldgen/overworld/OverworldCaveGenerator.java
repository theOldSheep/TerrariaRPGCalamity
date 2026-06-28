package terraria.worldgen.overworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.noise.SimplexOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.WorldGenHelper;

import java.util.Random;
import java.util.concurrent.ExecutionException;

import static terraria.util.WorldHelper.CAVERN_Y_BELOW_BEDROCK;

public class OverworldCaveGenerator {
    int yOffset;
    SimplexOctaveGenerator cheeseCaveGenerator, spaghettiGeneratorOne, spaghettiGeneratorTwo,
            aquiferFeatureNoise, aquiferBarrierNoise,
            sunkenSeaAbyssCaveGenerator;

    int testInfoIndex;
    static long[] testCaveDurTotal = {0, 0}, testCaveSetupDurEst = {0, 0}, testCaveSetupDurFill = {0, 0},
            testAquiferDurTotal = {0, 0},
            testCaveGenAmount = {0, 0};
    static final boolean CAVE_GEN_TIME_LOG = TerrariaHelper.optimizationConfig.getBoolean("worldGen.opt.caveGenDurationLog", false);
    static final int CAVE_ROUGH_SKETCH_DIAMETER = Math.max( TerrariaHelper.optimizationConfig.getInt("worldGen.opt.caveSketchSize", 3), 1);

    // Cavern
    private static final double CHEESE_THRESHOLD = 0.5;
    private static final double SPAGHETTI_THRESHOLD = 0.15;
    // Aquifer
    private static final double AQUIFER_NOISE_REQUIREMENT = 0.35;
    private static final int GRID_SCALE_XZ = 24;
    private static final int GRID_SCALE_Y = 12;
    private static final int CELL_SIZE_XZ = 36;
    private static final int CELL_SIZE_Y = 12;
    private static final int LAVA_DEPTH_THRESHOLD = -150;

    /*
     * ===============================
     * Cave generation
     * ===============================
     */
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

        // Drives the properties of the 3D Voronoi cells
        aquiferFeatureNoise = new SimplexOctaveGenerator(rdm.nextLong(), 4);
        aquiferFeatureNoise.setScale(0.008); // Slightly higher frequency for 3D distinctness

        // Introduces higher locality frequency and smoothness for the 3D distinctness stone crust boundaries
        aquiferBarrierNoise = new SimplexOctaveGenerator(rdm.nextLong(), 3);
        aquiferBarrierNoise.setScale(0.05);

        sunkenSeaAbyssCaveGenerator = new SimplexOctaveGenerator(rdm.nextLong(), OCTAVES);
        sunkenSeaAbyssCaveGenerator.setScale(0.01);
        sunkenSeaAbyssCaveGenerator.setYScale(sunkenSeaAbyssCaveGenerator.getYScale() * 3);

    }
    public boolean generateCaveForBiomeType(WorldHelper.BiomeType biome) {
        switch (biome) {
            // caves for these biomes will be customized
            case DESERT:
                // the biome type from biome feature always assume surface world, thus sunken sea goes under desert
                return yOffset == OverworldChunkGenerator.Y_OFFSET_OVERWORLD ? true : false;
            case SULPHUROUS_OCEAN:
            case ABYSS:
                return false;
            default:
                return true;
        }
    }
    // gets the "air" material for the biome
    private static Material getAirMaterial(Biome biome) {
        switch (biome) {
            // caves for these biomes will be filled with water
            case MUTATED_DESERT:    // sunken sea
            case COLD_BEACH:        // sulphurous beach
            case FROZEN_OCEAN:      // sulphurous ocean
            case DEEP_OCEAN:        // abyss
                return Material.WATER;
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
                    {
                        result[0] = sunkenSeaAbyssCaveGenerator.noise(currX, effectualY, currZ, 2, 0.5, false);
                        double amplify = 0.4;
                        result[0] += amplify;
                        // noise multi closer to 0 -> position deeper within the biome
                        result[0] = (result[0]) * (1 - noiseMulti) + (-1) * noiseMulti;
                    }
                    return result;
                    // abyss
                    case COLD_BEACH:
                    case FROZEN_OCEAN:
                    case DEEP_OCEAN:
                    {
                        result[0] = sunkenSeaAbyssCaveGenerator.noise(currX, effectualY, currZ, 2, 0.5, false);
                        double amplify = effectualY < -200 ? -1 : 0.4;
                        // make sure the 3rd level is pretty empty (handled by modifying amplify)
                        double emptyMultiplier = 0d;
                        // -155(y=100) -> -165(y=90) ||| -225-(y=30) ==> -250(y=5)
                        if (effectualY < -155 && effectualY > -250) {
                            emptyMultiplier = 1d;
                            if (effectualY > -165) {
                                emptyMultiplier = (-effectualY - 155) / 10d;
                            }
                            else if (effectualY < -225) {
                                emptyMultiplier = (effectualY + 250) / 25d;
                            }
                        }
                        amplify = amplify * (1 - emptyMultiplier) + 1.5 * emptyMultiplier;

                        result[0] += amplify;
                        // noise multi closer to 0 -> position deeper within the biome
                        result[0] = (result[0]) * (1 - noiseMulti) + (-1) * noiseMulti;
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
        // cheese - valid near 1; subtract by up to 2 with complement
        result[0] = result[0] * noiseMulti - 2 * complementNoise;
        // spaghetti caves - valid near 0 (symmetric); take abs and add by up to 1 with complement
        result[1] = Math.abs(result[1]) * noiseMulti + complementNoise;
        result[2] = Math.abs(result[2]) * noiseMulti + complementNoise;
        return result;
    }
    private boolean validateCaveEstimate(double[] noise) {
        return (noise[0] > CHEESE_THRESHOLD) || (
                (Math.abs(noise[1]) < SPAGHETTI_THRESHOLD) &&
                        (Math.abs(noise[2]) < SPAGHETTI_THRESHOLD));
    }
    private boolean validateCave(double[] noise) {
        return (noise[0] > CHEESE_THRESHOLD) || (
                (Math.abs(noise[1]) < SPAGHETTI_THRESHOLD) &&
                        (Math.abs(noise[2]) < SPAGHETTI_THRESHOLD));
    }

    /*
     * ===============================
     * Aquifer generation
     * ===============================
     */
    private boolean isBiomeAllowedForAquifers(Biome biome) {
        switch (biome) {
            case MUTATED_DESERT:    // sunken sea
            case COLD_BEACH:        // sulphurous beach
            case FROZEN_OCEAN:      // sulphurous ocean
            case DEEP_OCEAN:        // abyss
                return false;
            default:
                return true;
        }
    }

    private static class AquiferSubzone {
        final int surfaceLevel;
        final Material fluidType;
        final boolean isBoundary; // Tracks if this block sits on the edge of two Voronoi cells

        AquiferSubzone(int surfaceLevel, Material fluidType, boolean isBoundary) {
            this.surfaceLevel = surfaceLevel;
            this.fluidType = fluidType;
            this.isBoundary = isBoundary;
        }
    }

    private double getFastJitter(int cx, int cy, int cz, long axisSalt) {
        long hash = ((long) cx * 3432918353L) ^ ((long) cy * 8192837129L) ^ ((long) cz * 4619922659L) ^ axisSalt;
        hash = (hash ^ (hash >>> 33)) * 0xff51afd7ed558ccdL;
        hash = (hash ^ (hash >>> 33)) * 0xc4ceb9fe1a85ec53L;
        hash = hash ^ (hash >>> 33);
        return (Math.abs(hash) & 0xFFFF) / (double) 0xFFFF;
    }

    private AquiferSubzone getVoronoiSubzone(int globalX, int globalY, int globalZ) {
        int cellX = Math.floorDiv(globalX, CELL_SIZE_XZ);
        int cellY = Math.floorDiv(globalY, CELL_SIZE_Y);
        int cellZ = Math.floorDiv(globalZ, CELL_SIZE_XZ);

        double minDist = Double.MAX_VALUE;
        double secondMinDist = Double.MAX_VALUE;
        int targetCenterX = 0;
        int targetCenterY = 0;
        int targetCenterZ = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int cx = cellX + dx;
                    int cy = cellY + dy;
                    int cz = cellZ + dz;

                    double offsetX = getFastJitter(cx, cy, cz, 1L) * CELL_SIZE_XZ;
                    double offsetY = getFastJitter(cx, cy, cz, 2L) * CELL_SIZE_Y;
                    double offsetZ = getFastJitter(cx, cy, cz, 3L) * CELL_SIZE_XZ;

                    double centerX = (cx * CELL_SIZE_XZ) + offsetX;
                    double centerY = (cy * CELL_SIZE_Y) + offsetY;
                    double centerZ = (cz * CELL_SIZE_XZ) + offsetZ;

                    double dist = ((centerX - globalX) * (centerX - globalX)) +
                            ((centerY - globalY) * (centerY - globalY)) +
                            ((centerZ - globalZ) * (centerZ - globalZ));

                    // Track closest and second-closest cells to determine boundary walls
                    if (dist < minDist) {
                        secondMinDist = minDist;
                        minDist = dist;
                        targetCenterX = (int) centerX;
                        targetCenterY = (int) centerY;
                        targetCenterZ = (int) centerZ;
                    } else if (dist < secondMinDist) {
                        secondMinDist = dist;
                    }
                }
            }
        }

        double featureNoise = aquiferFeatureNoise.noise(targetCenterX, targetCenterY, targetCenterZ, 2, 0.5, true);

        // Tolerance threshold to identify if two liquids are pressing against each other
        boolean isBoundary = (secondMinDist - minDist) < 150.0;

        Material fluid = null;
        int surfaceLevel = -999;

        if (featureNoise > AQUIFER_NOISE_REQUIREMENT) {
            surfaceLevel = targetCenterY + (int) (featureNoise * 15);
            if (surfaceLevel <= LAVA_DEPTH_THRESHOLD) {
                fluid = Material.LAVA;
            } else {
                fluid = Material.WATER;
            }
        }

        return new AquiferSubzone(surfaceLevel, fluid, isBoundary);
    }

    private double get3DVertexDensity(int globalX, int effectualY, int globalZ) {
        int xGrid = Math.floorDiv(globalX, GRID_SCALE_XZ);
        int yGrid = Math.floorDiv(effectualY, GRID_SCALE_Y);
        int zGrid = Math.floorDiv(globalZ, GRID_SCALE_XZ);

        double tx = ((double) globalX / GRID_SCALE_XZ) - xGrid;
        double ty = ((double) effectualY / GRID_SCALE_Y) - yGrid;
        double tz = ((double) globalZ / GRID_SCALE_XZ) - zGrid;

        double densityTotal = 0;
        double weightTotal = 0;

        for (int idx = -1; idx <= 1; idx++) {
            double wx = 1.0 - Math.abs(tx - idx);
            if (wx <= 0) continue;
            for (int idy = -1; idy <= 1; idy++) {
                double wy = 1.0 - Math.abs(ty - idy);
                if (wy <= 0) continue;
                for (int idz = -1; idz <= 1; idz++) {
                    double wz = 1.0 - Math.abs(tz - idz);
                    if (wz <= 0) continue;

                    int vx = (xGrid + idx) * GRID_SCALE_XZ;
                    int vy = (yGrid + idy) * GRID_SCALE_Y;
                    int vz = (zGrid + idz) * GRID_SCALE_XZ;

                    double sample = aquiferFeatureNoise.noise(vx, vy, vz, 2, 0.5, false);
                    double combinedWeight = wx * wy * wz;
                    densityTotal += sample * combinedWeight;
                    weightTotal += combinedWeight;
                }
            }
        }
        return weightTotal > 0 ? (densityTotal / weightTotal) : 0;
    }

    /*
     * ===============================
     * Populate
     * ===============================
     */
    public void populate(World wld, ChunkGenerator.ChunkData chunk, ChunkGenerator.BiomeGrid biome, int[][] heightMap, int blockXStart, int blockZStart, double[][] caveMultiMap) {
        if (CAVE_GEN_TIME_LOG && testCaveGenAmount[testInfoIndex] % 20 == 19) {
            TerrariaHelper.LOGGER.info("洞穴所属世界：" + testInfoIndex + "（0为地表，1为地下）");
            TerrariaHelper.LOGGER.info("洞穴粗略估算平均使用时间（单位：纳秒）: " + (testCaveSetupDurEst[testInfoIndex] / testCaveGenAmount[testInfoIndex]) );
            TerrariaHelper.LOGGER.info("洞穴上采样估算平均使用时间（单位：纳秒）: " + (testCaveSetupDurFill[testInfoIndex] / testCaveGenAmount[testInfoIndex]) );
            TerrariaHelper.LOGGER.info("地下水平均使用时间（单位：纳秒）: " + (testAquiferDurTotal[testInfoIndex] / testCaveGenAmount[testInfoIndex]) );
            TerrariaHelper.LOGGER.info("洞穴总体平均使用时间（单位：纳秒，含估算时长）: " + (testCaveDurTotal[testInfoIndex] / testCaveGenAmount[testInfoIndex]) );
        }

        long timing = System.nanoTime();

        try {
            Boolean[][][] caveFlags = WorldGenHelper.getChunkData(16, 255, 16, CAVE_ROUGH_SKETCH_DIAMETER,
                    Boolean.class, (info) -> {
                        int i = info[0];
                        int yCoord = info[1];
                        int j = info[2];

                        int currX = blockXStart + i;
                        int effectualY = yOffset + yCoord;
                        int currZ = blockZStart + j;

                        Biome columnBiome = OverworldBiomeGenerator.getBiome(currX, currZ);
                        return validateCaveEstimate(getCavernNoise(columnBiome, heightMap[i][j], currX, effectualY, currZ, caveMultiMap[i][j]));
                    },
                    CAVE_GEN_TIME_LOG, testCaveSetupDurEst, testCaveSetupDurFill, testInfoIndex);

            long aquiferTiming = System.nanoTime();
            for (int i = 0; i < 16; i ++) {
                int currX = blockXStart + i;
                Biome currentColumnBiome = biome.getBiome(i, 0);
                boolean aquiferAllowed = isBiomeAllowedForAquifers(currentColumnBiome);

                for (int k = 0; k < 16; k++) {
                    int currZ = blockZStart + k;

                    for (int j = 1; j <= CAVERN_Y_BELOW_BEDROCK; j ++) {
                        int effectualY = yOffset + j;
                        if (caveFlags[i][j][k] && effectualY < 50) {
                            AquiferSubzone subzone = aquiferAllowed ? getVoronoiSubzone(currX, effectualY, currZ) : null;

                            if (subzone != null && subzone.fluidType != null) {
                                // 1. Determine local 3D barrier noise
                                double barrierNoise = aquiferBarrierNoise.noise(currX, effectualY, currZ, 2, 0.5, true);
                                int topCrust = (int) Math.round((barrierNoise + 1) * 1.75 - 1.5);      // Yields -1.5 - 2 block height above
                                int bottomCrust = 2 + (int) Math.round(barrierNoise);                  // Yields 1, 2, or 3 block shell thickness below

                                // 2. Determine Density Zones
                                double fluidDensity = get3DVertexDensity(currX, effectualY, currZ);
                                // The threshold expands to account for bottom/horizontal barrier crust
                                double densityShellThreshold = -0.05 - (bottomCrust * 0.02);

                                boolean isCore = fluidDensity > -0.05;
                                boolean isShell = fluidDensity > densityShellThreshold && fluidDensity <= -0.05;

                                if (subzone.isBoundary) {
                                    // Liquid cell touches another cell (prevent cross-spillage)
                                    // Use 'topCrust' to variably cap the dividing wall height relative to liquid surface
                                    if (effectualY <= subzone.surfaceLevel + topCrust && (isCore || isShell)) {
                                        chunk.setBlock(i, j, k, Material.STONE);
                                    } else {
                                        chunk.setBlock(i, j, k, getAirMaterial(biome.getBiome(i, k)));
                                    }
                                } else {
                                    // Normal interior basin processing
                                    if (effectualY <= subzone.surfaceLevel) {
                                        if (isCore) {
                                            // Secure fluid core
                                            chunk.setBlock(i, j, k, subzone.fluidType);
                                        } else if (isShell) {
                                            // The outer density shell mathematically maps perfectly to
                                            // 'liquid meeting air horizontally or air is below'
                                            chunk.setBlock(i, j, k, Material.STONE);
                                        } else {
                                            chunk.setBlock(i, j, k, getAirMaterial(biome.getBiome(i, k)));
                                        }
                                    } else {
                                        // Prevents generating a stone roof directly over open lakes
                                        chunk.setBlock(i, j, k, getAirMaterial(biome.getBiome(i, k)));
                                    }
                                }
                            } else {
                                chunk.setBlock(i, j, k, getAirMaterial(biome.getBiome(i, k)));
                            }
                        }
                    }
                }
            }
            if (CAVE_GEN_TIME_LOG) {
                testAquiferDurTotal[testInfoIndex] += (System.nanoTime() - aquiferTiming);
            }
        }
        catch (Exception e) {
            TerrariaHelper.LOGGER.warning("利用线程精确计算洞穴信息时以下错误出现。本区块改为以单线程模式生成。");
            e.printStackTrace();

            populateBF(chunk, biome, heightMap, blockXStart, blockZStart, caveMultiMap);
        }
        if (CAVE_GEN_TIME_LOG) {
            testCaveDurTotal[testInfoIndex] += (System.nanoTime() - timing);
            testCaveGenAmount[testInfoIndex] ++;
        }
    }

    public void populateBF(ChunkGenerator.ChunkData chunk, ChunkGenerator.BiomeGrid biome, int[][] heightMap, int blockXStart, int blockZStart, double[][] caveMultiMap) {
        long timing = System.nanoTime();
        long aquiferTiming = System.nanoTime(); // Used for BF loop tracking as well

        for (int i = 0; i < 16; i ++) {
            int currX = blockXStart + i;
            boolean aquiferAllowed = isBiomeAllowedForAquifers(biome.getBiome(i, 0));

            for (int j = 0; j < 16; j ++) {
                int currZ = blockZStart + j;

                for (int y_coord = 1; y_coord < 255; y_coord ++) {
                    int effectualY = y_coord + yOffset;
                    Material currBlock = chunk.getType(i, y_coord, j);
                    if (!currBlock.isSolid()) break;

                    double[] noise = getCavernNoise(
                            biome.getBiome(i, j), heightMap[i][j], currX, effectualY, currZ, caveMultiMap[i][j]);
                    boolean isCave = validateCave(noise);
                    if (isCave) {
                        AquiferSubzone subzone = aquiferAllowed ? getVoronoiSubzone(currX, effectualY, currZ) : null;

                        if (subzone != null && subzone.fluidType != null) {
                            double barrierNoise = aquiferBarrierNoise.noise(currX, effectualY, currZ, 2, 0.5, true);
                            int topCrust = (int) Math.round(barrierNoise);
                            int bottomCrust = 3 + (int) Math.round(barrierNoise);

                            double fluidDensity = get3DVertexDensity(currX, effectualY, currZ);
                            double densityShellThreshold = -0.05 - (bottomCrust * 0.02);

                            boolean isCore = fluidDensity > -0.05;
                            boolean isShell = fluidDensity > densityShellThreshold && fluidDensity <= -0.05;

                            if (subzone.isBoundary) {
                                if (effectualY <= subzone.surfaceLevel + topCrust && (isCore || isShell)) {
                                    chunk.setBlock(i, y_coord, j, Material.STONE);
                                } else {
                                    chunk.setBlock(i, y_coord, j, getAirMaterial(biome.getBiome(i, j)));
                                }
                            } else {
                                if (effectualY <= subzone.surfaceLevel) {
                                    if (isCore) {
                                        chunk.setBlock(i, y_coord, j, subzone.fluidType);
                                    } else if (isShell) {
                                        chunk.setBlock(i, y_coord, j, Material.STONE);
                                    } else {
                                        chunk.setBlock(i, y_coord, j, getAirMaterial(biome.getBiome(i, j)));
                                    }
                                } else {
                                    chunk.setBlock(i, y_coord, j, getAirMaterial(biome.getBiome(i, j)));
                                }
                            }
                        } else {
                            chunk.setBlock(i, y_coord, j, getAirMaterial(biome.getBiome(i, j)));
                        }
                    }
                }
            }
        }

        // Registering time logs for fallback calculation
        if (CAVE_GEN_TIME_LOG){
            testAquiferDurTotal[testInfoIndex] += (System.nanoTime() - aquiferTiming);
            testCaveDurTotal[testInfoIndex] += (System.nanoTime() - timing);
        }
    }
}