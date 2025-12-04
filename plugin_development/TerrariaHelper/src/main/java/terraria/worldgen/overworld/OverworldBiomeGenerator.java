package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.util.noise.PerlinOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;

import java.util.*;

import static terraria.worldgen.overworld.OverworldChunkGenerator.*;

public class OverworldBiomeGenerator {
    static final int SPAWN_LOC_PROTECTION_RADIUS = 250;
    static final long MASK_LAST_HALF = 0xFFFFL;
    static long[] testGenDurTotal = {0, 0, 0, 0}; // cache hit, cache miss, biome gen, cache update
    static long[] testGenAmount = {0, 0}; // cache hit, cache miss

    static PerlinOctaveGenerator noiseCont = null, noiseTemp, noiseHum, noiseWrd, noiseTrH, noiseEros;

    public static class BiomeFeature {
        public static final int
                // large mag. negative: sulphurous ocean, large mag. positive: ocean
                CONTINENTALNESS = 0,
                // has to do with special biomes identification
                TEMPERATURE = 1,
                HUMIDITY = 2,
                WEIRDNESS = 3,
                // landscape noise
                TERRAIN_H = 4,
                EROSION = 5;
        public final Double[] features = new Double[6];
        public final double biomeSignificance;
        public final WorldHelper.BiomeType evaluatedBiome;

        public BiomeFeature(int x, int z) {
            features[CONTINENTALNESS] =     noiseCont.noise(x, z, 2, 0.5) * 1.25;
            features[TEMPERATURE] =         noiseTemp.noise(x, z, 2, 0.5) * 2;
            features[HUMIDITY] =            noiseHum .noise(x, z, 2, 0.5) * 2;
            features[WEIRDNESS] =           noiseWrd .noise(x, z, 2, 0.5) * 1.5;
            features[TERRAIN_H] =           noiseTrH .noise(x, z, 2, 0.5);
            features[EROSION] =             noiseEros.noise(x, z, 2, 0.5);
            // spawn protection: feature tweak
            if (Math.abs(x) < SPAWN_LOC_PROTECTION_RADIUS && Math.abs(z) < SPAWN_LOC_PROTECTION_RADIUS) {
                // legacy: prevent overflow from integer > 32768.
                double distFromSpawn = Math.sqrt((double) x * (double) x + (double) z * (double) z);
                double distFromSpawnFactor = distFromSpawn / SPAWN_LOC_PROTECTION_RADIUS;
                if (distFromSpawn < SPAWN_LOC_PROTECTION_RADIUS) {
                    features[CONTINENTALNESS] *= distFromSpawnFactor;
                    features[TEMPERATURE] *= distFromSpawnFactor;
                    features[HUMIDITY] *= distFromSpawnFactor;
                    features[WEIRDNESS] *= distFromSpawnFactor;
                }
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
                biomeSignificance = -hum;
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
                // -0.5 is the ocean; the dungeon is closer to the ocean side.
                biomeSignificance = -Math.abs( cnt + 0.35 );
            }
        }
    }

    // biome cache
    static HashMap<Long, BiomeFeature> biomeCache = new HashMap<>(BIOME_CACHE_LIMIT, (float) BIOME_CACHE_LOAD_FACTOR);

    // generate images and store biomes near the spawn
    public static void init() {
        setupGenerators();
        BiomeSummary.createBiomeCenters();
        BiomeSummary.generateBiomeImage();
    }
    // set up the generator based on seed
    private static void setupGenerators() {
        Random rdm = new Random(TerrariaHelper.WORLD_SEED);
        // temperature
        noiseTemp = new PerlinOctaveGenerator(rdm.nextLong(), 1);
        noiseTemp.setScale(0.001);
        // humidity
        noiseHum =  new PerlinOctaveGenerator(rdm.nextLong(), 1);
        noiseHum.setScale(0.001);
        // weirdness
        noiseWrd =  new PerlinOctaveGenerator(rdm.nextLong(), 1);
        noiseWrd.setScale(0.001);
        // terrain height
        noiseTrH = new PerlinOctaveGenerator(rdm.nextLong(), OCTAVES_TERRAIN);
        noiseTrH.setScale(SCALE_TERRAIN);
        // terrain eros.
        noiseEros = new PerlinOctaveGenerator(rdm.nextLong(), 3);
        noiseEros.setScale(0.005);
        // update continentalness finally, so there is less chance things get broken - setup is triggered by continentalness.
        noiseCont = new PerlinOctaveGenerator(rdm.nextLong(), 3);
        noiseCont.setScale(0.0003);
    }

    // get the key in the biome cache
    private static long getCacheKey(int x, int z) {
        long result = 0;

        result |= x;
        result <<= 16;
        // use logical right shift when bit shifting z
        // note: due to the java's hashing for long, it is better to flip the order of the first and last halves of z
        // so that the lower digits do not cancel out frequently
        result |= (z & MASK_LAST_HALF);
        result <<= 16;
        result |= z >>> 16;

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
    // get the biome feature at position; this should be used for tree growth etc.
    public static BiomeFeature getBiomeFeature(double actualX, double actualZ) {
        return getBiomeFeature((int) actualX, (int) actualZ);
    }
    public static BiomeFeature getBiomeFeature(int blockX, int blockZ) {
        long timing = System.nanoTime();

        long biomeLocKey = getCacheKey(blockX, blockZ);
        // return the cached value if available
        BiomeFeature result = biomeCache.get(biomeLocKey);
        if (result != null) {
            testGenDurTotal[0] += System.nanoTime() - timing;
            testGenAmount[0] ++;
            if (testGenAmount[0] % 1000 == 0) {
                Bukkit.broadcastMessage("Biome feature cache hit: " + testGenDurTotal[0] / testGenAmount[0]);
            }

            return result;
        }

        testGenDurTotal[1] += System.nanoTime() - timing;
        timing = System.nanoTime();

        // lazy initialization of noise functions
        if (noiseCont == null) {
            setupGenerators();
        }
        // the biome feature content is generated within the constructor
        result = new BiomeFeature(blockX, blockZ);

        testGenDurTotal[2] += System.nanoTime() - timing;
        timing = System.nanoTime();

        // save the result and return
        if (biomeCache.size() > BIOME_CACHE_LIMIT) biomeCache.clear();
        biomeCache.put(biomeLocKey, result);

        testGenDurTotal[3] += System.nanoTime() - timing;
        testGenAmount[1] ++;
        if (testGenAmount[1] % 1000 == 0) {
            Bukkit.broadcastMessage("Biome feature cache miss: " + testGenDurTotal[1] / testGenAmount[1]);
            Bukkit.broadcastMessage("Biome feature gen: " + testGenDurTotal[2] / testGenAmount[1]);
            Bukkit.broadcastMessage("Biome feature cache upd: " + testGenDurTotal[3] / testGenAmount[1]);
            Bukkit.broadcastMessage("Biome feature cache miss total: " + (testGenDurTotal[1] + testGenDurTotal[2] + testGenDurTotal[3]) / testGenAmount[1]);
            Bukkit.broadcastMessage("Biome cache size: " + biomeCache.size());
            Bukkit.broadcastMessage("Biome feature cache hit rate: " + ((double) testGenAmount[0] / (double) (testGenAmount[0] + testGenAmount[1])) );
        }

        return result;
    }
    public static Biome getBiome(int blockX, int blockZ) {
        return getBiomeFromType( getBiomeType(blockX, blockZ) );
    }
    public static WorldHelper.BiomeType getBiomeType(int blockX, int blockZ) {
        return getBiomeFeature(blockX, blockZ).evaluatedBiome;
    }
}