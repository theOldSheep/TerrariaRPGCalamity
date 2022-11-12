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
        int bounce = (int) (Math.random() * 2);
        int penetration = (int) (Math.random() * 2);
        boolean thruWall = Math.random() < 0.5;
        Bukkit.broadcastMessage("Bounce: " + bounce + ", Penetration: " + penetration + ", ThruWall: " + thruWall);
        Projectile pj = EntityHelper.spawnProjectile(e.getEntity().getLocation(), e.getEntity().getVelocity(), "木箭", e.getEntity().getShooter());
        TerrariaPotionProjectile proj = (TerrariaPotionProjectile) ((CraftProjectile) pj).getHandle();
        proj.bounce = bounce;
        proj.penetration = penetration;
        proj.blockHitAction = thruWall ? "thru" : "die";
        e.setCancelled(true);
    }
}
