package terraria.event.listener;

import net.minecraft.server.v1_12_R1.EntityFishingHook;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftFish;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.projectiles.ProjectileSource;
import terraria.entity.others.TerrariaFishingHook;
import terraria.util.EntityHelper;

import java.util.Set;


public class VanillaMechanicListener implements Listener {
    // entity related
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDeath(EntityDeathEvent evt) {
        evt.setDroppedExp(0);
        evt.getDrops().clear();
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onEat(PlayerItemConsumeEvent evt) {
        evt.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSleep(PlayerBedEnterEvent evt) {
        evt.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public static void onVanillaProjectileSpawn(ProjectileLaunchEvent e) {
        Entity spawned = e.getEntity();
        switch (spawned.getType()) {
            case FISHING_HOOK: {
                EntityFishingHook hook = ((CraftFish) spawned).getHandle();
                if (!(hook instanceof TerrariaFishingHook)) {
                    e.setCancelled(true);
                    spawned.remove();
                }
                break;
            }
            case ARROW: {
                ProjectileSource source = e.getEntity().getShooter();
                if (source instanceof LivingEntity) {
                    LivingEntity shooter = (LivingEntity) source;
                    EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                            shooter, spawned.getVelocity(), EntityHelper.getAttrMap(shooter), "木箭");
                    shootInfo.properties.put("noGravityTicks", 0);
                    EntityHelper.spawnProjectile(shootInfo);
                }
                e.setCancelled(true);
                break;
            }
        }
    }
    // world related
    @EventHandler(priority = EventPriority.LOW)
    public void onChunkUnload(ChunkUnloadEvent evt) {
        if (evt.isCancelled()) return;
        // do not unload chunks with boss/celestial pillar/npc in it
        for (Entity entity : evt.getChunk().getEntities()) {
            Set<String> scoreboardTags = entity.getScoreboardTags();
            if (scoreboardTags.contains("isBOSS") || scoreboardTags.contains("isPillar") || scoreboardTags.contains("isNPC")) {
                evt.setCancelled(true);
                return;
            }
        }
        // if the chunk is unloaded
        for (Entity entity : evt.getChunk().getEntities()) {
            Set<String> scoreboardTags = entity.getScoreboardTags();
            // remove fallen stars on chunk unload
            if (scoreboardTags.contains("isFallenStar") ) {
                entity.removeScoreboardTag("isFallenStar");
                entity.remove();
            }
        }
    }
}
