package terraria.entity.boss.postMoonLord.devourerOfGods;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

import java.util.Random;
import java.util.function.Supplier;


// AttackPattern (Abstract Class)
abstract class AttackPattern {
    // Abstract method to schedule projectile spawning
    public abstract void scheduleProjectiles(JavaPlugin plugin, Player player, EntityHelper.ProjectileShootInfo shootInfo, double projectileSpeed);
    // Helper method to display trajectory particle effect
    protected void displayTrajectory(Location startLoc, Vector particleOffset, Particle particle, int steps) {
        Location particleLoc = startLoc.clone();
        for (double t = 0; t <= steps; t ++) {
            particleLoc.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
            particleLoc.add(particleOffset);
        }
    }
    // Helper method to handle the projectile spawning
    protected void spawnProjectile(JavaPlugin plugin, EntityHelper.ProjectileShootInfo shootInfo, Location spawnLoc, Vector velocity,
                                   Particle particle, double particleLength, double particleInterval, int projectileSpawnDelay) {
        // Display particle
        Vector particleDirection = MathHelper.setVectorLength( velocity.clone(), particleInterval);
        int steps = (int) Math.round(particleLength / particleInterval);
        displayTrajectory(spawnLoc, particleDirection, particle, steps);
        // Schedule the projectile with a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                shootInfo.shootLoc = spawnLoc;
                shootInfo.velocity = velocity;
                EntityHelper.spawnProjectile(shootInfo);
            }
        }.runTaskLater(plugin, projectileSpawnDelay);
    }
}


class DelayedWallAttackPattern extends AttackPattern {
    public static final Supplier<Vector>
            GRID = () -> (Math.random() < 0.5 ? new Vector(0, 0, 1) : new Vector(1, 0, 0)).multiply(Math.random() < 0.5 ? 1 : -1),
            GRID_SLANTED = () -> (Math.random() < 0.5 ? new Vector(1, 0, 1) : new Vector(-1, 0, 1)).multiply(Math.random() < 0.5 ? 1 : -1),
            RANDOM = () -> MathHelper.vectorFromYawPitch_approx(Math.random() * 360, 0);
    private final double width; // In blocks
    private final double interval;
    private final int startDist;
    private final int delayPerProjectile; // Added delay per projectile
    private Supplier<Vector> fwdDistSupplier; // Supplies the forward distance.

    // This would produce the randomized direction.
    public DelayedWallAttackPattern(double width, double interval, int startDist, int delayPerProjectile, Supplier<Vector> fwdDistSupplier) {
        this.width = width;
        this.interval = interval;
        this.startDist = startDist;
        this.delayPerProjectile = delayPerProjectile;
        this.fwdDistSupplier = fwdDistSupplier;
    }

    @Override
    public void scheduleProjectiles(JavaPlugin plugin, Player player, EntityHelper.ProjectileShootInfo shootInfo, double projectileSpeed) {
        Location playerLoc = player.getEyeLocation();

        // Random Direction
        Vector direction = fwdDistSupplier.get();
        Vector offsetDir = new Vector(-direction.getZ(), 0, direction.getX()); // Perpendicular to direction
        Vector velocity = direction.clone().multiply(projectileSpeed);

        Location wallCenter = playerLoc.clone().subtract(direction.clone().multiply(startDist)); // Adjust for random direction

        // Calculate Delay for Each Projectile
        int loopIterations = (int) (width / interval / 2);
        int delay = 0;

        for (int i = loopIterations; i >= 0; i--) {
            Vector offset = offsetDir.clone().multiply(interval * i);
            boolean spawnPaired = i != 0;

            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnProjectile(plugin, shootInfo, wallCenter.clone().add(offset), velocity, Particle.SPELL_WITCH, startDist * 2, 0.5, 10);
                    if (spawnPaired) {
                        spawnProjectile(plugin, shootInfo, wallCenter.clone().subtract(offset), velocity, Particle.SPELL_WITCH, startDist * 2, 0.5, 10);
                    }
                }
            }.runTaskLater(plugin, delay);

            delay += delayPerProjectile;
        }
    }
}
class CircleAttackPattern extends AttackPattern {
    private final double radius;
    private final int numProjectiles;
    private final int delayBetweenProjectiles;
    private final double spreadAngle;
    private final double angleIncrement;

    public CircleAttackPattern(double radius, int numProjectiles, int delayBetweenProjectiles, double spreadAngle) {
        this.radius = radius;
        this.numProjectiles = numProjectiles;
        this.delayBetweenProjectiles = delayBetweenProjectiles;
        this.spreadAngle = spreadAngle;
        this.angleIncrement = spreadAngle / numProjectiles;
    }

