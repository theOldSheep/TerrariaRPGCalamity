package terraria.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import terraria.util.PlayerHelper;

public class ItemPickupListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onPickUp(EntityPickupItemEvent e) {
        Entity entity = e.getEntity();
        if (entity instanceof Player) {
            Player ply = (Player) entity;
            Item itemE = e.getItem();
            int amountLeft = PlayerHelper.giveItem(ply, itemE.getItemStack(), false);
            itemE.setPickupDelay(10);
            if (amountLeft > 0) itemE.getItemStack().setAmount(amountLeft);
            else itemE.remove();
        }
        e.setCancelled(true);
    }
}
