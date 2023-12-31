package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.*;
import net.minecraft.server.v1_12_R1.MathHelper;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.event.TerrariaProjectileHitEvent;
import terraria.event.listener.ArrowHitListener;
import terraria.util.*;

import java.util.*;

public class TerrariaPotionProjectile extends EntityPotion {
    private static final double distFromBlock = 1e-3, distCheckOnGround = 1e-1;
    public static final int DESTROY_HIT_BLOCK = 0, DESTROY_HIT_ENTITY = 1, DESTROY_TIME_OUT = 2;
    // projectile info
    public String projectileType, blockHitAction = "die", spawnSound = "", trailColor = null;
    public int autoTraceMethod = 1, bounce = 0, enemyInvincibilityFrame = 5, liveTime = 200,
            noAutoTraceTicks = 0, noGravityTicks = 5, maxAutoTraceTicks = 999999, minimumDamageTicks = 0,
            penetration = 0, trailLingerTime = 10, worldSpriteUpdateInterval = 1;
    public double autoTraceAbility = 4, autoTraceEndSpeedMultiplier = 1, autoTraceRadius = 12,
            blastRadius = 1.5, bounceVelocityMulti = 1,
            frictionFactor = 0.05, gravity = 0.05, maxSpeed = 100, projectileRadius = 0.125,
            spawnSoundPitch = 1, spawnSoundVolume = 1.5, speedMultiPerTick = 1,
            trailIntensityMulti = 1, trailSize = -1, trailStepSize = -1;
    public boolean arrowOrPotion = false, autoTrace = false, autoTraceSharpTurning = true, blastDamageShooter = false,
            blastOnContactBlock = false, blastOnContactEnemy = false, blastOnTimeout = true,
            bouncePenetrationBonded = false, canBeReflected = true, isGrenade = false, slowedByWater = true,
            trailVanillaParticle = true, worldSpriteMode = false;

    // projectile variables
    public int worldSpriteIdx = 0;
    public double speed;
    public boolean lastOnGround = false;
    public HashSet<org.bukkit.entity.Entity> damageCD;
    public HashMap<String, Object> properties;
    public org.bukkit.entity.Projectile bukkitEntity;
    HashMap<String, Double> attrMap, attrMapExtraProjectile;
    public Entity autoTraceTarget = null;
    Location lastTrailDisplayLocation = null;
    // extra projectile variables
    public ConfigurationSection extraProjectileConfigSection;
    int extraProjectileSpawnInterval;
    EntityHelper.ProjectileShootInfo extraProjectileShootInfo;
    // init with a small capacity, as this variable is not needed in most cases.
    HashMap<String, Object> extraVariables = new HashMap<>(1);


    private void setupProjectileProperties() {
        {
            this.autoTraceMethod = (int) properties.getOrDefault("autoTraceMethod", this.autoTraceMethod);
            this.bounce = (int) properties.getOrDefault("bounce", this.bounce);
            this.enemyInvincibilityFrame = (int) properties.getOrDefault("enemyInvincibilityFrame", this.enemyInvincibilityFrame);
            this.liveTime = (int) properties.getOrDefault("liveTime", this.liveTime);
            this.noAutoTraceTicks = (int) properties.getOrDefault("noAutoTraceTicks", this.noAutoTraceTicks);
            this.noGravityTicks = (int) properties.getOrDefault("noGravityTicks", this.noGravityTicks);
            this.maxAutoTraceTicks = (int) properties.getOrDefault("maxAutoTraceTicks", this.maxAutoTraceTicks);
            this.minimumDamageTicks = (int) properties.getOrDefault("minimumDamageTicks", this.minimumDamageTicks);
            this.penetration = (int) properties.getOrDefault("penetration", this.penetration);
            this.trailLingerTime = (int) properties.getOrDefault("trailLingerTime", this.trailLingerTime);
            this.worldSpriteUpdateInterval = (int) properties.getOrDefault("worldSpriteUpdateInterval", this.worldSpriteUpdateInterval);
            // thru, bounce, stick, slide
            this.blockHitAction = (String) properties.getOrDefault("blockHitAction", this.blockHitAction);
            this.spawnSound = (String) properties.getOrDefault("spawnSound", this.spawnSound);
            this.trailColor = (String) properties.getOrDefault("trailColor", this.trailColor);
            // multiple trail color: select one at random
            if (this.trailColor != null && this.trailColor.contains(";")) {
                String[] candidates = this.trailColor.split(";");
                this.trailColor = candidates[(int) (Math.random() * candidates.length)];
            }

            this.autoTraceAbility = (double) properties.getOrDefault("autoTraceAbility", this.autoTraceAbility);
            this.autoTraceEndSpeedMultiplier = (double) properties.getOrDefault("autoTraceEndSpeedMultiplier", this.autoTraceEndSpeedMultiplier);
            this.autoTraceRadius = (double) properties.getOrDefault("autoTraceRadius", this.autoTraceRadius);
            this.blastRadius = (double) properties.getOrDefault("blastRadius", this.blastRadius);
            this.bounceVelocityMulti = (double) properties.getOrDefault("bounceVelocityMulti", this.bounceVelocityMulti);
            this.frictionFactor = (double) properties.getOrDefault("frictionFactor", this.frictionFactor);
            this.gravity = (double) properties.getOrDefault("gravity", this.gravity);
            this.maxSpeed = (double) properties.getOrDefault("maxSpeed", this.maxSpeed);
            this.projectileRadius = (double) properties.getOrDefault("projectileSize", this.projectileRadius * 2) / 2;
            this.spawnSoundPitch = (double) properties.getOrDefault("spawnSoundPitch", this.spawnSoundPitch);
            this.spawnSoundVolume = (double) properties.getOrDefault("spawnSoundVolume", this.spawnSoundVolume);
            this.speedMultiPerTick = (double) properties.getOrDefault("speedMultiPerTick", this.speedMultiPerTick);
            this.trailIntensityMulti = (double) properties.getOrDefault("trailIntensityMulti", this.trailIntensityMulti);
            this.trailSize = (double) properties.getOrDefault("trailSize", this.projectileRadius);
            this.trailStepSize = (double) properties.getOrDefault("trailStepSize", this.projectileRadius);

            this.arrowOrPotion = (boolean) properties.getOrDefault("arrowOrPotion", this.arrowOrPotion);
            this.autoTrace = (boolean) properties.getOrDefault("autoTrace", this.autoTrace);
            this.autoTraceSharpTurning = (boolean) properties.getOrDefault("autoTraceSharpTurning", this.autoTraceSharpTurning);
            this.blastDamageShooter = (boolean) properties.getOrDefault("blastDamageShooter", this.blastDamageShooter);
            this.blastOnContactBlock = (boolean) properties.getOrDefault("blastOnContactBlock", this.blastOnContactBlock);
            this.blastOnContactEnemy = (boolean) properties.getOrDefault("blastOnContactEnemy", this.blastOnContactEnemy);
            this.blastOnTimeout = (boolean) properties.getOrDefault("blastOnTimeout", this.blastOnTimeout);
            this.bouncePenetrationBonded = (boolean) properties.getOrDefault("bouncePenetrationBonded", this.bouncePenetrationBonded);
            this.canBeReflected = (boolean) properties.getOrDefault("canBeReflected", this.canBeReflected);
            this.isGrenade = (boolean) properties.getOrDefault("isGrenade", this.isGrenade);
            this.slowedByWater = (boolean) properties.getOrDefault("slowedByWater", this.slowedByWater);
            this.trailVanillaParticle = (boolean) properties.getOrDefault("trailVanillaParticle", this.trailVanillaParticle);
            this.worldSpriteMode = (boolean) properties.getOrDefault("worldSpriteMode", this.worldSpriteMode);
        }
        this.setNoGravity(true);
        this.noclip = true;
        this.damageCD = new HashSet<>(5);
    }

