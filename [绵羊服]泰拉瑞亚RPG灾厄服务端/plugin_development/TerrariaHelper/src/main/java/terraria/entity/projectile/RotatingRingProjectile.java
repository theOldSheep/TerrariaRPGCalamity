package terraria.entity.projectile;


import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
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
        private Location centerLocation;
        private RotationDirection rotationDirection;
        private double angleChange;
        private double initialRotationDegrees;
        private double radiusMultiplier;

        // Builder pattern for creating RingProperties
        private RingProperties(Builder builder) {
            this.centerLocation = builder.centerLocation;
            this.rotationDirection = builder.rotationDirection;
            this.angleChange = builder.angleChange;
            this.initialRotationDegrees = builder.initialRotationDegrees;
            this.radiusMultiplier = builder.radiusMultiplier;
        }

        // Builder class for RingProperties
        public static class Builder {
            private Location centerLocation;
            private RotationDirection rotationDirection;
            private double angleChange;
            private double initialRotationDegrees;
            private double radiusMultiplier;

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

            // Set the theta value of the ring
            public Builder withInitialRotationDegrees(double initialRotationDegrees) {
                this.initialRotationDegrees = initialRotationDegrees;
                return this;
            }

            // Set the radius multiplier of the ring
            public Builder withRadiusMultiplier(double radiusMultiplier) {
                this.radiusMultiplier = radiusMultiplier;
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

        public double getInitialRotationDegrees() {
            return initialRotationDegrees;
        }

        public double getRadiusMultiplier() {
            return radiusMultiplier;
        }
    }

    // Default rotation axis
    private static Vector getDefaultRotationAxis() {
        return new Vector(0, 0, 1);
    }

    // Fields for the projectile
    final Player target;
    final RingProperties ringProperties;
    final double initialAngle;

    // Constructor for a dummy projectile
    public RotatingRingProjectile(World world) {
        super(world);
        target = null;
        ringProperties = null;
        this.initialAngle = 0.0;
        die();
    }

    // Constructor for a real projectile
    public RotatingRingProjectile(EntityHelper.ProjectileShootInfo shootInfo, Player target, RingProperties ringProperties, double initialAngle) {
        super(shootInfo);
        this.target = target;
        this.ringProperties = ringProperties;
        this.initialAngle = initialAngle;
    }


    // Field to store the current closest axis
    private Vector currentClosestAxis;

    // Helper method to get the current closest axis
    private Vector getCurrentClosestAxis() {
        return currentClosestAxis;
    }
    // Tick function for the projectile
    @Override
    public void B_() {
        // Check if ringProperties is not null
        if (ringProperties != null) {
            // Calculate the angle and radius based on the initial angle, rotation direction, angle change, and ticks lived
            double angle = Math.toRadians(initialAngle + (ringProperties.getRotationDirection() == RotationDirection.CLOCKWISE ? 1 : -1) * ringProperties.getAngleChange() * this.ticksLived);
            double radius = ringProperties.getRadiusMultiplier() * this.ticksLived;

            // Calculate the direction vector from the center location to the target's eye location
            Vector directionVector = MathHelper.getDirection(ringProperties.getCenterLocation(), target.getEyeLocation(), 1d);

            // Calculate the closest axis (a vector parallel to the x-z plane) from the direction vector
            // with smooth interpolation to prevent rapid rotations
            Vector targetClosestAxis = MathHelper.setVectorLength(directionVector.clone().setY(0), 1d);
            if (currentClosestAxis == null) {
                currentClosestAxis = targetClosestAxis.clone(); // initialize with the first targetClosestAxis
            }
            double interpolationFactor = 0.1;
            Vector closestAxis = currentClosestAxis.add(targetClosestAxis.subtract(currentClosestAxis).multiply(interpolationFactor));
            MathHelper.setVectorLength(closestAxis, 1d);

            // Update the current closest axis
            this.currentClosestAxis = closestAxis;

            // Calculate the align axis (a vector perpendicular to the direction vector and closest axis)
            Vector alignAxis = MathHelper.setVectorLength(closestAxis.getCrossProduct(directionVector), 1d);

            // Calculate the position of the projectile based on the radius, angle, and center location
            // and rotate the position around the closest axis and align axis
            Vector position = new Vector(radius * Math.cos(angle), 0, radius * Math.sin(angle));
            position = MathHelper.rotateAroundAxisDegree(position, closestAxis, ringProperties.getInitialRotationDegrees());
            position = MathHelper.rotateAroundAxisDegree(position, alignAxis, Math.toDegrees(Math.acos(directionVector.dot(closestAxis))));
            position = position.add(ringProperties.getCenterLocation().toVector());

            // Calculate the velocity of the projectile based on its current location and the position
            // and set the velocity of the projectile
            Vector velocity = position.clone().subtract(bukkitEntity.getLocation().toVector());
            bukkitEntity.setVelocity(velocity);

            // Set the impulse index to a random value
            this.impulse_index = RANDOMIZED_IMPULSE_TICK_INTERVAL;
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