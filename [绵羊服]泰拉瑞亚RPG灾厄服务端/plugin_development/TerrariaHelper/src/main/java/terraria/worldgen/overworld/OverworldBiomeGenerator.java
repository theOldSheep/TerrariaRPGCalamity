package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.util.noise.PerlinNoiseGenerator;
import org.bukkit.util.noise.PerlinOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Random;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;

public class OverworldBiomeGenerator {

    static boolean generatedImg = false;
    static long SEED = 0;
    static final int
            SINGLE_CACHE_SIZE = 1000000,
            SPAWN_LOC_PROTECTION_RADIUS = 750,
            XZ_HASH_MASK = (1 << 26) - 1;

    static PerlinOctaveGenerator noiseCont = null, noiseTemp, noiseHum, noiseWrd,
            noiseEros, noiseTrH;

    public static class BiomeFeature {
        public static final int
                // large mag. negative: sulphurous ocean, large mag. positive: ocean
                CONTINENTALNESS = 0,
                // has to do with desert identification
                TEMPERATURE = 1,
                HUMIDITY = 2,
                WEIRDNESS = 3,
                EROSION = 4,
                TERRAIN_H = 5;
        public final Double[] features = new Double[6];
        public final double biomeSignificance;
        public final WorldHelper.BiomeType evaluatedBiome;

        public BiomeFeature(int x, int z) {
            // IMPORTANT: prevent overflow.
            double distFromSpawn = Math.sqrt((double) x * (double) x + (double) z * (double) z);
            double distFromSpawnFactor = distFromSpawn / SPAWN_LOC_PROTECTION_RADIUS;

            features[CONTINENTALNESS] =     noiseCont.noise(x, z, 2, 0.5);
            features[TEMPERATURE] =         noiseTemp.noise(x, z, 2, 0.5);
            features[HUMIDITY] =            noiseHum .noise(x, z, 2, 0.5);
            features[WEIRDNESS] =           noiseWrd .noise(x, z, 2, 0.5);
            features[EROSION] =             noiseEros.noise(x, z, 2, 0.5);
            features[TERRAIN_H] =           noiseTrH .noise(x, z, 2, 0.5);
            // spawn protection: feature tweak
            if (distFromSpawn < SPAWN_LOC_PROTECTION_RADIUS) {
                features[CONTINENTALNESS] *= distFromSpawnFactor;
                features[TEMPERATURE] *= distFromSpawnFactor;
                features[HUMIDITY] *= distFromSpawnFactor;
                features[WEIRDNESS] *= distFromSpawnFactor;
            }
            // evaluate the biome for this noise
            double tmp = features[TEMPERATURE], hum = features[HUMIDITY],
                    cnt = features[CONTINENTALNESS], wrd = features[WEIRDNESS];
            // sulphurous ocean
            if (cnt > 0.5) {
                evaluatedBiome = WorldHelper.BiomeType.SULPHUROUS_OCEAN;
                biomeSignificance = cnt;
            }
            // ocean
            else if (cnt < -0.5) {
                evaluatedBiome = WorldHelper.BiomeType.OCEAN;
                biomeSignificance = -cnt;
            }
            // astral infection
            else if (Math.abs(cnt) < 0.25 && hum < -0.5) {
                evaluatedBiome = WorldHelper.BiomeType.ASTRAL_INFECTION;
                biomeSignificance = -hum - Math.abs(cnt);
            }
            // hallow
            else if (wrd < -0.5) {
                evaluatedBiome = WorldHelper.BiomeType.HALLOW;
                biomeSignificance = -wrd;
            }
            // corruption
            else if (wrd > 0.5) {
                evaluatedBiome = WorldHelper.BiomeType.CORRUPTION;
                biomeSignificance = wrd;
            }
            // jungle
            else if (tmp > 0.3 && hum > 0.3) {
                evaluatedBiome = WorldHelper.BiomeType.JUNGLE;
                biomeSignificance = tmp + hum;
            }
            // desert
            else if (tmp > 0.3 && hum < -0.3) {
                evaluatedBiome = WorldHelper.BiomeType.DESERT;
                biomeSignificance = tmp - hum;
            }
            // tundra
            else if (tmp < -0.5) {
                evaluatedBiome = WorldHelper.BiomeType.TUNDRA;
                biomeSignificance = -tmp;
            }
            // normal
            else {
                evaluatedBiome = WorldHelper.BiomeType.NORMAL;
                biomeSignificance = features[EROSION];
            }
        }
    }

