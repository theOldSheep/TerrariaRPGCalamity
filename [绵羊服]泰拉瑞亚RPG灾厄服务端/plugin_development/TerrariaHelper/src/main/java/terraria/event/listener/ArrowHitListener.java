package terraria.event.listener;

import net.minecraft.server.v1_12_R1.EntityProjectile;
import net.minecraft.server.v1_12_R1.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftProjectile;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.TerrariaPotionProjectile;
import terraria.event.TerrariaProjectileHitEvent;
import terraria.gameplay.EventAndTime;
import terraria.util.EntityHelper;
import terraria.util.ItemHelper;
import terraria.util.MathHelper;
import terraria.util.YmlHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


public class ArrowHitListener implements Listener {
    public static final YmlHelper.YmlSection projectileConfig = YmlHelper.getFile(
            TerrariaHelper.Constants.DATA_FOLDER_DIR + "projectiles.yml");
    private void handleHitBlock(TerrariaProjectileHitEvent e, Projectile projectile, Block block) {
        // explode
        Set<String> scoreboardTags = projectile.getScoreboardTags();
        if (scoreboardTags.contains("isGrenade")) {
            Location projectileDestroyLoc = MathHelper.toBukkitVector(e.movingObjectPosition.pos).toLocation(block.getWorld());
            if (scoreboardTags.contains("blastOnContactBlock")) {
                handleProjectileBlast(projectile, projectileDestroyLoc);
            }
        }
    }
    private void handleHitEntity(TerrariaProjectileHitEvent e, Projectile projectile, Entity entity) {
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(projectile);
        Set<String> projectileScoreboardTags = projectile.getScoreboardTags();
        // if the projectile is reflected
        if (entity.getScoreboardTags().contains("reflectProjectile")) {
            TerrariaPotionProjectile nmsProjectile = (TerrariaPotionProjectile) ((CraftProjectile) projectile).getHandle();
            if (nmsProjectile.canBeReflected) {
                e.setCancelled(true);
                // set velocity in the next tick, so it do not get overridden
                Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(), () -> {
                    projectile.setVelocity( MathHelper.getDirection(
                            projectile.getLocation(),
                            ( (LivingEntity) projectile.getShooter() ).getEyeLocation(),
                            projectile.getVelocity().length()   ) );
                    nmsProjectile.autoTrace = false;
                    projectile.setShooter((ProjectileSource) entity);
                });
                return;
            }
        }
        // Daawnlight targets
        if (entity.getScoreboardTags().contains("isDaawnlight")) {
            projectile.addScoreboardTag("hitDaawnlight");
            e.setCancelled(true);
            entity.remove();
            return;
        }
        if (projectileScoreboardTags.contains("hitDaawnlight")) {
            projectile.removeScoreboardTag("hitDaawnlight");
            double lastCrit = attrMap.getOrDefault("crit", 0d);
            attrMap.put("crit", 100d);
            EntityHelper.handleDamage(projectile, entity, attrMap.getOrDefault("damage", 20d), EntityHelper.DamageReason.DAAWNLIGHT);
            attrMap.put("crit", lastCrit);
        } else {
            EntityHelper.handleDamage(projectile, entity, attrMap.getOrDefault("damage", 20d), EntityHelper.DamageReason.PROJECTILE);
        }
        // explode
        if (projectileScoreboardTags.contains("isGrenade")) {
            Location projectileDestroyLoc = MathHelper.toBukkitVector(e.movingObjectPosition.pos).toLocation(entity.getWorld());
            if (projectileScoreboardTags.contains("blastOnContactBlock")) {
                handleProjectileBlast(projectile, projectileDestroyLoc);
            }
        }
    }
    private void handleProjectileBlast(Projectile projectile, Location projectileDestroyLoc) {
        // explode
        HashSet<org.bukkit.entity.Entity> damageExceptions = (HashSet<Entity>) EntityHelper.getMetadata(projectile,
                EntityHelper.MetadataName.PROJECTILE_ENTITIES_COLLIDED).value();
        String projectileName = projectile.getName();
        int blastDuration = projectileConfig.getInt( projectileName + ".blastDuration", 1);
        double blastRadius = projectileConfig.getDouble( projectileName + ".blastRadius", 1.5);
        EntityHelper.handleEntityExplode(projectile, blastRadius, damageExceptions, projectileDestroyLoc, blastDuration);
    }
    private void handleDestroy(TerrariaProjectileHitEvent e, Projectile projectile) {
        Location projectileDestroyLoc = projectile.getLocation();
        String destroyReason = "";
        MetadataValue destroyReasonMetadata = EntityHelper.getMetadata(projectile, EntityHelper.MetadataName.PROJECTILE_DESTROY_REASON);
        if (destroyReasonMetadata != null) destroyReason = destroyReasonMetadata.asString();
        // explode
        if (projectile.getScoreboardTags().contains("isGrenade")) {
            handleProjectileBlast(projectile, projectileDestroyLoc);
        }
        // fallen star
        if (projectile.getScoreboardTags().contains("isFallenStar")) {
            Item droppedItem = ItemHelper.dropItem(projectileDestroyLoc,
                    "坠星", false, false);
            if (droppedItem != null) {
                droppedItem.addScoreboardTag("isFallenStar");
                EventAndTime.fallenStars.add(droppedItem);
            }
        }
        // other mechanism
        String projectileType = projectile.getName();
        ConfigurationSection projectileSection = projectileConfig.getConfigurationSection(projectileType);
        if (projectileSection == null) return;
        EntityHelper.DamageType damageType = EntityHelper.getDamageType(projectile);
        Entity projectileSource = null;
        if (projectile.getShooter() instanceof Entity) projectileSource = (Entity) projectile.getShooter();
        // cluster bomb etc
        {
            ConfigurationSection clusterSection = projectileSection.getConfigurationSection("clusterBomb");
            boolean shouldFire;
            if (clusterSection == null)
                shouldFire = false;
            else if (destroyReason.equals("hitBlock"))
                shouldFire = clusterSection.getBoolean("fireOnHitBlock", true);
            else if (destroyReason.equals("hitEntity"))
                shouldFire = clusterSection.getBoolean("fireOnHitEntity", true);
            else
                shouldFire = clusterSection.getBoolean("fireOnTimeout", true);
            if (shouldFire) {
                String clusterName = clusterSection.getString("name");
                if (clusterName != null) {
                    int clusterAmount = clusterSection.getInt("amount", 3);
                    String clusterType = clusterSection.getString("type", "normal");
                    double clusterDamageMulti = clusterSection.getDouble("damageMulti", 1d);
                    double clusterSpeed = clusterSection.getDouble("velocity", 1d);
                    // set damage
                    HashMap<String, Double> attrMap = (HashMap<String, Double>) EntityHelper.getAttrMap(projectile).clone();
                    attrMap.put("damage", attrMap.getOrDefault("damage", 20d) * clusterDamageMulti);
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
                            case "surround": {
                                velocity = MathHelper.randomVector();
                                spawnLoc = projectile.getLocation().add(velocity.clone().multiply(
                                        10 + Math.random() * 15));
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
        // grappling hooks should ignore entities
        if (arrow.getScoreboardTags().contains("isHook") && e.getHitEntity() != null) {
            e.setCancelled(true);
            EntityProjectile nmsArrow = ((CraftProjectile) arrow).getHandle();
            Vector velocity = new Vector(nmsArrow.motX, nmsArrow.motY, nmsArrow.motZ);
            Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(), () -> arrow.setVelocity(velocity));
            return;
        }
        if (e.getHitBlock() != null) handleHitBlock(e, arrow, e.getHitBlock());
        else if (e.getHitEntity() != null) handleHitEntity(e, arrow, e.getHitEntity());
        else handleDestroy(e, arrow);
    }
}
