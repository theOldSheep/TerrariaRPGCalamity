package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.event.TerrariaProjectileHitEvent;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;

import java.util.*;

public class TerrariaPotionProjectile extends EntityPotion {
    private static final double distFromBlock = 1e-5, distCheckOnGround = 1e-1;
    // projectile info
    public String projectileType, blockHitAction = "die", trailColor = null;
    public int autoTraceMethod = 1, bounce = 0, enemyInvincibilityFrame = 5, liveTime = 200, noAutoTraceTicks = 0, maxAutoTraceTicks = 999999,
            noGravityTicks = 15, trailLingerTime = 10, penetration = 0;
    public double autoTraceAbility = 4, autoTraceRadius = 12, blastRadius = 1.5, bounceVelocityMulti = 1,
            frictionFactor = 0.05, gravity = 0.05, maxSpeed = 100, projectileRadius = 0.125, speedMultiPerTick = 1,
            trailSize = -1, trailStepSize = -1;
    public boolean autoTrace = false, autoTraceSharpTurning = true, blastDamageShooter = false,
            blastOnContactBlock = false, blastOnContactEnemy = false, bouncePenetrationBonded = false,
            canBeReflected = true, isGrenade = false, slowedByWater = true;

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


    private void setupProjectileProperties() {
        {
            this.autoTraceMethod = (int) properties.getOrDefault("autoTraceMethod", this.autoTraceMethod);
            this.bounce = (int) properties.getOrDefault("bounce", this.bounce);
            this.enemyInvincibilityFrame = (int) properties.getOrDefault("enemyInvincibilityFrame", this.enemyInvincibilityFrame);
            this.liveTime = (int) properties.getOrDefault("liveTime", this.liveTime);
            this.noAutoTraceTicks = (int) properties.getOrDefault("noAutoTraceTicks", this.noAutoTraceTicks);
            this.maxAutoTraceTicks = (int) properties.getOrDefault("maxAutoTraceTicks", this.maxAutoTraceTicks);
            this.noGravityTicks = (int) properties.getOrDefault("noGravityTicks", this.noGravityTicks);
            this.trailLingerTime = (int) properties.getOrDefault("trailLingerTime", this.trailLingerTime);
            this.penetration = (int) properties.getOrDefault("penetration", this.penetration);
            // thru, bounce, stick, slide
            this.blockHitAction = (String) properties.getOrDefault("blockHitAction", this.blockHitAction);
            this.trailColor = (String) properties.getOrDefault("trailColor", this.trailColor);

            this.autoTraceAbility = (double) properties.getOrDefault("autoTraceAbility", this.autoTraceAbility);
            this.autoTraceRadius = (double) properties.getOrDefault("autoTraceRadius", this.autoTraceRadius);
            this.blastRadius = (double) properties.getOrDefault("blastRadius", this.blastRadius);
            this.bounceVelocityMulti = (double) properties.getOrDefault("bounceVelocityMulti", this.bounceVelocityMulti);
            this.frictionFactor = (double) properties.getOrDefault("frictionFactor", this.frictionFactor);
            this.gravity = (double) properties.getOrDefault("gravity", this.gravity);
            this.maxSpeed = (double) properties.getOrDefault("maxSpeed", this.maxSpeed);
            this.projectileRadius = (double) properties.getOrDefault("projectileSize", this.projectileRadius);
            this.speedMultiPerTick = (double) properties.getOrDefault("speedMultiPerTick", this.speedMultiPerTick);
            this.trailSize = (double) properties.getOrDefault("trailSize", this.projectileRadius);
            this.trailStepSize = (double) properties.getOrDefault("trailStepSize", this.projectileRadius);

            this.autoTrace = (boolean) properties.getOrDefault("autoTrace", this.autoTrace);
            this.autoTraceSharpTurning = (boolean) properties.getOrDefault("autoTraceSharpTurning", this.autoTraceSharpTurning);
            this.blastDamageShooter = (boolean) properties.getOrDefault("blastDamageShooter", this.blastDamageShooter);
            this.blastOnContactBlock = (boolean) properties.getOrDefault("blastOnContactBlock", this.blastOnContactBlock);
            this.blastOnContactEnemy = (boolean) properties.getOrDefault("blastOnContactEnemy", this.blastOnContactEnemy);
            this.bouncePenetrationBonded = (boolean) properties.getOrDefault("bouncePenetrationBonded", this.bouncePenetrationBonded);
            this.canBeReflected = (boolean) properties.getOrDefault("canBeReflected", this.canBeReflected);
            this.isGrenade = (boolean) properties.getOrDefault("isGrenade", this.isGrenade);
            this.slowedByWater = (boolean) properties.getOrDefault("slowedByWater", this.slowedByWater);
        }
        this.setNoGravity(true);
        this.noclip = true;
        this.damageCD = new HashSet<>((int) (penetration * 1.5));
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
        if (blastDamageShooter) addScoreboardTag("blastDamageShooter");
        else removeScoreboardTag("blastDamageShooter");
        EntityHelper.setMetadata(bukkitEntity, "penetration", this.penetration);
        EntityHelper.setMetadata(bukkitEntity, "collided", this.damageCD);
    }
    // setup properties of the specific type, including its item displayed
    public void setType(String type) {
        setItem(generateItemStack(type));
        setProperties(type);
    }
    public static ItemStack generateItemStack(String projectileType) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.SPLASH_POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        meta.setDisplayName(projectileType);
        meta.setColor(org.bukkit.Color.fromRGB(255, 255, 255));
        item.setItemMeta(meta);
        return CraftItemStack.asNMSCopy(item);
    }
    // constructor
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
        bukkitEntity.setShooter(shooter);
        this.attrMap = (HashMap<String, Double>) attrMap.clone();
        EntityHelper.setMetadata(bukkitEntity, "attrMap", this.attrMap);
        EntityHelper.setMetadata(bukkitEntity, "damageType", damageType);
        setProperties(projectileType);
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
    public void hitEntity(Entity e, MovingObjectPosition position) {
        // handle damage CD before doing anything else. Otherwise, exploding projectiles will damage the enemy being hit twice.
        GenericHelper.damageCoolDown(damageCD, e.getBukkitEntity(), enemyInvincibilityFrame);
        // handles post-hit mechanism: damage is handled by a listener
        if (bouncePenetrationBonded) bounce --;
        if (--penetration < 0) {
            setPosition(position.pos.x, position.pos.y, position.pos.z);
            EntityHelper.setMetadata(bukkitEntity, "destroyReason", "hitEntity");
            die();
        }
        EntityHelper.setMetadata(bukkitEntity, "penetration", this.penetration);
    }

    // override functions
    @Override
    public void extinguish() {
        switch (projectileType) {
            case "小火花":
                EntityHelper.setMetadata(bukkitEntity, "destroyReason", "hitBlock");
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
    // tick
    // this helper function is called every tick as long as the projectile is alive.
    protected void spawnExtraProjectiles() {
        if (extraProjectileConfigSection == null)
            return;
        // validate CD
        if (extraProjectileSpawnInterval > 0 && ticksLived % extraProjectileSpawnInterval == 0) {
            Vector velocity;
            double offset = extraProjectileConfigSection.getDouble("offset", 1d);
            extraProjectileShootInfo.shootLoc = bukkitEntity.getLocation();
            switch (extraProjectileConfigSection.getString("spawnMechanism", "BOTTOM")) {
                case "SURROUND":
                    velocity = terraria.util.MathHelper.randomVector();
                    extraProjectileShootInfo.shootLoc.add(
                            (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset);
                    break;
                case "FORWARD":
                    velocity = bukkitEntity.getVelocity().normalize();
                    extraProjectileShootInfo.shootLoc.add(
                            (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset, (Math.random() - 0.5) * offset);
                    break;
                case "BOTTOM":
                default:
                    velocity = new Vector(0, -1, 0);
                    extraProjectileShootInfo.shootLoc.add(
                            (Math.random() - 0.5) * offset, 0, (Math.random() - 0.5) * offset);
            }
            velocity.multiply(extraProjectileConfigSection.getDouble("speed", 1d));
            extraProjectileShootInfo.velocity = velocity;
            EntityHelper.spawnProjectile(extraProjectileShootInfo);
        }
    }
    // this helper function is called every tick as long as the projectile is alive.
    protected void extraTicking() {
        switch (projectileType) {
            case "风刃": {
                double radius = 12, suckSpeed = 0.25;
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
                    velocity.multiply(Math.max(0, radius - dist) * suckSpeed / dist / radius);
                    EntityHelper.knockback(enemy, velocity, true);
                }
                break;
            }
        }
    }
    // this helper function is called every tick only if the projectile would move(not homing into enemies and not stuck on wall)
    protected void extraMovingTick() {

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
                    EntityHelper.setMetadata(bukkitEntity, "destroyReason", "hitBlock");
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
                    if (autoTraceMethod == 2) {
                        acceleration.multiply(autoTraceAbility / acceleration.length());
                    } else {
                        acceleration.multiply(autoTraceAbility * velocity.length() / acceleration.lengthSquared());
                    }
                }

                velocity.add(acceleration);

                // if no sharp turning is allowed, then as long as the velocity length is above 0, it is being regularized.
                double normalizeThreshold = autoTraceSharpTurning ? speed * speed : 0;
                if (velocity.lengthSquared() > normalizeThreshold)
                    velocity.normalize().multiply(speed);
            } else {
                extraMovingTick();
                if (this.onGround && this.lastOnGround) {
                    velocity.multiply(1 - frictionFactor);
                    // friction
                } else if (this.ticksLived >= noGravityTicks) {
                    // gravity
                    velocity.subtract(new Vector(0, gravity, 0));
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
                    this.noGravityTicks = this.ticksLived;
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
                                if (--bounce < 0) {
                                    EntityHelper.setMetadata(bukkitEntity, "destroyReason", "hitBlock");
                                    die();
                                    return;
                                }
                                if (bouncePenetrationBonded) {
                                    penetration--;
                                    EntityHelper.setMetadata(bukkitEntity, "penetration", this.penetration);
                                }
                                // chlorophyte arrow bounce into enemies
                                if (projectileType.equals("叶绿箭")) {
                                    velocity.multiply(-1);
                                    double homeInRadius = 24,
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
                                } else {
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
        {
            // get list of entities that could get hit
            Set<HitEntityInfo> hitCandidates = HitEntityInfo.getEntitiesHit(
                    this.world,
                    new Vec3D(locX, locY, locZ),
                    futureLoc,
                    this.projectileRadius,
                    (Entity toCheck) -> {
                        if (this.shooter != null && this.shooter == toCheck) return false;
                        return checkCanHit(toCheck);
                    });

            // hit entities
            for (HitEntityInfo toHit : hitCandidates) {
                Entity hitEntity = toHit.getHitEntity();
                MovingObjectPosition hitInfo = new MovingObjectPosition(hitEntity, toHit.getHitLocation().pos);
                if (TerrariaProjectileHitEvent.callProjectileHitEvent(this, hitInfo).isCancelled()) continue;
                hitEntity(hitEntity, hitInfo);
                // if the projectile reached its penetration capacity, stop damaging enemies
                if (this.dead) break;
            }
        }

        // draw particle trail
        {
            if (trailColor != null && this.ticksLived > 1) {
                // init last display location as current location if it is not set
                if (lastTrailDisplayLocation == null)
                    lastTrailDisplayLocation = bukkitEntity.getLocation();
                // get the smooth transition from last displayed location to future location
                Location targetDisplayLoc = bukkitEntity.getLocation().add(bukkitEntity.getVelocity());
                Vector displayDir = targetDisplayLoc.subtract(lastTrailDisplayLocation).toVector();
                double displayDirLen = displayDir.length();
                // round down the number of steps between the current and future location
                int steps = (int) (displayDirLen / trailStepSize);
                double newDirLen = trailStepSize * steps;
                displayDir.multiply(newDirLen / displayDirLen);
                // display particles
                GenericHelper.handleParticleLine(displayDir, lastTrailDisplayLocation,
                        new GenericHelper.ParticleLineOptions()
                                .setLength(newDirLen)
                                .setWidth(trailSize, false)
                                .setStepsize(trailStepSize)
                                .setTicksLinger(trailLingerTime)
                                .setParticleColor(trailColor));
                // update last location
                lastTrailDisplayLocation.add(displayDir);
            }
        }
        // set position and velocity info
        setPosition(futureLoc.x, futureLoc.y, futureLoc.z);
        // spawn projectiles
        spawnExtraProjectiles();
        // extra ticking
        extraTicking();
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

        this.motX = velocity.getX();
        this.motY = velocity.getY();
        this.motZ = velocity.getZ();

        // time out removal
        if (this.ticksLived >= liveTime) die();
        // timing
        this.world.methodProfiler.b();
    }
}
