package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import terraria.util.MathHelper;
import terraria.worldgen.RandomGenerator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

public class OverworldBiomeGenerator {
    static long seed = 0;
    static final int CACHE_SIZE = 150000,
            CACHE_DELETION_SIZE = 100000,
            SPAWN_LOC_PROTECTION_RADIUS = 500;
    static final double SPECIAL_BIOME_RATE = 0.5;


    static HashMap<Long, Integer> biomeCache = new HashMap<>(CACHE_SIZE, 0.8f);
    static HashMap<Long, Integer> biomeGridCache = new HashMap<>(CACHE_SIZE, 0.8f);
    // biomeCache ONLY STORES biome info, while biomeGridCache ONLY STORES biome grid info that are used to derive biome info
    // further info about the key format can be seen in the comment of function getCacheKey
    static boolean test = false; // should we always return forest to test out other functionalities?

    static String[] biomeGenProcess = new String[] {
        "zoom_in",
        "add_islands",
        "add_islands",
        "fill_ocean",
        "zoom_in_smooth",
        "zoom_in_smooth",
        "setup_rough_biome",
        "zoom_in",
        "zoom_in",
        "zoom_in_smooth",
        "smooth_biome",
        "add_beach",
        "add_beach",
        "zoom_in_smooth",
        "zoom_in_smooth",
        "zoom_in_smooth",
        "zoom_in_smooth",
//        remove two zoom in smooth and divide actual x and z by 4 if we wish for faster biome generation
//        "zoom_in_smooth",
//        "zoom_in_smooth",
        "smooth_biome",
    };


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
        int jump = 20;
        File dir_biome_map = new File("worldGenDebug/biomesMap(20000x20000).png");
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
                int blockX = (i - (scale / 2)) * jump + center, blockZ = (j - (scale / 2)) * jump + center;
                Biome currBiome = getBiome(seed, blockX, blockZ);
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
    private static long getCacheKey(int recursion, int x, int z) {
        long result = 0;
        // first two bytes denotes x and z
        if (x < 0) result ++;
        result = result << 1;
        if (z < 0) result ++;
        // reserve 6 bytes for recursion level
        result = result << 6;
        result += recursion;
        // reserve 25 bytes for each of x and z
        result = result << 25;
        result += Math.abs(x);
        result = result << 25;
        result += Math.abs(z);
        return result;
    }
    // biome enlarge helper functions
    private static int[][] zoom_in(int[][] original, int x, int z, int scale, boolean is_smooth) {
        int sizeOriginal = original.length;
        int size = original.length * 2 - 1;
        int[][] result = new int[size][size];
        // * · *
        // · · ·
        // * · *
        for (int i = 0; i < sizeOriginal; i ++)
            for (int j = 0; j < sizeOriginal; j ++) {
                result[i * 2][j * 2] = original[i][j];
            }
        for (int i = 0; i < sizeOriginal; i ++)
            for (int j = 0; j + 1 < sizeOriginal; j ++) {
                // · * ·
                // · · ·
                // · * ·
                double rdmResult = RandomGenerator.getRandom(seed, x + ((j * 2 + 1) * scale), z + (i * 2 * scale));
                if (rdmResult < 0.5) {
                    result[i * 2][j * 2 + 1] = original[i][j];
                } else {
                    result[i * 2][j * 2 + 1] = original[i][j + 1];
                }
            }
        for (int i = 0; i + 1 < sizeOriginal; i ++)
            for (int j = 0; j < sizeOriginal; j ++) {
                // · · ·
                // * · *
                // · · ·
                double rdmResult = RandomGenerator.getRandom(seed, x + (j * 2 * scale), z + ((i * 2 + 1) * scale));
                if (rdmResult < 0.5) {
                    result[i * 2 + 1][j * 2] = original[i][j];
                } else {
                    result[i * 2 + 1][j * 2] = original[i + 1][j];
                }
            }
        for (int i = 0; i + 1 < sizeOriginal; i ++)
            for (int j = 0; j + 1 < sizeOriginal; j ++) {
                // · · ·
                // · * ·
                // · · ·
                if (is_smooth) {
                    ArrayList<Integer> candidates = new ArrayList<>(5);
                    HashMap<Integer, Integer> occurrence = new HashMap<>(8);
                    for (int indX = j; indX <= j + 1; indX++)
                        for (int indZ = i; indZ <= i + 1; indZ++) {
                            int gridType = original[indZ][indX];
                            occurrence.put(gridType, occurrence.getOrDefault(gridType, 0) + 1);
                        }
                    // only the biome that have the highest rate of occurrence can be the result.
                    int maxOccurrence = 0;
                    for (int createdGrid : occurrence.keySet()) {
                        int currOccurrence = occurrence.get(createdGrid);
                        if (currOccurrence > maxOccurrence) {
                            maxOccurrence = currOccurrence;
                            candidates.clear();
                        }
                        if (currOccurrence == maxOccurrence)
                            candidates.add(createdGrid);
                    }
                    int rdmResult = RandomGenerator.getRandomGenerator(seed, x + ((j * 2 + 1) * scale), z + ((i * 2 + 1) * scale)).nextInt();
                    result[i * 2 + 1][j * 2 + 1] = candidates.get(Math.abs(rdmResult) % candidates.size());
                } else {
                    double rdmResult = RandomGenerator.getRandom(seed, x + ((j * 2 + 1) * scale), z + ((i * 2 + 1) * scale));
                    if (rdmResult < 0.25)
                        result[i * 2 + 1][j * 2 + 1] = original[i][j];
                    else if (rdmResult < 0.5)
                        result[i * 2 + 1][j * 2 + 1] = original[i + 1][j];
                    else if (rdmResult < 0.75)
                        result[i * 2 + 1][j * 2 + 1] = original[i][j + 1];
                    else
                        result[i * 2 + 1][j * 2 + 1] = original[i + 1][j + 1];
                }
            }
        return result;
    }
    private static int[][] add_islands(int[][] mapLand, int x, int z, int scale) {
        int size = mapLand.length;
        int[][] result = new int[size][size];
        for (int i = 0; i < size; i ++)
            for (int j = 0; j < size; j ++) {
                int blockX = x + (j * scale), blockZ = z + (i * scale);
                boolean isNearSpawnloc = (Math.abs(blockX) < SPAWN_LOC_PROTECTION_RADIUS * 2 && Math.abs(blockZ) < SPAWN_LOC_PROTECTION_RADIUS * 2);
                result[i][j] = mapLand[i][j];
                if (i == 0 || j == 0 || i + 1 == size || j + 1 == size) continue; // skip margins
                boolean hasAdjacentLand = false;
                boolean hasAdjacentOcean = false;
                for (int checkI = i - 1; checkI <= i + 1; checkI += 2) {
                    for (int checkJ = j - 1; checkJ <= j + 1; checkJ += 2) {
                        if (mapLand[checkI][checkJ] >= 1) hasAdjacentLand = true;
                        else hasAdjacentOcean = true;
                        if (hasAdjacentLand && hasAdjacentOcean) break;
                    }
                    if (hasAdjacentLand && hasAdjacentOcean) break;
                }
                double rdmResult = RandomGenerator.getRandom(seed, x + (j * scale), z + (i * scale));
                if (hasAdjacentLand && mapLand[i][j] <= 0 && rdmResult < 0.33) result[i][j] = 1;
                else if (hasAdjacentOcean && mapLand[i][j] >= 1 && rdmResult < 0.2 && !isNearSpawnloc) result[i][j] = 0;
                else result[i][j] = mapLand[i][j];
            }
        return result;
    }
    private static int[][] fill_ocean(int[][] mapLand, int x, int z, int scale) {
        int size = mapLand.length;
        int[][] result = new int[size][size];
        for (int i = 0; i < size; i ++)
            for (int j = 0; j < size; j ++) {
                int blockX = x + (j * scale), blockZ = z + (i * scale);
                boolean isNearSpawnloc = (Math.abs(blockX) + Math.abs(blockZ) < (SPAWN_LOC_PROTECTION_RADIUS + scale) * 2);
                if (isNearSpawnloc) result[i][j] = 1;
                else {
                    result[i][j] = mapLand[i][j];
                    if (i == 0 || j == 0 || i + 1 == size || j + 1 == size) continue; // skip margins
                    if (mapLand[i][j] >= 1) continue; // do nothing to lands
                    boolean hasAdjacentLand = false;
                    for (int checkI = i - 1; checkI < i + 1; checkI += 2) {
                        if (checkI < 0) continue;
                        if (mapLand[checkI][j] >= 1) {
                            hasAdjacentLand = true;
                            break;
                        }
                    }
                    if (!hasAdjacentLand)
                        for (int checkJ = j - 1; checkJ < j + 1; checkJ += 2) {
                            if (checkJ < 0) continue;
                            if (mapLand[i][checkJ] == 1) {
                                hasAdjacentLand = true;
                                break;
                            }
                        }
                    if (!hasAdjacentLand) {
                        // only try to make ocean a land when it has all water around
                        if (RandomGenerator.getRandom(seed, x + (j * scale), z + (i * scale)) < 0.5)
                            result[i][j] = 1;
                    }
                }
            }
        return result;
    }
    private static boolean biome_need_forest_margin(int toCheck) {
        return toCheck >= 2 && toCheck <= 7;
    }
    private static int[][] smooth_biome(int[][] mapLand, int x, int z, int scale) {
        int size = mapLand.length;
        int[][] result = new int[size][size];
        for (int i = 0; i < size; i ++)
            for (int j = 0; j < size; j ++) {
                result[i][j] = mapLand[i][j];
                if (i == 0 || j == 0 || i + 1 == size || j + 1 == size) continue; // skip margins
                // smooth biome: put all biomes in adjacent grid in a hashmap
                ArrayList<Integer> candidates = new ArrayList<>(5);
                HashMap<Integer, Integer> occurrence = new HashMap<>(8);
                for (int xOffset = -1; xOffset <= 1; xOffset ++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && zOffset == 0) continue;
                        int gridType;
                        gridType = mapLand[i + zOffset][j + xOffset];
                        occurrence.put(gridType, occurrence.getOrDefault(gridType, 0) + 1);
                    }
                }
                // only the biome that have the highest rate of occurrence can be the result.
                int maxOccurrence = 0, numBiomeNeedMargin = 0;
                for (int createdGrid : occurrence.keySet()) {
                    // if two conflicting biomes around this grid needed a margin
                    if (biome_need_forest_margin(createdGrid) && ++numBiomeNeedMargin > 1)
                        break;
                    int currOccurrence = occurrence.get(createdGrid);
                    if (currOccurrence > maxOccurrence) {
                        maxOccurrence = currOccurrence;
                        candidates.clear();
                    }
                    if (currOccurrence == maxOccurrence)
                        candidates.add(createdGrid);
                }
                // smoothed biome
                if (numBiomeNeedMargin > 1)
                    result[i][j] = 1;
                else {
                    int rdmResult = RandomGenerator.getRandomGenerator(seed, x + j * scale, z + i * scale).nextInt();
                    result[i][j] = candidates.get(Math.abs(rdmResult) % candidates.size());
                }
            }
        return result;
    }
    private static int[][] add_beach(int[][] mapLand, int x, int z, int scale) {
        int size = mapLand.length;
        int[][] result = new int[size][size];
        for (int i = 0; i < size; i ++)
            for (int j = 0; j < size; j ++) {
                result[i][j] = mapLand[i][j];
                if (i == 0 || j == 0 || i + 1 == size || j + 1 == size) continue; // skip margins
                if (mapLand[i][j] <= 0) continue; // no beach in oceans!
                int adjacentOcean = 0, adjacentSulphurous = 0;
                for (int idxOffset = -1; idxOffset <= 1; idxOffset += 2) {
                    int toCheck;
                    toCheck = mapLand[i + idxOffset][j];
                    if (toCheck == -1 || toCheck == 8) adjacentSulphurous ++;
                    else if (toCheck == 0 || toCheck == 9) adjacentOcean ++;
                    toCheck = mapLand[i][j + idxOffset];
                    if (toCheck == -1 || toCheck == 8) adjacentSulphurous ++;
                    else if (toCheck == 0 || toCheck == 9) adjacentOcean ++;
                }
                if (adjacentSulphurous >= adjacentOcean && adjacentSulphurous > 0) result[i][j] = 8;
                else if (adjacentOcean > adjacentSulphurous) result[i][j] = 9;
            }
        return result;
    }
    private static boolean biomeNeedTemperatureBuffer(int biome) {
        switch(biome) {
            case 2:
            case 3:
            case 4:
                return true;
        }
        return false;
    }
    private static int[][] setup_rough_biome(int[][] mapLand, int x, int z, int scale) {
        // -1: sulphurous ocean  0: ocean
        // 1: forest  2: jungle  3: tundra  4: desert  5: corruption  6: hallow  7: astral infection
        // 8: sulphurous beach  9: beach
        int size = mapLand.length;
        int[][] result = new int[size][size];
        for (int i = 0; i < size; i ++)
            for (int j = 0; j < size; j ++) {
                result[i][j] = mapLand[i][j];
                int blockX = x + (j * scale), blockZ = z + (i * scale);
                boolean isNearSpawnloc = (Math.abs(blockX) < SPAWN_LOC_PROTECTION_RADIUS && Math.abs(blockZ) < SPAWN_LOC_PROTECTION_RADIUS);
                double randomNum = RandomGenerator.getRandom(seed, blockX, blockZ);
                if (mapLand[i][j] <= 0) {
                    // ocean
                    if (randomNum < 0.2)
                        result[i][j] = -1;
                } else if (!isNearSpawnloc && mapLand[i][j] <= 7) {
                    // land (not beach)
                    if      (randomNum < SPECIAL_BIOME_RATE / 6)        result[i][j] = 2; // jungle
                    else if (randomNum < SPECIAL_BIOME_RATE / 3)        result[i][j] = 3; // tundra
                    else if (randomNum < SPECIAL_BIOME_RATE / 2)        result[i][j] = 4; // desert
                    else if (randomNum < SPECIAL_BIOME_RATE * 2 / 3)    result[i][j] = 5; // corruption
                    else if (randomNum < SPECIAL_BIOME_RATE * 5 / 6)    result[i][j] = 6; // hallow
                    else if (randomNum < SPECIAL_BIOME_RATE)            result[i][j] = 7; // astral infection
                }
            }
        // make sure tundra, jungle and desert do not meet each other
        for (int i = 1; i + 1 < size; i ++)
            for (int j = 1; j + 1 < size; j ++) {
                if (biomeNeedTemperatureBuffer(result[i][j])) {
                    if (biomeNeedTemperatureBuffer(result[i + 1][j]) && result[i + 1][j] != result[i][j])
                        result[i][j] = 1;
                    else if (biomeNeedTemperatureBuffer(result[i][j + 1]) && result[i][j + 1] != result[i][j])
                        result[i][j] = 1;
                    else if (biomeNeedTemperatureBuffer(result[i + 1][j + 1]) && result[i + 1][j + 1] != result[i][j])
                        result[i][j] = 1;
                    else if (biomeNeedTemperatureBuffer(result[i + 1][j - 1]) && result[i + 1][j - 1] != result[i][j])
                        result[i][j] = 1;
                }
            }
        return result;
    }

    private static int[][] getUpperLevelBiomeGrid(int radius, int x_begin, int z_begin, int gridSizeOffset, int recursion) {
        int land_grid[][] = new int[radius * 2 + 1][radius * 2 + 1];
        // load the grid 1 recursion level higher than current
        for (int i = 0; i < radius * 2 + 1; i++) {
            for (int j = 0; j < radius * 2 + 1; j++) {
                int blockX, blockZ;
                blockX = x_begin + (j * gridSizeOffset);
                blockZ = z_begin + (i * gridSizeOffset);
                if (recursion < biomeGenProcess.length) {
                    // up 1 recursion level
                    land_grid[i][j] = getGeneralBiomeGrid(blockX, blockZ, gridSizeOffset, recursion + 1);
                } else {
                    // initialize the highest level grid
                    if (RandomGenerator.getRandom(seed, blockX, blockZ) < 0.1)
                        land_grid[i][j] = 1;
                    else land_grid[i][j] = 0;
                }
            }
        }
        return land_grid;
    }
    private static int[][] manipulateBiomeGrid(int[][] land_grid, String operation, int x_begin, int z_begin, int gridSize) {
        // manipulate the grid according to current operation
        int[][] result;
        switch (operation) {
            case "setup_rough_biome":
                result = setup_rough_biome(land_grid, x_begin, z_begin, gridSize);
                break;
            case "zoom_in":
                result = zoom_in(land_grid, x_begin, z_begin, gridSize, false);
                break;
            case "zoom_in_smooth":
                result = zoom_in(land_grid, x_begin, z_begin, gridSize, true);
                break;
            case "add_islands":
                result = add_islands(land_grid, x_begin, z_begin, gridSize);
                break;
            case "fill_ocean":
                result = fill_ocean(land_grid, x_begin, z_begin, gridSize);
                break;
            case "add_beach":
                result = add_beach(land_grid, x_begin, z_begin, gridSize);
                break;
            default:
                result = smooth_biome(land_grid, x_begin, z_begin, gridSize);
        }
        return result;
    }
    private static void saveBiomeGrid(int[][] land_grid, int marginDiscard, int x_begin, int z_begin, int gridSize, int recursion) {
        int grid_x_save_start = MathHelper.betterFloorDivision(x_begin, gridSize),
                grid_z_save_start = MathHelper.betterFloorDivision(z_begin, gridSize);
        for (int i = marginDiscard; i + marginDiscard < land_grid.length; i++) {
            for (int j = marginDiscard; j + marginDiscard < land_grid[i].length; j++) {
                int grid_x_save = grid_x_save_start + j, grid_z_save = grid_z_save_start + i;
                long tempKey = getCacheKey(recursion, grid_x_save, grid_z_save);
                if (recursion == 1)
                    biomeCache.put(tempKey, land_grid[i][j]);
                else
                    biomeGridCache.put(tempKey, land_grid[i][j]);
            }
        }
    }
    private static int getGeneralBiomeGrid(int x, int z, int gridSize, int recursion) {
        int gridX = MathHelper.betterFloorDivision(x, gridSize), gridZ = MathHelper.betterFloorDivision(z, gridSize);
        long biomeLocKey = getCacheKey(recursion, gridX, gridZ);
        HashMap<Long, Integer> cache = recursion == 1 ? biomeCache : biomeGridCache;
        if (!cache.containsKey(biomeLocKey)) {
            // setup original position info.
            final int radius = 7;
            String operation = biomeGenProcess[biomeGenProcess.length - recursion];
            int[][] land_grid;
            int grid_x_begin, grid_z_begin, gridSizeOffset;
            if (operation.startsWith("zoom_in")) {
                gridSizeOffset = gridSize * 2;
            } else {
                gridSizeOffset = gridSize;
            }
            grid_x_begin = MathHelper.betterFloorDivision(x, gridSizeOffset * (radius - 2));
            grid_z_begin = MathHelper.betterFloorDivision(z, gridSizeOffset * (radius - 2));
            // we do a floor division by radius - 2 (margins are removed later).
            // so that the chunks we divide biome grid into can largely be independent
            // reducing unnecessary performance consumption
            grid_x_begin --;
            grid_z_begin --;
            int x_begin = grid_x_begin * gridSizeOffset * (radius - 2), z_begin = grid_z_begin * gridSizeOffset * (radius - 2);

            // load the grid 1 recursion level higher than current
            land_grid = getUpperLevelBiomeGrid(radius, x_begin, z_begin, gridSizeOffset, recursion);
            // manipulate the grid according to current operation
            int[][] manipulated_grid = manipulateBiomeGrid(land_grid, operation, x_begin, z_begin, gridSize);
            int marginDiscard = 1;
            if (operation.startsWith("zoom_in")) marginDiscard = 0;
            //save the grid info
            saveBiomeGrid(manipulated_grid, marginDiscard, x_begin, z_begin, gridSize, recursion);
        }
        int result = cache.get(biomeLocKey);
        if (cache.size() > CACHE_DELETION_SIZE) {
            cache.clear();
        }
        return result;
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
    public static Biome getBiome(long seed, int actualX, int actualZ) {
        if (test)
            return Biome.FOREST;
        int x = actualX / 4, z = actualZ / 4;
        long biomeLocKey = getCacheKey(1, x, z);
        int rst;
        if (biomeCache.containsKey(biomeLocKey)) {
            rst = biomeCache.get(biomeLocKey);
        } else {
            if (OverworldBiomeGenerator.seed == 0) {
                OverworldBiomeGenerator.seed = seed;
                generateBiomeImage();
            }
            rst = getGeneralBiomeGrid(x, z, 1, 1);
        }

        Biome result;
        switch (rst) {
            case -1:
                result = Biome.FROZEN_OCEAN; // sulphurous ocean
                break;
            case 0:
                result = Biome.OCEAN; // ocean
                break;
            case 2:
                result = Biome.JUNGLE; // jungle
                break;
            case 3:
                result = Biome.TAIGA_COLD; // tundra
                break;
            case 4:
                result = Biome.DESERT; // desert
                break;
            case 5:
                result = Biome.MUSHROOM_ISLAND; // corruption
                break;
            case 6:
                result = Biome.ICE_FLATS; // hallow
                break;
            case 7:
                result = Biome.MESA; // astral infection
                break;
            case 8:
                result = Biome.COLD_BEACH; // sulphurous beach
                break;
            case 9:
                result = Biome.BEACHES; // beach
                break;
            default:
                result = Biome.FOREST; //forest
        }
        return result;
    }
}