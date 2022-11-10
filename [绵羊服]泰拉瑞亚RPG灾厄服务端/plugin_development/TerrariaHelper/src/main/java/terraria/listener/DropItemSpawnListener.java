package terraria.listener;

import org.bukkit.craftbukkit.v1_12_R1.entity.CraftItem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import terraria.entity.TerrariaItem;
import terraria.util.ItemHelper;

public class DropItemSpawnListener implements Listener {
    @EventHandler
    public void onDroppedItemSpawn(ItemSpawnEvent e) {
        Item droppedItem = e.getEntity();
        if (((CraftItem) droppedItem).getHandle() instanceof TerrariaItem) return;
        ItemStack droppedItemStack = droppedItem.getItemStack();
        ItemHelper.dropItem(droppedItem.getLocation(), droppedItemStack).setVelocity(droppedItem.getVelocity());
        droppedItem.remove();
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onChunkLoad(ChunkLoadEvent e) {
        for (Entity entity : e.getChunk().getEntities())
            if (entity instanceof Item) {
                Item droppedItem = (Item) entity;
                ItemHelper.dropItem(droppedItem.getLocation(), droppedItem.getItemStack()).setVelocity(droppedItem.getVelocity());
                droppedItem.remove();
            }
    }
}
