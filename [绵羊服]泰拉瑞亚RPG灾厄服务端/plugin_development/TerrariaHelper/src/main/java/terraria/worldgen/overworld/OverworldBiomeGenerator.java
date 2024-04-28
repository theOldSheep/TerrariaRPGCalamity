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

public class OverworldBiomeGenerator {

    static boolean generatedImg = false;
    static long SEED = 0;
    static final int
            CACHE_SIZE = 150000,
            CACHE_DELETION_SIZE = 100000,
            SPAWN_LOC_PROTECTION_RADIUS = 750;

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
        // the functions below produce non-normal feature vectors (prob. distribution). Normalize them after produced!
        public static final HashMap<WorldHelper.BiomeType, ToDoubleFunction<Double[]>> biomeFeatureConversion = new HashMap<>();
        static {
            biomeFeatureConversion.put(WorldHelper.BiomeType.NORMAL, (lst) -> 0.5);
            // 10 is a really high weight, just to ensure low spaces are indeed initialized as the two types of ocean
            biomeFeatureConversion.put(WorldHelper.BiomeType.OCEAN, (Double[] lst) ->
                    lst[CONTINENTALNESS] < -0.5 ? 10 : 0);
            biomeFeatureConversion.put(WorldHelper.BiomeType.SULPHUROUS_OCEAN, (Double[] lst) ->
                    lst[CONTINENTALNESS] > 0.5 ? 10 : 0);
            // note that multiplication makes astral and jungle significantly more rare

            // astral infection: prefer areas higher and dryer; this associates this infection with less foliage.
            biomeFeatureConversion.put(WorldHelper.BiomeType.ASTRAL_INFECTION, (Double[] lst) ->
                    Math.max(0, lst[TERRAIN_H] - lst[HUMIDITY] )
                            * Math.max(0, -lst[WEIRDNESS] * 2.5 ) );
            // hallow: prefer non-weird places
            biomeFeatureConversion.put(WorldHelper.BiomeType.HALLOW, (Double[] lst) ->
                    Math.max(0, -lst[WEIRDNESS] * 1.5 ) );
            // corruption: prefer wet places
            biomeFeatureConversion.put(WorldHelper.BiomeType.CORRUPTION, (Double[] lst) ->
                    Math.max(0, lst[WEIRDNESS] - lst[HUMIDITY] ) * 0.75 );
            // desert: prefer dry, hot places
            biomeFeatureConversion.put(WorldHelper.BiomeType.DESERT, (Double[] lst) ->
                    Math.max(0, -lst[HUMIDITY] + lst[TEMPERATURE]) * 0.75 );
            // tundra: prefer cold places
            biomeFeatureConversion.put(WorldHelper.BiomeType.TUNDRA, (Double[] lst) ->
                    Math.max(0, -lst[TEMPERATURE] * 1.35 ) );
            // jungle: prefer hot, wet places that are low
            biomeFeatureConversion.put(WorldHelper.BiomeType.JUNGLE, (Double[] lst) ->
                    Math.max(0, lst[HUMIDITY] * 2)
                            * Math.max(0, lst[TEMPERATURE] * 0.6 - lst[TERRAIN_H] * 1.4 ) * 1.75 );

        }

        public final Double[] features = new Double[6];
        public final HashMap<WorldHelper.BiomeType, Double> biomeFeatures = new HashMap<>();
        public final WorldHelper.BiomeType evaluatedBiome;

        public BiomeFeature(int x, int z) {
            double distFromSpawn = Math.sqrt(x * x + z * z);
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
            }
            // convert to biome features
            for (WorldHelper.BiomeType biomeType : biomeFeatureConversion.keySet()) {
                double magnitude = biomeFeatureConversion.get(biomeType).applyAsDouble(features);
                biomeFeatures.put(biomeType, magnitude);
            }
            // spawn protection: biome tweak
            if (distFromSpawn < SPAWN_LOC_PROTECTION_RADIUS) {
                double protectedValue = Math.max(
                        (1 - distFromSpawnFactor) * 5,
                        biomeFeatures.get(WorldHelper.BiomeType.NORMAL)
                );
                biomeFeatures.put(WorldHelper.BiomeType.NORMAL, protectedValue);
            }
            // compute total magSqr
            double totalMagSqr = 0;
            for (double magnitude : biomeFeatures.values()) {
                totalMagSqr += magnitude * magnitude;
            }

