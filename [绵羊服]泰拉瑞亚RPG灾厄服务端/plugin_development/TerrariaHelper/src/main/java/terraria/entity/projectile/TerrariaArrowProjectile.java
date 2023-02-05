package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.event.TerrariaProjectileHitEvent;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;

import java.util.*;

public class TerrariaArrowProjectile extends EntityTippedArrow {
    private static final double distFromBlock = 1e-5, distCheckOnGround = 1e-1;
    // projectile info
    public String projectileType, blockHitAction = "die", trailColor = null;
    public int bounce = 0, enemyInvincibilityFrame = 5, liveTime = 200, noAutoTraceTicks = 0, noGravityTicks = 15, trailLingerTime = 10, penetration = 0;
    public double autoTraceAbility = 4, autoTraceRadius = 12, blastRadius = 1.5, bounceVelocityMulti = 1,
            frictionFactor = 0.05, gravity = 0.05, maxSpeed = 100, projectileSize = 0.125, speedMultiPerTick = 1;
    public boolean autoTrace = false, autoTraceSharpTurning = true, blastDamageShooter = false, bouncePenetrationBonded = false,
            canBeReflected = true, isGrenade = false, slowedByWater = true;

    public double speed;
    public boolean lastOnGround = false;
    public HashMap<UUID, Integer> damageCD;
    public org.bukkit.entity.Projectile bukkitEntity;
    public Entity autoTraceTarget = null;


    private void setupProjectileProperties() {
        ConfigurationSection section = TerrariaHelper.projectileConfig.getConfigurationSection(projectileType);
        if (section != null) {
            this.bounce = section.getInt("bounce", this.bounce);
            this.enemyInvincibilityFrame = section.getInt("enemyInvincibilityFrame", this.enemyInvincibilityFrame);
            this.liveTime = section.getInt("liveTime", this.liveTime);
            this.noAutoTraceTicks = section.getInt("noAutoTraceTicks", this.noAutoTraceTicks);
            this.noGravityTicks = section.getInt("noGravityTicks", this.noGravityTicks);
            this.trailLingerTime = section.getInt("trailLingerTime", this.trailLingerTime);
            this.penetration = section.getInt("penetration", this.penetration);
            // thru, bounce, stick, slide
            this.blockHitAction = section.getString("blockHitAction", this.blockHitAction);

            this.trailColor = section.getString("trailColor");

            this.autoTraceAbility = section.getDouble("autoTraceAbility", this.autoTraceAbility);
            this.autoTraceRadius = section.getDouble("autoTraceRadius", this.autoTraceRadius);
            this.blastRadius = section.getDouble("blastRadius", this.blastRadius);
            this.bounceVelocityMulti = section.getDouble("bounceVelocityMulti", this.bounceVelocityMulti);
            this.frictionFactor = section.getDouble("frictionFactor", this.frictionFactor);
            this.gravity = section.getDouble("gravity", this.gravity);
            this.maxSpeed = section.getDouble("maxSpeed", this.maxSpeed);
            this.projectileSize = section.getDouble("projectileSize", this.projectileSize);
            this.speedMultiPerTick = section.getDouble("speedMultiPerTick", this.speedMultiPerTick);

            this.autoTrace = section.getBoolean("autoTrace", this.autoTrace);
            this.autoTraceSharpTurning = section.getBoolean("autoTraceSharpTurning", this.autoTraceSharpTurning);
            this.blastDamageShooter = section.getBoolean("blastDamageShooter", this.blastDamageShooter);
            this.bouncePenetrationBonded = section.getBoolean("bouncePenetrationBonded", this.bouncePenetrationBonded);
            this.canBeReflected = section.getBoolean("canBeReflected", this.canBeReflected);
            this.isGrenade = section.getBoolean("isGrenade", this.isGrenade);
            this.slowedByWater = section.getBoolean("slowedByWater", this.slowedByWater);
        }
        this.setNoGravity(true);
        this.noclip = true;
        this.damageCD = new HashMap<>((int) (penetration * 1.5));
    }

