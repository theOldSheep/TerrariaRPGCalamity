package terraria.util;

import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.PacketPlayOutBlockBreakAnimation;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.block.CraftSkull;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.MetadataValue;
import terraria.TerrariaHelper;
import terraria.entity.others.TerrariaMinecart;
import terraria.worldgen.overworld.OverworldBiomeGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameplayHelper {
    // this set is also used by ItemUseHelper to determine ray trace block result.
    // that is, materials in this set would be ignored while mining.
    public static Set<Material> noMiningSet;
    static {
        noMiningSet = new HashSet<>();
        noMiningSet.add(Material.AIR);
        noMiningSet.add(Material.WATER);
        noMiningSet.add(Material.STATIONARY_WATER);
        noMiningSet.add(Material.LAVA);
        noMiningSet.add(Material.STATIONARY_LAVA);
    }
    public static String getBlockCategory(Block block) {
        switch (block.getType()) {
            case ANVIL:
                return "ANVIL";
            case WOOL:
                return "CLOTH";
            case GLASS:
            case ICE:
            case PACKED_ICE:
            case FROSTED_ICE:
                return "GLASS";
            case LEAVES:
            case LEAVES_2:
            case GRASS:
            case MYCEL:
            case PUMPKIN:
            case PUMPKIN_STEM:
            case LONG_GRASS:
            case YELLOW_FLOWER:
            case RED_ROSE:
            case SKULL:
                return "GRASS";
            case CLAY:
            case DIRT:
            case GRAVEL:
            case NETHERRACK:
                return "GRAVEL";
            case LADDER:
                return "LADDER";
            case SAND:
                return "SAND";
            case SNOW:
            case SNOW_BLOCK:
                return "SNOW";
            case LOG:
            case LOG_2:
            case WOOD:
            case CHEST:
            case TRAPPED_CHEST:
            case WORKBENCH:
            case WOOD_DOOR:
            case WOODEN_DOOR:
                return "WOOD";
            case STONE:
            case SANDSTONE:
            case RED_SANDSTONE:
            case OBSIDIAN:
            case HARD_CLAY:
            case STAINED_CLAY:
            case PRISMARINE:
            case SEA_LANTERN:
            case RED_NETHER_BRICK:
                return "STONE";
        }
        return "METAL";
    }
    public static void playBlockParticleAndSound(Block blockToBreak) {
        double particleRadius = 0.5;
        MaterialData data = new MaterialData(blockToBreak.getType(), blockToBreak.getData());
        blockToBreak.getWorld().spawnParticle(Particle.BLOCK_CRACK,
                blockToBreak.getLocation().add(0.5, 0.5, 0.5), 25,
                particleRadius, particleRadius, particleRadius,
                data);
        String soundCategory = getBlockCategory(blockToBreak);
            blockToBreak.getWorld().playSound(blockToBreak.getLocation(),
                    "block." + soundCategory + ".place",2, 1);
    }
    private static ConfigurationSection getBlockConfigSection(Block blockToBreak) {
        String material = blockToBreak.getType().toString();
        String data = String.valueOf(blockToBreak.getData());
        BlockState state = blockToBreak.getState();
        if (state instanceof Furnace) {
            String furnaceName = GenericHelper.trimText( ((Furnace) state).getCustomName() );
            if (TerrariaHelper.blockConfig.contains(furnaceName))
                return TerrariaHelper.blockConfig.getConfigurationSection(furnaceName);
        }
        if (state instanceof CraftSkull) {
            CraftSkull craftSkull = (CraftSkull) state;
            String skullUUID = craftSkull.getOwningPlayer().getUniqueId().toString();
            if (TerrariaHelper.blockConfig.contains(skullUUID))
                return TerrariaHelper.blockConfig.getConfigurationSection(skullUUID);
        }
        if (TerrariaHelper.blockConfig.contains(material + "_" + data)) {
            return TerrariaHelper.blockConfig.getConfigurationSection(material + "_" + data);
        } if (TerrariaHelper.blockConfig.contains(material)) {
            return TerrariaHelper.blockConfig.getConfigurationSection(material);
        }
//        Bukkit.broadcastMessage("Not handled block type: " + blockToBreak.getType() + " with data " + blockToBreak.getData() + " and state " + state);
        return null;
    }
    public static boolean isBreakable(Block block, Player ply) {
        if (noMiningSet.contains(block.getType())) return false;
        // the block directly below a tree can not be mined, unless it is also a log.
        {
            Block logBlock = block.getRelative(BlockFace.UP);
            switch (logBlock.getType()){
                case LOG:
                case LOG_2:
                    // horizontal logs should not make the block unbreakable
                    if (logBlock.getData() < 4) {
                        // logs should not make other logs unbreakable
                        switch (block.getType()){
                            case LOG:
                            case LOG_2:
                                break;
                            default:
                                return false;
                        }
                    }
            }
        }
        // special breaking mechanics for certain blocks
        switch (block.getType()) {
            // unbreakable
            case BEDROCK:
            case ENDER_PORTAL_FRAME:
                return false;
            // unbreakable when items are present in the block
            case CHEST:
            case TRAPPED_CHEST:
                Inventory chestInv = ((Chest) block.getState()).getBlockInventory();
                for (ItemStack item : chestInv.getStorageContents()) {
                    if (item != null && item.getType() != Material.AIR)
                        return false;
                }
                break;
        }
        ConfigurationSection breakRule = getBlockConfigSection(block);
        if (breakRule != null) {
            if (breakRule.contains("progress")) {
                // requires the player to defeat a certain boss
                if (!PlayerHelper.hasDefeated(ply, breakRule.getString("progress")))
                    return false;
            }
            if (breakRule.contains("powerPickaxe")) {
                // requires certain pickaxe power
                if (EntityHelper.getAttrMap(ply).getOrDefault("powerPickaxe", 0d) <
                        breakRule.getDouble("powerPickaxe", 0d))
                    return false;
            }
        }
        BlockBreakEvent evt = new BlockBreakEvent(block, ply);
        Bukkit.getPluginManager().callEvent(evt);
        return !evt.isCancelled();
    }
    private static int getBlockBreakingDisplayID(Location loc) {
        int result = loc.getBlockX();
        result <<= 1;
        result += loc.getBlockZ();
        result <<= 1;
        result += loc.getBlockY();
        return result;
    }
    public static void playerMineBlock(Block blockToBreak, Player ply) {
        if (blockToBreak.getType() == Material.AIR) return;
        playBlockParticleAndSound(blockToBreak);
        if (!isBreakable(blockToBreak, ply)) return;
        // get existing breaking progress
        MetadataValue temp = EntityHelper.getMetadata(blockToBreak, EntityHelper.MetadataName.BLOCK_BREAK_PROGRESS);
        int breakingProgress = 0;
        if (temp != null) breakingProgress = temp.asInt();
        // get breaking progress required to break the block
        ConfigurationSection configSection = getBlockConfigSection(blockToBreak);
        int breakingProgressMax = 100;
        if (configSection != null)
            breakingProgressMax = configSection.getInt("totalProgress");
        // get pickaxe power
        int powerPickaxe = EntityHelper.getAttrMap(ply).getOrDefault("powerPickaxe", 0d).intValue();
        // handle breaking progress
        breakingProgress += powerPickaxe;
        int breakProgressDisplayID = getBlockBreakingDisplayID(blockToBreak.getLocation());
        PacketPlayOutBlockBreakAnimation packetToSend;
        if (breakingProgress >= breakingProgressMax) {
            // send packet and metadata before breaking to prevent bug
            EntityHelper.setMetadata(blockToBreak, EntityHelper.MetadataName.BLOCK_BREAK_PROGRESS, 0);
            packetToSend = new PacketPlayOutBlockBreakAnimation(
                    breakProgressDisplayID,
                    new BlockPosition(blockToBreak.getX(), blockToBreak.getY(), blockToBreak.getZ()),
                    -1
            );
            // send packet
            for (Player player : Bukkit.getOnlinePlayers())
                ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetToSend);
            // break the block if progress is met
            playerBreakBlock(blockToBreak, ply);
        } else {
            // save block breaking progress and send animation packet
            EntityHelper.setMetadata(blockToBreak, EntityHelper.MetadataName.BLOCK_BREAK_PROGRESS, breakingProgress);
            int breakProgress = (int) Math.floor((double) (8 * breakingProgress) / breakingProgressMax);
            packetToSend = new PacketPlayOutBlockBreakAnimation(
                    breakProgressDisplayID,
                    new BlockPosition(blockToBreak.getX(), blockToBreak.getY(), blockToBreak.getZ()),
                    breakProgress
            );
            // send packet
            for (Player player : Bukkit.getOnlinePlayers())
                ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetToSend);
        }
    }
    public static boolean playerBreakBlock(Block blockToBreak, Player ply) {
        return playerBreakBlock(blockToBreak, ply, true, true);
    }
    public static boolean playerBreakBlock(Block blockToBreak, Player ply, boolean validateBreakable, boolean playSound) {
        return playerBreakBlock(blockToBreak, ply, validateBreakable, playSound, false);
    }
    public static boolean playerBreakBlock(Block blockToBreak, Player ply, boolean validateBreakable, boolean playSound, boolean noDrop) {
        Material blockMat = blockToBreak.getType();
        if (blockMat == Material.AIR)
            return true;
        if (playSound)
            playBlockParticleAndSound(blockToBreak);
        if (validateBreakable && !isBreakable(blockToBreak, ply))
            return false;
        // several blocks that are dropped using vanilla mechanism
        switch (blockMat) {
            case BED_BLOCK:
            case WOOD_DOOR:
            case WOODEN_DOOR:
                blockToBreak.breakNaturally();
                break;
            default:
                if (! noDrop) {
                    ConfigurationSection configSection = getBlockConfigSection(blockToBreak);
                    if (configSection != null) {
                        // item to drop
                        List<String> itemsToDrop = configSection.getStringList("dropItem");
                        Location locToDrop = blockToBreak.getLocation().add(0.5, 0.4, 0.5);
                        for (String currItem : itemsToDrop) {
                            org.bukkit.inventory.ItemStack itemToDrop = ItemHelper.getItemFromDescription(currItem);
                            ItemHelper.dropItem(locToDrop, itemToDrop);
                        }
                        // boss spawn
                        String bossSpawn = configSection.getString("spawnBoss");
                        if (bossSpawn != null)
                            BossHelper.spawnBoss(ply, BossHelper.BossType.valueOf(bossSpawn));
                    }
                }
                blockToBreak.setType(Material.AIR);
        }
        // special handling
        handleTreeConsecutiveBreak(ply, blockToBreak, blockMat, noDrop);
        handleGrassConsecutiveBreak(ply, blockToBreak);
        handleSpecialBlockBreakMechanism(ply, blockToBreak, blockMat);
        return true;
    }
    // tree breaking helpers
    public enum BlockTreePart {
        NONE, LOG, LEAVES;
    }
    public static BlockTreePart getBlockTreePart(Material brokenMaterial) {
        switch (brokenMaterial) {
            case LOG:
            case LOG_2:
                return BlockTreePart.LOG;
            case LEAVES:
            case LEAVES_2:
                return BlockTreePart.LEAVES;
            default:
                return BlockTreePart.NONE;
        }
    }
    private static void handleTreeConsecutiveBreak(Player ply, Block brokenBlock, Material brokenMaterial, boolean noDrop) {
        BlockTreePart originalBlockTreePart = getBlockTreePart(brokenMaterial);
        if (originalBlockTreePart == BlockTreePart.NONE) return;
        BlockFace[] directionsToTest = {
                BlockFace.UP,
                BlockFace.DOWN,
                BlockFace.WEST,
                BlockFace.EAST,
                BlockFace.NORTH,
                BlockFace.SOUTH
        };
        for (BlockFace direction : directionsToTest) {
            Block currBlock = brokenBlock.getRelative(direction);
            BlockTreePart currBlockTreePart = getBlockTreePart(currBlock.getType());
            boolean shouldBreak = false;
            switch (currBlockTreePart) {
                // not tree part
                case NONE:
                    break;
                // logs are broken bottom-up (and probably sideways too)
                case LOG:
                    if (originalBlockTreePart == BlockTreePart.LOG && direction != BlockFace.DOWN)
                        shouldBreak = true;
                    break;
                // leaves are always broken
                case LEAVES:
                    shouldBreak = true;
            }
            if (shouldBreak) {
                playerBreakBlock(currBlock, ply, false, false, noDrop);
            }
        }
    }
    // grass breaks after the block beneath it is broken
    private static void handleGrassConsecutiveBreak(Player ply, Block brokenBlock) {
        Block blockAbove = brokenBlock.getRelative(BlockFace.UP);
        switch (blockAbove.getType()) {
            case LONG_GRASS:
            case YELLOW_FLOWER:
            case RED_ROSE:
            case SKULL:
            case PUMPKIN_STEM:
            case SAPLING:
            case BED:
            case BED_BLOCK:
                playerBreakBlock(blockAbove, ply, true, false);
        }
    }
    // other block special feature when broken
    private static void handleSpecialBlockBreakMechanism(Player ply, Block brokenBlock, Material blockMat) {
        switch (blockMat) {
            case MAGMA:
                WorldHelper.createTemporaryLava(brokenBlock);
                break;
        }
    }


    // when player right click interacts with a block
    public static void playerRightClickBlock(Player ply, Block blk) {
        if (blk == null) return;
        ItemStack plyTool = ply.getEquipment().getItemInMainHand();
        String plyToolType = ItemHelper.splitItemName(plyTool)[1];
        Material blockType = blk.getType();
        byte blockData = blk.getData();
        switch (blockType) {
            // wool changes color
            case WOOL: {
                int data = blockData;
                if (ply.isSneaking())
                    data = (data + 1) % 16;
                else
                    data = (data + 15) % 16;
                int finalData = data;
                Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(), () -> blk.setData((byte) finalData));
                break;
            }
            // bed change spawn point
            case BED_BLOCK: {
                Location bedLoc = ply.getBedSpawnLocation(), newLoc = blk.getLocation();
                if (bedLoc == null ||
                        bedLoc.getWorld() != newLoc.getWorld() ||
                        bedLoc.distanceSquared(newLoc) > 5) {
                    ply.setBedSpawnLocation(blk.getLocation(), false);
                    ply.sendMessage("§7出生点已设置。");
                }
                else {
                    ply.setBedSpawnLocation(null);
                    ply.sendMessage("§7出生点已移除。");
                }
                break;
            }
            // attempt to change world
            case BEDROCK: {
                Location teleportedLoc = blk.getLocation();
                teleportedLoc.add(0.5, 0, 0.5);
                String worldName = blk.getWorld().getName();
                // going downward
                if (blk.getY() < 64) {
                    if (worldName.equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE)) {
                        teleportedLoc.setY(253.1);
                        teleportedLoc.setWorld(
                                Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_CAVERN));
                    }
                    else if (worldName.equals(TerrariaHelper.Constants.WORLD_NAME_CAVERN)) {
                        teleportedLoc.setY(126.001);
                        teleportedLoc.setWorld(
                                Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD));
                    }
                    else {
                        break;
                    }
                }
                // going upward
                else {
                    teleportedLoc.setY(1.001);
                    if (worldName.equals(TerrariaHelper.Constants.WORLD_NAME_CAVERN)) {
                        teleportedLoc.setWorld(
                                Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE));
                    }
                    else if (worldName.equals(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD)) {
                        teleportedLoc.setWorld(
                                Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_CAVERN));
                    }
                    else {
                        break;
                    }
                }
                // teleport
                Block blockToBreak = teleportedLoc.getBlock();
                blockToBreak.setType(Material.AIR);
                blockToBreak.getRelative(BlockFace.UP).setType(Material.AIR);
                ply.teleport(teleportedLoc);
                break;
            }
            // dirt/grass
            case DIRT:
            case GRASS:
            case MYCEL: {
                switch (plyToolType) {
                    // seeds
                    case "草种": {
                        if (blockType == Material.DIRT && blockData == 0) {
                            blk.setType(Material.GRASS, false);
                            plyTool.setAmount(plyTool.getAmount() - 1);
                        }
                        break;
                    }
                    case "腐化种子": {
                        if (blockType == Material.DIRT && blockData == 0) {
                            blk.setType(Material.MYCEL, false);
                            plyTool.setAmount(plyTool.getAmount() - 1);
                            // set biome
                            World wld = blk.getWorld();
                            int blkX = blk.getX();
                            int blkZ = blk.getZ();
                            boolean isCavern = WorldHelper.HeightLayer.getHeightLayer(blk.getLocation()) == WorldHelper.HeightLayer.CAVERN;
                            Biome biomeToSet = Biome.MUSHROOM_ISLAND;
                            if (isCavern) biomeToSet = OverworldBiomeGenerator.getUndergroundEquivalent(biomeToSet);
                            for (int i = -10; i <= 10; i ++)
                                for (int j = -10; j <= 10; j ++)
                                    wld.setBiome(blkX + i, blkZ + j, biomeToSet);
                        }
                        break;
                    }
                    case "南瓜种子": {
                        Block blockToPlant = blk.getRelative(BlockFace.UP);
                        if (blockToPlant.getType() == Material.AIR) {
                            blockToPlant.setType(Material.PUMPKIN_STEM, false);
                            blockToPlant.setData((byte) 1, false);
                            plyTool.setAmount(plyTool.getAmount() - 1);
                        }
                        break;
                    }
                }
                break;
            }
            // stone
            case STONE: {
                // embed a chlorophyte ore in the dirt
                if (plyToolType.equals("叶绿矿")) {
                    WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(blk.getLocation());
                    if (biomeType != WorldHelper.BiomeType.JUNGLE) {
                        ply.sendMessage("§7只能在丛林洞穴层把叶绿矿埋进石头里哦");
                        break;
                    }
                    WorldHelper.HeightLayer heightLayer = WorldHelper.HeightLayer.getHeightLayer(blk.getLocation());
                    if (heightLayer != WorldHelper.HeightLayer.CAVERN) {
                        ply.sendMessage("§7只能在丛林洞穴层把叶绿矿埋进石头里哦");
                        break;
                    }

                    blk.setType(Material.MOSSY_COBBLESTONE, false);
                    plyTool.setAmount(plyTool.getAmount() - 1);
                }
                break;
            }
            // rail
            case RAILS:
            case ACTIVATOR_RAIL:
            case DETECTOR_RAIL:
            case POWERED_RAIL: {
                new TerrariaMinecart(plyToolType, blk.getLocation().add(0.5, 0.1, 0.5), ply);
                break;
            }
        }

        switch (plyToolType) {
            case "橡实": {
                if (WorldHelper.canGrowPlant(blk, true)) {
                    Block blockToPlant = blk.getRelative(BlockFace.UP);
                    WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(blockToPlant.getLocation());
                    byte data = WorldHelper.getSaplingVariantByBiome(biomeType);
                    blockToPlant.setType(Material.SAPLING, false);
                    blockToPlant.setData(data, false);
                    plyTool.setAmount(plyTool.getAmount() - 1);
                }
                break;
            }
            case "树之祭祀": {
                int buffDuration = -1;
                switch (blk.getType()) {
                    case WOOD:
                        // 10 seconds
                        buffDuration = 200;
                        break;
                    case LOG:
                    case LOG_2:
                        // 16 seconds
                        buffDuration = 320;
                        break;
                }
                if (buffDuration > 0) {
                    if (playerBreakBlock(blk, ply, true, true, true)) {
                        plyTool.setAmount(plyTool.getAmount() - 1);
                        EntityHelper.applyEffect(ply, "再生", buffDuration);
                    }
                }
                break;
            }
        }
    }
}
