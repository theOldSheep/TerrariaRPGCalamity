package terraria.util;

import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.PacketPlayOutBlockBreakAnimation;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.MetadataValue;
import terraria.TerrariaHelper;

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
        if (TerrariaHelper.blockConfig.contains(material + "_" + data)) {
            return TerrariaHelper.blockConfig.getConfigurationSection(material + "_" + data);
        } if (TerrariaHelper.blockConfig.contains(material)) {
            return TerrariaHelper.blockConfig.getConfigurationSection(material);
        }
        return null;
    }
    public static boolean isBreakable(Block block, Player ply) {
        if (noMiningSet.contains(block.getType())) return false;
        switch (block.getType()) {
            case BEDROCK:
            case ENDER_PORTAL_FRAME:
                return false;
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
                if (EntityHelper.getAttrMap(ply).getOrDefault("powerPickaxe", 0d) < breakRule.getDouble("powerPickaxe", 0d))
                    return false;
            }
        }
        BlockBreakEvent evt = new BlockBreakEvent(block, ply);
        Bukkit.getPluginManager().callEvent(evt);
        return !evt.isCancelled();
    }
    public static void playerMineBlock(Block blockToBreak, Player ply) {
        if (blockToBreak.getType() == Material.AIR) return;
        playBlockParticleAndSound(blockToBreak);
        if (!isBreakable(blockToBreak, ply)) return;
        // get existing breaking progress
        MetadataValue temp = EntityHelper.getMetadata(blockToBreak, "breakProgress");
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
        int plyId = ((CraftPlayer) ply).getHandle().getId();
        PacketPlayOutBlockBreakAnimation packetToSend;
        if (breakingProgress >= breakingProgressMax) {
            // send packet and metadata before breaking to prevent bug
            EntityHelper.setMetadata(blockToBreak, "breakProgress", 0);
            packetToSend = new PacketPlayOutBlockBreakAnimation(
                    plyId,
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
            EntityHelper.setMetadata(blockToBreak, "breakProgress", breakingProgress);
            int breakProgress = (int) Math.floor((double) (8 * breakingProgress) / breakingProgressMax);
            packetToSend = new PacketPlayOutBlockBreakAnimation(
                    plyId,
                    new BlockPosition(blockToBreak.getX(), blockToBreak.getY(), blockToBreak.getZ()),
                    breakProgress
            );
            // send packet
            for (Player player : Bukkit.getOnlinePlayers())
                ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetToSend);
        }
    }
    public static void playerBreakBlock(Block blockToBreak, Player ply) {
        if (blockToBreak.getType() == Material.AIR) return;
        playBlockParticleAndSound(blockToBreak);
        if (!isBreakable(blockToBreak, ply)) return;
        ConfigurationSection configSection = getBlockConfigSection(blockToBreak);
        if (configSection != null) {
            List<String> itemsToDrop = configSection.getStringList("dropItem");
            Location locToDrop = blockToBreak.getLocation().add(0.5, 0.4, 0.5);
            for (String currItem : itemsToDrop) {
                String[] itemInfo = currItem.split(":");
                // 物品名:最小数量:最大数量:几率
                int itemAmount = 1;
                switch (itemInfo.length) {
                    case 4:
                        double chance = Double.parseDouble(itemInfo[3]);
                        if (Math.random() > chance) continue;
                        break;
                    case 3:
                        int itemMax = Integer.parseInt(itemInfo[2]);
                        int itemMin = Integer.parseInt(itemInfo[1]);
                        itemAmount = (int) (itemMin + (itemMax - itemMin + 1) * Math.random());
                        break;
                    case 2:
                        itemAmount = Integer.parseInt(itemInfo[1]);
                }
                org.bukkit.inventory.ItemStack itemToDrop = ItemHelper.getRawItem(itemInfo[0]);
                itemToDrop.setAmount(itemAmount);
                ItemHelper.dropItem(locToDrop, itemToDrop);
            }
        } else {
            Bukkit.broadcastMessage("Not handled block type: " + blockToBreak.getType() + " with data " + blockToBreak.getData());
        }
        blockToBreak.setType(Material.AIR);
    }
    public static void playerRightClickBlock(Player ply, Block blk) {
        switch (blk.getType()) {
            // wool changes color
            case WOOL: {
                int data = blk.getData();
                Bukkit.broadcastMessage(data + "");
                if (ply.isSneaking())
                    data = (data + 1) % 16;
                else
                    data = (data + 15) % 16;
                Bukkit.broadcastMessage(data + "");
                int finalData = data;
                Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(), () -> blk.setData((byte) finalData));
                break;
            }
        }
    }
}
