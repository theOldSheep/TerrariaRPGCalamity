package terraria.util;

import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;

import java.util.List;

public class EntityMovementHelper {
    public static class WormSegmentMovementOptions {
        // "straightening" multiplier: last segment - next segment
        // note that this vector "smoothens and straightens" the worm, but it may cause the segments to recoil and coil up again.
        double straighteningMultiplier = 0;
        // "following" multiplier: last segment - current segment
        // note that this vector let the segment strictly follows the last.
        double followingMultiplier = 1;
        double followDistance = 1;
        boolean velocityOrTeleport = true;
        public WormSegmentMovementOptions setStraighteningMultiplier(double straighteningMultiplier) {
            this.straighteningMultiplier = straighteningMultiplier;
            return this;
        }
        public WormSegmentMovementOptions setFollowingMultiplier(double followingMultiplier) {
            this.followingMultiplier = followingMultiplier;
            return this;
        }
        public WormSegmentMovementOptions setFollowDistance(double followDistance) {
            this.followDistance = followDistance;
            return this;
        }
        public WormSegmentMovementOptions setVelocityOrTeleport(boolean velocityOrTeleport) {
            this.velocityOrTeleport = velocityOrTeleport;
            return this;
        }
    }
    public static Entity getMount(Entity entity) {
        if (entity instanceof Player)
            return PlayerHelper.getMount((Player) entity);
        return entity.getVehicle();
    }

    // for player, use either this getVelocity or the getPlayerVelocity in PlayerHelper to get the underlying velocity without liquid slow etc.
    public static Vector getRawVelocity(Entity entity) {
        if (entity instanceof Player)
            return PlayerHelper.getPlayerRawVelocity((Player) entity);
        return entity.getVelocity();
    }

    // for player, use this setVelocity instead of vanilla one
    public static void setVelocity(Entity entity, Vector spd) {
        // handle unreasonable magnitude (> 100)
        if (spd.lengthSquared() > 1e5)
            spd.zero();
        if (entity instanceof Player) {
            MetadataHelper.setMetadata(entity, MetadataHelper.MetadataName.PLAYER_VELOCITY_INTERNAL, spd);
            MetadataValue mtv = MetadataHelper.getMetadata(entity, MetadataHelper.MetadataName.PLAYER_VELOCITY_MULTI);
            spd = spd.clone();
            if (mtv != null)
                spd.multiply(mtv.asDouble());
        }
        entity.setVelocity(spd);
    }

    // Teleportation would update last location; this function undoes this side effect.
    // This is important for bosses' aim helper recording mechanism to work as intended.
    public static void movementTP(Entity entity, Location destination) {
        net.minecraft.server.v1_12_R1.Entity entityNMS = ((CraftEntity) entity).getHandle();
        double lastX = entityNMS.lastX, lastY = entityNMS.lastY, lastZ = entityNMS.lastZ;

        entity.teleport(destination); // Teleportation

        entityNMS.lastX = lastX;
        entityNMS.lastY = lastY;
        entityNMS.lastZ = lastZ;
    }

    public static void handleSegmentsFollow(List<LivingEntity> segments, WormSegmentMovementOptions moveOption) {
        handleSegmentsFollow(segments, moveOption, 0);
    }

    public static void handleSegmentsFollow(List<LivingEntity> segments, WormSegmentMovementOptions moveOption, int startIndex) {
        handleSegmentsFollow(segments, moveOption, startIndex, segments.size());
    }

    public static void handleSegmentsFollow(List<LivingEntity> segments, WormSegmentMovementOptions moveOption,
                                            int startIndex, int endIndex) {
        for (int i = startIndex + 1; i < endIndex; i++) {
            LivingEntity segmentCurrent = segments.get(i);
            if (!segmentCurrent.isValid()) { // Check if segment is valid (alive and healthy)
                return;
            }

            LivingEntity segmentLast = segments.get(i - 1); // Get the segment in front
            LivingEntity segmentNext = null; // Initialize segmentNext for possible later use
            Vector segDVec; // Direction vector is guaranteed to be initialized in the if-else branch

            // Check for next segment or handle as tail
            if (i + 1 < endIndex && segments.get(i + 1).isValid()) {
                segmentNext = segments.get(i + 1); // Get the segment behind
                segDVec = MathHelper.getDirection(segmentNext.getLocation(), segmentLast.getLocation(), 1.0);  // Point towards the previous segment
            } else {
                // Treat as tail segment - just follow the segment in front
                segDVec = MathHelper.getDirection(segmentCurrent.getLocation(), segmentLast.getLocation(), 1.0); // Point towards the previous segment
            }

            Vector followDir = MathHelper.getDirection(segmentCurrent.getLocation(), segmentLast.getLocation(), 1.0);
            Vector dVec = segDVec.multiply(moveOption.straighteningMultiplier) // Straightening
                    .add(followDir.multiply(moveOption.followingMultiplier)); // Following

            if (dVec.lengthSquared() > 1e-9) { // Check if movement is significant
                dVec.normalize().multiply(moveOption.followDistance);
                Location targetLoc = segmentLast.getLocation().subtract(dVec);

                if (moveOption.velocityOrTeleport) {
                    Vector velocity = targetLoc.subtract(segmentCurrent.getLocation()).toVector();
                    segmentCurrent.setVelocity(velocity); // Smooth movement
                } else {
                    movementTP(segmentCurrent, targetLoc); // Teleportation
                    segmentCurrent.setVelocity(new Vector());
                }

                MetadataHelper.setMetadata(segmentCurrent, "yaw", (float) MathHelper.getVectorYaw( dVec ));
                MetadataHelper.setMetadata(segmentCurrent, "pitch", (float) MathHelper.getVectorPitch( dVec ));
            }
        }
    }
}
