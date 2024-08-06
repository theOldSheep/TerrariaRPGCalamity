package terraria.entity.projectile;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

public class BulletHellProjectile extends GenericProjectile {
    public static final int VEL_UPDATE_INTERVAL = TerrariaHelper.settingConfig.getInt("optimization.projVelUpdItvBulletHell", 2);
    public enum ProjectileType {
        SQUARE_BORDER, SQUARE_BORDER_SIDES, CIRCUMFERENCE,
        BLAST_8, BLAST_16, BLAST_32, CALCULATED
    }
    public static class BulletHellDirectionInfo {
        // the vectors forming the basis of the bullet hell subspace
        public Vector e1, e2;
        public Vector planeNormal;
        public Player target;

        public BulletHellDirectionInfo(Player target) {
            this.target = target;
            e1 = MathHelper.vectorFromYawPitch_approx(
                    MathHelper.getVectorYaw(target.getLocation().getDirection()) - 90, 0);
            e2 = new Vector(0, 1, 0);
            this.planeNormal = e1.getCrossProduct(e2);
        }
    }

    BulletHellDirectionInfo directionInfo;

    public BulletHellProjectile(EntityHelper.ProjectileShootInfo shootInfo, ProjectileType type, double distance, double speed, BulletHellDirectionInfo directionInfo) {
        super(calculateProjectileInfo(shootInfo, type, distance, speed, directionInfo));
        this.directionInfo = directionInfo;

        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BULLET_HELL_PROJECTILE_DIRECTION, directionInfo);
    }

    private static EntityHelper.ProjectileShootInfo calculateProjectileInfo(EntityHelper.ProjectileShootInfo shootInfo, ProjectileType type, double distance, double speed, BulletHellDirectionInfo directionInfo) {
        Location playerLocation = directionInfo.target.getEyeLocation();

        switch (type) {
            case SQUARE_BORDER:
                calculateSquareBorderProjectileInfo(shootInfo, playerLocation, directionInfo, distance, speed, false);
                break;
            case SQUARE_BORDER_SIDES:
                calculateSquareBorderProjectileInfo(shootInfo, playerLocation, directionInfo, distance, speed, true);
                break;
            case CIRCUMFERENCE:
                calculateCircumferenceProjectileInfo(shootInfo, playerLocation, directionInfo, distance, speed);
                break;
            case BLAST_8:
                calculateBlastProjectileInfo(shootInfo, directionInfo, speed, 8);
                break;
            case BLAST_16:
                calculateBlastProjectileInfo(shootInfo, directionInfo, speed, 16);
                break;
            case BLAST_32:
                calculateBlastProjectileInfo(shootInfo, directionInfo, speed, 32);
                break;
            case CALCULATED:
                break;
            default:
                throw new UnsupportedOperationException("Unsupported projectile type");
        }

        shootInfo.setLockedTarget(directionInfo.target);

        return shootInfo;
    }

    private static EntityHelper.ProjectileShootInfo calculateSquareBorderProjectileInfo(EntityHelper.ProjectileShootInfo shootInfo, Location playerLocation,
                                                                                        BulletHellDirectionInfo directionInfo, double distance, double speed, boolean sidesOnly) {
        // Calculate a random point on the border of a square centered at the player's eye location
        double minX = -distance;
        double maxX = distance;
        double minZ = -distance;
        double maxZ = distance;

        double x, z;
        Vector velocity;

        // Randomly choose a side of the square
        int side = (int) (Math.random() * (sidesOnly ? 2 : 4));

        switch (side) {
            case 0: // Left side
                x = minX;
                z = minZ + (maxZ - minZ) * Math.random();
                velocity = directionInfo.e1.clone(); // Move to the right
                break;
            case 1: // Right side
                x = maxX;
                z = minZ + (maxZ - minZ) * Math.random();
                velocity = directionInfo.e1.clone().multiply(-1); // Move to the left
                break;
            case 2: // Top side
                x = minX + (maxX - minX) * Math.random();
                z = maxZ;
                velocity = directionInfo.e2.clone().multiply(-1); // Move downwards
                break;
            case 3: // Bottom side
                x = minX + (maxX - minX) * Math.random();
                z = minZ;
                velocity = directionInfo.e2.clone(); // Move upwards
                break;
            default:
                throw new RuntimeException("Unexpected side");
        }

        shootInfo.shootLoc = playerLocation.clone().add(directionInfo.e1.clone().multiply(x)).add(directionInfo.e2.clone().multiply(z));
        shootInfo.velocity = velocity.multiply(speed);

        return shootInfo;
    }
    private static EntityHelper.ProjectileShootInfo calculateCircumferenceProjectileInfo(EntityHelper.ProjectileShootInfo shootInfo, Location playerLocation,
                                                                                         BulletHellDirectionInfo directionInfo, double distance, double speed) {
        // Calculate a random point on the circumference of a circle centered at the player's eye location
        double angle = Math.random() * 2 * Math.PI;
        double x = distance * Math.cos(angle);
        double z = distance * Math.sin(angle);

        shootInfo.shootLoc = playerLocation.clone().add(directionInfo.e1.clone().multiply(x)).add(directionInfo.e2.clone().multiply(z));
        shootInfo.velocity = MathHelper.setVectorLength(directionInfo.e1.clone().multiply(-x).add(directionInfo.e2.clone().multiply(-z)), speed);

        return shootInfo;
    }
    private static EntityHelper.ProjectileShootInfo calculateBlastProjectileInfo(EntityHelper.ProjectileShootInfo shootInfo, BulletHellDirectionInfo directionInfo, double speed, int fireAmount) {
        double angle = Math.random() * 2 * Math.PI;
        double angleOffset = Math.PI * 2 / fireAmount;

        for (int i = 1; i <= fireAmount; i ++) {
            angle += angleOffset;
            double x = Math.cos(angle);
            double z = Math.sin(angle);

            shootInfo.velocity = directionInfo.e1.clone().multiply(x).add(directionInfo.e2.clone().multiply(z)).multiply(speed);
            if (i != fireAmount) {
                new BulletHellProjectile(shootInfo, ProjectileType.CALCULATED, 0, speed, directionInfo);
            }
        }

        return shootInfo;
    }

    @Override
    protected void extraTicking() {
        if (projectileType.equals("深渊炙颅")) {
            motY = MathHelper.xcos_degree(ticksLived * 9) * 0.2;
        }


        // Update the projectile's position to be on the plane
        Location projectileLocation = bukkitEntity.getLocation();
        // sometimes this would be NaN
        if (! Double.isNaN(projectileLocation.getX())) {
            Vector vectorToPlane = projectileLocation.toVector().subtract(directionInfo.target.getEyeLocation().toVector());
            Vector correctionVector = MathHelper.vectorProjection(directionInfo.planeNormal, vectorToPlane);
            bukkitEntity.teleport(projectileLocation.subtract(correctionVector));
        }

        // Update the projectile's velocity to be parallel to the plane
        Vector velocity = bukkitEntity.getVelocity();
        // sometimes this would be NaN as well
        if (! Double.isNaN(velocity.getX())) {
            Vector velocityProjection = MathHelper.vectorProjection(directionInfo.planeNormal, velocity);
            bukkitEntity.setVelocity(velocity.subtract(velocityProjection));
        }
    }

    @Override
    protected int getVelocityUpdateInterval() {
        return VEL_UPDATE_INTERVAL;
    }
}