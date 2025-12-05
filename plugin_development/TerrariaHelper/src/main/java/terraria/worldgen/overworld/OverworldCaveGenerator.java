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

import static terraria.util.WorldHelper.CAVERN_Y_BELOW_BEDROCK;

public class OverworldCaveGenerator {
    int yOffset;
    SimplexOctaveGenerator cheeseCaveGenerator, spaghettiGeneratorOne, spaghettiGeneratorTwo,
            sunkenSeaAbyssCaveGenerator;


    int testInfoIndex;
    static long[] testCaveDurTotal = {0, 0}, testCaveSetupDurTotal = {0, 0}, testCaveGenAmount = {0, 0};
    static final boolean CAVE_GEN_TIME_LOG = TerrariaHelper.optimizationConfig.getBoolean("worldGen.opt.caveGenDurationLog", false);
    static final int CAVE_ROUGH_SKETCH_DIAMETER = Math.max( TerrariaHelper.optimizationConfig.getInt("worldGen.opt.caveSketchSize", 3), 1);


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
                return Material.STATIONARY_WATER;
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

    public void populate(World wld, ChunkGenerator.ChunkData chunk, ChunkGenerator.BiomeGrid biome, int[][] heightMap, int blockXStart, int blockZStart, double[][] caveMultiMap) {
        // print time benchmarking info
        if (CAVE_GEN_TIME_LOG && testCaveGenAmount[testInfoIndex] % 20 == 19) {
            TerrariaHelper.LOGGER.info("洞穴所属世界：" + testInfoIndex + "（0为地表，1为地下）");
            TerrariaHelper.LOGGER.info("洞穴估算平均使用时间（单位：纳秒）: " + (testCaveSetupDurTotal[testInfoIndex] / testCaveGenAmount[testInfoIndex]) );
            TerrariaHelper.LOGGER.info("洞穴总体平均使用时间（单位：纳秒，含估算时长）: " + (testCaveDurTotal[testInfoIndex] / testCaveGenAmount[testInfoIndex]) );
        }

        // setup cave estimates
        long timing = System.nanoTime();

        try {
            // create estimates
            Boolean[][][] caveFlags = WorldGenHelper.getChunkFlag(CAVE_ROUGH_SKETCH_DIAMETER, (info) -> {
                int i = info[0];
                int yCoord = info[1];
                int j = info[2];

                int currX = blockXStart + i;
                int effectualY = yOffset + yCoord;
                int currZ = blockZStart + j;

                Biome columnBiome = OverworldBiomeGenerator.getBiome(currX, currZ);
                return validateCaveEstimate(getCavernNoise(columnBiome, heightMap[i][j], currX, effectualY, currZ, caveMultiMap[i][j]));
            }, CAVE_GEN_TIME_LOG, testCaveSetupDurTotal, testInfoIndex);

            // place the cave
            for (int i = 0; i < 16; i ++) {
                for (int j = 1; j <= CAVERN_Y_BELOW_BEDROCK; j ++) {
                    for (int k = 0; k < 16; k++) {
                        if (caveFlags[i][j][k])
                            chunk.setBlock(i, j, k, getAirMaterial(biome.getBiome(i, k)));
                    }
                }
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

    // brute-force fallback in case thread-opt fails somehow
    public void populateBF(ChunkGenerator.ChunkData chunk, ChunkGenerator.BiomeGrid biome, int[][] heightMap, int blockXStart, int blockZStart, double[][] caveMultiMap) {
        long timing = System.nanoTime();
        // setup actual blocks
        for (int i = 0; i < 16; i ++) {
            int currX = blockXStart + i;
            for (int j = 0; j < 16; j ++) {
                int currZ = blockZStart + j;
                // loop through y to set blocks
                for (int y_coord = 1; y_coord < 255; y_coord ++) {
                    int effectualY = y_coord + yOffset;
                    Material currBlock = chunk.getType(i, y_coord, j);
                    if (!currBlock.isSolid()) break;

                    // setup two types of cave noise
                    double[] noise = getCavernNoise(
                            biome.getBiome(i, j), heightMap[i][j], currX, effectualY, currZ, caveMultiMap[i][j]);
                    // cheese cave noise should be decreased above y=30, and completely gone above y=50
                    boolean isCave = validateCave(noise);
                    if (isCave) {
                        chunk.setBlock(i, y_coord, j, getAirMaterial(biome.getBiome(i, j)));
                    }
                }
            }
        }
        if (CAVE_GEN_TIME_LOG){
            testCaveDurTotal[testInfoIndex] += (System.nanoTime() - timing);
        }
    }
}
