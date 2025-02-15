package terraria.event.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.libs.jline.internal.Nullable;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftProjectile;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.others.PlayerTornado;
import terraria.entity.projectile.BulletHellProjectile;
import terraria.entity.projectile.GenericProjectile;
import terraria.event.TerrariaProjectileHitEvent;
import terraria.gameplay.EventAndTime;
import terraria.util.*;

import java.util.*;


public class ArrowHitListener implements Listener {
    private void handleHitBlock(TerrariaProjectileHitEvent e, Projectile projectile, Block block) {
        // explode
        Set<String> scoreboardTags = projectile.getScoreboardTags();
        if (scoreboardTags.contains("isGrenade")) {
            Location projectileDestroyLoc = MathHelper.toBukkitVector(e.movingObjectPosition.pos).toLocation(block.getWorld());
            MetadataValue bounceRemain = EntityHelper.getMetadata(projectile, EntityHelper.MetadataName.PROJECTILE_BOUNCE_LEFT);
            if (bounceRemain == null || bounceRemain.asInt() < 0 || scoreboardTags.contains("blastOnContactBlock")) {
                handleProjectileBlast(projectile, projectileDestroyLoc);
            }
        }
    }
    private static void handleHitEntity(TerrariaProjectileHitEvent e, Projectile projectile, Entity entity) {
        HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(projectile);
        Set<String> projectileScoreboardTags = projectile.getScoreboardTags();
        String projectileName = projectile.getName();
        // if the projectile is reflected
        if (entity.getScoreboardTags().contains("reflectProjectile")) {
            GenericProjectile nmsProjectile = (GenericProjectile) ((CraftProjectile) projectile).getHandle();
            if (nmsProjectile.canBeReflected) {
                e.setCancelled(true);
                // set velocity in the next tick, so it do not get overridden
                Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(), () -> {
                    projectile.setVelocity( MathHelper.getDirection(
                            projectile.getLocation(),
                            ( (LivingEntity) projectile.getShooter() ).getEyeLocation(),
                            projectile.getVelocity().length()   ) );
                    nmsProjectile.homing = false;
                    projectile.setShooter((ProjectileSource) entity);
                });
                return;
            }
        }
        if (projectileName.equals("大型火焰龙卷"))
            return;
        // handle damage
        boolean guaranteeCrit = false;
        DamageHelper.DamageReason dmgReason = DamageHelper.DamageReason.PROJECTILE;
        if (projectileName.equals("瘟疫自爆无人机") && EntityHelper.hasEffect(entity, "瘟疫"))
            guaranteeCrit = true;
        if (guaranteeCrit) {
            double lastCrit = attrMap.getOrDefault("crit", 0d);
            attrMap.put("crit", 100d);
            DamageHelper.handleDamage(projectile, entity, attrMap.getOrDefault("damage", 20d), dmgReason);
            attrMap.put("crit", lastCrit);
        } else {
            DamageHelper.handleDamage(projectile, entity, attrMap.getOrDefault("damage", 20d), dmgReason);
        }
        // explosive projectiles
        if (projectileScoreboardTags.contains("isGrenade")) {
            Location projectileDestroyLoc = MathHelper.toBukkitVector(e.movingObjectPosition.pos).toLocation(entity.getWorld());
            if (projectileScoreboardTags.contains("blastOnContactEnemy")) {
                handleProjectileBlast(projectile, projectileDestroyLoc);
            }
        }
        // projectile-specific characteristics:
        handleProjectileOnHit(projectile, projectileName, entity, projectileScoreboardTags);

        // weapon-specific characteristics:
        handleProjectileWeapon(projectile, projectileName, projectileScoreboardTags, entity);