            // normalize biome features and record the most possible biome
            double magMultiplier = 1 / Math.sqrt(totalMagSqr);
            // record the most possible biome; only consider prob > 0.1
            double mostProb = 0.1;
            WorldHelper.BiomeType mostProbType = WorldHelper.BiomeType.NORMAL;
            for (WorldHelper.BiomeType biomeType : biomeFeatureConversion.keySet()) {
                double updated = biomeFeatures.get(biomeType) * magMultiplier;
                biomeFeatures.put(biomeType, updated);
                if (updated > mostProb) {
                    mostProb = updated;
                    mostProbType = biomeType;
                }
            }
            // save the most possible biome
            evaluatedBiome = mostProbType;
        }
    }

    static HashMap<Long, BiomeFeature> biomeCache = new HashMap<>(CACHE_SIZE, 0.8f);


    // set up the generator based on seed
    private static void setupGenerators(long seed) {
        SEED = seed;
        Random rdm = new Random(seed);
        // temperature
        noiseTemp = new PerlinOctaveGenerator(rdm.nextLong(), 3);
        noiseTemp.setScale(0.001);
        // humidity
        noiseHum =  new PerlinOctaveGenerator(rdm.nextLong(), 3);
        noiseHum.setScale(0.001);
        // weirdness
        noiseWrd =  new PerlinOctaveGenerator(rdm.nextLong(), 3);
        noiseWrd.setScale(0.001);
        // erosion
        noiseEros = new PerlinOctaveGenerator(rdm.nextLong(), 8);
        noiseEros.setScale(0.0025);
        // terrain height
        noiseTrH = new PerlinOctaveGenerator(rdm.nextLong(), 8);
        noiseTrH.setScale(0.001);
        // update continentalness finally, so there is less chance things get broken
        noiseCont = new PerlinOctaveGenerator(rdm.nextLong(), 5);
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
        Bukkit.getLogger().info("Cache size: " + biomeCache.size() + " / " + CACHE_SIZE);
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
                    Bukkit.getLogger().info("Cache size: " + biomeCache.size() + " / " + CACHE_SIZE);
                }
            }
        Bukkit.getLogger().info("Generation progress: " + progress / progressMax);
        Bukkit.getLogger().info("Progress detail: " + progress + "/" + progressMax);
        Bukkit.getLogger().info("Cache size: " + biomeCache.size() + " / " + CACHE_SIZE);
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
            result ++;
            x *= -1;
        }
        result <<= 1;
        if (z < 0) {
            result ++;
            z *= -1;
        }
        // reserve 25 bytes for each of x and z
        result <<= 25;
        result += x;
        result <<= 25;
        result += z;
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
    public static BiomeFeature getBiomeFeature(int actualX, int actualZ) {
        int x = actualX >> 2, z = actualZ >> 2;

        long biomeLocKey = getCacheKey(x, z);
        if (biomeCache.containsKey(biomeLocKey)) {
            return biomeCache.get(biomeLocKey);
        }
        // evaluate the
        else {
            return getBiomeFeature(0, actualX, actualZ);
        }
    }
    // this should be called if possible!
    public static BiomeFeature getBiomeFeature(long seed, int actualX, int actualZ) {
//        int x = actualX >> 2, z = actualZ >> 2;
        int x = actualX, z = actualZ;

        long biomeLocKey = getCacheKey(x, z);
        if (biomeCache.containsKey(biomeLocKey)) {
            return biomeCache.get(biomeLocKey);
        }
        // evaluate the biome features
        else {
            if (noiseCont == null) {
                setupGenerators(seed);
            }
            BiomeFeature result = new BiomeFeature(actualX, actualZ);
            // clear cache when needed
            if (biomeCache.size() > CACHE_DELETION_SIZE)
                biomeCache.clear();
            // save the result and return
            biomeCache.put(biomeLocKey, result);
            return result;
        }
    }
    public static Biome getBiome(long seed, int actualX, int actualZ) {
        if (! generatedImg) {
            generatedImg = true;
            generateBiomeImage();
        }
        return getBiomeFromType( getBiomeFeature(seed, actualX, actualZ).evaluatedBiome );
    }
}