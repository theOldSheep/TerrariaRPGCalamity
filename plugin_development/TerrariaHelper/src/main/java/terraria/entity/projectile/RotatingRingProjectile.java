package terraria.entity.projectile;


import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;



public class RotatingRingProjectile extends GenericProjectile {
    // Enum for rotation direction
    public enum RotationDirection {
        CLOCKWISE,
        COUNTER_CLOCKWISE;

        // Maps a boolean value to a RotationDirection
        public static RotationDirection fromBoolean(boolean clockwise) {
            return clockwise ? CLOCKWISE : COUNTER_CLOCKWISE;
        }
    }

    // Properties of the ring
    public static class RingProperties {
        private final Location centerLocation;
        private final RotationDirection rotationDirection;
        private final double angleChange;
        private final double radiusMultiplier;
        private Vector forwardDirection;
        private Vector rotationAxis;

        // Builder pattern for creating RingProperties
        private RingProperties(Builder builder) {
            this.centerLocation = builder.centerLocation;
            this.rotationDirection = builder.rotationDirection;
            this.angleChange = builder.angleChange;
            this.radiusMultiplier = builder.radiusMultiplier;

            // Calculate the initial forward direction
            Vector directionToPlayer = MathHelper.getDirection(centerLocation, builder.player.getEyeLocation(), 1d);
            directionToPlayer.setY(0); // Make it horizontal
            if (directionToPlayer.lengthSquared() < 1e-9) {
                directionToPlayer = new Vector(1, 0, 0); // If the horizontal component is zero, set it to an arbitrary one
            } else {
                directionToPlayer.normalize();
            }
            this.forwardDirection = directionToPlayer;

            // Initialize rotationAxis as (0,1,0) and then rotate it about forwardDirection for initialRotationDegrees degrees
            this.rotationAxis = MathHelper.rotateAroundAxisDegree(new Vector(0, 1, 0), forwardDirection, builder.initialRotationDegrees);
        }

        // Builder class for RingProperties
        public static class Builder {
            private Location centerLocation;
            private RotationDirection rotationDirection;
            private double angleChange;
            private double radiusMultiplier;
            private double initialRotationDegrees;
            private Player player;

            // Set the center location of the ring
            public Builder withCenterLocation(Location centerLocation) {
                this.centerLocation = centerLocation;
                return this;
            }

            // Set the rotation direction of the ring
            public Builder withRotationDirection(RotationDirection rotationDirection) {
                this.rotationDirection = rotationDirection;
                return this;
            }

            // Set the angle change of the ring
            public Builder withAngleChange(double angleChange) {
                this.angleChange = angleChange;
                return this;
            }

            // Set the radius multiplier of the ring
            public Builder withRadiusMultiplier(double radiusMultiplier) {
                this.radiusMultiplier = radiusMultiplier;
                return this;
            }

            // Set the initial rotation degrees
            public Builder withInitialRotationDegrees(double initialRotationDegrees) {
                this.initialRotationDegrees = initialRotationDegrees;
                return this;
            }

            // Set the player
            public Builder withPlayer(Player player) {
                this.player = player;
                return this;
            }

            // Build the RingProperties instance
            public RingProperties build() {
                return new RingProperties(this);
            }
        }

        // Getters for RingProperties
        public Location getCenterLocation() {
            return centerLocation;
        }

        public RotationDirection getRotationDirection() {
            return rotationDirection;
        }

        public double getAngleChange() {
            return angleChange;
        }

        public double getRadiusMultiplier() {
            return radiusMultiplier;
        }

        public Vector getForwardDirection() {
            return forwardDirection;
        }

        public Vector getRotationAxis() {
            return rotationAxis;
        }
    }

    // Fields for the projectile
    private final Player target;
    private final RingProperties ringProperties;
    private final double initialAngle;

    // Constructor for a real projectile
    public RotatingRingProjectile(EntityHelper.ProjectileShootInfo shootInfo, Player target, RingProperties ringProperties, double initialAngle) {
        super(shootInfo);
        this.target = target;
        this.ringProperties = ringProperties;
        this.initialAngle = initialAngle;
    }

    // Tick function for the projectile
    @Override
    public void B_() {
        if (ringProperties != null) {
            // Calculate the direction to the player's eye location
            Vector newForwardDirection = MathHelper.getDirection(ringProperties.getCenterLocation(), target.getEyeLocation(), 1d);

            // Project the newForwardDirection onto the normal of the plane
            Vector projectedForwardDirection = newForwardDirection.clone()
                    .subtract( MathHelper.vectorProjection(ringProperties.getRotationAxis(), newForwardDirection) );

            // Skip redundant rotation if it is almost parallel to the plane
            if (projectedForwardDirection.lengthSquared() < 1 - 1e-9) {
                // 90 degrees change (perpendicular to the plane): use default forward direction.
                if (projectedForwardDirection.lengthSquared() < 1e-9) {
                    projectedForwardDirection = ringProperties.getForwardDirection();
                }
                else {
                    projectedForwardDirection.normalize();
                }

                // Calculate the rotation axis and angle
                Vector axis = projectedForwardDirection.getCrossProduct(newForwardDirection);
                double angle = Math.acos(projectedForwardDirection.dot(newForwardDirection) );

                // Rotate the rotationAxis and forwardDirection around the axis
                ringProperties.rotationAxis = MathHelper.rotateAroundAxisRadian(ringProperties.rotationAxis, axis, angle);
                ringProperties.forwardDirection = MathHelper.rotateAroundAxisRadian(ringProperties.forwardDirection, axis, angle);
            }

            // Calculate the offset direction
            double radius = ringProperties.getRadiusMultiplier() * this.ticksLived;
            double offsetAngle = initialAngle + (ringProperties.getRotationDirection() == RotationDirection.CLOCKWISE ? 1 : -1) * ringProperties.getAngleChange() * this.ticksLived;
            Vector offsetDirection = MathHelper.rotateAroundAxisDegree(ringProperties.getForwardDirection(), ringProperties.getRotationAxis(), offsetAngle);
            offsetDirection.multiply(radius);

            // Update the velocity
            Vector position = ringProperties.getCenterLocation().toVector().add(offsetDirection);
            Vector velocity = position.clone().subtract(bukkitEntity.getLocation().toVector());
            motX = velocity.getX();
            motY = velocity.getY();
            motZ = velocity.getZ();
        }

        // Call the superclass's B_ method
        super.B_();
    }

    // Summon a ring of projectiles
    public static void summonRingOfProjectiles(EntityHelper.ProjectileShootInfo shootInfo, Player target, RingProperties ringProperties, double initialAngle, int numProjectiles) {
        if (numProjectiles <= 0) {
            // Check if the number of projectiles is valid
            throw new IllegalArgumentException("numProjectiles must be greater than 0");
        }

        // Set up the shoot info
        shootInfo.shootLoc = ringProperties.centerLocation.clone();
        shootInfo.velocity = new Vector(0, 0, 0);

        // Calculate the angle between projectiles
        double angleBetweenProjectiles = 360d / numProjectiles;

        // Summon the projectiles
        for (int i = 0; i < numProjectiles; i++) {
            double angle = initialAngle + i * angleBetweenProjectiles;

            new RotatingRingProjectile(shootInfo, target, ringProperties, angle);
        }
    }
}