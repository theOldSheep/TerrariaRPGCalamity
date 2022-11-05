package terraria.dragoncorehelper;

import eos.moe.dragoncore.api.SlotAPI;
import eos.moe.dragoncore.database.IDataBase;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SlotHelper {
    static class SlotCallback implements IDataBase.Callback<ItemStack> {
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
    static ItemStack getSlotItem(Player ply, String slot) {
        SlotCallback cbk = new SlotCallback();
        SlotAPI.getSlotItem(ply, slot, cbk);
        return cbk.getResult();
    }
    static void setSlotItem(Player ply, String slot, ItemStack itemToSet) {
        SlotAPI.setSlotItem(ply, slot, itemToSet, true);
    }
}