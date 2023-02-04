package terraria.event.listener;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
        switch (e.getCause()) {
            case SUFFOCATION:
                if (victim.getType() == EntityType.SLIME) break;
            case VOID:
                EntityHelper.handleDamage(victim, victim, 10, "Suffocation");
                break;
            case LAVA:
                EntityHelper.handleDamage(victim, victim, 200, "Lava");
                break;
            case DROWNING:
                EntityHelper.handleDamage(victim, victim, 50, "Drowning");
                break;
            case FALL:
                if (victim.getType() == EntityType.SLIME) break;
                if (victimScoreboardTags.contains("noFallDamage")) break;
                EntityHelper.handleDamage(victim, victim, 50, "Fall");
                break;
            /*
            case CRAMMING:
                if (victim instanceof Player || victimScoreboardTags.contains("isNPC") || victimScoreboardTags.contains("isAnimal")) {
                    AxisAlignedBB boundingBox = ((CraftEntity) victim).getHandle().getBoundingBox();
                    double length = (boundingBox.d - boundingBox.a) / 2;
                    double height = (boundingBox.e - boundingBox.b) / 2;
                    double width  = (boundingBox.f - boundingBox.c) / 2;
                    Location centerLoc = new Location(victim.getWorld(),
                            boundingBox.a + length, boundingBox.b + height, boundingBox.c + width);
                    double maxDmg = 0;
                    Entity damager = null;
                    for (Entity curr : victim.getWorld().getNearbyEntities(centerLoc, length, height, width)) {
                        if (! EntityHelper.checkCanDamage(curr, victim, false)) continue;
                        if (curr instanceof LivingEntity && ((LivingEntity) curr).getHealth() <= 0) continue;
                        if (curr.getScoreboardTags().contains("noMelee")) continue;
                        HashMap<String, Double> damagerAttrMap = EntityHelper.getAttrMap(curr);
                        double currDmg = damagerAttrMap.getOrDefault("damage", 1d);
                        String currDmgType = EntityHelper.getDamageType(curr);
                        if (!(currDmgType.equals("Melee"))) currDmg *= 0.75;
                        if (currDmg > maxDmg) {
                            maxDmg = currDmg;
                            damager = curr;
                        }
                    }
                    if (damager != null)
                        EntityHelper.handleDamage(damager, victim, maxDmg, "DirectDamage");
                }
                break;
            */
        }
    }
}
