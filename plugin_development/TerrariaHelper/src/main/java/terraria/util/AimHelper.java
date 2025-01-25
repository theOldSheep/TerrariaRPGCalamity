package terraria.util;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;

public class AimHelper {
    public static class AimHelperOptions {
        double projectileGravity = 0d, projectileSpeed = 0d, projectileSpeedMax = 99d, projectileSpeedMulti = 1d,
                intensity = 1d, randomOffsetRadius = 0d, ticksTotal = 0, ticksMonsterExtra = 0;
        boolean useAcceleration = false, useTickOrSpeedEstimation = false;
        int epoch = 5, noGravityTicks = 5;
        Vector accelerationOffset = new Vector();
        // constructor with default values
        public AimHelperOptions() {
            super();
        }
        public AimHelperOptions(String projectileType) {
            super();
            ConfigurationSection projConfSec = TerrariaHelper.projectileConfig.getConfigurationSection(projectileType);
            if (projConfSec == null) {
                setProjectileSpeedMax(99d);
                setProjectileSpeedMulti(1d);
                setProjectileGravity(0.05);
                setNoGravityTicks(5);
            }
            else {
                setProjectileSpeedMax(projConfSec.getDouble("maxSpeed", 99d));
                setProjectileSpeedMulti(projConfSec.getDouble("speedMultiPerTick", 1d));
                setProjectileGravity(projConfSec.getDouble("gravity", 0.05));
                setNoGravityTicks(projConfSec.getInt("noGravityTicks", 5));
            }
        }
        public AimHelperOptions setTicksTotal(double ticksTotal) {
            this.ticksTotal = ticksTotal;
            return this;
        }
        public AimHelperOptions setTicksMonsterExtra(double ticksMonsterExtra) {
            this.ticksMonsterExtra = ticksMonsterExtra;
            return this;
        }
        public AimHelperOptions setProjectileGravity(double projectileGravity) {
            this.projectileGravity = projectileGravity;
            return this;
        }
        public AimHelperOptions setProjectileSpeed(double projectileSpeed) {
            this.projectileSpeed = projectileSpeed;
            return this;
        }
        public AimHelperOptions setProjectileSpeedMax(double projectileSpeedMax) {
            this.projectileSpeedMax = projectileSpeedMax;
            return this;
        }
        public AimHelperOptions setProjectileSpeedMulti(double projectileSpeedMulti) {
            this.projectileSpeedMulti = projectileSpeedMulti;
            return this;
        }
        public AimHelperOptions setIntensity(double intensity) {
            this.intensity = intensity;
            return this;
        }
        public AimHelperOptions setRandomOffsetRadius(double randomOffsetRadius) {
            this.randomOffsetRadius = randomOffsetRadius;
            return this;
        }
        public AimHelperOptions setEpoch(int epoch) {
            this.epoch = epoch;
            return this;
        }
        public AimHelperOptions setNoGravityTicks(int noGravityTicks) {
            this.noGravityTicks = noGravityTicks;
            return this;
        }
        public AimHelperOptions setAccelerationMode(boolean useAcceleration) {
            this.useAcceleration = useAcceleration;
            return this;
        }
        public AimHelperOptions setAimMode(boolean useTickOrSpeedEstimation) {
            this.useTickOrSpeedEstimation = useTickOrSpeedEstimation;
            return this;
        }
        public AimHelperOptions setAccOffset(Vector accelerationOffset) {
            this.accelerationOffset = accelerationOffset;
            return this;
        }
        public AimHelperOptions addAccOffset(Vector accelerationOffset) {
            this.accelerationOffset.add(accelerationOffset);
            return this;
        }
        public AimHelperOptions subtractAccOffset(Vector accelerationOffset) {
            this.accelerationOffset.subtract(accelerationOffset);
            return this;
        }
    }

