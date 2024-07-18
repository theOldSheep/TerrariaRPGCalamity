package terraria.util;

import eos.moe.dragoncore.api.SlotAPI;
import eos.moe.dragoncore.database.IDataBase;
import eos.moe.dragoncore.network.PacketSender;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Predicate;

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
    public static class DragonCoreParticleInfo {
        String name, positionalInfo, rotationalInfo, uidInfo;
        UUID uid;
        public DragonCoreParticleInfo(String name, Location spawnPos) {
            this.name = name;
            this.positionalInfo = String.format("%1$f,%2$f,%3$f",
                    spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
            this.rotationalInfo = "0,0,0";
            setUID(UUID.randomUUID());
        }
        public DragonCoreParticleInfo(String name, Entity spawnEntity) {
            this.name = name;
            this.positionalInfo = spawnEntity.getUniqueId().toString();
            this.rotationalInfo = "0,0,0";
            setUID(UUID.randomUUID());
        }
        public void setUID(UUID newUid) {
            this.uid = newUid;
            this.uidInfo = this.uid.toString();
        }
    }

    public static void displayBlizzardParticle(DragonCoreParticleInfo particleInfo, int duration) {
        displayBlizzardParticle((p)->true, particleInfo, duration);
    }
    public static void displayBlizzardParticle(Predicate<Player> validation, DragonCoreParticleInfo particleInfo, int duration) {
        for (Player ply : Bukkit.getOnlinePlayers()) {
            if (validation.test(ply))
                displayBlizzardParticle(ply, particleInfo, duration);
        }
    }
    public static void displayBlizzardParticle(Player ply, DragonCoreParticleInfo particleInfo, int duration) {
        PacketSender.addParticle(ply, particleInfo.name, particleInfo.uidInfo, particleInfo.positionalInfo, particleInfo.rotationalInfo, duration);
    }
    public static void moveCamera(Player ply, double distance, int fadeInMillis, int sustainMillis, int fadeOutMillis) {
        String function = String.format("方法.拉远视角(%.2f,%d,%d,%d)", distance, fadeInMillis, sustainMillis, fadeOutMillis);
        PacketSender.sendRunFunction(ply, "default",
                function, false);
    }
}