    // setup properties of the specific type, including its item displayed
    public void setType(String type) {
        setProperties(type);
    }
    // setup properties of the specific type, excluding its item displayed
    public void setProperties(String type) {
        projectileType = type;
        setupProjectileProperties();
        setCustomName(type);
        if (isGrenade) addScoreboardTag("isGrenade");
        else removeScoreboardTag("isGrenade");
        if (blastDamageShooter) addScoreboardTag("blastDamageShooter");
        else removeScoreboardTag("blastDamageShooter");
        EntityHelper.setMetadata(bukkitEntity, "penetration", this.penetration);
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
    public TerrariaArrowProjectile(org.bukkit.Location loc, Vector velocity, String projectileType) {
        super(((CraftWorld) loc.getWorld()).getHandle(), loc.getX(), loc.getY(), loc.getZ());
        this.motX = velocity.getX();
        this.motY = velocity.getY();
        this.motZ = velocity.getZ();
        this.speed = velocity.length();
        this.projectileType = projectileType;
        bukkitEntity = (org.bukkit.entity.Projectile) getBukkitEntity();
        setProperties(projectileType);
    }

    protected double getAutoTraceInterest(Entity target) {
        // should not be following critters, dead entities etc. and attempt to damage them
        if (!EntityHelper.checkCanDamage(bukkitEntity, target.getBukkitEntity())) return -1e9;
        // returns distance squared / velocity squared, that is, ticks to get there squared, * -1
        double distSqr = target.d(this.locX, this.locY, this.locZ) - (this.projectileSize * this.projectileSize);
        return distSqr * -1;
    }
    public boolean checkCanHit(Entity e) {
        if (damageCD.containsKey(e.getUniqueID())) return false;
        // should still be able to hit entities that are neither monster nor forbidden to hit
        return EntityHelper.checkCanDamage(bukkitEntity, e.getBukkitEntity(), false);
    }
    public void hitEntity(Entity e, MovingObjectPosition position) {
        // handles post-hit mechanism: damage is handled by a listener
        if (--penetration < 0) {
            setPosition(position.pos.x, position.pos.y, position.pos.z);
            die();
        }
        EntityHelper.setMetadata(bukkitEntity, "penetration", this.penetration);
        if (bouncePenetrationBonded) bounce --;
        damageCD.put(e.getUniqueID(), enemyInvincibilityFrame);
    }

    // override functions
    @Override
    public void extinguish() {
        switch (projectileType) {
            case "小火花":
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
    public void extraTicking() {
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
    public void extraMovingTick() {

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
                if (ticksLived < noAutoTraceTicks) {
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

                double accMagnitude = acceleration.length(), velMagnitude = velocity.length();
                if (velMagnitude > 1e-5) {
//                    velocity.multiply(Math.sqrt(Math.min(accMagnitude, autoTraceAbility)) / velMagnitude);
                    velocity.multiply(accMagnitude / autoTraceAbility / velMagnitude);
                }
                if (accMagnitude > 1e-5) {
//                    acceleration.multiply(autoTraceAbility / accMagnitude);
                    acceleration.multiply(autoTraceAbility / accMagnitude);
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
                    this.world.addParticle(EnumParticle.WATER_BUBBLE, this.locX - this.motX * projectileSize, this.locY - this.motY * projectileSize, this.locZ - this.motZ * projectileSize, this.motX, this.motY, this.motZ, new int[0]);
            }
        }

        // handle projectile hit
        {
            // update invincibility map
            ArrayList<UUID> toRemove = new ArrayList<>();
            for (UUID currCheck : damageCD.keySet()) {
                int currInvincibility = damageCD.get(currCheck);
                if (currInvincibility <= 1) toRemove.add(currCheck);
                else damageCD.put(currCheck, currInvincibility - 1);
            }
            for (UUID currRemove : toRemove) damageCD.remove(currRemove);

            // get list of entities that could get hit
            Set<HitEntityInfo> hitCandidates = HitEntityInfo.getEntitiesHit(
                    this.world,
                    new Vec3D(locX, locY, locZ),
                    futureLoc,
                    this.projectileSize,
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
        if (trailColor != null && this.ticksLived > 0)
            GenericHelper.handleParticleLine(velocity, bukkitEntity.getLocation(),
                    new GenericHelper.ParticleLineOptions()
                            .setLength(velocity.length())
                            .setWidth(projectileSize * 2)
                            .setTicksLinger(trailLingerTime)
                            .setParticleColor(trailColor));
        // set position and velocity info
        setPosition(futureLoc.x, futureLoc.y, futureLoc.z);
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
