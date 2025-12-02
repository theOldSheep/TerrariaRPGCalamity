package terraria.worldgen.overworld;

import org.bukkit.block.Biome;
import org.bukkit.util.noise.PerlinOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;

import java.util.*;

import static terraria.worldgen.overworld.OverworldChunkGenerator.OCTAVES_TERRAIN;
import static terraria.worldgen.overworld.OverworldChunkGenerator.SCALE_TERRAIN;

public class OverworldBiomeGenerator {
    static final int
            BIOME_FEATURE_CACHE_SIZE = 500000,
            SPAWN_LOC_PROTECTION_RADIUS = 250;
    static final long MASK_LAST_HALF = 0xFFFFL;

    static PerlinOctaveGenerator noiseCont = null, noiseTemp, noiseHum, noiseWrd, noiseTrH;

    public static class BiomeFeature {
        public static final int
                // large mag. negative: sulphurous ocean, large mag. positive: ocean
                CONTINENTALNESS = 0,
                // has to do with special biomes identification
                TEMPERATURE = 1,
                HUMIDITY = 2,
                WEIRDNESS = 3,
                // determines the landscape's height
                TERRAIN_H = 4;
        public final Double[] features = new Double[6];
        public final double biomeSignificance;
        public final WorldHelper.BiomeType evaluatedBiome;

        public BiomeFeature(int x, int z) {
            features[CONTINENTALNESS] =     noiseCont.noise(x, z, 2, 0.5) * 1.25;
            features[TEMPERATURE] =         noiseTemp.noise(x, z, 2, 0.5) * 2;
            features[HUMIDITY] =            noiseHum .noise(x, z, 2, 0.5) * 2;
            features[WEIRDNESS] =           noiseWrd .noise(x, z, 2, 0.5) * 1.5;
            features[TERRAIN_H] =           noiseTrH .noise(x, z, 2, 0.5);
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
    static LinkedHashMap<Long, BiomeFeature> biomeCache = new LinkedHashMap<Long, BiomeFeature>(BIOME_FEATURE_CACHE_SIZE, 0.75f) {
        @Override
        // the eldest entry should be removed once the cache reaches its designed capacity
        protected boolean removeEldestEntry(Map.Entry entry) {
            return size() >= BIOME_FEATURE_CACHE_SIZE;
        }
    };

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
        // update continentalness finally, so there is less chance things get broken - setup is triggered by continentalness.
        noiseCont = new PerlinOctaveGenerator(rdm.nextLong(), 3);
        noiseCont.setScale(0.0002);
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
        long biomeLocKey = getCacheKey(blockX, blockZ);
        // return the cached value if available
        if (biomeCache.containsKey(biomeLocKey)) {
            return biomeCache.get(biomeLocKey);
        }
        // lazy initialization of noise functions
        if (noiseCont == null) {
            setupGenerators();
        }
        // the biome feature content is generated within the constructor
        BiomeFeature result = new BiomeFeature(blockX, blockZ);
        // save the result and return
        biomeCache.put(biomeLocKey, result);
        return result;
    }
    public static Biome getBiome(int blockX, int blockZ) {
        return getBiomeFromType( getBiomeType(blockX, blockZ) );
    }
    public static WorldHelper.BiomeType getBiomeType(int blockX, int blockZ) {
        return getBiomeFeature(blockX, blockZ).evaluatedBiome;
    }
}