    // biome cache
    static HashMap<Long, BiomeFeature>[] biomeCache;
    static int biomeCacheIdx = 0;
    static {
        biomeCache = new HashMap[2];
        for (int i = 0; i < 2; i ++)
            biomeCache[i] = new HashMap<>(SINGLE_CACHE_SIZE, 0.8f);
    }


    // set up the generator based on seed
    private static void setupGenerators(long seed) {
        SEED = seed;
        Random rdm = new Random(seed);
        // temperature
        noiseTemp = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        noiseTemp.setScale(0.001);
        // humidity
        noiseHum =  new PerlinOctaveGenerator(rdm.nextLong(), 1);
        noiseHum.setScale(0.001);
        // weirdness
        noiseWrd =  new PerlinOctaveGenerator(rdm.nextLong(), 1);
        noiseWrd.setScale(0.001);
        // erosion
        noiseEros = new PerlinOctaveGenerator(rdm.nextLong(), 4);
        noiseEros.setScale(0.0025);
        // terrain height
        noiseTrH = new PerlinOctaveGenerator(rdm.nextLong(), 8);
        noiseTrH.setScale(0.001);
        // update continentalness finally, so there is less chance things get broken
        noiseCont = new PerlinOctaveGenerator(rdm.nextLong(), 3);
        noiseCont.setScale(0.00025);
    }
    // save the biome image for testing purposes and so on
    private static void generateBiomeImage() {
        HashMap<Biome, Integer> biomeColors = new HashMap<>();
        biomeColors.put(Biome.FOREST,               new Color(0, 175, 0).getRGB()); //forest(normal)
        biomeColors.put(Biome.JUNGLE,               new Color(0, 100, 0).getRGB()); //jungle
        biomeColors.put(Biome.DESERT,               new Color(255, 255, 0).getRGB()); //desert
        biomeColors.put(Biome.MUTATED_DESERT,       new Color(0, 50, 80).getRGB()); //sunken sea
        biomeColors.put(Biome.BEACHES,              new Color(255, 255, 150).getRGB()); //beach
        biomeColors.put(Biome.OCEAN,                new Color(0, 0, 255).getRGB()); //ocean
        biomeColors.put(Biome.COLD_BEACH,           new Color(130, 110, 100).getRGB()); //sulphurous beach
        biomeColors.put(Biome.FROZEN_OCEAN,         new Color(120, 200, 150).getRGB()); //sulphurous ocean
        biomeColors.put(Biome.TAIGA_COLD,           new Color(150, 200, 255).getRGB()); //tundra
        biomeColors.put(Biome.MUSHROOM_ISLAND,      new Color(150, 0, 150).getRGB()); //corruption
        biomeColors.put(Biome.MESA,                 new Color(50, 25, 60).getRGB()); //astral infection
        biomeColors.put(Biome.ICE_FLATS,            new Color(255, 255, 255).getRGB()); //hallow
        // test: save a map of biomes for testing purposes
        int center = 0;
        int scale = 1000;
        int jump = 50;
        File dir_biome_map = new File("worldGenDebug" + File.separator + "biomesMap(50000x50000).png");
        Bukkit.getLogger().info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        if (dir_biome_map.exists()) {
            Bukkit.getLogger().info("BIOME MAP FILE ALREADY EXISTS. DELETE THE FILE IF YOU WISH FOR GENERATING A NEW ONE.");
            return;
        }
        Bukkit.getLogger().info("START GENERATING BIOME MAP");
        double progress = 0, progressMax = scale * scale;
        long lastPrinted = Calendar.getInstance().getTimeInMillis();
        BufferedImage biomeMap = new BufferedImage(scale, scale, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < scale; i++)
            for (int j = 0; j < scale; j++) {
                // i : x-coordinate corresponding to the point on map increases as we move to the right (bigger i)
                // j : z-coordinate corresponding to the point on map increases as we move to the top (smaller j)
                int blockX = (i - (scale / 2)) * jump + center, blockZ = ((scale / 2) - j) * jump + center;
                Biome currBiome = getBiome(SEED, blockX, blockZ);
                biomeMap.setRGB(i, j, biomeColors.getOrDefault(currBiome, new Color(0, 255, 0).getRGB()));
                progress++;
                if (lastPrinted + 1000 < Calendar.getInstance().getTimeInMillis()) {
                    lastPrinted = Calendar.getInstance().getTimeInMillis();
                    Bukkit.getLogger().info("Generation progress: " + progress / progressMax);
                    Bukkit.getLogger().info("Progress detail: " + progress + "/" + progressMax);
                }
            }
        Bukkit.getLogger().info("Generation progress: " + progress / progressMax);
        Bukkit.getLogger().info("Progress detail: " + progress + "/" + progressMax);
        try {
            ImageIO.write(biomeMap, "png", dir_biome_map);
        } catch (IOException e) {
            e.printStackTrace();
            Bukkit.getLogger().warning(e.getMessage());
        }
        Bukkit.getLogger().info("FINISHED GENERATING BIOME MAP.");
        Bukkit.getLogger().info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }
    // get the key in the biome cache
    private static long getCacheKey(int x, int z) {
        long result = 0;
        // first two bytes denotes x and z sign
        if (x < 0) {
            result |= 1;
            x *= -1;
        }
        result <<= 1;
        if (z < 0) {
            result |= 1;
            z *= -1;
        }
        // reserve 25 bytes for each of x and z
        result <<= 25;
        result |= (x & XZ_HASH_MASK);
        result <<= 25;
        result |= (z & XZ_HASH_MASK);

        return result;
    }

