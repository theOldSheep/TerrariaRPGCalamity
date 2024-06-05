package terraria.entity.projectile;


import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

public class RotatingRingProjectile extends GenericProjectile {
    public enum RotationDirection {
        CLOCKWISE,
        COUNTER_CLOCKWISE
    }

    public static class RingProperties {
        private Location centerLocation;
        private Vector planeNormal;
        private Vector rotationAxis;
        private RotationDirection rotationDirection;
        private double angleChange;

        public RingProperties(Location centerLocation, Vector planeNormal, Vector rotationAxis, RotationDirection rotationDirection, double angleChange) {
            this.centerLocation = centerLocation;
            this.planeNormal = planeNormal;
            this.rotationAxis = rotationAxis;
            this.rotationDirection = rotationDirection;
            this.angleChange = angleChange;
        }

        public Location getCenterLocation() {
            return centerLocation;
        }

        public Vector getPlaneNormal() {
            return planeNormal;
        }

        public Vector getRotationAxis() {
            return rotationAxis;
        }

        public RotationDirection getRotationDirection() {
            return rotationDirection;
        }

        public double getAngleChange() {
            return angleChange;
        }
    }

    private static final double FULL_CIRCLE_DEGREES = 360d;

    Player target;
    RingProperties ringProperties;
    double initialAngle;

    public RotatingRingProjectile(World world) {
        super(world);
        die();
    }

    public RotatingRingProjectile(EntityHelper.ProjectileShootInfo shootInfo, Player target, RingProperties ringProperties, double initialAngle) {
        super(shootInfo);
        this.target = target;
        this.ringProperties = ringProperties;
        this.initialAngle = initialAngle;
    }

    @Override
    public void B_() {
        if (ringProperties != null) {
            double angle = Math.toRadians(initialAngle + (ringProperties.getRotationDirection() == RotationDirection.CLOCKWISE ? 1 : -1) * ringProperties.getAngleChange() * this.ticksLived);
            double radius = 0.5 * this.ticksLived; // Adjust the radius growth rate as needed

            // Calculate the position vector in the local coordinate system of the ring
            Vector position = new Vector(
                    radius * Math.cos(angle),
                    radius * Math.sin(angle),
                    0
            );

            // Calculate the direction vector from the center of the ring to the target player
            Vector directionVector = target.getEyeLocation().toVector().subtract(ringProperties.getCenterLocation().toVector());

            Vector rotationAxis = new Vector(0, 1, 0); // Rotation axis is the y-axis
            double rotationAngle = - Math.atan2(directionVector.getZ(), directionVector.getX()); // Calculate the rotation angle

            position = MathHelper.rotateAroundAxisRadian(position, rotationAxis, rotationAngle);

            // Add the center location of the ring to the position vector
            position = position.add(ringProperties.getCenterLocation().toVector());

            // Calculate the velocity vector
            Vector velocity = position.clone().subtract(bukkitEntity.getLocation().toVector());

            // Check if the velocity values are finite
            if (Double.isFinite(velocity.getX()) && Double.isFinite(velocity.getY()) && Double.isFinite(velocity.getZ())) {
                bukkitEntity.setVelocity(velocity);
            } else {
                // Handle the case where the velocity values are not finite
                bukkitEntity.remove();
            }
        }

        super.B_();
    }

    public static void summonRingOfProjectiles(EntityHelper.ProjectileShootInfo shootInfo, Player target, Location spawnLocation, double theta, RotationDirection rotationDirection, double initialAngle, double angleChange, int numProjectiles) {
        if (numProjectiles <= 0) {
            throw new IllegalArgumentException("numProjectiles must be greater than 0");
        }

        shootInfo.shootLoc = spawnLocation.clone();
        shootInfo.velocity = new Vector(0, 0, 0);

        // Calculate the direction vector from the center of the ring to the target player
        Vector directionVector = target.getEyeLocation().toVector().subtract(spawnLocation.toVector());

        // Calculate the normal vector of the plane that contains the ring
        Vector normalVector = directionVector.clone().normalize();

        // Calculate the rotation axis for the ring
        Vector rotationAxis = new Vector(0, 1, 0); // For vertical rings, the rotation axis is the y-axis

        // Calculate the angle between adjacent projectiles
        double angleBetweenProjectiles = FULL_CIRCLE_DEGREES / numProjectiles;

        RingProperties ringProperties = new RingProperties(spawnLocation, normalVector, rotationAxis, rotationDirection, angleChange);

        // Summon the projectiles in the ring
        for (int i = 0; i < numProjectiles; i++) {
            double angle = initialAngle + i * angleBetweenProjectiles;
            new RotatingRingProjectile(shootInfo, target, ringProperties, angle);
        }
    }

}