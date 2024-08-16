package terraria.event.listener;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import terraria.util.EntityHelper;

import java.util.HashMap;
import java.util.Set;

public class DamageListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        e.setCancelled(true);
        Entity victim = e.getEntity();
        Set<String> victimScoreboardTags = victim.getScoreboardTags();
        if (victimScoreboardTags.contains("isBoss")) return;
        if (! (victim instanceof LivingEntity)) return;
        switch (e.getCause()) {
            case SUFFOCATION:
                if (victim.getType() == EntityType.SLIME) break;
            case VOID:
                EntityHelper.handleDamage(victim, victim, 10, EntityHelper.DamageReason.SUFFOCATION);
                break;
            case LAVA:
                EntityHelper.handleDamage(victim, victim, 200, EntityHelper.DamageReason.LAVA);
                break;
            // drowning damage is handled in extra ticking
            case DROWNING:
                break;
            case FALL:
                if (victim.getType() == EntityType.SLIME) break;
                if (victimScoreboardTags.contains("noFallDamage")) break;
                EntityHelper.handleDamage(victim, victim, 50, EntityHelper.DamageReason.FALL);
                break;
            case FIRE:
            case FIRE_TICK:
                victim.setFireTicks(0);
                break;
        }
    }
}
