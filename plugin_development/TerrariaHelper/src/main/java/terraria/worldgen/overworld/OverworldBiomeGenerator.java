package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.util.noise.PerlinOctaveGenerator;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;
import terraria.worldgen.Interpolate;

import java.util.*;

import static terraria.worldgen.overworld.OverworldChunkGenerator.*;

public class OverworldBiomeGenerator {
    static final int SPAWN_LOC_PROTECTION_RADIUS = 250;
    static final long MASK_LAST_HALF = 0xFFFFL;

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

            // find the evaluated biome & significance
            HashMap<WorldHelper.BiomeType, Double> relevance = getBiomesWeight();
            WorldHelper.BiomeType evalBiomeType = WorldHelper.BiomeType.NORMAL;
            double evalBiomeSig = 0d;
            for (WorldHelper.BiomeType currBiome : relevance.keySet()) {
                double currSig = relevance.get(currBiome);
                if (currSig > evalBiomeSig) {
                    evalBiomeSig = currSig;
                    evalBiomeType = currBiome;
                }
            }
            this.evaluatedBiome = evalBiomeType;
            this.biomeSignificance = evalBiomeSig;
        }

        private static final Interpolate SULPHUROUS_OCEAN_REL;
        private static final Interpolate OCEAN_REL;
        private static final Interpolate ASTRAL_REL;
        private static final Interpolate HALLOW_REL;
        private static final Interpolate CORRUPTION_REL;
        private static final Interpolate JUNGLE_REL;
        private static final Interpolate DESERT_REL;
        private static final Interpolate TUNDRA_REL;
        static {
            // note: make sure the noise will increase indefinitely from 0.9 to 1.0, so that biome-centers can be computed correctly
            // sulphurous ocean is an exception - otherwise the deepest places will not show bedrock.

            // both oceans: pass in cont.
            SULPHUROUS_OCEAN_REL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    new Interpolate.InterpolatePoint(0.5 - BIOME_INTERPOLATE, 0),
                    new Interpolate.InterpolatePoint(0.5 + BIOME_INTERPOLATE, 1),
            }, null);
            OCEAN_REL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    new Interpolate.InterpolatePoint(-2.0, 1),
                    new Interpolate.InterpolatePoint(-0.5 - BIOME_INTERPOLATE, 0.95),
                    new Interpolate.InterpolatePoint(-0.5 + BIOME_INTERPOLATE, 0),
            }, null);
            // astral: pass in hum + 2 * abs(cnt) (prefer negative)
            ASTRAL_REL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    new Interpolate.InterpolatePoint(-2.0, 1),
                    new Interpolate.InterpolatePoint(-0.25 - BIOME_INTERPOLATE, 0.95),
                    new Interpolate.InterpolatePoint(-0.25 + BIOME_INTERPOLATE, 0),
            }, null);
            // hallow: pass in weirdness (prefer negative)
            HALLOW_REL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    new Interpolate.InterpolatePoint(-2.0, 1),
                    new Interpolate.InterpolatePoint(-0.55 - BIOME_INTERPOLATE, 0.95),
                    new Interpolate.InterpolatePoint(-0.55 + BIOME_INTERPOLATE, 0),
            }, null);
            // corruption: pass in weirdness (prefer positive)
            CORRUPTION_REL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    new Interpolate.InterpolatePoint(0.55 - BIOME_INTERPOLATE, 0),
                    new Interpolate.InterpolatePoint(0.55 + BIOME_INTERPOLATE, 0.95),
                    new Interpolate.InterpolatePoint(2.0, 1),
            }, null);
            // jungle: pass in max(temp, 0) * hum (prefer positive)
            JUNGLE_REL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    new Interpolate.InterpolatePoint(0.08  - BIOME_INTERPOLATE, 0),
                    new Interpolate.InterpolatePoint(0.08  + BIOME_INTERPOLATE, 0.95),
                    new Interpolate.InterpolatePoint(2.0, 1),
            }, null);
            // desert: pass in max(temp, 0) * -hum (prefer positive)
            DESERT_REL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    new Interpolate.InterpolatePoint(0.08  - BIOME_INTERPOLATE, 0),
                    new Interpolate.InterpolatePoint(0.08  + BIOME_INTERPOLATE, 0.95),
                    new Interpolate.InterpolatePoint(2.0, 1),
            }, null);
            // tundra: pass in tmp (prefer negative)
            TUNDRA_REL = new Interpolate(new Interpolate.InterpolatePoint[]{
                    new Interpolate.InterpolatePoint(-2.0, 1),
                    new Interpolate.InterpolatePoint(-0.55 - BIOME_INTERPOLATE, 0.95),
                    new Interpolate.InterpolatePoint(-0.55 + BIOME_INTERPOLATE, 0),
            }, null);
        }
        public HashMap<WorldHelper.BiomeType, Double> getBiomesWeight() {
            HashMap<WorldHelper.BiomeType, Double> result = new HashMap<>();

            // evaluate the biome for this noise
            double tmp = features[TEMPERATURE], hum = features[HUMIDITY],
                    cnt = features[CONTINENTALNESS], wrd = features[WEIRDNESS];

            double prioritySum = 0d;
            // oceans - independent and has the highest priority
            double sulphurousOcean = SULPHUROUS_OCEAN_REL.getY(cnt);
            double ocean = OCEAN_REL.getY(cnt);
            prioritySum += ocean + sulphurousOcean;
            // astral infection - the second-highest priority
            double astral = ASTRAL_REL.getY(hum + 2 * Math.abs(cnt));
            astral = Math.max(astral - prioritySum, 0);
            prioritySum += astral;
            // hallow and corruption - next highest, independent
            double hallow = HALLOW_REL.getY(wrd);
            double corruption = CORRUPTION_REL.getY(wrd);
            hallow = Math.max(hallow - prioritySum, 0);
            corruption = Math.max(corruption - prioritySum, 0);
            prioritySum += hallow + corruption;
            // jungle - next highest
            double jungle = JUNGLE_REL.getY(Math.max(tmp, 0) * hum);
            jungle = Math.max(jungle - prioritySum, 0);
            prioritySum += jungle;
            // desert - next highest
            double desert = DESERT_REL.getY(Math.max(tmp, 0) * -hum);
            desert = Math.max(desert - prioritySum, 0);
            prioritySum += desert;
            // tundra - next highest
            double tundra = TUNDRA_REL.getY(tmp);
            tundra = Math.max(tundra - prioritySum, 0);
            prioritySum += tundra;
            // normal - left over
            double normal = Math.max(1 - prioritySum, 0);

            // assemble the result
            if (ocean > 1e-9) result.put(WorldHelper.BiomeType.OCEAN, ocean);
            if (sulphurousOcean > 1e-9) result.put(WorldHelper.BiomeType.SULPHUROUS_OCEAN, sulphurousOcean);
            if (astral > 1e-9) result.put(WorldHelper.BiomeType.ASTRAL_INFECTION, astral);
            if (hallow > 1e-9) result.put(WorldHelper.BiomeType.HALLOW, hallow);
            if (corruption > 1e-9) result.put(WorldHelper.BiomeType.CORRUPTION, corruption);
            if (jungle > 1e-9) result.put(WorldHelper.BiomeType.JUNGLE, jungle);
            if (desert > 1e-9) result.put(WorldHelper.BiomeType.DESERT, desert);
            if (tundra > 1e-9) result.put(WorldHelper.BiomeType.TUNDRA, tundra);
            if (normal > 1e-9) result.put(WorldHelper.BiomeType.NORMAL, normal);
            return result;
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
        long biomeLocKey = getCacheKey(blockX, blockZ);
        // return the cached value if available
        BiomeFeature result = biomeCache.get(biomeLocKey);
        if (result != null) {
            return result;
        }

        // lazy initialization of noise functions
        if (noiseCont == null) {
            setupGenerators();
        }
        // the biome feature content is generated within the constructor
        result = new BiomeFeature(blockX, blockZ);

        // save the result and return
        if (biomeCache.size() > BIOME_CACHE_LIMIT) biomeCache.clear();
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