    public static Location helperAimEntity(Location shootLoc, Entity target, AimHelperOptions aimHelperOption) {
        shootLoc.checkFinite();
        assert aimHelperOption.projectileSpeed != 0d;
        Vector enemyVel, enemyAcc;
        // get target velocity and acceleration
        Location targetLoc;
        if (target instanceof Player) {
            Player targetPly = (Player) target;
            targetLoc = PlayerHelper.getAccurateLocation(targetPly);

            enemyVel = (Vector) EntityHelper.getMetadata(target, EntityHelper.MetadataName.PLAYER_LAST_VELOCITY_ACTUAL).value();
            enemyAcc = (Vector) EntityHelper.getMetadata(target, EntityHelper.MetadataName.PLAYER_ACCELERATION).value();
        }
        else {
            targetLoc = target.getLocation();
            MetadataValue currVelMetadata = EntityHelper.getMetadata(target, EntityHelper.MetadataName.ENTITY_CURRENT_VELOCITY);
            MetadataValue lastVelMetadata = EntityHelper.getMetadata(target, EntityHelper.MetadataName.ENTITY_LAST_VELOCITY);
            // placeholder; use the saved current velocity for teleportation-based AI compatibility below.
            enemyVel = target.getVelocity();
            // if any of the two are not yet recorded, assume acceleration is none.
            if (currVelMetadata == null || lastVelMetadata == null) {
                enemyAcc = new Vector();
            }
            // otherwise, calculate acceleration.
            else {
                enemyVel = (Vector) currVelMetadata.value();
                Vector lastSavedVel = (Vector) lastVelMetadata.value();
                enemyAcc = enemyVel.clone().subtract(lastSavedVel);
            }
            // for enemies with gravity, upper cap the acceleration at y = -0.08
            if (target.hasGravity())
                enemyAcc.setY( Math.min(enemyAcc.getY(), -0.08) );
        }
        // offset enemyAcc
        enemyAcc.add(aimHelperOption.accelerationOffset);

        // setup target location
        Location predictedLoc;
        // aim at the middle of the entity
        if (target instanceof LivingEntity) {
            EntityLiving targetNMS = ((CraftLivingEntity) target).getHandle();
            AxisAlignedBB boundingBox = targetNMS.getBoundingBox();
            targetLoc.add(0, (boundingBox.e - boundingBox.b) / 2, 0);
        }
        // a placeholder, so that the function does not report an error
        predictedLoc = targetLoc.clone();
        // "hyper-params" for prediction; note that ticks offset is roughly estimated before entering the loop.
        boolean checkBlockColl;
        Entity targetMount = EntityMovementHelper.getMount(target);
        if (targetMount == null)
            checkBlockColl = ! ((CraftEntity) target).getHandle().noclip;
        else {
            checkBlockColl = !(targetMount instanceof Minecart);
        }
        double predictionIntensity = aimHelperOption.intensity;
        double ticksElapse = targetLoc.distance(shootLoc) / aimHelperOption.projectileSpeed, lastTicksOffset;
        // approximate the velocity to use with epochs requested
        for (int currEpoch = 0; currEpoch < aimHelperOption.epoch; currEpoch ++) {
            // calculate the predicted enemy location
            double ticksMonsterMovement = ticksElapse + aimHelperOption.ticksMonsterExtra;
            {
                predictedLoc = targetLoc.clone();
                // account for displacement that are caused by velocity
                predictedLoc.add(enemyVel.clone().multiply(ticksMonsterMovement * predictionIntensity));
                // account for displacement that are caused by acceleration, IF NEEDED
                // first tick acc. is in effect for (n-1) times, second for (n-2) and so on
                // in total = sum(1, 2, ..., n-2, n-1) = n(n-1) / 2
                if (aimHelperOption.useAcceleration)
                    predictedLoc.add(enemyAcc.clone().multiply(ticksMonsterMovement * (ticksMonsterMovement - 1) * predictionIntensity / 2d));
                // before handling gravity, make sure entities that clip with block do not go through blocks
                // note that the loop is done with the "foot" position
                if ( checkBlockColl ) {
                    // loopBeginLoc is the position from where the rough entity movement check STARTS in the current loop call
                    Location loopBeginLoc = target.getLocation().add(0, 1e-5, 0);
                    Vector locOffset = targetLoc.clone().subtract(loopBeginLoc).toVector();
                    // the loop end loc should be the "foot" of entity, because they are effected by gravity.
                    Location loopEndLoc = predictedLoc.clone().subtract(locOffset);
                    World loopWorld = loopBeginLoc.getWorld();
                    // check for block collision for max of 3 times (this is meant to be a rough estimation after all).
                    for (int blockCheckIdx = 0; blockCheckIdx < 3; blockCheckIdx ++) {
                        // terminate if the initial loc and final loc are really close to each other
                        if (loopBeginLoc.distanceSquared(loopEndLoc) < 1e-5)
                            break;
                        MovingObjectPosition blockCollInfo = HitEntityInfo.rayTraceBlocks(loopWorld, loopBeginLoc.toVector(), loopEndLoc.toVector());
                        // no block hit: terminate loop
                        if (blockCollInfo == null)
                            break;
                        // block hit: handle velocity change
                        loopBeginLoc = MathHelper.toBukkitVector(blockCollInfo.pos).toLocation(loopWorld);
                        Vector updatedMoveDir = loopEndLoc.subtract(loopBeginLoc).toVector();
                        switch (blockCollInfo.direction) {
                            case UP:
                            case DOWN:
                                updatedMoveDir.setY(0);
                                break;
                            case EAST:
                            case WEST:
                                updatedMoveDir.setX(0);
                                break;
                            default:
                                updatedMoveDir.setZ(0);
                                break;
                        }
                        // update the expected new location based on new velocity
                        loopEndLoc = loopBeginLoc.clone().add(updatedMoveDir);
                    }
                    // account for the foot loc offset
                    predictedLoc = loopEndLoc.add(locOffset);
                }
                // projectile gravity, it is equivalent to target acceleration, for the ease of computation
                // it is not simply acceleration - it only takes effect after some time, usually 5 ticks
                if (ticksElapse >= aimHelperOption.noGravityTicks) {
                    predictedLoc.add(new Vector(0,
                            (ticksElapse - aimHelperOption.noGravityTicks + 1) * (ticksElapse - aimHelperOption.noGravityTicks + 2)
                                    * aimHelperOption.projectileGravity / 2d, 0));
                }
                // random offset
                {
                    double randomOffset = aimHelperOption.randomOffsetRadius;
                    if (randomOffset > 1e-5) {
                        double randomOffsetHalved = randomOffset / 2;
                        predictedLoc.add(Math.random() * randomOffset - randomOffsetHalved,
                                Math.random() * randomOffset - randomOffsetHalved,
                                Math.random() * randomOffset - randomOffsetHalved);
                    }
                }
            }

            // then, update actual ticks needed to reach the designated point
            lastTicksOffset = ticksElapse;
            if (aimHelperOption.useTickOrSpeedEstimation)
                ticksElapse = aimHelperOption.ticksTotal;
            else {
                double distance = predictedLoc.distance(shootLoc), currSpd = aimHelperOption.projectileSpeed;
                // if speed does not change over time, use that speed
                if (aimHelperOption.projectileSpeedMulti == 1) {
                    ticksElapse = distance / currSpd;
                }
                // if a speed multiplier is in place, account for it.
                else {
                    ticksElapse = 0;
                    double distTraveled = 0;
                    // prevent possible inf loop IF the projectile has decaying speed
                    while (distTraveled < distance && (aimHelperOption.projectileSpeedMulti >= 1d || ticksElapse < 20)) {
                        ticksElapse ++;
                        distTraveled += currSpd;
                        currSpd *= aimHelperOption.projectileSpeedMulti;
                        // after reaching the max speed, do not bother using the loop
                        if (currSpd > aimHelperOption.projectileSpeedMax) {
                            ticksElapse += (distance - distTraveled) / aimHelperOption.projectileSpeedMax;
                            break;
                        }
                    }
                }
                // account for at most 3 seconds
                ticksElapse = Math.min( Math.floor(ticksElapse),  60  );
            }

            // end the loop early if the last tick offset agrees with the current
            if (lastTicksOffset == ticksElapse)
                break;
        }
        return predictedLoc;
    }

    public static Location helperAimEntity(Entity source, Entity target, AimHelperOptions aimHelperOption) {
        Location shootLoc = source.getLocation();
        if (source instanceof LivingEntity)
            shootLoc = ((LivingEntity) source).getEyeLocation();
        return helperAimEntity(shootLoc, target, aimHelperOption);
    }
}
