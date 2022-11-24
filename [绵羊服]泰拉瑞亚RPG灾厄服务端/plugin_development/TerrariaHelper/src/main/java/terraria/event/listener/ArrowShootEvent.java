package terraria.event.listener;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftProjectile;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import terraria.entity.TerrariaPotionProjectile;
import terraria.util.EntityHelper;

public class ArrowShootEvent implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onArrowFire(ProjectileLaunchEvent e) {
        if (((CraftEntity) e.getEntity()).getHandle() instanceof TerrariaPotionProjectile) return;
        e.setCancelled(true);
    }
}
