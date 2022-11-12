package terraria.event.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import terraria.util.EntityHelper;

public class DamageListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.STARVATION) e.setCancelled(true);
//        e.setCancelled(true);
        if (e.getEntity() instanceof Player) EntityHelper.setMetadata(e.getEntity(), "regenTime", 0);
        if (e instanceof EntityDamageByBlockEvent) return;
        if (e instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent evt = (EntityDamageByEntityEvent) e;
        } else {
            switch (e.getCause()) {
            }
        }
    }
}