    private void setupExtraProjectileInfo() {
        extraProjectileConfigSection = TerrariaHelper.projectileConfig.getConfigurationSection(this.projectileType + ".spawnProjectiles");
        if (extraProjectileConfigSection == null) {
            extraProjectileSpawnInterval = -1;
            return;
        }
        extraProjectileSpawnInterval = extraProjectileConfigSection.getInt("interval", 10);
        attrMapExtraProjectile = (HashMap<String, Double>) attrMap.clone();
        attrMapExtraProjectile.put("damage",
                attrMapExtraProjectile.get("damage") * extraProjectileConfigSection.getDouble("damageMulti", 0.5));
        extraProjectileShootInfo = new EntityHelper.ProjectileShootInfo(getShooter().getBukkitEntity(), new Vector(),
                attrMapExtraProjectile, extraProjectileConfigSection.getString("spawnType", "木箭"));
    }
    // setup properties of the specific type, excluding its item displayed
    public void setProperties(String type) {
        projectileType = type;
        setupExtraProjectileInfo();
        setupProjectileProperties();
        setCustomName(type);
        if (isGrenade) addScoreboardTag("isGrenade");
        else removeScoreboardTag("isGrenade");
        if (blastOnContactBlock) addScoreboardTag("blastOnContactBlock");
        else removeScoreboardTag("blastOnContactBlock");
        if (blastOnContactEnemy) addScoreboardTag("blastOnContactEnemy");
        else removeScoreboardTag("blastOnContactEnemy");
        if (blastOnTimeout) addScoreboardTag("blastOnTimeout");
        else removeScoreboardTag("blastOnTimeout");
        if (blastDamageShooter) addScoreboardTag("blastDamageShooter");
        else removeScoreboardTag("blastDamageShooter");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_PENETRATION_LEFT, this.penetration);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_BOUNCE_LEFT, this.bounce);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_ENTITIES_COLLIDED, this.damageCD);
    }
    // setup properties of the specific type, including its item displayed
    public void setType(String type) {
        // if world sprite mode is on, do not give the projectile an item sprite that may interfere with the world sprite.
        if (! worldSpriteMode)
            setItem(generateItemStack(type));
        setProperties(type);
    }
    public static org.bukkit.inventory.ItemStack generateBukkitItemStack(String projectileType) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.SPLASH_POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        meta.setDisplayName(projectileType);
        meta.setColor(org.bukkit.Color.fromRGB(255, 255, 255));
        item.setItemMeta(meta);
        return item;
    }
    public static ItemStack generateItemStack(String projectileType) {
        return CraftItemStack.asNMSCopy(generateBukkitItemStack(projectileType));
    }
    // constructor
    public TerrariaPotionProjectile(World world) {
        super(world);
        die();
    }
    public TerrariaPotionProjectile(EntityHelper.ProjectileShootInfo shootInfo) {
        this(shootInfo.shootLoc, TerrariaPotionProjectile.generateItemStack(shootInfo.projectileName),
                shootInfo.velocity, shootInfo.projectileName, shootInfo.properties,
                shootInfo.attrMap, shootInfo.shooter, shootInfo.damageType);
    }
    public TerrariaPotionProjectile(org.bukkit.Location loc, ItemStack projectileItem, Vector velocity,
                                    String projectileType, HashMap<String, Object> properties,
                                    HashMap<String, Double> attrMap, ProjectileSource shooter, EntityHelper.DamageType damageType) {
        super(((CraftWorld) loc.getWorld()).getHandle(), loc.getX(), loc.getY(), loc.getZ(), projectileItem);
        this.motX = velocity.getX();
        this.motY = velocity.getY();
        this.motZ = velocity.getZ();
        // add to world
        this.world.addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        this.speed = velocity.length();
        this.projectileType = projectileType;
        this.properties = properties;
        bukkitEntity = (org.bukkit.entity.Projectile) getBukkitEntity();
        // other properties
        if (shooter != null)
           bukkitEntity.setShooter(shooter);
        this.attrMap = (HashMap<String, Double>) attrMap.clone();
        this.lastTrailDisplayLocation = loc.clone();
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, this.attrMap);
        EntityHelper.setDamageType(bukkitEntity, damageType);
        setProperties(projectileType);
        // play spawned sound
        if (spawnSound.length() > 0) {
            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), spawnSound, (float) spawnSoundVolume, (float) spawnSoundPitch);
        }
    }

    protected double getAutoTraceInterest(Entity target) {
        // should not be following critters, dead entities etc. and attempt to damage them
        if (!EntityHelper.checkCanDamage(bukkitEntity, target.getBukkitEntity())) return -1e9;
        // returns distance squared / velocity squared, that is, ticks to get there squared, * -1
        double distSqr = target.d(this.locX, this.locY, this.locZ) - (this.projectileRadius * this.projectileRadius);
        return distSqr * -1;
    }
    public boolean checkCanHit(Entity e) {
        org.bukkit.entity.Entity bukkitE = e.getBukkitEntity();
        if (damageCD.contains(bukkitE)) return false;
        // should still be able to hit entities that are neither monster nor forbidden to hit
        return EntityHelper.checkCanDamage(bukkitEntity, bukkitE, false);
    }
    public void hitEntityExtraHandling(Entity e, MovingObjectPosition position) {
        switch (projectileType) {
            case "钫弹":
                noAutoTraceTicks = ticksLived + 1;
                break;
            case "宇宙灯笼光束":
                noAutoTraceTicks = ticksLived + 2;
                break;
            case "粉色脉冲":
                noAutoTraceTicks = ticksLived + 5;
                break;
            case "脉冲步枪电弧":
                noAutoTraceTicks = ticksLived + 2;
                break;
            case "精元镰刀":
            case "裂魔":
            case "生命血火":
            case "猩红烈焰珠":
            case "血炎龙息":
                PlayerHelper.heal((LivingEntity) shooter.getBukkitEntity(), 1, false);
                break;
            case "巨蟹之礼星环":
                PlayerHelper.heal((LivingEntity) shooter.getBukkitEntity(), 5, false);
                break;
        }
    }
    public Vec3D hitEntity(Entity e, MovingObjectPosition position) {
        // handle damage CD before doing anything else. Otherwise, exploding projectiles will damage the enemy being hit twice.
        GenericHelper.damageCoolDown(damageCD, e.getBukkitEntity(), enemyInvincibilityFrame);
        // handles post-hit mechanism: damage is handled by a listener
        if (bouncePenetrationBonded) {
            updateBounce(bounce - 1);
        }
        setPosition(position.pos.x, position.pos.y, position.pos.z);
        updatePenetration(penetration - 1);
        // special projectile
        hitEntityExtraHandling(e, position);
        // returns the hit location if the projectile breaks (returns null if the projectile is still alive)
        if (penetration < 0) {
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_DESTROY_REASON, DESTROY_HIT_ENTITY);
            die();
            return new Vec3D(position.pos.x, position.pos.y, position.pos.z);
        }
        else {
            boolean shouldSpawnClusterBomb = TerrariaHelper.projectileConfig.getBoolean(
                    projectileType + ".clusterBomb.fireOnCollideEntity", false);
            if (shouldSpawnClusterBomb) {
                EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_DESTROY_REASON, DESTROY_HIT_ENTITY);
                ArrowHitListener.spawnProjectileClusterBomb(bukkitEntity);
                EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_DESTROY_REASON, null);
            }
        }
        return null;
    }

    // tick
    // this helper function is called every tick as long as the projectile is alive.
    protected void spawnExtraProjectiles() {
        if (extraProjectileConfigSection == null)
            return;
        // validate CD
        if (extraProjectileSpawnInterval > 0 && ticksLived % extraProjectileSpawnInterval == 0) {
            Vector velocity = null;
            double offset = extraProjectileConfigSection.getDouble("offset", 1d);
            double extraProjectileSpeed = extraProjectileConfigSection.getDouble("speed", 1d);
            extraProjectileShootInfo.shootLoc = bukkitEntity.getLocation();
            switch (extraProjectileConfigSection.getString("spawnMechanism", "BOTTOM")) {
                case "SURROUND":
                    velocity = terraria.util.MathHelper.randomVector();
                    switch (projectileType) {
                        case "泰拉能量爆炸":
                            extraProjectileShootInfo.shootLoc.subtract(
                                    velocity.clone().multiply(extraProjectileSpeed / 2));
                            break;
                        default:
                            extraProjectileShootInfo.shootLoc.add(
                                    (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset);
                    }
                    break;
                case "FORWARD":
                    velocity = bukkitEntity.getVelocity().normalize();
                    extraProjectileShootInfo.shootLoc.add(
                            (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset);
                    break;
                case "ENEMY":
                    Set<HitEntityInfo> hitCandidates = HitEntityInfo.getEntitiesHit(
                            this.world,
                            new Vec3D(locX, locY, locZ),
                            new Vec3D(locX, locY, locZ),
                            offset,
                            (Entity toCheck) -> {
                                if (this.shooter != null && this.shooter == toCheck) return false;
                                return EntityHelper.checkCanDamage(bukkitEntity, toCheck.getBukkitEntity(), true);
                            });

                    for (HitEntityInfo hitInfo : hitCandidates) {
                        org.bukkit.entity.Entity hitEntity = hitInfo.getHitEntity().getBukkitEntity();
                        if (hitEntity instanceof LivingEntity) {
                            Location targetLoc = ((LivingEntity) hitEntity).getEyeLocation();
                            velocity = terraria.util.MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, 1);
                            break;
                        }
                    }
                    break;
                case "BOTTOM":
                default:
                    velocity = new Vector(0, -1, 0);
                    extraProjectileShootInfo.shootLoc.add(
                            (Math.random() - 0.5) * offset, 0, (Math.random() - 0.5) * offset);
            }
            if (velocity != null) {
                velocity.multiply(extraProjectileSpeed);
                extraProjectileShootInfo.velocity = velocity;
                EntityHelper.spawnProjectile(extraProjectileShootInfo);
            }
        }
    }
    // this helper function is called every tick as long as the projectile is alive.
    protected void extraTicking() {
        switch (projectileType) {
            case "旋风":
            case "风暴管束者剑气":
            case "小遗爵硫海漩涡": {
                double radius = 1, suckSpeed = 0.1, maxSpeed = 0.5;
                switch (projectileType) {
                    case "旋风":
                        radius = 6;
                        suckSpeed = 0.35;
                        maxSpeed = 0.75;
                        break;
                    case "风暴管束者剑气":
                        radius = 15;
                        suckSpeed = 0.75;
                        maxSpeed = 2;
                        break;
                    case "小遗爵硫海漩涡":
                        radius = 32;
                        suckSpeed = 1.75;
                        maxSpeed = 2.5;
                        break;
                }
                // get list of entities that could get sucked in
                Set<HitEntityInfo> hitCandidates = HitEntityInfo.getEntitiesHit(
                        this.world,
                        new Vec3D(lastX, lastY, lastZ),
                        new Vec3D(locX, locY, locZ),
                        radius,
                        (Entity toCheck) -> {
                            if (this.shooter != null && this.shooter == toCheck) return false;
                            return EntityHelper.checkCanDamage(bukkitEntity, toCheck.getBukkitEntity(), true);
                        });

                // suck enemies towards projectile
                for (HitEntityInfo toHit : hitCandidates) {
                    org.bukkit.entity.Entity enemy = toHit.getHitEntity().getBukkitEntity();
                    Vector velocity = new Vector(locX, locY, locZ);
                    velocity.subtract(enemy.getLocation().toVector());
                    double dist = velocity.length();
                    // pulling force decays as distance increases, to a min of 0.25x
                    velocity.multiply(Math.max(radius / 4, radius - dist) * suckSpeed / dist / radius);
                    EntityHelper.knockback(enemy, velocity, true, maxSpeed);
                }
                break;
            }
            case "遗忘沙刃": {
                double speedScaleMultiplier = 20d;
                switch (ticksLived % 15) {
                    case 10:
                        noAutoTraceTicks = 0;
                        break;
                    case 11:
                        noAutoTraceTicks = 999999;
                        speed *= speedScaleMultiplier;
                        motX *= speedScaleMultiplier;
                        motY *= speedScaleMultiplier;
                        motZ *= speedScaleMultiplier;
                        break;
                    case 1:
                        speed /= speedScaleMultiplier;
                        motX /= speedScaleMultiplier;
                        motY /= speedScaleMultiplier;
                        motZ /= speedScaleMultiplier;
                        break;
                }
                break;
            }
            case "黑蚀之星": {
                // once a target is spotted, do not rotate ever again.
                if (autoTraceTarget != null)
                    noAutoTraceTicks = 0;
                // only spin if no target is ever spotted
                else if (ticksLived <= 64 && noAutoTraceTicks != 0) {
                    // get angle and radius
                    double angle, radius;
                    Location center;
                    {
                        if (extraVariables.containsKey("a")) {
                            angle = (double) extraVariables.get("a");
                        }
                        else {
                            // 90 deg and 270( = -90) deg
                            if (motX < 1e-5 && motX > -1e-5)
                                angle = motZ > 0 ? Math.PI / 2 : Math.PI / -2;
                            // otherwise, use arc tan.
                            else {
                                angle = Math.atan(motZ / motX);
                                if (motZ < 0)
                                    angle += Math.PI;
                            }
                        }

                        radius = (double) extraVariables.getOrDefault("r", 3d);

                        if (!extraVariables.containsKey("c"))
                            extraVariables.put("c", bukkitEntity.getLocation().subtract(motX, motY, motZ));
                        center = (Location) extraVariables.get("c");
                    }
                    // increment angle and radius
                    int phase = ticksLived % 21;
                    // expanding phase
                    if (phase < 10) {
                        radius += 0.6;
                    }
                    // retraction phase
                    else if (phase > 14) {
                        radius -= 0.2;
                    }
                    angle += Math.PI / 14;
                    // update speed
                    Vector vel = center.clone().add(
                                    terraria.util.MathHelper.xcos_radian(angle) * radius, 0,
                                    terraria.util.MathHelper.xsin_radian(angle) * radius)
                            .subtract(bukkitEntity.getLocation()).toVector();
                    motX = vel.getX();
                    motY = vel.getY();
                    motZ = vel.getZ();
                    // save angle and radius
                    extraVariables.put("a", angle);
                    extraVariables.put("r", radius);
                }
                break;
            }
            case "镜之刃镜子": {
                Location eyeLoc = ((LivingEntity) shooter.getBukkitEntity()).getEyeLocation();
                Vector dir = terraria.util.MathHelper.getDirection(eyeLoc, bukkitEntity.getLocation(), 8);
                Location targetLoc = eyeLoc.add(dir);
                Vector newVel = terraria.util.MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc,
                        this.speed, true);

                motX = newVel.getX();
                motY = newVel.getY();
                motZ = newVel.getZ();
                break;
            }
            case "命运幽灵魂火": {
                Location eyeLoc = ((LivingEntity) shooter.getBukkitEntity()).getEyeLocation();
                Vector acc = terraria.util.MathHelper.getDirection(bukkitEntity.getLocation(), eyeLoc, this.speed * 0.15);
                Vector newVel = bukkitEntity.getVelocity().add(acc);
                newVel.normalize().multiply(this.speed);

                motX = newVel.getX();
                motY = newVel.getY();
                motZ = newVel.getZ();
                break;
            }
            case "狂野之镰": {
                if (ticksLived + 1 == noAutoTraceTicks) {
                    this.speed = maxSpeed;
                }
                break;
            }
            case "以太流光":
            case "舞光之刃": {
                double offsetRatio, rotationFreq;
                switch (projectileType) {
                    case "以太流光":
                        offsetRatio = 0.05;
                        // 2 full rotation per second
                        rotationFreq = 72;
                        break;
                    case "舞光之刃":
                    default:
                        offsetRatio = 0.1;
                        // a full rotation per second
                        rotationFreq = 36;
                        break;
                }
                // on first tick, save the forward direction, initialize twitch direction
                if (!extraVariables.containsKey("v")) {
                    Vector fwdDir = bukkitEntity.getVelocity();
                    // make sure twitch one and two are linearly independent
                    Vector twitchOne = new Vector();
                    while (twitchOne.lengthSquared() < 1e-5) {
                        twitchOne = terraria.util.MathHelper.randomVector();
                        twitchOne.subtract(terraria.util.MathHelper.vectorProjection(fwdDir, twitchOne));
                    }
                    twitchOne.normalize().multiply(speed * offsetRatio);
                    Vector twitchTwo = fwdDir.getCrossProduct(twitchOne);
                    twitchTwo.normalize().multiply(speed * offsetRatio);
                    // random twitch two's direction so some projectiles are going CW and some are CCW.
                    if (Math.random() < 0.5)
                        twitchTwo.multiply(-1);
                    // save variables
                    extraVariables.put("v", fwdDir);
                    extraVariables.put("o1", twitchOne);
                    extraVariables.put("o2", twitchTwo);
                }
                // update the projectile's velocity
                Vector fwd = (Vector) extraVariables.get("v");
                Vector offset1 = (Vector) extraVariables.get("o1");
                Vector offset2 = (Vector) extraVariables.get("o2");
                double angle = ticksLived * rotationFreq;
                fwd = fwd.clone().add(
                        offset1.clone().multiply(terraria.util.MathHelper.xsin_degree(angle) ) ).add(
                        offset2.clone().multiply(terraria.util.MathHelper.xcos_degree(angle) ) );
                motX = fwd.getX();
                motY = fwd.getY();
                motZ = fwd.getZ();
                break;
            }
            case "小异端诡灵":
            case "异端诡灵": {
                Player owner = (Player) (shooter.getBukkitEntity());
                // continue moving if the owner is still properly swinging and is close to owner
                if (PlayerHelper.isProperlyPlaying(owner) && owner.getScoreboardTags().contains("temp_autoSwing") &&
                        bukkitEntity.getWorld() == owner.getWorld() && bukkitEntity.getLocation().distanceSquared(owner.getLocation()) < 10000 ) {
                    addScoreboardTag("ignoreCanDamageCheck");
                    if (projectileType.equals("小异端诡灵")) {
                        // dash into the owner
                        if (ticksLived % 15 == 14) {
                            Vector newVelocity = owner.getEyeLocation().subtract(bukkitEntity.getLocation()).toVector();
                            terraria.util.MathHelper.setVectorLength(newVelocity, speed);
                            this.motX = newVelocity.getX();
                            this.motY = newVelocity.getY();
                            this.motZ = newVelocity.getZ();
                        }
                        // transform into the mature form
                        if (ticksLived >= 280)
                            setProperties("异端诡灵");
                    }
                    // ram into the owner's target
                    else {
                        Location targetLoc;
                        MetadataValue targetCache = EntityHelper.getMetadata(owner, EntityHelper.MetadataName.PLAYER_TARGET_LOC_CACHE);
                        if (targetCache != null &&
                                targetCache.value() instanceof LivingEntity &&
                                EntityHelper.checkCanDamage(owner, ((LivingEntity) targetCache.value()), true))
                            targetLoc = ((LivingEntity) targetCache.value()).getEyeLocation();
                        else
                            targetLoc = ItemUseHelper.getPlayerTargetLoc(owner, 80, 5,
                                    new EntityHelper.AimHelperOptions().setAimMode(true).setTicksOffset(0), true);
                        // move towards the owner's target
                        Vector dir = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                        terraria.util.MathHelper.setVectorLength(dir, speed);
                        this.motX = dir.getX();
                        this.motY = dir.getY();
                        this.motZ = dir.getZ();
                    }
                }
                else {
                    die();
                    owner.removeScoreboardTag("temp_autoSwing");
                }
                break;
            }
            case "幽花追踪能量弹": {
                if (ticksLived == 10) {
                    speedMultiPerTick = 0;
                    speed = 3;
                }
                break;
            }
            case "冲击波1": {
                // after 30 ticks, the radius is 30 + 1 = 31, total size = 62
                projectileRadius += 1;
                attrMap.put("damage", attrMap.getOrDefault("damage", 10d) * 0.95);
                break;
            }
        }
    }
    // this helper function is called every tick only if the projectile would move
    // (NOT homing into enemies and NOT stuck on wall)
    protected void extraMovingTick() {

    }
    protected void handleWorldSpriteParticleTrail() {
        // world sprite
        if (worldSpriteMode) {
            if (worldSpriteIdx % worldSpriteUpdateInterval == 0) {
                int currSpriteIdx = worldSpriteIdx / worldSpriteUpdateInterval + 1;
                org.bukkit.inventory.ItemStack displaySprite = generateBukkitItemStack(projectileType + "_" + currSpriteIdx);
                GenericHelper.displayNonDirectionalHoloItem(bukkitEntity.getLocation(), displaySprite,
                        worldSpriteUpdateInterval, (float) (projectileRadius * 2));
            }
            worldSpriteIdx++;
        }
        // trail
        if (trailColor != null) {
            // get the smooth transition from last displayed location to future location
            Location targetDisplayLoc = bukkitEntity.getLocation();
            Vector displayDir = targetDisplayLoc.subtract(lastTrailDisplayLocation).toVector();
            double displayDirLen = displayDir.length();
            // round down the number of steps between the current and future location
            int steps = (int) (displayDirLen / trailStepSize);
            double newDirLen = trailStepSize * steps;
            displayDir.multiply(newDirLen / displayDirLen);
            // display particles
            if (ticksLived > 1 || lastTrailDisplayLocation.distance(
                    ((LivingEntity) shooter.getBukkitEntity()).getEyeLocation() ) > (2 + trailSize) ) {
                GenericHelper.handleParticleLine(displayDir, lastTrailDisplayLocation,
                        new GenericHelper.ParticleLineOptions()
                                .setVanillaParticle(trailVanillaParticle)
                                .setLength(newDirLen)
                                .setWidth(trailSize, false)
                                .setStepsize(trailStepSize)
                                .setTicksLinger(trailLingerTime)
                                .setIntensityMulti(trailIntensityMulti)
                                .setParticleColor(trailColor));
            }
            // if the particle is too close to the eye location, attempt to move further and display particle
            else if (steps > 1) {
                displayDir.multiply(1d / steps);
                lastTrailDisplayLocation.add(displayDir);
                handleWorldSpriteParticleTrail();
                return;
            }
            // update last location
            lastTrailDisplayLocation.add(displayDir);
        }
    }
    protected void updateBounce(int newBounce) {
        bounce = newBounce;
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_BOUNCE_LEFT, newBounce);
    }
    protected void updatePenetration(int newPenetration) {
        penetration = newPenetration;
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_PENETRATION_LEFT, newPenetration);
    }
    // override functions
    @Override
    public void extinguish() {
        switch (projectileType) {
            case "小火花":
                EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_DESTROY_REASON, DESTROY_HIT_BLOCK);
                this.die();
                break;
            case "烈焰箭":
                setType("木箭");
                break;
        }
    }
    @Override
    // tick water
    public boolean aq() {
        if (this.world.a(this.getBoundingBox().grow(0.0, -0.4, 0.0).shrink(0.001), Material.WATER, this)) {
            this.inWater = true;
            this.extinguish();
        } else {
            this.inWater = false;
        }
        return this.inWater;
    }
    // die
    @Override
    public void die() {
        super.die();
        TerrariaProjectileHitEvent.callProjectileHitEvent(this);
    }
    // bounding box override on location update
    @Override
    public void setPosition(double x, double y, double z) {
        super.setPosition(x, y, z);
        // bounding box
        this.a(new AxisAlignedBB(x - projectileRadius, y, z - projectileRadius,
                x + projectileRadius, y + projectileRadius * 2, z + projectileRadius) );
    }
    @Override
    public void B_() {
        // start timing
        this.world.methodProfiler.a("entityBaseTick");

        // basic ticking (mainly from Entity.class)
        this.onGround = false;
        switch (blockHitAction) {
            // only bouncing and sliding projectiles should consider on ground cases (prevent glitch etc.)
            case "bounce":
            case "slide": {
                double distCheck = distCheckOnGround * (gravity > 0 ? -1 : 1);
                Vec3D checkLocInitial = new Vec3D(this.locX, this.locY, this.locZ);
                Vec3D checkLocTerminal = new Vec3D(this.locX, this.locY + distCheck, this.locZ);
                MovingObjectPosition onGroundResult = HitEntityInfo.rayTraceBlocks(this.world, checkLocInitial, checkLocTerminal);
                this.onGround = onGroundResult != null;
            }
        }

//        Bukkit.broadcastMessage(this.locX + ", " + this.locY + ", " + this.locZ + "||" + this.motX + ", " + this.motY + ", " + this.motZ);
//        Bukkit.broadcastMessage(this.onGround + ", " + ticksLived + "/" + liveTime);
        this.I = this.J;
        this.lastX = this.locX;
        this.lastY = this.locY;
        this.lastZ = this.locZ;
        this.lastPitch = this.pitch;
        this.lastYaw = this.yaw;
        this.lastOnGround = this.onGround;
        // tick water
        this.aq();
        if (this.locY < -64.0) this.die();
        if (!isAlive()) return;

        // comment out to send fresh movement speed packets to client frequently
        this.justCreated = false;


        // ticking from EntityProjectile.class
        this.M = this.locX;
        this.N = this.locY;
        this.O = this.locZ;
        // in lava or water
        double speedMulti = 1;
        if ((this.au() || this.inWater) && slowedByWater) {
            speedMulti = 1d/3;
        }

        // movement setup
        boolean shouldMove = true;
        if (this.inGround) {
            switch (blockHitAction) {
                case "bounce":
                case "slide":
                case "die":
                    updateBounce(-1);
                    EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_DESTROY_REASON, DESTROY_HIT_BLOCK);
                    this.die();
                    return;
                case "stick":
                    if (this.world.getType(new BlockPosition(this.locX, this.locY, this.locZ)).getMaterial().isSolid())
                        shouldMove = false;
                    else
                        this.inGround = false;
            }
        }


        // move
        Vec3D initialLoc = new Vec3D(this.locX, this.locY, this.locZ);
        Vec3D futureLoc = new Vec3D(this.locX, this.locY, this.locZ);
        Vector velocity = new Vector(this.motX, this.motY, this.motZ);
        this.setNoGravity(true);
        if (shouldMove) {
            // optimize auto trace target
            if (autoTrace) {
                // auto trace should not work before the projectile's age exceeds no auto trace tick
                if (ticksLived < noAutoTraceTicks || ticksLived > maxAutoTraceTicks) {
                    autoTraceTarget = null;
                } else {
                    if (autoTraceTarget != null && getAutoTraceInterest(autoTraceTarget) < -1e5)
                        autoTraceTarget = null;
                    if (autoTraceTarget == null) {
                        double maxInterest = -1e5;
                        List<Entity> list = this.world.getEntities(this, getBoundingBox().g(autoTraceRadius));
                        for (Entity toCheck : list) {
                            // only living entities are valid targets
                            if (!toCheck.isInteractable()) continue;
                            if (this.shooter != null && this.shooter == toCheck) continue;
                            // if the target is unreachable
                            if (!blockHitAction.equals("thru")) {
                                Vec3D traceEnd = toCheck.d();
                                MovingObjectPosition blockHitPos = HitEntityInfo.rayTraceBlocks(this.world, initialLoc, traceEnd);
                                if (blockHitPos != null) continue;
                            }
                            double currInterest = getAutoTraceInterest(toCheck);
                            if (currInterest > maxInterest) {
                                maxInterest = currInterest;
                                autoTraceTarget = toCheck;
                            }
                        }
                    }
                }
            }

            // tweak speed: handle auto trace or tweak by per tick speed decay and gravity
            if (autoTrace && autoTraceTarget != null) {
                Vector acceleration;
                if (autoTraceTarget instanceof EntityLiving)
                    acceleration = ((LivingEntity) autoTraceTarget.getBukkitEntity()).getEyeLocation().subtract(this.locX, this.locY, this.locZ).toVector();
                else
                    acceleration = autoTraceTarget.getBukkitEntity().getLocation().subtract(this.locX, this.locY, this.locZ).toVector();

                if (acceleration.lengthSquared() > 1e-5) {
                    // fixed homing efficiency
                    if (autoTraceMethod == 2) {
                        acceleration.multiply(autoTraceAbility / acceleration.length());
                    }
                    // homing efficiency proportional to (projectile speed / distance)
                    else {
                        acceleration.multiply(autoTraceAbility * velocity.length() / acceleration.lengthSquared());
                    }
                }

                velocity.add(acceleration);

                // if no sharp turning is allowed, then as long as the velocity length is above 0, it is being regularized.
                double normalizeThreshold = autoTraceSharpTurning ? speed * speed : 0;
                if (velocity.lengthSquared() > normalizeThreshold)
                    velocity.normalize().multiply(speed);

                // if a target is valid when auto trace timeout, multiply the speed by the multiplier
                if (ticksLived == maxAutoTraceTicks) {
                    velocity.multiply(autoTraceEndSpeedMultiplier);
                }
            } else {
                extraMovingTick();
                // friction if on ground
                if (this.onGround && this.lastOnGround) {
                    velocity.multiply(1 - frictionFactor);
                }
                // gravity if not on ground
                else if (this.ticksLived >= noGravityTicks) {
                    velocity.subtract( new Vector(0, gravity, 0) );
                }
                // regulate velocity
                velocity.multiply(speedMultiPerTick);
                if (velocity.lengthSquared() > maxSpeed * maxSpeed)
                    terraria.util.MathHelper.setVectorLength(velocity, maxSpeed);
            }

            // get the block it will cram into, then set the final location after ticking
            double spdX = velocity.getX() * speedMulti, spdY = velocity.getY() * speedMulti, spdZ = velocity.getZ() * speedMulti;
            futureLoc = new Vec3D(this.locX + spdX, this.locY + spdY, this.locZ + spdZ);
            MovingObjectPosition movingobjectposition = HitEntityInfo.rayTraceBlocks(this.world, initialLoc, futureLoc);
            this.inGround = false;
            if (movingobjectposition != null) {
                // call hit block event
                TerrariaProjectileHitEvent evt = TerrariaProjectileHitEvent.callProjectileHitEvent(this, movingobjectposition);
                if (!evt.isCancelled()) {
                    // gravity turns on after bouncing
                    this.noGravityTicks = this.ticksLived - 1;
                    this.inGround = true;
                    switch (blockHitAction) {
                        case "bounce":
                        case "slide": {
                            // for sliding off and bouncing off, pull it back a bit, so it does not get stuck in the wall
                            this.inGround = false;
                            Vector travelled = new Vector(movingobjectposition.pos.x - this.locX,
                                    movingobjectposition.pos.y - this.locY,
                                    movingobjectposition.pos.z - this.locZ);
                            double dist = travelled.length();
                            if (dist > distFromBlock) {
                                travelled.multiply((dist - distFromBlock) / dist);
                            } else {
                                travelled.multiply(0);
                            }
                            futureLoc = new Vec3D(this.locX + travelled.getX(), this.locY + travelled.getY(), this.locZ + travelled.getZ());
                            // tweak velocity
                            if (blockHitAction.equals("bounce")) {
                                updateBounce(bounce - 1);
                                if (bounce < 0) {
                                    EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.PROJECTILE_DESTROY_REASON, DESTROY_HIT_BLOCK);
                                    die();
                                    return;
                                }
                                if (bouncePenetrationBonded) {
                                    updatePenetration(penetration - 1);
                                }
                                // chlorophyte arrow bounce into enemies
                                switch (projectileType) {
                                    case "叶绿箭": {
                                        velocity.multiply(-1);
                                        double homeInRadius = 32,
                                                smallestDistSqr = 1e9;
                                        Location currLoc = new Location(this.bukkitEntity.getWorld(), futureLoc.x, futureLoc.y, futureLoc.z);
                                        for (org.bukkit.entity.Entity entity : getBukkitEntity().getNearbyEntities(homeInRadius, homeInRadius, homeInRadius)) {
                                            // should strictly home into "proper" enemies (no critters!)
                                            if (!EntityHelper.checkCanDamage(this.bukkitEntity, entity, true))
                                                continue;
                                            // make sure the closest entity is targeted
                                            double currDistSqr = entity.getLocation().distanceSquared(currLoc);
                                            if (currDistSqr >= smallestDistSqr)
                                                continue;
                                            // predicts the enemy's future location
                                            Location predictedLoc = EntityHelper.helperAimEntity(this.bukkitEntity, entity,
                                                    new EntityHelper.AimHelperOptions()
                                                            .setProjectileSpeed(this.speed));
                                            // initializes direction the projectile should go to
                                            Vector dir = predictedLoc.subtract(currLoc).toVector();
                                            double predictedDist = dir.length();
                                            dir.multiply(this.speed / predictedDist);
                                            // account for gravity
                                            double ticksToTravel = Math.ceil(predictedDist / this.speed);
                                            // distY = acceleration(gravity) * ticksToTravel^2 / 2
                                            // to counter that, yVelocityOffset = distY / ticksToTravel = gravity * ticksToTravel / 2
                                            double yVelocityOffset = this.gravity * ticksToTravel / 2;
                                            dir.setY(dir.getY() + yVelocityOffset);
                                            // check if the direction is clear. If it is obstructed with block, skip this entity.
                                            Vec3D checkLocVec;
                                            if (entity instanceof LivingEntity)
                                                checkLocVec = terraria.util.MathHelper.toNMSVector(((LivingEntity) entity).getEyeLocation().toVector());
                                            else
                                                checkLocVec = terraria.util.MathHelper.toNMSVector(entity.getLocation().toVector());
                                            MovingObjectPosition blockedLocation = HitEntityInfo.rayTraceBlocks(this.world,
                                                    futureLoc, checkLocVec);
                                            if (blockedLocation != null)
                                                continue;
                                            // update the smallest distance only if the target is reachable.
                                            smallestDistSqr = currDistSqr;
                                            // update velocity
                                            velocity = dir;
                                        }
                                        break;
                                    }
                                    case "不稳定物质": {
                                        // increases damage, but disappears after striking one enemy.
                                        attrMap.put("damage", attrMap.get("damage") * 3);
                                        updatePenetration(0);
                                        // default bounce behaviour
                                        double yVelocityThreshold = gravity * speedMulti;
                                        switch (movingobjectposition.direction) {
                                            case UP:
                                                velocity.setY(velocity.getY() * -1);
                                                // prevent projectile twitching
                                                if (gravity > 0 && velocity.getY() < yVelocityThreshold)
                                                    velocity.setY(0);
                                                break;
                                            case DOWN:
                                                velocity.setY(velocity.getY() * -1);
                                                // prevent projectile twitching
                                                if (gravity < 0 && velocity.getY() > yVelocityThreshold)
                                                    velocity.setY(0);
                                                break;
                                            case EAST:
                                            case WEST:
                                                velocity.setX(velocity.getX() * -1);
                                                break;
                                            case SOUTH:
                                            case NORTH:
                                                velocity.setZ(velocity.getZ() * -1);
                                                break;
                                        }
                                        // homing into enemy
                                        double homeInRadius = 16,
                                                smallestDistSqr = 1e9;
                                        Location currLoc = new Location(this.bukkitEntity.getWorld(), futureLoc.x, futureLoc.y, futureLoc.z);
                                        for (org.bukkit.entity.Entity entity : getBukkitEntity().getNearbyEntities(homeInRadius, homeInRadius, homeInRadius)) {
                                            // should strictly home into "proper" enemies (no critters!)
                                            if (!EntityHelper.checkCanDamage(this.bukkitEntity, entity, true))
                                                continue;
                                            // make sure the closest entity is targeted
                                            double currDistSqr = entity.getLocation().distanceSquared(currLoc);
                                            if (currDistSqr >= smallestDistSqr)
                                                continue;
                                            // predicts the enemy's future location
                                            Location predictedLoc = EntityHelper.helperAimEntity(this.bukkitEntity, entity,
                                                    new EntityHelper.AimHelperOptions()
                                                            .setProjectileSpeed(this.speed));
                                            // initializes direction the projectile should go to
                                            Vector dir = predictedLoc.subtract(currLoc).toVector();
                                            double predictedDist = dir.length();
                                            dir.multiply(this.speed / predictedDist);
                                            // update the smallest distance only if the target is reachable.
                                            smallestDistSqr = currDistSqr;
                                            // update velocity
                                            velocity = dir;
                                        }
                                        break;
                                    }
                                    case "黑翼蝙蝠": {
                                        velocity.multiply(-1);
                                        break;
                                    }
                                    default: {
                                        velocity.multiply(bounceVelocityMulti);
                                        double yVelocityThreshold = gravity * speedMulti;
                                        switch (movingobjectposition.direction) {
                                            case UP:
                                                velocity.setY(velocity.getY() * -1);
                                                // prevent projectile twitching
                                                if (gravity > 0 && velocity.getY() < yVelocityThreshold)
                                                    velocity.setY(0);
                                                break;
                                            case DOWN:
                                                velocity.setY(velocity.getY() * -1);
                                                // prevent projectile twitching
                                                if (gravity < 0 && velocity.getY() > yVelocityThreshold)
                                                    velocity.setY(0);
                                                break;
                                            case EAST:
                                            case WEST:
                                                velocity.setX(velocity.getX() * -1);
                                                break;
                                            case SOUTH:
                                            case NORTH:
                                                velocity.setZ(velocity.getZ() * -1);
                                                break;
                                        }
                                    }
                                }
                            } else{
                                // slide
                                switch (movingobjectposition.direction) {
                                    case UP:
                                    case DOWN:
                                        velocity.setY(0);
                                        break;
                                    case EAST:
                                    case WEST:
                                        velocity.setX(0);
                                        break;
                                    case SOUTH:
                                    case NORTH:
                                        velocity.setZ(0);
                                        break;
                                }
                            }
                            break;
                        }
                        // thru: it never gets stuck, so make a break here
                        case "thru":
                            break;
                        // stick: it is supposed to get stuck in the wall
                        case "stick":
                            velocity.multiply(0);
                            Vector travelled = new Vector(movingobjectposition.pos.x - this.locX,
                                    movingobjectposition.pos.y - this.locY,
                                    movingobjectposition.pos.z - this.locZ);
                            double dist = travelled.length();
                            if (dist > 0)
                                travelled.multiply((dist + distFromBlock) / dist);
                            futureLoc = new Vec3D(this.locX + travelled.getX(), this.locY + travelled.getY(), this.locZ + travelled.getZ());
                            break;
                        default:
                            futureLoc = movingobjectposition.pos;
                    }
                }
            }

            // update yaw and pitch
            {
                float horizontalSpeed = MathHelper.sqrt(this.motX * this.motX + this.motZ * this.motZ);
                this.yaw = (float) (MathHelper.c(this.motX, this.motZ) * 57.2957763671875D);
                for (this.pitch = (float) (MathHelper.c(this.motY, horizontalSpeed) * 57.2957763671875D);
                     this.pitch - this.lastPitch < -180.0F;
                     this.lastPitch -= 360.0F);
                while (this.pitch - this.lastPitch >= 180.0F)
                    this.lastPitch += 360.0F;
                while (this.yaw - this.lastYaw < -180.0F)
                    this.lastYaw -= 360.0F;
                while (this.yaw - this.lastYaw >= 180.0F)
                    this.lastYaw += 360.0F;
                this.pitch = this.lastPitch + (this.pitch - this.lastPitch) * 0.2F;
                this.yaw = this.lastYaw + (this.yaw - this.lastYaw) * 0.2F;
            }

            // handle water particle
            if (isInWater()) {
                for (int j = 0; j < 4; j++)
                    this.world.addParticle(EnumParticle.WATER_BUBBLE, this.locX - this.motX * projectileRadius, this.locY - this.motY * projectileRadius, this.locZ - this.motZ * projectileRadius, this.motX, this.motY, this.motZ, new int[0]);
            }
        }

        // handle projectile hit
        if (ticksLived >= minimumDamageTicks) {
            // get list of entities that could get hit
            Set<HitEntityInfo> hitCandidates = HitEntityInfo.getEntitiesHit(
                    this.world,
                    new Vec3D(locX, locY, locZ),
                    futureLoc,
                    this.projectileRadius,
                    this::checkCanHit);

            // hit entities
            if (projectileType.equals("光之舞闪光")) {
                attrMap.put("damage", attrMap.getOrDefault("damage", 6666d) / hitCandidates.size());
            }
            for (HitEntityInfo toHit : hitCandidates) {
                Entity hitEntity = toHit.getHitEntity();
                MovingObjectPosition hitInfo = new MovingObjectPosition(hitEntity, toHit.getHitLocation().pos);
                if (TerrariaProjectileHitEvent.callProjectileHitEvent(this, hitInfo).isCancelled()) continue;
                Vec3D newLoc = hitEntity(hitEntity, hitInfo);
                if (newLoc != null) futureLoc = newLoc;
                // if the projectile reached its penetration capacity, stop damaging enemies
                if (this.dead) break;
            }
        }
        // handle vegetation hit
        {
            org.bukkit.World bukkitWorld = bukkitEntity.getWorld();
            Location bukkitInitialLoc = terraria.util.MathHelper.toBukkitVector(initialLoc).toLocation(bukkitWorld);
            Location bukkitFinalLoc = terraria.util.MathHelper.toBukkitVector(futureLoc).toLocation(bukkitWorld);
            WorldHelper.attemptDestroyVegetation(bukkitInitialLoc, bukkitFinalLoc);
        }

        // set position and velocity info
        setPosition(futureLoc.x, futureLoc.y, futureLoc.z);
        this.motX = velocity.getX();
        this.motY = velocity.getY();
        this.motZ = velocity.getZ();
        // send new velocity if:
        //     velocity changed
        //     the projectile has gravity
        // otherwise send new velocity regularly every 5 ticks
//        if (!this.velocityChanged && (ticksLived % 5 == 0 ||
//                velocity.distanceSquared(new Vector(this.motX, this.motY, this.motZ)) > 0.0001 ||
//                !isNoGravity()))
//            this.velocityChanged = true;

        // prevents client glitch
        this.velocityChanged = true;
        this.positionChanged = true;
        this.impulse = true;
        // draw particle trail
        handleWorldSpriteParticleTrail();
        // spawn projectiles
        spawnExtraProjectiles();
        // extra ticking
        extraTicking();

        // time out removal
        if (this.ticksLived >= liveTime) die();
        // timing
        this.world.methodProfiler.b();
    }
}
