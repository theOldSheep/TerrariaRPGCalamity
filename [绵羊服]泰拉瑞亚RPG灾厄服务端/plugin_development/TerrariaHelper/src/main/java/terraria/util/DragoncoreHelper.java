package terraria.util;

import eos.moe.dragoncore.api.SlotAPI;
import eos.moe.dragoncore.database.IDataBase;
import eos.moe.dragoncore.network.PacketSender;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class DragoncoreHelper {
    public static class SlotCallback implements IDataBase.Callback<ItemStack> {
        ItemStack result = new ItemStack(Material.AIR);
        public ItemStack getResult() {
            return result;
        }

        @Override
        public void onResult(ItemStack rst) {
            result = rst;
        }
        @Override
        public void onFail() {
        }
    }
    public static ItemStack getSlotItem(Player ply, String slot) {
//        SlotCallback cbk = new SlotCallback();
//        SlotAPI.getSlotItem(ply, slot, cbk);
//        return cbk.getResult();
        return SlotAPI.getCacheSlotItem(ply, slot);
    }
    public static void setSlotItem(Player ply, String slot, ItemStack itemToSet) {
        SlotAPI.setSlotItem(ply, slot, itemToSet, true);
    }
    public static void displayParticle(Player ply, String name, Location spawnPos, int duration) {
        String posInfo = String.format("%1$f,%2$f,%3$f",
                spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        displayParticle(ply, name, posInfo, duration);
    }
    public static void displayParticle(Player ply, String name, Entity spawnEntity, int duration) {
        displayParticle(ply, name, spawnEntity.getUniqueId().toString(), duration);
    }
    private static void displayParticle(Player ply, String name, String posInfo, int duration) {
        PacketSender.addParticle(ply, name, UUID.randomUUID().toString(), posInfo, "0,0,0", duration);
    }
}