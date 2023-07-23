package terraria.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.block.CraftSkull;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

public class WorldHelper {
    public static final double CHANCE_SPECIAL_PLANT = 0.1, CHANCE_LIFE_FRUIT = 0.1, CHANCE_BULB = 0.025;
    public static final String
            UUID_LIFE_FRUIT = "81834b91-513d-450f-b920-6880fd47c997",
            UUID_BULB = "50af25ef-adb9-44d0-880f-6890ea0dc182",
            TEXTURE_LIFE_FRUIT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmNiNmU2YTg5NTJmNDlhNTRlZThjY2RhMzQzOGExNjlmNjU3OGZmZDY5NjhmYTA0Y2FhYjYyOGE5NTllZTFlMSJ9fX0=",
            TEXTURE_BULB = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTVkODk0MzFkMTRiZmVmMjA2MDQ2MWI0YTM1NjU2MTRkYzUxMTE1YzAwMWZhZTI1MDhlODY4NGJjMGFlNmE4MCJ9fX0=";
    public static BlockFace[] SKULL_FACING_DIRECTIONS = new BlockFace[] {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST,
            BlockFace.NORTH_EAST,
            BlockFace.NORTH_WEST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH_WEST,
            BlockFace.WEST_NORTH_WEST,
            BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH_NORTH_EAST,
            BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST_SOUTH_EAST,
            BlockFace.SOUTH_SOUTH_EAST,
            BlockFace.SOUTH_SOUTH_WEST,
            BlockFace.WEST_SOUTH_WEST,
    };
    public static BlockFace[] DIRECT_CONTACT_DIRECTIONS = new BlockFace[] {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN,
    };

