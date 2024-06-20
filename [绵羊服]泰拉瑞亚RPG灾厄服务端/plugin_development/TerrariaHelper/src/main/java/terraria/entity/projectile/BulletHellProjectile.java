package terraria.entity.projectile;

import eos.moe.dragoncore.api.CoreAPI;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

public class BulletHellProjectile extends GenericProjectile {
    public enum ProjectileType {
        SQUARE_BORDER,
        CIRCUMFERENCE
    }


    private Vector planeNormal;
    private Player player;

    public BulletHellProjectile(EntityHelper.ProjectileShootInfo shootInfo, Player player, ProjectileType type, double distance, Vector planeNormal) {
        super(calculateProjectileInfo(shootInfo, player, type, distance, planeNormal));
        this.player = player;
        this.planeNormal = planeNormal;
    }

    private static EntityHelper.ProjectileShootInfo calculateProjectileInfo(EntityHelper.ProjectileShootInfo shootInfo, Player player, ProjectileType type, double distance, Vector planeNormal) {
        Location playerLocation = player.getEyeLocation();

        Vector upVector = new Vector(0, 1, 0);
        Vector forwardVector = MathHelper.getNonZeroCrossProd(planeNormal, upVector).normalize();

        Vector rightVector = forwardVector.getCrossProduct(planeNormal);

        switch (type) {
            case SQUARE_BORDER:
                shootInfo = calculateSquareBorderProjectileInfo(shootInfo, playerLocation, forwardVector, rightVector, distance);
                break;
            case CIRCUMFERENCE:
                shootInfo = calculateCircumferenceProjectileInfo(shootInfo, playerLocation, forwardVector, rightVector, distance);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported projectile type");
        }

        return shootInfo;
    }

    private static EntityHelper.ProjectileShootInfo calculateSquareBorderProjectileInfo(EntityHelper.ProjectileShootInfo shootInfo, Location playerLocation, Vector forwardVector, Vector rightVector, double distance) {
        // Calculate a random point on the border of a square centered at the player's eye location
        double minX = -distance;
        double maxX = distance;
        double minZ = -distance;
        double maxZ = distance;

        double x, z;
        Vector velocity;

        // Randomly choose a side of the square
        int side = (int) (Math.random() * 4);

        switch (side) {
            case 0: // Top side
                x = minX + (maxX - minX) * Math.random();
                z = minZ;
                velocity = rightVector.clone().multiply(-1); // Move downwards
                break;
            case 1: // Right side
                x = maxX;
                z = minZ + (maxZ - minZ) * Math.random();
                velocity = forwardVector.clone().multiply(-1); // Move to the left
                break;
            case 2: // Bottom side
                x = minX + (maxX - minX) * Math.random();
                z = maxZ;
                velocity = rightVector.clone(); // Move upwards
                break;
            case 3: // Left side
                x = minX;
                z = minZ + (maxZ - minZ) * Math.random();
                velocity = forwardVector.clone(); // Move to the right
                break;
            default:
                throw new RuntimeException("Unexpected side");
        }

        shootInfo.shootLoc = playerLocation.clone().add(forwardVector.clone().multiply(x)).add(rightVector.clone().multiply(z));
        shootInfo.velocity = velocity;

        return shootInfo;
    }

    private static EntityHelper.ProjectileShootInfo calculateCircumferenceProjectileInfo(EntityHelper.ProjectileShootInfo shootInfo, Location playerLocation, Vector forwardVector, Vector rightVector, double distance) {
        // Calculate a random point on the circumference of a circle centered at the player's eye location
        double angle = Math.random() * 2 * Math.PI;
        double x = distance * Math.cos(angle);
        double z = distance * Math.sin(angle);

        shootInfo.shootLoc = playerLocation.clone().add(forwardVector.clone().multiply(x)).add(rightVector.clone().multiply(z));
        shootInfo.velocity = MathHelper.setVectorLength(forwardVector.clone().multiply(-x).add(rightVector.clone().multiply(-z)), 1);

        return shootInfo;
    }

    @Override
    protected void extraTicking() {
        // Update the projectile's position to be on the plane
        Location projectileLocation = bukkitEntity.getLocation();
        Vector vectorToPlane = projectileLocation.toVector().subtract(player.getEyeLocation().toVector());
        Vector correctionVector = MathHelper.vectorProjection(planeNormal, vectorToPlane);
        bukkitEntity.teleport(projectileLocation.subtract(correctionVector));

        // Update the projectile's velocity to be parallel to the plane
        Vector velocity = bukkitEntity.getVelocity();
        Vector velocityProjection = MathHelper.vectorProjection(planeNormal, velocity);
        bukkitEntity.setVelocity(velocity.subtract(velocityProjection));

        // Reset the impulse index
        this.impulse_index = RANDOMIZED_IMPULSE_TICK_INTERVAL;
    }
}