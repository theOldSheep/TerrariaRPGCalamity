package terraria.event.listener;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftItem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import terraria.TerrariaHelper;
import terraria.entity.others.TerrariaDroppedItem;
import terraria.util.ItemHelper;

import java.util.HashSet;

public class DropItemSpawnListener implements Listener {
    @EventHandler
    public void onDroppedItemSpawn(ItemSpawnEvent e) {
        Item droppedItem = e.getEntity();
        if (((CraftItem) droppedItem).getHandle() instanceof TerrariaDroppedItem) return;
        ItemStack droppedItemStack = droppedItem.getItemStack();
        // regularize dropped item (vanilla items are converted to Terraria correspondents)
        droppedItemStack = ItemHelper.regularizeItemDropped(droppedItemStack, droppedItem.getLocation());
        // create the new Terraria dropped item and remove the old one
        Item newItem = ItemHelper.dropItem(droppedItem.getLocation(), droppedItemStack);
        if (newItem != null) newItem.setVelocity(droppedItem.getVelocity());
        droppedItem.remove();
        e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onChunkLoad(ChunkLoadEvent e) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            // remove temporary scoreboard tags
            HashSet<String> tagRemove = new HashSet<>();
            for (Entity entity : e.getChunk().getEntities()) {
                for (String s : entity.getScoreboardTags()) {
                    if (s.startsWith("temp")) tagRemove.add(s);
                }
                for (String s : tagRemove) entity.removeScoreboardTag(s);
            }
            // cast dropped item to Terraria equivalent
            for (Entity entity : e.getChunk().getEntities()) {
                if (entity instanceof Item) {
                    Item droppedItem = (Item) entity;
                    Item newItem = ItemHelper.dropItem(droppedItem.getLocation(), droppedItem.getItemStack());
                    if (newItem != null) newItem.setVelocity(droppedItem.getVelocity());
                    droppedItem.remove();
                }
            }
        }, 1);
    }
}