    public enum HeightLayer {
        SPACE("太空"), SURFACE("地表"), UNDERGROUND("地下"), CAVERN("洞穴"), UNDERWORLD("地狱");
        public final String name;
        HeightLayer(String name) {
            this.name = name;
        }
        public static HeightLayer getHeightLayer(Location loc) {
            switch (loc.getWorld().getName()) {
                case TerrariaHelper.Constants.WORLD_NAME_SURFACE: {
                    if (loc.getY() > 200) return SPACE;
                    if (loc.getY() > 50) return SURFACE;
                    return UNDERGROUND;
                }
                case TerrariaHelper.Constants.WORLD_NAME_CAVERN: {
                    return CAVERN;
                }
                case TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD: {
                    return UNDERWORLD;
                }
                default:
                    return SURFACE;
            }
        }
    }
    public enum BiomeType {
        ABYSS("深渊"), ASTRAL_INFECTION("星辉瘟疫"), BRIMSTONE_CRAG("硫火之崖"), CORRUPTION("腐化之地"),
        DESERT("沙漠"), DUNGEON("地牢"), HALLOW("神圣之地"), JUNGLE("丛林"), METEOR("陨石"),
        NORMAL("森林"), OCEAN("海洋"), SPACE("太空"), SULPHUROUS_OCEAN("硫磺海"),
        SUNKEN_SEA("沉沦之海"), TEMPLE("丛林神庙"), TUNDRA("雪原"), UNDERWORLD("地狱");
        public final String name;
        BiomeType(String name) {
            this.name = name;
        }
        public static BiomeType getBiome(Player ply) {
            return getBiome(ply, true);
        }
        public static BiomeType getBiome(Player ply, boolean considerSpace) {
            BiomeType biomeType = (BiomeType) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_BIOME).value();
            if (biomeType != NORMAL) return biomeType;
            return getBiome(ply.getLocation(), considerSpace);
        }
        public static BiomeType getBiome(Location loc) {
            return getBiome(loc, true);
        }
        public static BiomeType getBiome(Location loc, boolean considerSpace) {
            if (considerSpace) {
                HeightLayer height = HeightLayer.getHeightLayer(loc);
                if (height == HeightLayer.SPACE) {
                    return SPACE;
                }
            }
            Biome biome = loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockZ());
            switch (biome) {
                case HELL:
                    return UNDERWORLD;
                case SAVANNA:
                    return BRIMSTONE_CRAG;
                case MUSHROOM_ISLAND:
                case MUSHROOM_ISLAND_SHORE:
                    return CORRUPTION;
                case ICE_FLATS:
                case MUTATED_ICE_FLATS:
                    return HALLOW;
                case MESA:
                case MUTATED_MESA:
                    return ASTRAL_INFECTION;
                case DESERT:
                    return DESERT;
                case MUTATED_DESERT:
                    return SUNKEN_SEA;
                case BEACHES:
                case OCEAN:
                    return OCEAN;
                case FROZEN_OCEAN:
                case COLD_BEACH:
                    return SULPHUROUS_OCEAN;
                case DEEP_OCEAN:
                    return ABYSS;
                case TAIGA_COLD:
                case MUTATED_TAIGA_COLD:
                    return TUNDRA;
                case JUNGLE:
                case MUTATED_JUNGLE:
                    return JUNGLE;
                default:
                    return NORMAL;
            }
        }
    }
    public static int getMoonPhase() {
        World overworld = Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE);
        if (overworld != null) {
            return (int) ((overworld.getFullTime() / 24000) % 8);
        }
        return 0;
    }
    public static boolean isDayTime(World wld) {
        return isDayTime(wld.getTime());
    }
    public static boolean isDayTime(long timeInTick) {
        return ! (MathHelper.isBetween(timeInTick, 13500, 22500));
    }
    public static void initWorldRules(World wld) {
        // game rules
        wld.setGameRuleValue("doDaylightCycle",     "false");
        wld.setGameRuleValue("doMobSpawning",       "false");
        wld.setGameRuleValue("doWeatherCycle",      "false");
        wld.setGameRuleValue("keepInventory",       "true");
        wld.setGameRuleValue("maxEntityCramming",   "1");
        wld.setGameRuleValue("randomTickSpeed",     "0");
        // clear weather
        wld.setThundering(false);
        wld.setStorm(false);
    }
    public static void attemptDestroyVegetation(Location startLoc, Location endLoc) {
        Vector direction = endLoc.clone().subtract(startLoc).toVector();
        double traceLengthDouble = direction.length();
        if (traceLengthDouble < 1e-5)
            return;
        int traceLength = (int) traceLengthDouble;
        if (traceLength < 1)
            traceLength = 1;
        BlockIterator blockIterator = new BlockIterator(startLoc.getWorld(),
                startLoc.toVector(), direction, 0d, traceLength);
        blockIterator.forEachRemaining(WorldHelper::attemptDestroyVegetation);
    }
    public static void attemptDestroyVegetation(Block block) {
        switch (block.getType()) {
            case LONG_GRASS:
            case RED_ROSE:
            case YELLOW_FLOWER:
            case RED_MUSHROOM:
            case BROWN_MUSHROOM:
                block.breakNaturally();
        }
    }
    public static void createTemporaryLava(Block block) {
        if (block.getType() == Material.AIR) {
            block.setType(Material.LAVA);
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                Block currBlock = block.getLocation().getBlock();
                switch (currBlock.getType()) {
                    case LAVA:
                    case STATIONARY_LAVA:
                        currBlock.setType(Material.AIR);
                }
            }, 50);
        }
    }

    //
    // World Tick
    //
    public static void worldRandomTick(World wld) {
        for (Chunk chunk : wld.getLoadedChunks()) {
            // chunks can only tick when a 3*3 chunk grid is all loaded.
            boolean canTick = true;
            for (int offsetX = -1; offsetX <= 1; offsetX ++)
                for (int offsetZ = -1; offsetZ <= 1; offsetZ ++)
                    if (! wld.isChunkLoaded(chunk.getX() + offsetX, chunk.getZ() + offsetZ)) {
                        canTick = false;
                        offsetX = 999;
                        offsetZ = 999;
                    }
            if (! canTick)
                continue;
            // chunk tick mechanisms
            worldRandomTickGrass(chunk);
            worldRandomTickVegetation(chunk);
        }
    }
    //
    // grass spread ticking
    //
    private static Block getRandomBlockInChunk(Chunk chunk) {
        Random randomGenerator = ((CraftWorld) chunk.getWorld()).getHandle().random;
        return chunk.getBlock(
                randomGenerator.nextInt(16),
                randomGenerator.nextInt(256),
                randomGenerator.nextInt(16));
    }
    private static Block getRandomBlockInRadius(Block block, int radius) {
        int diameter = radius * 2 + 1;
        Random randomGenerator = ((CraftWorld) block.getWorld()).getHandle().random;
        return block.getRelative(
                randomGenerator.nextInt(diameter) - radius,
                randomGenerator.nextInt(diameter) - radius,
                randomGenerator.nextInt(diameter) - radius);
    }
    private static boolean blockPosValidForGrass(Block block) {
        return ! block.getRelative(BlockFace.UP).getType().isSolid();
    }
    private static Block getSpreadBlock(Block block, Material... targetMat) {
        ArrayList<Block> validBLocks = new ArrayList<>();
        for (int xOffset = -1; xOffset <= 1; xOffset ++)
            for (int yOffset = -1; yOffset <= 1; yOffset ++)
                for (int zOffset = -1; zOffset <= 1; zOffset ++) {
                    Block testBlock = block.getRelative(xOffset, yOffset, zOffset);
                    Material testMat = testBlock.getType();
                    // validate material
                    boolean materialValid = false;
                    for (Material validMat : targetMat) {
                        if (testMat == validMat) {
                            materialValid = true;
                            break;
                        }
                    }
                    // make sure it is not identical as the original block
                    if (materialValid && !(testMat == block.getType() && testBlock.getData() == block.getData()))
                        validBLocks.add(testBlock);
                }
        if (validBLocks.size() > 1)
            return validBLocks.get( (int) (Math.random() * validBLocks.size()) );
        return null;
    }
    private static void worldRandomTickGrass(Chunk chunk) {
        // grass block spread; fade when covered by solid block
        for (int i = 0; i < MathHelper.randomRound(25); i ++) {
            Block blockToTick = getRandomBlockInChunk(chunk);
            switch (blockToTick.getType()) {
                // "grass" blocks of different biomes
                case GRASS: {
                    if (blockPosValidForGrass(blockToTick)) {
                        Block blockToSpread = getSpreadBlock(blockToTick, Material.DIRT);
                        if (blockToSpread != null &&
                                blockToSpread.getType() == Material.DIRT && blockPosValidForGrass(blockToSpread))
                            blockToSpread.setType(Material.GRASS, false);
                    } else
                        blockToTick.setType(Material.DIRT, false);
                    break;
                }
                case DIRT: {
                    if (blockToTick.getData() == 2) {
                        if (blockPosValidForGrass(blockToTick)) {
                            Block blockToSpread = getSpreadBlock(blockToTick, Material.DIRT, Material.GRASS, Material.MYCEL);
                            if (blockToSpread != null &&
                                    blockPosValidForGrass(blockToSpread) &&
                                    BiomeType.getBiome(blockToSpread.getLocation()) == BiomeType.HALLOW) {
                                blockToSpread.setType(Material.DIRT, false);
                                blockToSpread.setData((byte) 2, false);
                            }
                        } else
                            blockToTick.setType(Material.DIRT, false);
                    }
                    break;
                }
                case MYCEL: {
                    if (blockPosValidForGrass(blockToTick)) {
                        Block blockToSpread = getSpreadBlock(blockToTick, Material.DIRT, Material.GRASS, Material.MYCEL);
                        if (blockToSpread != null &&
                                blockPosValidForGrass(blockToSpread) &&
                                BiomeType.getBiome(blockToSpread.getLocation()) == BiomeType.CORRUPTION) {
                            blockToSpread.setType(Material.DIRT, false);
                            blockToSpread.setData((byte) 2, false);
                        }
                    } else
                        blockToTick.setType(Material.DIRT, false);
                    break;
                }
                // special stone and sand blocks spread
                case SAND: {
                    if (blockToTick.getData() == 1) {
                        Block blockToSpread = getSpreadBlock(blockToTick, Material.SAND);
                        if (blockToSpread != null &&
                                BiomeType.getBiome(blockToSpread.getLocation()) == BiomeType.SULPHUROUS_OCEAN) {
                            blockToSpread.setData((byte) 1, false);
                        }
                    }
                    break;
                }
                case STAINED_CLAY: {
                    BiomeType biome = null;
                    switch (blockToTick.getData()) {
                        case 0:
                            biome = BiomeType.HALLOW;
                            break;
                        case 7:
                            biome = BiomeType.CORRUPTION;
                            break;
                    }
                    if (biome != null) {
                        Block blockToSpread = getSpreadBlock(blockToTick, Material.STONE, Material.STAINED_CLAY);
                        if (blockToSpread != null &&
                                BiomeType.getBiome(blockToSpread.getLocation()) == biome) {
                            blockToSpread.setType(Material.STAINED_CLAY, false);
                            blockToSpread.setData(blockToTick.getLightFromBlocks(), false);
                        }
                    }
                    break;
                }
                case MOSSY_COBBLESTONE: {
                    // spreads at a slower rate than dirt
                    if (Math.random() < 0.35)
                        return;
                    Block blockToSpread = getSpreadBlock(blockToTick, Material.DIRT, Material.GRASS);
                    if (blockToSpread != null &&
                            BiomeType.getBiome(blockToSpread.getLocation()) == BiomeType.JUNGLE &&
                            HeightLayer.getHeightLayer(blockToSpread.getLocation()) == HeightLayer.CAVERN) {
                        // make sure the area is not crowded with chlorophyte ore
                        int estimatedAmount = 0;
                        for (int testIndex = 0; testIndex < 27; testIndex ++) {
                            Block blockToTest = getRandomBlockInRadius(blockToTick, 4);
                            if (blockToTest.getType() == Material.MOSSY_COBBLESTONE)
                                estimatedAmount ++;
                        }
                        // only spread when the area is not crowded
                        if (estimatedAmount < 3)
                            blockToSpread.setType(Material.MOSSY_COBBLESTONE, false);
                    }
                    break;
                }
            }
        }
        // chlorophyte spread
        for (int i = 0; i < MathHelper.randomRound(17.5); i ++) {
            Block blockToTick = getRandomBlockInChunk(chunk);
            if (blockToTick.getType() == Material.MOSSY_COBBLESTONE) {
                // make sure the area is not crowded with chlorophyte ore
                int estimatedAmount = 0;
                for (int testIndex = 0; testIndex < 27; testIndex++) {
                    Block blockToTest = getRandomBlockInRadius(blockToTick, 4);
                    if (blockToTest.getType() == Material.MOSSY_COBBLESTONE)
                        estimatedAmount++;
                }
                // spreads when not crowded
                if (estimatedAmount <= 1) {
                    Block blockToSpread = getSpreadBlock(blockToTick, Material.DIRT, Material.GRASS);
                    if (blockToSpread != null &&
                            BiomeType.getBiome(blockToSpread.getLocation()) == BiomeType.JUNGLE &&
                            HeightLayer.getHeightLayer(blockToSpread.getLocation()) == HeightLayer.CAVERN) {
                        // only spread when the area is not crowded
                        blockToSpread.setType(Material.MOSSY_COBBLESTONE, false);
                    }
                }
                // becomes dirt when too crowded
                else if (estimatedAmount >= 3) {
                    if (blockToTick.getRelative(BlockFace.UP).getType().isSolid())
                        blockToTick.setType(Material.DIRT, false);
                    else
                        blockToTick.setType(Material.GRASS, false);
                }
            }
        }
    }
    //
    // vegetation and plant growth ticking
    //
    //                  flower and long grass
    private static void placeSpecialPlant(Block block, String nameUUID, String texture) {
        block.setType(Material.SKULL, false);
        block.setData( (byte) 1 );
        BlockState blockState = block.getState();
        if (blockState instanceof Skull) {
            Skull skullState = (Skull) blockState;
            skullState.setRotation( SKULL_FACING_DIRECTIONS[(int) (Math.random() * SKULL_FACING_DIRECTIONS.length)] );
            skullState.setSkullType(SkullType.PLAYER);

            GameProfile profile = new GameProfile( UUID.fromString(nameUUID), null );
            profile.getProperties().put("textures", new Property("textures", texture) );
            try {
                Field profileField = CraftSkull.class.getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(skullState, profile);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            skullState.update();
        }
        else
            TerrariaHelper.getInstance().getLogger().log(Level.SEVERE,
                    "FAILED PLACING SPECIAL SKULL AT BLOCK " + block);
    }
    // TODO: different sprite of vegetation
    private enum PlantType {
        GRASS(Material.LONG_GRASS, (byte) 1),
        GRASS_HALLOW(Material.LONG_GRASS, (byte) 1),
        GRASS_CORRUPTION(Material.LONG_GRASS, (byte) 1),
        CACTUS(Material.CACTUS),
        MUSHROOM(Material.RED_MUSHROOM),
        GLOWING_MUSHROOM(Material.BROWN_MUSHROOM),
        BLINK_ROOT(Material.RED_ROSE, (byte) 5),
        DAY_BLOOM(Material.YELLOW_FLOWER),
        DEATH_WEED(Material.RED_ROSE, (byte) 1),
        FIRE_BLOSSOM(Material.RED_ROSE, (byte) 0),
        MOON_GLOW(Material.RED_ROSE, (byte) 2),
        SHIVER_THORN(Material.RED_ROSE, (byte) 3),
        WATER_LEAF(Material.RED_ROSE, (byte) 4),
        SPECIAL_OR_NONE(null);
        final Material material;
        final byte data;
        PlantType(Material material) {
            this(material, (byte) 0);
        }
        PlantType(Material material, byte data) {
            this.material = material;
            this.data = data;
        }
    }
    public static boolean canGrowPlant(Block block) {
        return canGrowPlant(block, false);
    }
    public static boolean canGrowPlant(Block block, boolean strict) {
        return canGrowPlant(block, strict, true);
    }
    public static boolean canGrowPlant(Block block, boolean strict, boolean checkAbove) {
        if (checkAbove && block.getRelative(BlockFace.UP).getType() != Material.AIR)
            return false;
        Material blockMat = block.getType();
        byte data = block.getData();
        switch (BiomeType.getBiome(block.getLocation())) {
            case UNDERWORLD:
                return !strict && blockMat == Material.NETHERRACK;
            case BRIMSTONE_CRAG:
                return !strict && blockMat == Material.RED_NETHER_BRICK;
            case TUNDRA:
                return blockMat == Material.SNOW_BLOCK;
            case OCEAN:
            case DESERT:
                return !strict && blockMat == Material.SAND;
            case HALLOW:
                return blockMat == Material.DIRT && data == 2;
            case CORRUPTION:
                return blockMat == Material.MYCEL;
            default:
                return blockMat == Material.GRASS;
        }
    }
    public static void attemptGrowPlant(Block plantBlock) {
        if (plantBlock.getType() != Material.AIR)
            return;
        // plant different blocks according to location
        BiomeType biome = BiomeType.getBiome(plantBlock.getLocation());
        HeightLayer height = HeightLayer.getHeightLayer(plantBlock.getLocation());
        // determine the type of plant
        PlantType plantType = PlantType.GRASS;
        switch (height) {
            case SPACE:
            case SURFACE:
                if (Math.random() < CHANCE_SPECIAL_PLANT) {
                    switch (biome) {
                        case OCEAN:
                        case DESERT:
                            plantType = PlantType.WATER_LEAF;
                            break;
                        case JUNGLE:
                            plantType = PlantType.MOON_GLOW;
                            break;
                        case TUNDRA:
                            plantType = PlantType.SHIVER_THORN;
                            break;
                        case CORRUPTION:
                            plantType = PlantType.DEATH_WEED;
                            break;
                        case NORMAL:
                        case HALLOW:
                            if (Math.random() < 0.3) {
                                plantType = PlantType.MUSHROOM;
                            }
                            else {
                                plantType = PlantType.DAY_BLOOM;
                            }
                            break;
                    }
                }
                else {
                    switch (biome) {
                        case TUNDRA:
                        case CORRUPTION:
                            plantType = PlantType.SPECIAL_OR_NONE;
                            break;
                        case OCEAN:
                        case DESERT:
                            if (Math.random() < 0.025)
                                plantType = PlantType.CACTUS;
                            else
                                plantType = PlantType.SPECIAL_OR_NONE;
                            break;
                    }
                }
                break;
            case UNDERGROUND:
            case CAVERN:
                if (Math.random() < CHANCE_SPECIAL_PLANT) {
                    switch (biome) {
                        case JUNGLE:
                            if (Math.random() < 0.5) {
                                plantType = PlantType.GLOWING_MUSHROOM;
                            }
                            if (Math.random() < CHANCE_BULB) {
                                plantType = PlantType.SPECIAL_OR_NONE;
                                placeSpecialPlant(plantBlock, UUID_BULB, TEXTURE_BULB);
                            }
                            else if (Math.random() < CHANCE_LIFE_FRUIT) {
                                plantType = PlantType.SPECIAL_OR_NONE;
                                placeSpecialPlant(plantBlock, UUID_LIFE_FRUIT, TEXTURE_LIFE_FRUIT);
                            }
                            break;
                        case TUNDRA:
                            plantType = PlantType.SHIVER_THORN;
                            break;
                        case CORRUPTION:
                            plantType = PlantType.DEATH_WEED;
                            break;
                        case NORMAL:
                        case HALLOW:
                            plantType = PlantType.SPECIAL_OR_NONE;
                            break;
                    }
                }
                else {
                    switch (biome) {
                        case TUNDRA:
                        case CORRUPTION:
                        case OCEAN:
                        case DESERT:
                            plantType = PlantType.SPECIAL_OR_NONE;
                            break;
                    }
                }
                break;
            case UNDERWORLD:
                plantType = Math.random() < CHANCE_SPECIAL_PLANT ?
                        PlantType.FIRE_BLOSSOM : PlantType.SPECIAL_OR_NONE;
                break;
        }
        if (plantType != PlantType.SPECIAL_OR_NONE) {
            plantBlock.setType(plantType.material, false);
            if (plantType.data != 0)
                plantBlock.setData(plantType.data);
        }
    }
    //                  trees
    public static class LeafShape {
        LeafLayerShape[] layers;
        public LeafShape(LeafLayerShape... layers) {
            this.layers = layers;
        }
    }
    public static class LeafLayerShape {
        int radius, roundness;
        public LeafLayerShape(int radius, int roundness) {
            this.radius = radius;
            this.roundness = roundness;
        }
    }
    static ArrayList<LeafShape> candidateCanopyShapes, candidateBranchShapes;
    static {
        candidateCanopyShapes = new ArrayList<>(50);
        {
            // empty
            {
                LeafShape empty = new LeafShape();
                candidateCanopyShapes.add(empty);
            }
            // round
            {
                LeafShape round = new LeafShape(
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(1, 0)
                );
                candidateCanopyShapes.add(round);
                candidateCanopyShapes.add(round);
                candidateCanopyShapes.add(round);
                candidateCanopyShapes.add(round);
                candidateCanopyShapes.add(round);
            }
            // small round
            {
                LeafShape small_round = new LeafShape(
                        new LeafLayerShape(2, 1),
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(2, 1)
                );
                candidateCanopyShapes.add(small_round);
                candidateCanopyShapes.add(small_round);
                candidateCanopyShapes.add(small_round);
            }
            // tall
            {
                LeafShape tall = new LeafShape(
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(2, 1),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(2, 1),
                        new LeafLayerShape(0, 0)
                );
                candidateCanopyShapes.add(tall);
                candidateCanopyShapes.add(tall);
                candidateCanopyShapes.add(tall);
            }
            // boxy
            {
                LeafShape boxy = new LeafShape(
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(0, 0)
                );
                candidateCanopyShapes.add(boxy);
                candidateCanopyShapes.add(boxy);
            }
            // tiny
            {
                LeafShape tiny = new LeafShape(
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(0, 0)
                );
                candidateCanopyShapes.add(tiny);
            }
            // large spiky
            {
                LeafShape large_spiky = new LeafShape(
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(3, 0),
                        new LeafLayerShape(4, 1),
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(2, 1),
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(0, 0)
                );
                candidateCanopyShapes.add(large_spiky);
                candidateCanopyShapes.add(large_spiky);
            }
            // small spiky
            {
                LeafShape small_spiky = new LeafShape(
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(2, 1),
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(0, 0),
                        new LeafLayerShape(0, 0)
                );
                candidateCanopyShapes.add(small_spiky);
            }
            // tower
            {
                LeafShape tower = new LeafShape(
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(3, 1),
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(0, 0)
                );
                candidateCanopyShapes.add(tower);
                candidateCanopyShapes.add(tower);
                candidateCanopyShapes.add(tower);
            }
            // minecraft
            {
                LeafShape minecraft = new LeafShape(
                        new LeafLayerShape(3, 0),
                        new LeafLayerShape(4, 1),
                        new LeafLayerShape(2, 0),
                        new LeafLayerShape(1, 0)
                );
                candidateCanopyShapes.add(minecraft);
                candidateCanopyShapes.add(minecraft);
            }
        }

        candidateBranchShapes = new ArrayList<>(10);
        {
            // empty
            {
                LeafShape empty = new LeafShape();
                candidateBranchShapes.add(empty);
            }
            // small
            {
                LeafShape small = new LeafShape(
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(0, 0)
                );
                candidateBranchShapes.add(small);
            }
            // medium
            {
                LeafShape medium = new LeafShape(
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(1, 0),
                        new LeafLayerShape(0, 0)
                );
                candidateBranchShapes.add(medium);
            }
        }
    }
    public static byte getSaplingVariantByBiome(BiomeType biomeType) {
        switch (biomeType) {
            case ASTRAL_INFECTION:
                return 4;
            case CORRUPTION:
                return 5;
            case HALLOW:
                return 2;
            case TUNDRA:
                return 1;
            case JUNGLE:
                return 3;
            default:
                return 0;
        }
    }
    private static void generateLeaves(Block topTrunk, ArrayList<Block> leafBlocks, LeafShape shape, BlockFace dir) {
        for (LeafLayerShape currentLayer : shape.layers) {
            int radius = currentLayer.radius;
            int restraint = currentLayer.roundness;
            // loop one direction
            for (int i = -radius; i <= radius; i ++) {
                // account for roundness restraint
                int absI = Math.abs(i);
                int jRadius = radius - Math.abs(i);
                if (absI < restraint) jRadius -= restraint - absI;
                // loop another direction
                for (int j = -jRadius; j <= jRadius; j++) {
                    // account for roundness restraint
                    int absJ = Math.abs(j);
                    if (absJ < restraint && absI + absJ >= radius)
                        continue;
                    // add block
                    switch (dir) {
                        case SOUTH:
                        case NORTH:
                            leafBlocks.add(topTrunk.getRelative(i, j, 0));
                            break;
                        case EAST:
                        case WEST:
                            leafBlocks.add(topTrunk.getRelative(0, j, i));
                            break;
                        default:
                            leafBlocks.add(topTrunk.getRelative(i, 0, j));
                    }
                }
            }
            topTrunk = topTrunk.getRelative(dir);
        }
    }
    private static Block generateSideBranch(Block branchTrunk,
                                            ArrayList<Block> trunkBlocks, ArrayList<Block> leafBlocks, LeafShape shape) {
        // randomize branch direction
        BlockFace branchDir;
        {
            double random = Math.random();
            if (random < 0.25) {
                branchDir = BlockFace.EAST;
            } else if (random < 0.5) {
                branchDir = BlockFace.SOUTH;
            } else if (random < 0.75) {
                branchDir = BlockFace.WEST;
            } else {
                branchDir = BlockFace.NORTH;
            }
        }
        // register the branched log
        Block branchBlock = branchTrunk.getRelative(branchDir);
        trunkBlocks.add(branchBlock);
        // leaves
        generateLeaves(branchBlock, leafBlocks, shape, branchDir);

        return branchBlock;
    }
    public static void attemptGenerateTree(Block rootBlock) {
        attemptGenerateTree(rootBlock, 4 + (int) (Math.random() * 8) );
    }
    public static void attemptGenerateTree(Block rootBlock, int trunkHeight) {
        // validate the block below
        if ( ! canGrowPlant(rootBlock.getRelative(BlockFace.DOWN), true, false) ) {
            return;
        }
        Block branchTrunkBlock = null;
        ArrayList<Block> trunkBlocks = new ArrayList<>();
        ArrayList<Block> leafBlocks = new ArrayList<>();
        // pre-generate trunks
        for (int i = 0; i < trunkHeight; i ++) {
            trunkBlocks.add(rootBlock.getRelative(0, i, 0));
        }
        // pre-generate canopy leaves
        LeafShape leafShapeCanopy = candidateCanopyShapes.get((int) (Math.random() * candidateCanopyShapes.size()) );
        Block topTrunkBlock = rootBlock.getRelative(0, trunkHeight - 1, 0);
        generateLeaves(topTrunkBlock, leafBlocks, leafShapeCanopy, BlockFace.UP);
        // pre-generate branch, if the tree is tall enough
        if (trunkHeight > 8) {
            int branchHeight = (int) (4 + Math.random() * (trunkHeight - 8));
            Block branchTrunk = rootBlock.getRelative(0, branchHeight, 0);
            LeafShape leafShapeBranch = candidateBranchShapes.get((int) (Math.random() * candidateBranchShapes.size()) );
            branchTrunkBlock = generateSideBranch(branchTrunk, trunkBlocks, leafBlocks, leafShapeBranch);
        }
        // validate if the tree has enough space to grow
        {
            HashSet<Block> validateBlocks = new HashSet<>();
            // leaves
            for (Block leafBlock : leafBlocks) {
                for (BlockFace face : DIRECT_CONTACT_DIRECTIONS)
                    validateBlocks.add(leafBlock.getRelative(face));
            }
            // trunk
            for (Block trunkBlock : trunkBlocks) {
                for (BlockFace face : DIRECT_CONTACT_DIRECTIONS)
                    validateBlocks.add(trunkBlock.getRelative(face));
            }
            // the dirt below the root should not hinder the tree from growing
            validateBlocks.remove(rootBlock.getRelative(BlockFace.DOWN));
            // validate
            for (Block validateBlock : validateBlocks)
                if (validateBlock.getType().isSolid()) {
                    return;
                }
        }
        // place the tree
        {
            BiomeType biomeType = BiomeType.getBiome(rootBlock.getLocation());
            byte treeType = getSaplingVariantByBiome(biomeType);
            Material logMat = treeType < 4 ? Material.LOG : Material.LOG_2;
            Material leafMat = treeType < 4 ? Material.LEAVES : Material.LEAVES_2;
            byte treeData = (byte) (treeType & 3);
            // leaves
            for (Block leafBlock : leafBlocks) {
                leafBlock.setType(leafMat, false);
                leafBlock.setData(treeData, false);
            }
            // trunk
            for (Block trunkBlock : trunkBlocks) {
                trunkBlock.setType(logMat, false);
                trunkBlock.setData(treeData, false);
            }
            // update branch rotation
            if (branchTrunkBlock != null) {
                if (branchTrunkBlock.getX() != rootBlock.getX())
                    branchTrunkBlock.setData((byte) (treeData + 4), false);
                else
                    branchTrunkBlock.setData((byte) (treeData + 8), false);
            }
        }
    }
    private static void worldRandomTickVegetation(Chunk chunk) {
        // grass
        for (int i = 0; i < MathHelper.randomRound(1.25); i ++) {
            Block blockToTick = getRandomBlockInChunk(chunk);
            if (canGrowPlant(blockToTick)) {
                Block plantBlock = blockToTick.getRelative(BlockFace.UP);
                attemptGrowPlant(plantBlock);
            }
        }
        // other plant growth ticking
        for (int i = 0; i < MathHelper.randomRound(12.5); i ++) {
            Block blockToTick = getRandomBlockInChunk(chunk);
            switch (blockToTick.getType()) {
                case SAPLING: {
                    if (Math.random() < 0.5) {
                        attemptGenerateTree(blockToTick);
                    }
                    break;
                }
                case PUMPKIN_STEM: {
                    byte growthProgress = blockToTick.getData();
                    growthProgress ++;
                    if (growthProgress <= 7)
                        blockToTick.setData(growthProgress, false);
                    else
                        blockToTick.setType(Material.PUMPKIN, false);
                    break;
                }
            }
        }
    }
}
