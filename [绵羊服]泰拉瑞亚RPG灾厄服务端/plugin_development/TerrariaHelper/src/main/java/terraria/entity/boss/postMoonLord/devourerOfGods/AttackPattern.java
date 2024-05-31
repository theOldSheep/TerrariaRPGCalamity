package terraria.entity.boss.postMoonLord.devourerOfGods;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;


// AttackPattern (Abstract Class)
abstract class AttackPattern {
    // Abstract method to schedule projectile spawning
    public abstract void scheduleProjectiles(JavaPlugin plugin, Player player, EntityHelper.ProjectileShootInfo shootInfo);
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


class WallAttackPattern extends AttackPattern {
    private final int width; // Number of projectiles in the wall
    private final double interval; // Distance between projectiles in the wall
    private final int startDist; // Distance to start from the player

    public WallAttackPattern(int width, double interval, int startDist) {
        this.width = width;
        this.interval = interval;
        this.startDist = startDist;
    }

    @Override
    public void scheduleProjectiles(JavaPlugin plugin, Player player, EntityHelper.ProjectileShootInfo shootInfo) {
        Location playerLoc = player.getEyeLocation();
        // Directional info
        Vector direction = Math.random() < 0.5 ? new Vector(1, 0, 0) : new Vector(0, 0, 1);
        Vector offsetDir = new Vector(1, 0, 1).subtract(direction);
        // Randomized orientation
        if (Math.random() < 0.5)
            direction.multiply(-1);
        Vector velocity = direction.clone().multiply(shootInfo.velocity.length());
        Location wallCenter = playerLoc.clone().subtract(direction.clone().multiply(startDist));

        int loopRadius = (int) (width / interval / 2);
        for (int i = -loopRadius; i <= loopRadius; i++) {
            Location shootLoc = wallCenter.clone().add(offsetDir.clone().multiply(interval * i));

            spawnProjectile(plugin, shootInfo, shootLoc, velocity, Particle.CRIT_MAGIC, 20, 0.5,10);
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
    public void scheduleProjectiles(JavaPlugin plugin, Player player, EntityHelper.ProjectileShootInfo shootInfo) {
        Location playerLoc = player.getEyeLocation();
        double startingFireAngle = Math.random() * 360; // Random initial angle
        double sectorAngleMiddle = Math.toRadians(startingFireAngle + spreadAngle / 2);
        Location shootLoc = playerLoc.clone().subtract(Math.sin(sectorAngleMiddle) * radius, 0, Math.cos(sectorAngleMiddle) * radius);

        for (int i = 0; i < numProjectiles; i++) {
            double angle = startingFireAngle + i * angleIncrement;
            double angleRadian = Math.toRadians(angle);

            Vector velocity = new Vector(Math.sin(angleRadian), 0, Math.cos(angleRadian));
            velocity.multiply(shootInfo.velocity.length());

            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnProjectile(plugin, shootInfo, shootLoc, velocity, Particle.END_ROD, 20, 0.5, 20);
                }
            }.runTaskLater(plugin, i * delayBetweenProjectiles);
        }
    }
}