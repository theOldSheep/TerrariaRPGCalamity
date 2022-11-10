package terraria.listener;

import net.minecraft.server.v1_12_R1.EntityPotion;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import terraria.util.EntityHelper;

public class ArrowShootEvent implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onArrowFire(ProjectileLaunchEvent e) {
        int bounce = (int) (Math.random() * 2);
        int penetration = (int) (Math.random() * 2);
        boolean thruWall = Math.random() < 0.5;
        Bukkit.broadcastMessage("Bounce: " + bounce + ", Penetration: " + penetration + ", ThruWall: " + thruWall);
        EntityHelper.spawnProjectile(e.getEntity().getLocation(), e.getEntity().getVelocity(), "木箭", e.getEntity().getShooter(),
                bounce, penetration, false, thruWall);
        e.setCancelled(true);
    }
}