    public static Biome getBiomeFromType(WorldHelper.BiomeType biome) {
        switch (biome) {
            case HALLOW:
                return Biome.ICE_FLATS;
            case CORRUPTION:
                return Biome.MUSHROOM_ISLAND;
            case ASTRAL_INFECTION:
                return Biome.MESA;
            case OCEAN:
                return Biome.OCEAN;
            case SULPHUROUS_OCEAN:
                return Biome.FROZEN_OCEAN;
            case TUNDRA:
                return Biome.TAIGA_COLD;
            case JUNGLE:
                return Biome.JUNGLE;
            case DESERT:
                return Biome.DESERT;
            default:
                return Biome.FOREST;
        }
    }
    public static Biome getUndergroundEquivalent(Biome biome) {
        switch (biome) {
            case ICE_FLATS: // hallow
                return Biome.MUTATED_ICE_FLATS;
            case MUSHROOM_ISLAND: // corruption
                return Biome.MUSHROOM_ISLAND_SHORE;
            case MESA: // astral infection
                return Biome.MUTATED_MESA;
            case FROZEN_OCEAN: // sulphurous ocean
                return Biome.DEEP_OCEAN;
            case TAIGA_COLD: // tundra
                return Biome.MUTATED_TAIGA_COLD;
            case JUNGLE: // jungle
                return Biome.MUTATED_JUNGLE;
            case DESERT: // desert
                return Biome.MUTATED_DESERT;
            default:
                return Biome.MUTATED_FOREST;
        }
    }
    // get the biome feature at position; this should be used outside this function for tree growth etc.
    public static BiomeFeature getBiomeFeature(double actualX, double actualZ) {
        return getBiomeFeature((int) actualX, (int) actualZ);
    }
    public static BiomeFeature getBiomeFeature(int actualX, int actualZ) {
        int x = actualX >> 2, z = actualZ >> 2;

        long biomeLocKey = getCacheKey(x, z);
        for (HashMap<Long, BiomeFeature> cache : biomeCache) {
            if (cache.containsKey(biomeLocKey)) {
                return cache.get(biomeLocKey);
            }
        }
        // evaluate the feature. Technically the world seed do not need to be initialized here, but just in case.
        return getBiomeFeature(TerrariaHelper.worldSeed, actualX, actualZ);
    }
    // this should be called if possible!
    public static BiomeFeature getBiomeFeature(long seed, int actualX, int actualZ) {
//        int x = actualX >> 2, z = actualZ >> 2;
        int x = actualX, z = actualZ;

        long biomeLocKey = getCacheKey(x, z);
        if (biomeCache[biomeCacheIdx].containsKey(biomeLocKey)) {
            return biomeCache[biomeCacheIdx].get(biomeLocKey);
        }
        // evaluate & save the biome features
        if (noiseCont == null) {
            setupGenerators(seed);
        }
        BiomeFeature result = new BiomeFeature(actualX, actualZ);
        // go to the next generation when needed
        if (biomeCache[biomeCacheIdx].size() > SINGLE_CACHE_SIZE) {
            biomeCacheIdx = (biomeCacheIdx + 1) % 2;
        }
        // when the current generation is half-full, clear the other one.
        // this means the next time you switch cache, the most recent half is still recorded.
        if (biomeCache[biomeCacheIdx].size() == SINGLE_CACHE_SIZE / 2) {
            biomeCache[(biomeCacheIdx + 1) % 2].clear();
        }
        // save the result and return
        biomeCache[biomeCacheIdx].put(biomeLocKey, result);
        return result;
    }
    public static Biome getBiome(long seed, int actualX, int actualZ) {
        if (! generatedImg) {
            generatedImg = true;
            generateBiomeImage();
        }
        return getBiomeFromType( getBiomeFeature(seed, actualX, actualZ).evaluatedBiome );
    }
}