    @Override
    public void scheduleProjectiles(JavaPlugin plugin, Player player, EntityHelper.ProjectileShootInfo shootInfo, double projectileSpeed) {
        double angleDir = Math.random() > 0.5 ? 1 : -1;
        Location playerLoc = player.getEyeLocation();
        double startingFireAngle = Math.random() * 360; // Random initial angle
        double sectorAngleMiddle = Math.toRadians(startingFireAngle + spreadAngle * angleDir / 2);
        Location shootLoc = playerLoc.clone().subtract(MathHelper.xsin_radian(sectorAngleMiddle) * radius, 0, MathHelper.xcos_radian(sectorAngleMiddle) * radius);

        for (int i = 0; i < numProjectiles; i++) {
            double angle = startingFireAngle + i * angleIncrement * angleDir;
            double angleRadian = Math.toRadians(angle);

            Vector velocity = new Vector(MathHelper.xsin_radian(angleRadian), 0, MathHelper.xcos_radian(angleRadian));
            velocity.multiply(projectileSpeed);

            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnProjectile(plugin, shootInfo, shootLoc, velocity, Particle.SPELL_WITCH, radius * 2, 0.5, 20);
                }
            }.runTaskLater(plugin, i * delayBetweenProjectiles);
        }
    }
}
class SwingingArcAttackPattern extends AttackPattern {
    private final double radius;
    private final int numProjectiles;
    private final int delayBetweenProjectiles;
    private final double spreadAngle;
    private final double angleIncrement;
    private final int backswingDelay; // Delay before starting the backswing

    public SwingingArcAttackPattern(double radius, int numProjectiles, int delayBetweenProjectiles, double spreadAngle, int backswingDelay) {
        this.radius = radius;
        this.numProjectiles = numProjectiles;
        this.delayBetweenProjectiles = delayBetweenProjectiles;
        this.spreadAngle = spreadAngle;
        this.angleIncrement = spreadAngle / numProjectiles;
        this.backswingDelay = backswingDelay;
    }

    @Override
    public void scheduleProjectiles(JavaPlugin plugin, Player player, EntityHelper.ProjectileShootInfo shootInfo, double projectileSpeed) {
        Location playerLoc = player.getEyeLocation();
        int initialDirection = (Math.random() < 0.5) ? 1 : -1; // Randomly clockwise or counter-clockwise
        double startingAngle = Math.random() * 360; // Random initial angle

        // Fire location
        double sectorAngleMiddle = Math.toRadians(startingAngle + spreadAngle / 2 * initialDirection);
        Location shootLoc = playerLoc.clone().subtract(MathHelper.xsin_radian(sectorAngleMiddle) * radius, 0, MathHelper.xcos_radian(sectorAngleMiddle) * radius);

        // Initial Swing
        scheduleArc(plugin, shootLoc, shootInfo, startingAngle, initialDirection, projectileSpeed);

        // Backswing (after a delay)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Offset starting angle by half the increment to "fill in the gaps"
                double nextStartAngle = startingAngle + angleIncrement * (numProjectiles - 0.5) * initialDirection;
                scheduleArc(plugin, shootLoc, shootInfo, nextStartAngle, -initialDirection, projectileSpeed); // Reverse direction
            }
        }.runTaskLater(plugin, backswingDelay + numProjectiles * delayBetweenProjectiles);
    }

    private void scheduleArc(JavaPlugin plugin, Location shootLoc, EntityHelper.ProjectileShootInfo shootInfo,
                             double startingAngle, int direction, double projectileSpeed) {
        for (int i = 0; i < numProjectiles; i++) {
            double angle = startingAngle + i * angleIncrement * direction;
            double angleRadian = Math.toRadians(angle);
            Vector velocity = new Vector(MathHelper.xsin_radian(angleRadian), 0, MathHelper.xcos_radian(angleRadian));
            velocity.multiply(projectileSpeed);

            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnProjectile(plugin, shootInfo, shootLoc, velocity, Particle.SPELL_WITCH, radius * 2, 0.5, 20);
                }
            }.runTaskLater(plugin, 0 + i * delayBetweenProjectiles);
        }
    }
}
class ScatteringCircleAttackPattern extends AttackPattern {
    private final double radius;
    private final int numProjectiles;
    private final int maxDelay; // Maximum delay for each projectile
    private final double targetRadius; // Radius of the target circle around the player

    public ScatteringCircleAttackPattern(double radius, int numProjectiles, int maxDelay, double targetRadius) {
        this.radius = radius;
        this.numProjectiles = numProjectiles;
        this.maxDelay = maxDelay;
        this.targetRadius = targetRadius;
    }

    @Override
    public void scheduleProjectiles(JavaPlugin plugin, Player player, EntityHelper.ProjectileShootInfo shootInfo, double projectileSpeed) {
        Location playerLoc = player.getEyeLocation();
        Random random = new Random();

        for (int i = 0; i < numProjectiles; i++) {
            double angle = Math.random() * 2 * Math.PI; // Random angle for projectile spawning

            // Spawn location of the projectile on the circle
            Location shootLoc = playerLoc.clone().add(MathHelper.xsin_radian(angle) * radius, 0, MathHelper.xcos_radian(angle) * radius);

            // Random target location around the player
            double targetAngle = Math.random() * 2 * Math.PI;
            Location targetLoc = playerLoc.clone().add(
                    MathHelper.xsin_radian(targetAngle) * targetRadius,
                    0, // Assume the player is on the ground
                    MathHelper.xcos_radian(targetAngle) * targetRadius
            );

            // Calculate velocity towards the target
            Vector velocity = MathHelper.getDirection(shootLoc, targetLoc, projectileSpeed);

            int delay = random.nextInt(maxDelay); // Random delay

            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnProjectile(plugin, shootInfo, shootLoc, velocity, Particle.SPELL_WITCH, radius * 2, 0.5, 20);
                }
            }.runTaskLater(plugin, delay);
        }
    }
}