package terraria.event.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.event.TerrariaProjectileHitEvent;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.YmlHelper;

import java.util.HashMap;
import java.util.Set;


public class ArrowHitEvent implements Listener {
    public static final YmlHelper.YmlSection projectileConfig = YmlHelper.getFile("plugins/Data/projectiles.yml");
    private void handleHitBlock(TerrariaProjectileHitEvent e, Projectile projectile, Block block) {

    }
    private void handleHitEntity(TerrariaProjectileHitEvent e, Projectile projectile, Entity entity) {
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(projectile);
        // Daawnlight targets
        if (entity.getScoreboardTags().contains("isDaawnlight")) {
            projectile.addScoreboardTag("hitDaawnlight");
            e.setCancelled(true);
            entity.remove();
            return;
        }
        Set<String> projectileScoreboardTags = projectile.getScoreboardTags();
        // if the projectile is about to explode, do not deal direct damage
        if (EntityHelper.getMetadata(projectile, "penetration").asInt() <= 0 &&
                projectileScoreboardTags.contains("isGrenade")) return;
        if (projectileScoreboardTags.contains("hitDaawnlight")) {
            projectile.removeScoreboardTag("hitDaawnlight");
            double lastCrit = attrMap.getOrDefault("crit", 0d);
            attrMap.put("crit", 100d);
            EntityHelper.handleDamage(projectile, entity, attrMap.getOrDefault("damage", 20d), "Daawnlight");
            attrMap.put("crit", lastCrit);
        } else {
            EntityHelper.handleDamage(projectile, entity, attrMap.getOrDefault("damage", 20d), "Projectile");
        }
    }
    private void handleDestroy(TerrariaProjectileHitEvent e, Projectile projectile) {
        Location projectileDestroyLoc = projectile.getLocation();
        // explode
        if (projectile.getScoreboardTags().contains("isGrenade")) {
            org.bukkit.entity.Entity damageException = null;
            if (! projectile.getScoreboardTags().contains("blastDamageShooter")) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof LivingEntity) damageException = (LivingEntity) shooter;
            }
            EntityHelper.handleEntityExplode(projectile, damageException, projectileDestroyLoc);
        }
        // other ticking
        String projectileType = projectile.getName();
        ConfigurationSection projectileSection = projectileConfig.getConfigurationSection(projectileType);
        if (projectileSection == null) return;
        String damageType = EntityHelper.getDamageType(projectile);
        Entity projectileSource = null;
        if (projectile.getShooter() instanceof Entity) projectileSource = (Entity) projectile.getShooter();
        // cluster bomb etc
        {
            ConfigurationSection clusterSection = projectileSection.getConfigurationSection("clusterBomb");
            if (clusterSection != null) {
                String clusterName = clusterSection.getString("name");
                if (clusterName != null) {
                    int clusterAmount = clusterSection.getInt("amount", 3);
                    String clusterType = clusterSection.getString("type", "normal");
                    double clusterDamageMulti = clusterSection.getDouble("damageMulti", 1d);
                    double clusterSpeed = clusterSection.getDouble("velocity", 1d);
                    // set damage
                    HashMap<String, Double> attrMap = (HashMap<String, Double>) EntityHelper.getAttrMap(projectile).clone();
                    attrMap.put("damage", attrMap.getOrDefault("damage", 20d * clusterDamageMulti));
                    // tweak the spawn location a bit so that cluster projectiles would not all collide on block
                    Location spawnLoc = projectileDestroyLoc.clone();
                    // spawn clusters
                    for (int i = 0; i < clusterAmount; i ++) {
                        Vector velocity;
                        switch (clusterType) {
                            case "star": {
                                spawnLoc = projectile.getLocation().add(Math.random() * 10 - 5,
                                        Math.random() * 20 + 20,
                                        Math.random() * 10 - 5);
                                Location targetLoc = projectile.getLocation().add(Math.random() * 3 - 1.5,
                                        Math.random() * 3 - 1.5,
                                        Math.random() * 3 - 1.5);
                                velocity = targetLoc.subtract(spawnLoc).toVector().normalize();
                                break;
                            }
                            default:
                                velocity = MathHelper.vectorFromYawPitch_quick(Math.random() * 360, Math.random() * 360);
                        }
                        velocity.multiply(clusterSpeed);
                        EntityHelper.spawnProjectile(projectileSource, spawnLoc, velocity, attrMap, damageType, clusterName);
                    }
                }
            }
        }
        // vortex arrow
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArrowHit(TerrariaProjectileHitEvent e) {
        Projectile arrow = e.getEntity();
        if (arrow.getScoreboardTags().contains("isHook")) {
            e.setCancelled(true);
            Vector velocity = arrow.getVelocity();
            Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(), () -> arrow.setVelocity(velocity));
            return;
        }
        if (e.getHitBlock() != null) handleHitBlock(e, arrow, e.getHitBlock());
        else if (e.getHitEntity() != null) handleHitEntity(e, arrow, e.getHitEntity());
        else handleDestroy(e, arrow);
    }
}
