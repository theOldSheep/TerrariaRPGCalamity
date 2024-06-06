package terraria.entity.projectile;


import net.minecraft.server.v1_12_R1.World;
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
        return new Vector(1, 0, 0);
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


    // Tick function for the projectile
    @Override
    public void B_() {
        if (ringProperties != null) {
            // Calculate the angle and radius for the current tick
            double angle = Math.toRadians(initialAngle + (ringProperties.getRotationDirection() == RotationDirection.CLOCKWISE ? 1 : -1) * ringProperties.getAngleChange() * this.ticksLived);
            double radius = ringProperties.getRadiusMultiplier() * this.ticksLived;

            System.out.println("Angle: " + Math.toDegrees(angle));
            System.out.println("Radius: " + radius);

            // Calculate the position of the projectile
            Vector position = new Vector(
                    radius * Math.cos(angle),
                    0,
                    radius * Math.sin(angle)
            );

            System.out.println("Position: " + position);

            // Rotate the position around the default rotation axis
            position = MathHelper.rotateAroundAxisDegree(position, getDefaultRotationAxis(), ringProperties.getInitialRotationDegrees());

            // Calculate the direction vector to the target's eye location
            Vector directionVector = target.getEyeLocation().toVector().subtract(ringProperties.getCenterLocation().toVector()).normalize();

            System.out.println("Direction Vector: " + directionVector);

            // Calculate the alignment axis and angle
            Vector alignAxis = getDefaultRotationAxis().crossProduct(directionVector).normalize();
            double alignAngle = Math.toDegrees(Math.acos(directionVector.dot(getDefaultRotationAxis())));

            System.out.println("Alignment Axis: " + alignAxis);
            System.out.println("Alignment Angle: " + alignAngle);

            // Rotate the position around the alignment axis
            position = MathHelper.rotateAroundAxisDegree(position, alignAxis, alignAngle);

            // Add the center location to the position
            position = position.add(ringProperties.getCenterLocation().toVector());

            System.out.println("Final Position: " + position);

            // Calculate the velocity of the projectile
            Vector velocity = position.clone().subtract(bukkitEntity.getLocation().toVector());

            System.out.println("Velocity: " + velocity);

            // Check if the velocity is valid
            if (Double.isFinite(velocity.getX()) && Double.isFinite(velocity.getY()) && Double.isFinite(velocity.getZ())) {
                // Set the velocity of the projectile
                bukkitEntity.setVelocity(velocity);

                // Sync the velocity change to the client
                this.impulse_index = RANDOMIZED_IMPULSE_TICK_INTERVAL;
            } else {
                // Remove the projectile if the velocity is invalid
                bukkitEntity.remove();
            }
        }

        // Call the superclass tick function
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