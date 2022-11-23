package terraria.event.listener;

import net.minecraft.server.v1_12_R1.EntityLiving;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.projectiles.ProjectileSource;
import terraria.event.TerrariaProjectileHitEvent;
import terraria.util.EntityHelper;

import java.util.HashMap;


public class ArrowHitEvent implements Listener {
    private void handleHitBlock(TerrariaProjectileHitEvent e, Projectile projectile, Block block) {

    }
    private void handleHitEntity(TerrariaProjectileHitEvent e, Projectile projectile, Entity entity) {
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(projectile);
        if (entity.getScoreboardTags().contains("isDaawnlight")) {
            switch (EntityHelper.getDamageType(projectile)) {
                case "Arrow":
                case "Bullet":
                    double lastCrit = attrMap.getOrDefault("crit", 0d);
                    attrMap.put("crit", 100d);
                    EntityHelper.handleDamage(projectile, entity, attrMap.getOrDefault("damage", 20d), "Daawnlight");
                    entity.remove();
                    attrMap.put("crit", lastCrit);
                    return;
            }
        }
        EntityHelper.handleDamage(projectile, entity, attrMap.getOrDefault("damage", 20d), "Projectile");
    }
    private void handleDestroy(TerrariaProjectileHitEvent e, Projectile projectile) {
        if (projectile.getScoreboardTags().contains("isGrenade")) {
            org.bukkit.entity.Entity damageException = null;
            if (! projectile.getScoreboardTags().contains("blastDamageShooter")) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof LivingEntity) damageException = (LivingEntity) shooter;
            }
            EntityHelper.handleEntityExplode(projectile, damageException, projectile.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArrowHit(TerrariaProjectileHitEvent e) {
        if (e.getHitBlock() != null) handleHitBlock(e, e.getEntity(), e.getHitBlock());
        else if (e.getHitEntity() != null) handleHitEntity(e, e.getEntity(), e.getHitEntity());
        else handleDestroy(e, e.getEntity());
    }
}
