package terraria.util;

import net.minecraft.server.v1_12_R1.PacketPlayOutSetCooldown;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import terraria.TerrariaHelper;

import java.util.*;

public class ItemUseHelper {
    public static void applyCD(Player ply, double CD) {
        int coolDown = (int) CD;
        if (Math.random() < CD % 1) coolDown ++;
        applyCD(ply, coolDown);
    }
    public static void applyCD(Player ply, int CD) {
        ply.addScoreboardTag("useCD");
        long lastCDApply = Calendar.getInstance().getTimeInMillis();
        EntityHelper.setMetadata(ply, "useCDInternal", lastCDApply);
        ItemStack tool = ply.getInventory().getItemInMainHand();
        // the CD <= 0: never stops on its own
        PacketPlayOutSetCooldown packet = new PacketPlayOutSetCooldown(CraftItemStack.asNMSCopy(tool).getItem(), CD <= 0 ? 1919810 : CD);
        ((CraftPlayer) ply).getHandle().playerConnection.sendPacket(packet);
        if (CD > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (ply.isOnline() && EntityHelper.getMetadata(ply, "useCDInternal").asLong() == lastCDApply) {
                    if (!PlayerHelper.isProperlyPlaying(ply)) {
                        ply.removeScoreboardTag("autoSwing");
                    }
                    // handle next use
                    ply.removeScoreboardTag("useCD");
                    if (ply.getScoreboardTags().contains("autoSwing")) {
                        playerUseItem(ply);
                    }
                }
            }, CD);
        }
    }
    // util functions for use item
    private static void playerSwingPickaxe(Player ply, HashMap<String, Double> attrMap, boolean isRightClick) {
        ply.playSound(ply.getEyeLocation(), "item.genericSwing", 1, 1);
        double pickaxeReach = 4 + attrMap.getOrDefault("reachExtra", 0d);
        pickaxeReach *= attrMap.getOrDefault("meleeReachMulti", 1d);
        // mine block if applicable
        Block blk = ply.getTargetBlock(GameplayHelper.noMiningSet, (int) Math.round(pickaxeReach));
        if (blk != null) GameplayHelper.playerMineBlock(blk, ply);
        // left click swings until stopped
        if (!isRightClick)
            ply.addScoreboardTag("autoSwing");
        double useCD = attrMap.getOrDefault("useTime", 20d);
        double useSpeed = attrMap.getOrDefault("useSpeedMulti", 1d) *
                attrMap.getOrDefault("useSpeedMeleeMulti", 1d) *
                attrMap.getOrDefault("useSpeedMiningMulti", 1d);
        useCD /= useSpeed;
        applyCD(ply, useCD);
    }
    private static boolean playerUseMiscellaneous(Player ply, String itemName) {
        switch (itemName) {
            case "钱币槽": {
                ply.openInventory(PlayerHelper.getInventory(ply, "piggyBank"));
                return true;
            }
            case "虚空袋": {
                ply.openInventory(PlayerHelper.getInventory(ply, "voidBag"));
                return true;
            }
        }
        return false;
    }
    // note that use time CD handling are in individual helper functions.
    public static void playerUseItem(Player ply) {
        if (EntityHelper.hasEffect(ply, "诅咒")) {
            ply.removeScoreboardTag("autoSwing");
            EntityHelper.setMetadata(ply, "swingAmount", 0);
            return;
        }
        Set<String> scoreboardTags = ply.getScoreboardTags();
        if (scoreboardTags.contains("toolChanged"))
            PlayerHelper.setupAttribute(ply);
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(ply);
        boolean isRightClick = scoreboardTags.contains("isSecondaryAttack");
        // pickaxe
        if (attrMap.getOrDefault("powerPickaxe", 0d) > 1) {
            playerSwingPickaxe(ply, attrMap, isRightClick);
            return;
        }
        ItemStack mainHandItem = ply.getInventory().getItemInMainHand();
        String itemName = ItemHelper.splitItemName(mainHandItem)[1];
        // void bag, piggy bank, musical instruments etc.
        if (isRightClick && playerUseMiscellaneous(ply, itemName)) return;
        // potion and other consumable consumption
        // TODO
        // other weapon etc.
        else {
            String damageType = EntityHelper.getDamageType(ply);
            switch (damageType) {
                // TODO
            }
        }
    }
    public static void spawnSentryMinion(Player ply, String type, HashMap<String, Double> attrMap, boolean sentryOrMinion) {
        if (sentryOrMinion) {

        } else {

        }
    }
    private static void minionAI(Entity minion, Player owner, String nameMinion, int minionSlot) {

    }
}
