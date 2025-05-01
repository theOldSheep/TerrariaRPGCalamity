package terraria.util;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.Setting;

import java.util.Set;

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


    // smart aiming: helps the player to aim with a non-homing weapon in a 3-dimension world

    /**
     * Gets the player's aiming direction, accounting for aim helper.
     * @param ply player
     * @param startShootLoc shoot location
     * @param projectileSpeed projectile speed
     * @param projectileType projectile type
     * @param tickOffsetOrSpeed whether to use a fixed tick offset or projectile speed
     * @param tickOffset the fixed tick offset, if applicable
     * @return the aim direction
     */
    public static Vector getPlayerAimDir(Player ply, Location startShootLoc, double projectileSpeed, String projectileType,
                                         boolean tickOffsetOrSpeed, int tickOffset) {
        // default to acceleration-aim mode
        AimHelper.AimHelperOptions aimHelperOptions = new AimHelper.AimHelperOptions(projectileType)
                .setAccelerationMode(Setting.getOptionBool(ply, Setting.Options.AIM_HELPER_ACCELERATION))
                .setAimMode(tickOffsetOrSpeed)
                .setTicksTotal(tickOffset)
                .setProjectileSpeed(projectileSpeed);
        // get targeted location
        Location targetLoc = getPlayerTargetLoc(new PlyTargetLocInfo(ply, aimHelperOptions, true));
        // send the new direction
        Vector dir = targetLoc.subtract(startShootLoc).toVector();
        if (dir.lengthSquared() < 1e-5)
            dir = new Vector(1, 0, 0);
        return dir;
    }

    /**
     * trace dist: distance to trace into a block/entity
     * enlarge radius: max error distance allowed to target an entity that is not directly in line of sight
     * strict mode: do not target critters and entities that are strictly speaking, non-enemy
     */
    public static class PlyTargetLocInfo {
        Player ply = null;
        AimHelper.AimHelperOptions aimHelperInfo = null;
        double traceDist, entityEnlargeRadius, blockDist = 0;
        boolean strictMode = true;
        // constructors
        public PlyTargetLocInfo(Player ply, AimHelper.AimHelperOptions aimHelperInfo, boolean strictMode) {
            this.ply = ply;
            initPlyPreferences(ply);
            this.aimHelperInfo = aimHelperInfo;
            this.strictMode = strictMode;
        }
        public PlyTargetLocInfo(Player ply, double blockDist, AimHelper.AimHelperOptions aimHelperInfo, boolean strictMode) {
            this.ply = ply;
            initPlyPreferences(ply);
            this.blockDist = blockDist;
            this.aimHelperInfo = aimHelperInfo;
            this.strictMode = strictMode;
        }
        void initPlyPreferences(Player ply) {
            this.traceDist = Setting.getOptionDouble(ply, Setting.Options.AIM_HELPER_DISTANCE);
            this.entityEnlargeRadius = Setting.getOptionDouble(ply, Setting.Options.AIM_HELPER_RADIUS);
        }
        // setters
        public PlyTargetLocInfo setPly(Player ply) {
            this.ply = ply;
            return this;
        }
        public PlyTargetLocInfo setAimHelper(AimHelper.AimHelperOptions aimHelperInfo) {
            this.aimHelperInfo = aimHelperInfo;
            return this;
        }
        public PlyTargetLocInfo setTraceDist(double traceDist) {
            this.traceDist = traceDist;
            return this;
        }
        public PlyTargetLocInfo setEntityEnlargeRadius(double entityEnlargeRadius) {
            this.entityEnlargeRadius = entityEnlargeRadius;
            return this;
        }
        public PlyTargetLocInfo setBlockDist(double blockDist) {
            this.blockDist = blockDist;
            return this;
        }
        public PlyTargetLocInfo setStrictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }
    }

    /**
     * Gets the player's targeted location with the specified target location info
     * @param targetLocInfo the target location info, containing context & settings such as aim helper
     * @return the targeted location
     */
    public static Location getPlayerTargetLoc(PlyTargetLocInfo targetLocInfo) {
        Player ply = targetLocInfo.ply;
        AimHelper.AimHelperOptions aimHelperInfo = targetLocInfo.aimHelperInfo;
        double traceDist = targetLocInfo.traceDist;
        double blockDist = targetLocInfo.blockDist;
        double entityEnlargeRadius = targetLocInfo.entityEnlargeRadius;
        boolean strictMode = targetLocInfo.strictMode;

        Vector lookDir = ply.getEyeLocation().getDirection();
        World plyWorld = ply.getWorld();
        // init target location
        Location targetLoc = ply.getEyeLocation().add(lookDir.clone().multiply(traceDist));

        Vector eyeLoc = ply.getEyeLocation().toVector();
        Vector endLoc = eyeLoc.clone().add(lookDir.clone().multiply(traceDist));
        // the block the player is looking at, if near enough
        {
            MovingObjectPosition rayTraceResult = HitEntityInfo.rayTraceBlocks(
                    plyWorld,
                    eyeLoc.clone(),
                    endLoc);
            if (rayTraceResult != null) {
                endLoc = MathHelper.toBukkitVector(rayTraceResult.pos);
                if (blockDist > 0d) {
                    Vector blockDistOffset = endLoc.clone().subtract(ply.getEyeLocation().toVector());
                    blockDistOffset.normalize().multiply(blockDist);
                    endLoc.subtract(blockDistOffset);
                }
                targetLoc = endLoc.toLocation(plyWorld);
            }
        }

        // the enemy the player is looking at, if applicable
        Vector traceStart = eyeLoc.clone();
        Vector traceEnd = endLoc.clone();
        Set<HitEntityInfo> hits = HitEntityInfo.getEntitiesHit(
                plyWorld, traceStart, traceEnd,
                entityEnlargeRadius,
                (net.minecraft.server.v1_12_R1.Entity target) ->
                        DamageHelper.checkCanDamage(ply, target.getBukkitEntity(), strictMode) &&
                                ply.hasLineOfSight(target.getBukkitEntity()));
        // find the entity closest to the cursor
        Entity hitEntity = null;
        double shortestEnlargeDistSqr = 1e9, shortestDistSqr = 1e9;
        Location rayInfo = ply.getLocation();
        for (HitEntityInfo hitInfo : hits) {
            // prioritize entities requiring less enlargement
            double currEnlargementSqr = refineRayDistSqrToAABB(
                    rayInfo, hitInfo.getHitEntity().getBoundingBox(),
                    hitInfo.getHitLocation().pos);
            if (currEnlargementSqr > shortestEnlargeDistSqr)
                continue;

            Entity currEntity = hitInfo.getHitEntity().getBukkitEntity();
            double currDistSqr = eyeLoc.distanceSquared( currEntity.getLocation().toVector() );
            // resolve all entities with 0d enlargement (no precision issue); return the closest
            if (currEnlargementSqr == shortestEnlargeDistSqr && currDistSqr > shortestDistSqr) {
                continue;
            }
            shortestEnlargeDistSqr = currEnlargementSqr;
            shortestDistSqr = currDistSqr;
            hitEntity = currEntity;
        }

        // get the appropriate aim location if roughly looking at an enemy
        if (hitEntity != null) {
            targetLoc = AimHelper.helperAimEntity(ply, hitEntity, aimHelperInfo);
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TARGET_LOC_CACHE, hitEntity);
        }
        // add random offset to "looking location", consistent with aim helper's setting, if entity is not found
        else {
            double randomOffset = aimHelperInfo.randomOffsetRadius, randomOffsetHalved = randomOffset / 2d;
            targetLoc.add(Math.random() * randomOffset - randomOffsetHalved,
                    Math.random() * randomOffset - randomOffsetHalved,
                    Math.random() * randomOffset - randomOffsetHalved);
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TARGET_LOC_CACHE, targetLoc.clone());
        }
        return targetLoc;
    }

    /**
     * Get the player's cached target location (up to last aimed entity);
     * DOES NOT GENERATE a new target location when needed; this would be strictly according to the last aimed position.
     * @param ply the player
     * @param aimHelperInfo the aim info used to aim at the entity
     * @return the cached target location
     */
    public static Location getPlayerCachedTargetLoc(Player ply, AimHelper.AimHelperOptions aimHelperInfo) {
        MetadataValue metadataValue = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TARGET_LOC_CACHE);
        if (metadataValue == null)
            return ply.getEyeLocation();
        // if a location is cached
        if (metadataValue.value() instanceof Location)
            return ((Location) metadataValue.value()).clone();
        // otherwise, this must be an entity cached
        Entity targetEntity = (Entity) metadataValue.value();
        // if the entity is below bedrock layer (usually boss AI phase that should not be targeted)
        if (targetEntity.getLocation().getY() < 0d)
            return ply.getEyeLocation();
        return AimHelper.helperAimEntity(ply, targetEntity, aimHelperInfo);
    }

    /**
     * Refines the collision info derived from the expanded AABB and
     * returns the player's ray of vision's min distance squared from the original AABB
     * @param rayInfo the ray info, i.e. starting location with the correct direction
     * @param bb the original AxisAlignedBB
     * @param coll the collision derived from expanded bb
     * @return the ray's min distance squared from the original AABB
     */
    public static double refineRayDistSqrToAABB(Location rayInfo, AxisAlignedBB bb, Vec3D coll) {
        double[][] xyz = {
                {bb.a, bb.d},
                {bb.b, bb.e},
                {bb.c, bb.f}, };
        // case 1 - if the ray would bang on with the original AABB
        Vector dir = rayInfo.getDirection();
        double[] collLoc = {coll.x, coll.y, coll.z};
        double[] dirDeltas = {dir.getX(), dir.getY(), dir.getZ()};
        for (int i = 0; i < 3; i ++) {
            // no delta value in this direction, skip
            if (Math.abs(dirDeltas[i]) < 1e-5) continue;
            // try the two faces of the AABB on this direction
            for (double targetAxisVal : xyz[i]) {
                double step = (targetAxisVal - collLoc[i]) / dirDeltas[i];
                // see if there is "collision position" on the original bb
                boolean isOnFace = true;
                for (int j = 0; j < 3; j++) {
                    if (i == j) continue;
                    double axisLoc = collLoc[j] + dirDeltas[j] * step;
                    // in this axis component, start <= curr <= end; otherwise this is invalid
                    if (axisLoc < xyz[j][0] - 1e-5 || xyz[j][1] + 1e-5 < axisLoc) {
                        isOnFace = false;
                        break;
                    }
                }
                // on AABB's face - distance is 0
                if (isOnFace) return 0d;
            }
        }
        // case 2 - the ray would not collide with the original AABB, its distance can be approximated by min(vertex's distance to the ray)
        double minDistSqr = 1e9;
        for (double x : xyz[0]) {
            for (double y : xyz[1]) {
                for (double z : xyz[2]) {
                    // offset = ray start -> vertex
                    Vector offset = new Vector(x, y, z).subtract(rayInfo.toVector());
                    Vector dirComponent = MathHelper.vectorProjection(dir, offset);
                    // if offset is in the same direction as the line of sight ray, remove it
                    if (dirComponent.dot(dir) > 0) {
                        offset.subtract(dirComponent);
                    }
                    minDistSqr = Math.min(minDistSqr, offset.lengthSquared());
                }
            }
        }
        return minDistSqr;
    }
}
