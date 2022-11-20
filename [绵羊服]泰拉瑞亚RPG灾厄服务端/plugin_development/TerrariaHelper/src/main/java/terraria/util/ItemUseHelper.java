package terraria.util;

import net.minecraft.server.v1_12_R1.PacketPlayOutSetCooldown;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import terraria.TerrariaHelper;

import java.util.Calendar;
import java.util.HashMap;

public class ItemUseHelper {
    public static void applyCD(Player ply, int CD) {
        ply.addScoreboardTag("useCD");
        long lastCDApply = Calendar.getInstance().getTimeInMillis();
        EntityHelper.setMetadata(ply, "useCDInternal", lastCDApply);
        int invSlot = EntityHelper.getMetadata(ply, "heldSlot").asInt();
        ItemStack tool = DragoncoreHelper.getSlotItem(ply, "inventory" + invSlot);
        // the CD <= 0: never stops on its own
        PacketPlayOutSetCooldown packet = new PacketPlayOutSetCooldown(CraftItemStack.asNMSCopy(tool).getItem(), CD <= 0 ? 1919810 : CD);
        ((CraftPlayer) ply).getHandle().playerConnection.sendPacket(packet);
        if (CD > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (ply.isOnline() && EntityHelper.getMetadata(ply, "useCDInternal").asLong() == lastCDApply) {
                    ply.removeScoreboardTag("useCD");
                    if (PlayerHelper.isProperlyPlaying(ply)) {
                        if (ply.getScoreboardTags().contains("autoSwing")) playerUseItem(ply);
                    } else ply.removeScoreboardTag("autoSwing");
                }
            }, CD);
        }
    }
    public static void playerUseItem(Player ply) {
        // TODO
    }
    public static void spawnSentryMinion(Player ply, String type, HashMap<String, Double> attrMap, boolean sentryOrMinion) {
        if (sentryOrMinion) {

        } else {

        }
    }
    private static void minionAI(Entity minion, Player owner, String nameMinion, int minionSlot) {

    }
}