        // armor set specific characteristics:
        handleProjectileArmorSet(projectile, attrMap);
    }
    private static void handleProjectileOnHit(Projectile projectile, String projectileName,
                                              Entity entityHit, Set<String> projectileScoreboardTags) {
        // vortex arrow
        if (projectileScoreboardTags.contains("isVortex")) {
            HashMap<String, Double> attrMapProj = (HashMap<String, Double>) AttributeHelper.getAttrMap(projectile).clone();
            attrMapProj.put("damage", attrMapProj.get("damage") * 0.3);
            attrMapProj.put("knockback", 0d);
            LivingEntity shooter = (LivingEntity) projectile.getShooter();
            int waitTime = 3 + (int) (Math.random() * 5);
            for (int i = 0; i < 2; i ++) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                    Vector velocity = MathHelper.getDirection(shooter.getLocation(), entityHit.getLocation(), 1);
                    velocity.multiply(17);
                    velocity.add(MathHelper.randomVector());
                    velocity.normalize().multiply(1.75);

                    EntityHelper.spawnProjectile(shooter, velocity, attrMapProj, "幻象箭");
                    ItemUseHelper.playerUseItemSound(shooter, "BOW", "幻象弓", true);
                }, waitTime);

                waitTime += 4 + (int) (Math.random() * 7);
            }
        }
        // god slayer slug
        if (projectileName.equals("弑神弹")) {
            // only handle extra projectile once per bullet
            if (! projectileScoreboardTags.contains("godSlyHandled")) {
                HashMap<String, Double> attrMapProj = (HashMap<String, Double>) AttributeHelper.getAttrMap(projectile).clone();
                attrMapProj.put("damage", attrMapProj.get("damage") * 0.85);
                LivingEntity shooter = (LivingEntity) projectile.getShooter();
                Player ply = (Player) DamageHelper.getDamageSource(shooter);
                projectile.addScoreboardTag("godSlyHandled");
                // after 5 ticks, remove current bullet and fire a new one
                Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                    projectile.remove();

                    double projSpd = projectile.getVelocity().length();
                    String projType = "弑神折返弹";
                    Location fireLoc = shooter.getEyeLocation().add(MathHelper.randomVector().multiply(3.5));
                    Vector velocity = ItemUseHelper.getPlayerAimDir(
                            ply, fireLoc, projSpd, projType, false, 0);
                    MathHelper.setVectorLength(velocity, projSpd);

                    EntityHelper.spawnProjectile(shooter, fireLoc, velocity, attrMapProj, DamageHelper.DamageType.BULLET, projType);
                }, 5);
            }
        }
    }
    private static void handleProjectileWeapon(Projectile projectile, String projectileName,
                                               Set<String> projectileScoreboardTags, Entity entityHit) {
        // hell-born bullet
        if (projectileScoreboardTags.contains("isHellborn")) {
            Player shooter = (Player) projectile.getShooter();
            ItemUseHelper.applyCD(shooter, 2);
        }
        // adamantite particle accelerator's double damage handling
        if (projectileScoreboardTags.contains("isAPA")) {
            MetadataValue lastParticleName = EntityHelper.getMetadata(entityHit,
                    EntityHelper.MetadataName.LAST_ADAMANTITE_PARTICLE_TYPE);
            if (lastParticleName != null && ! (lastParticleName.asString().equals(projectileName) )) {
                HashMap<String, Double> attrMapProj = AttributeHelper.getAttrMap(projectile);
                DamageHelper.handleDamage((Entity) projectile.getShooter(), entityHit,
                        attrMapProj.get("damage"), DamageHelper.DamageReason.PROJECTILE);
            }
            EntityHelper.setMetadata(entityHit, EntityHelper.MetadataName.LAST_ADAMANTITE_PARTICLE_TYPE,
                    projectileName);
        }
    }
    private static void handleProjectileArmorSet(Projectile projectile, HashMap<String, Double> attrMap) {
        if (projectile.getShooter() instanceof Player) {
            Player shooter = (Player) projectile.getShooter();
            String armorSet = PlayerHelper.getArmorSet(shooter);
            DamageHelper.DamageType damageType = DamageHelper.getDamageType(projectile);
            Set<String> plyScoreboardTags = shooter.getScoreboardTags();
            switch (armorSet) {
                case "龙蒿远程套装":
                case "金源远程套装": {
                    switch ( damageType ) {
                        case ARROW:
                        case BULLET:
                        case ROCKET: {
                            // cool down
                            String coolDownTag = "temp_tarragonRanged";
                            if (plyScoreboardTags.contains(coolDownTag))
                                break;
                            // apply cool down
                            EntityHelper.handleEntityTemporaryScoreboardTag(shooter, coolDownTag, 20);
                            // fire projectile
                            HashMap<String, Double> extraProjAttrMap = (HashMap<String, Double>) attrMap.clone();
                            extraProjAttrMap.put("damage", extraProjAttrMap.getOrDefault("damage", 20d) * 0.45);
                            for (int i = 0; i < 2; i ++) {
                                EntityHelper.spawnProjectile(shooter, projectile.getLocation(),
                                        MathHelper.randomVector().multiply(2), extraProjAttrMap,
                                        DamageHelper.DamageType.MAGIC, "龙蒿生命能量");
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
    private static void handleProjectileBlast(Projectile projectile, Location projectileDestroyLoc) {
        // explode
        HashSet<org.bukkit.entity.Entity> damageExceptions = (HashSet<Entity>) EntityHelper.getMetadata(projectile,
                EntityHelper.MetadataName.PROJECTILE_ENTITIES_COLLIDED).value();
        String projectileName = projectile.getName();
        int blastDuration = TerrariaHelper.projectileConfig.getInt( projectileName + ".blastDuration", 1);
        double blastRadius = TerrariaHelper.projectileConfig.getDouble( projectileName + ".blastRadius", 1.5);
        EntityHelper.handleEntityExplode(projectile, blastRadius, damageExceptions, projectileDestroyLoc, blastDuration);
    }
    private static void handleDestroy(Projectile projectile) {
        String projectileType = projectile.getName();
        Set<String> projScoreboardTags = projectile.getScoreboardTags();
        Location projectileDestroyLoc = projectile.getLocation();
        // explode
        if (projScoreboardTags.contains("isGrenade") && projScoreboardTags.contains("blastOnTimeout")) {
            handleProjectileBlast(projectile, projectileDestroyLoc);
        }
        // fallen star
        if (projScoreboardTags.contains("isFallenStar")) {
            Item droppedItem = ItemHelper.dropItem(projectileDestroyLoc,
                    "坠星", false, false);
            if (droppedItem != null) {
                droppedItem.addScoreboardTag("isFallenStar");
                EventAndTime.fallenStars.add(droppedItem);
            }
        }
        // tornado
        switch (projectileType) {
            case "海之烧灼水弹":
            case "台风箭": {
                new PlayerTornado(
                        (Player) projectile.getShooter(), projectileDestroyLoc.clone().subtract(0, 2, 0),
                        new ArrayList<>(),
                        0, 18, 20, 9,
                        2, 0.9, "鲨鱼旋风", AttributeHelper.getAttrMap(projectile));
                break;
            }
        }
        handleProjectileClusterBomb(projectile);
    }
    /**
     * Triggers the cluster bomb mechanism of the projectile.
     * This method is called when the projectile destroys or collides with an entity.
     */
    public static void handleProjectileClusterBomb(Projectile projectile) {
        String projectileType = projectile.getName();
        ConfigurationSection projectileSection = TerrariaHelper.projectileConfig.getConfigurationSection(projectileType);
        if (projectileSection == null)
            return;
        ConfigurationSection clusterSection = projectileSection.getConfigurationSection("clusterBomb");

        int destroyReason = GenericProjectile.DESTROY_TIME_OUT;
        MetadataValue destroyReasonMetadata = EntityHelper.getMetadata(projectile, EntityHelper.MetadataName.PROJECTILE_DESTROY_REASON);
        if (destroyReasonMetadata != null) destroyReason = destroyReasonMetadata.asInt();

        boolean shouldFire;
        Entity entityHit = null;
        if (clusterSection == null)
            shouldFire = false;
        else if (destroyReason == GenericProjectile.DESTROY_HIT_BLOCK)
            shouldFire = clusterSection.getBoolean("fireOnHitBlock", true);
        else if (destroyReason == GenericProjectile.DESTROY_HIT_ENTITY) {
            shouldFire = clusterSection.getBoolean("fireOnHitEntity", true);
            MetadataValue collidedVal = EntityHelper.getMetadata(projectile, EntityHelper.MetadataName.PROJECTILE_LAST_HIT_ENTITY);
            if (collidedVal != null)
                entityHit = (Entity) collidedVal.value();
        }
        else
            shouldFire = clusterSection.getBoolean("fireOnTimeout", true);
        if (shouldFire) {
            HashMap<String, Double> attrMap = (HashMap<String, Double>) AttributeHelper.getAttrMap(projectile).clone();
            spawnProjectileClusterBomb(clusterSection, projectile, attrMap, entityHit);
        }
    }

    /**
     * Spawns the specified cluster bomb from the projectile.
     * An entity may be specified for more precise falling star aiming.
     */
    public static void spawnProjectileClusterBomb(ConfigurationSection clusterSection, Projectile projectile,
                                                  HashMap<String, Double> attrMap, @Nullable Entity entityHit) {
        String projectileType = projectile.getName();
        String clusterName = clusterSection.getString("name");
        if (clusterName != null) {
            int clusterAmount = clusterSection.getInt("amount", 3);
            String clusterType = clusterSection.getString("type", "normal");
            double clusterDamageMulti = clusterSection.getDouble("damageMulti", 1d);
            double clusterDamageFixed = clusterSection.getDouble("damageFixed", -1d);
            double clusterSpeed = clusterSection.getDouble("velocity", 1d);
            // setup damage
            double projectileBaseDmg = clusterDamageFixed;
            if (projectileBaseDmg < 0)
                projectileBaseDmg = attrMap.getOrDefault("damage", 20d) * clusterDamageMulti;
            attrMap.put("damage", projectileBaseDmg);

            // projectile basic info
            Entity projectileSource = null;
            if (projectile.getShooter() instanceof Entity) projectileSource = (Entity) projectile.getShooter();
            DamageHelper.DamageType damageType = DamageHelper.getDamageType(projectile);
            // get a more precise location at the edge of the enemy's bounding box
            Location projectileDestroyLoc = projectile.getLocation();
            // bullet hell blasts
            MetadataValue bulletHellInfo = EntityHelper.getMetadata(projectile, EntityHelper.MetadataName.BULLET_HELL_PROJECTILE_DIRECTION);
            if (bulletHellInfo != null) {
                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                        projectileSource, projectileDestroyLoc, new Vector(),
                        attrMap, damageType, clusterName);
                BulletHellProjectile.ProjectileType blastType = BulletHellProjectile.ProjectileType.BLAST_8;
                if (clusterAmount > 24) blastType = BulletHellProjectile.ProjectileType.BLAST_32;
                else if (clusterAmount > 12) blastType = BulletHellProjectile.ProjectileType.BLAST_16;
                new BulletHellProjectile(shootInfo, blastType, 0, clusterSpeed, (BulletHellProjectile.BulletHellDirectionInfo) bulletHellInfo.value());
            }
            // normal clusters
            else {
                if (clusterType.equals("spread")) {
                    EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                            projectileSource, projectileDestroyLoc, new Vector(), attrMap, damageType, clusterName);
                    for (Vector velocity : MathHelper.getEvenlySpacedProjectileDirections(clusterAmount, 180, MathHelper.randomVector(), clusterSpeed)) {
                        shootInfo.velocity = velocity;
                        EntityHelper.spawnProjectile(shootInfo);
                    }
                }
                else if (clusterType.equals("arc")) {
                    EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                            projectileSource, projectileDestroyLoc, new Vector(), attrMap, damageType, clusterName);
                    int arcAmount = clusterSection.getInt("arcAmount", 1);
                    double halfArcAngleDeg = clusterSection.getDouble("surroundMaxPitch", 30d);
                    for (Vector velocity : MathHelper.getCircularProjectileDirections(
                            clusterAmount, arcAmount, halfArcAngleDeg, projectile.getVelocity(), clusterSpeed)) {
                        shootInfo.velocity = velocity;
                        EntityHelper.spawnProjectile(shootInfo);
                    }
                }
                else {
                    Location spawnLoc;
                    // spawn clusters
                    for (int i = 0; i < clusterAmount; i++) {
                        // spawn type dynamic tweaks
                        if (projectileType.equals("三色大地流星")) {
                            double rdm = Math.random();
                            if (rdm < 0.3333)
                                clusterName = "红色大地流星";
                            else if (rdm < 0.6666)
                                clusterName = "绿色大地流星";
                            else
                                clusterName = "蓝色大地流星";
                        }
                        // spawn loc & velocity
                        Vector velocity = new Vector();
                        boolean aimEnemy = false;
                        switch (clusterType) {
                            case "star": {
                                spawnLoc = projectileDestroyLoc.clone().add(Math.random() * 10 - 5,
                                        Math.random() * 20 + 20,
                                        Math.random() * 10 - 5);
                                // velocity is calculated outside the switch block
                                aimEnemy = true;
                                break;
                            }
                            case "surround": {
                                double velYaw = Math.random() * 360;
                                double velPitch = Math.random() * clusterSection.getDouble("surroundMaxPitch", 30d);
                                if (Math.random() < 0.5) velPitch *= -1;
                                double offsetLen = clusterSection.getDouble("surroundOffset", 10d);
                                Vector offset = MathHelper.vectorFromYawPitch_approx(velYaw, velPitch).multiply(offsetLen);
                                spawnLoc = projectileDestroyLoc.clone().add(offset);
                                // velocity is calculated outside the switch block
                                aimEnemy = true;
                                break;
                            }
                            default:
                                velocity = MathHelper.vectorFromYawPitch_approx(Math.random() * 360, Math.random() * 360);
                                spawnLoc = projectileDestroyLoc;
                        }
                        // aim enemy if needed
                        if (aimEnemy) {
                            Location aimLoc;
                            if (entityHit == null)
                                aimLoc = projectileDestroyLoc.clone().add(Math.random() * 3 - 1.5,
                                        Math.random() * 3 - 1.5,
                                        Math.random() * 3 - 1.5);
                            else
                                aimLoc = AimHelper.helperAimEntity(spawnLoc, entityHit,
                                        new AimHelper.AimHelperOptions(clusterName)
                                                .setAccelerationMode(true)
                                                .setProjectileSpeed(clusterSpeed)
                                                .setRandomOffsetRadius(1.5));
                            velocity = MathHelper.getDirection(spawnLoc, aimLoc, 1, false);
                        }
                        // setup speed and spawn projectile
                        velocity.multiply(clusterSpeed);
                        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                projectileSource, spawnLoc, velocity, attrMap, damageType, clusterName);
                        EntityHelper.spawnProjectile(shootInfo);
                    }
                }
            }
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onArrowHit(TerrariaProjectileHitEvent e) {
        Projectile arrow = e.getEntity();
        // grappling hooks should ignore entities
        if (arrow.getScoreboardTags().contains("isHook") && e.getHitEntity() != null) {
            e.setCancelled(true);
            return;
        }
        if (e.getHitBlock() != null) handleHitBlock(e, arrow, e.getHitBlock());
        else if (e.getHitEntity() != null) handleHitEntity(e, arrow, e.getHitEntity());
        else handleDestroy(arrow);
    }
}
