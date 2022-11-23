package terraria.entity;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import terraria.event.TerrariaProjectileHitEvent;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;
import terraria.util.YmlHelper;

import java.util.*;

public class TerrariaArrowProjectile extends EntityArrow {
    public static final YmlHelper.YmlSection projectileConfig = YmlHelper.getFile("plugins/Data/projectiles.yml");
    // projectile info
    public String projectileType, blockHitAction = "die", trailColor = null;
    public int bounce = 0, enemyInvincibilityFrame = 5, liveTime = 200, noGravityTicks = 15, trailLingerTime = 10, penetration = 0;
    public double autoTraceAbility = 4, autoTraceRadius = 12, blastRadius = 1.5, bounceVelocityMulti = 1, gravity = 0.05, maxSpeed = 2, projectileSize = 0.125, speedMultiPerTick = 1;
    public boolean autoTrace = false, blastDamageShooter = false, bouncePenetrationBonded = false, canBeReflected = true, isGrenade = false, slowedByWater = true;

    public double speed;
    public HashMap<UUID, Integer> damageCD;
    public org.bukkit.entity.Projectile bukkitEntity;
    public Entity autoTraceTarget = null;

    @Override
    protected ItemStack j() {
        return new ItemStack(Items.ARROW);
    }

    private void setupProjectileProperties() {
        ConfigurationSection section = projectileConfig.getConfigurationSection(projectileType);
        if (section != null) {
            this.bounce = section.getInt("bounce", this.bounce);
            this.enemyInvincibilityFrame = section.getInt("enemyInvincibilityFrame", this.enemyInvincibilityFrame);
            this.liveTime = section.getInt("liveTime", this.liveTime);
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
            this.gravity = section.getDouble("gravity", this.gravity);
            this.maxSpeed = section.getDouble("maxSpeed", this.maxSpeed);
            this.projectileSize = section.getDouble("projectileSize", this.projectileSize);
            this.speedMultiPerTick = section.getDouble("speedMultiPerTick", this.speedMultiPerTick);

            this.autoTrace = section.getBoolean("autoTrace", this.autoTrace);
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

    // setup properties of the specific type, excluding its item displayed
    public void setProperties(String type) {
        projectileType = type;
        setupProjectileProperties();

        setCustomName(type);
        if (isGrenade) addScoreboardTag("isGrenade");
        else removeScoreboardTag("isGrenade");
        if (blastDamageShooter) addScoreboardTag("blastDamageShooter");
        else removeScoreboardTag("blastDamageShooter");
    }
    // setup properties of the specific type, including its item displayed
    public void setType(String type) {
        setProperties(type);
    }





    // constructor
    public TerrariaArrowProjectile(org.bukkit.Location loc, Vector velocity, String projectileType) {
        super(((CraftWorld) loc.getWorld()).getHandle(), loc.getX(), loc.getY(), loc.getZ());
        this.fromPlayer = PickupStatus.DISALLOWED;
        this.motX = velocity.getX();
        this.motY = velocity.getY();
        this.motZ = velocity.getZ();
        this.projectileType = projectileType;
        setProperties(projectileType);
        bukkitEntity = (org.bukkit.entity.Projectile) getBukkitEntity();
        this.speed = velocity.length();
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
        TerrariaProjectileHitEvent.callProjectileHitEvent(this);
        super.die();
    }
    // tick
    @Override
    public void B_() {
        // start timing
        this.world.methodProfiler.a("entityBaseTick");

        // ticking from Entity.class
        this.I = this.J;
        this.lastX = this.locX;
        this.lastY = this.locY;
        this.lastZ = this.locZ;
        this.lastPitch = this.pitch;
        this.lastYaw = this.yaw;
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
            // get the block it will cram into, then set the final location after ticking
            double spdX = this.motX * speedMulti, spdY = this.motY * speedMulti, spdZ = this.motZ * speedMulti;
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
                            if (dist > 0.001) {
                                travelled.multiply((dist - 0.001) / dist);
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
                                if (bouncePenetrationBonded) penetration--;
                                switch (movingobjectposition.direction) {
                                    case UP:
                                    case DOWN:
                                        velocity.setY(velocity.getY() * -1);
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
                                velocity.multiply(bounceVelocityMulti);
                            } else {
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
                                travelled.multiply((dist + 0.001) / dist);
                            futureLoc = new Vec3D(this.locX + travelled.getX(), this.locY + travelled.getY(), this.locZ + travelled.getZ());
                            break;
                        default:
                            futureLoc = new Vec3D(movingobjectposition.pos.x, movingobjectposition.pos.y, movingobjectposition.pos.z);
                    }
                }
            }

            // update yaw and pitch
            {
                float horizontalSpeed = MathHelper.sqrt(this.motX * this.motX + this.motZ * this.motZ);
                this.yaw = (float) (MathHelper.c(this.motX, this.motZ) * 57.2957763671875D);
                for (this.pitch = (float) (MathHelper.c(this.motY, horizontalSpeed) * 57.2957763671875D);
                     this.pitch - this.lastPitch < -180.0F;
                     this.lastPitch -= 360.0F)
                    ;
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

            // optimize auto trace target
            if (autoTrace) {
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
                            MovingObjectPosition blockHitPos = this.world.rayTrace(initialLoc, traceEnd);
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

            // tweak speed: handle auto trace or tweak by per tick speed decay and gravity
            if (autoTrace && autoTraceTarget != null) {
                Vector acceleration;
                if (autoTraceTarget instanceof EntityLiving)
                    acceleration = ((LivingEntity) autoTraceTarget.getBukkitEntity()).getEyeLocation().subtract(this.locX, this.locY, this.locZ).toVector();
                else
                    acceleration = autoTraceTarget.getBukkitEntity().getLocation().add(0, 1.5, 0).subtract(this.locX, this.locY, this.locZ).toVector();

                double accMagnitude = acceleration.length(), velMagnitude = velocity.length();
                velocity.multiply(Math.sqrt(Math.min(accMagnitude, autoTraceAbility)) / velMagnitude);
                acceleration.multiply(autoTraceAbility / accMagnitude);

                velocity.add(acceleration);

                if (velocity.lengthSquared() > 0)
                    velocity.normalize().multiply(speed);
            } else {
                // gravity
                if (this.ticksLived >= noGravityTicks && gravity > 0) {
                    // prevent client side glitch
                    if (!inGround) this.setNoGravity(false);
                    velocity.subtract(new Vector(0, gravity, 0));
                }
                // regulate velocity
                if (velocity.lengthSquared() > maxSpeed * maxSpeed)
                    terraria.util.MathHelper.setVectorLength(velocity, maxSpeed);
            }
        }

        // handle projectile hit
        {
            // update invincibility map
            ArrayList<UUID> toRemove = new ArrayList<>(5);
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
                    new Vec3D(locX + velocity.getX(), locY + velocity.getY(), locZ + velocity.getZ()),
                    this.projectileSize,
                    (Entity toCheck) -> {
                        if (this.shooter != null && this.shooter == toCheck) return false;
                        return checkCanHit(toCheck);
                    });

            // hit entities
            for (HitEntityInfo toHit : hitCandidates) {
                Entity hitEntity = toHit.getHitEntity();
                MovingObjectPosition hitInfo = new MovingObjectPosition(hitEntity);
                if (TerrariaProjectileHitEvent.callProjectileHitEvent(this, hitInfo).isCancelled()) continue;
                // we use the former hit location because it contains the information of collision location
                hitEntity(hitEntity, toHit.getHitLocation());
                // if the projectile reached its penetration capacity, stop damaging enemies
                if (this.dead) break;
            }
        }

        // draw particle trail
        if (trailColor != null)
            GenericHelper.handleParticleLine(velocity, velocity.length(), projectileSize * 2, trailLingerTime, bukkitEntity.getLocation(), trailColor);
        // set position and velocity info
        setPosition(futureLoc.x, futureLoc.y, futureLoc.z);
        // send new velocity if:
        //     velocity changed
        //     the projectile has gravity
        // otherwise send new velocity regularly every 10 ticks
        this.velocityChanged = ticksLived % 10 == 0 ||
                velocity.distanceSquared(new Vector(this.motX, this.motY, this.motZ)) > 0.0001 ||
                !isNoGravity();

        this.motX = velocity.getX();
        this.motY = velocity.getY();
        this.motZ = velocity.getZ();

        // time out removal
        if (this.ticksLived >= liveTime) die();
        // timing
        this.world.methodProfiler.b();
    }
}
