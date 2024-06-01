package terraria.entity.boss.postMoonLord.devourerOfGods;


import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

// AttackManager
class AttackManager {
    static Random RANDOM = new Random();
    // Instance variables
    List<AttackPattern> patterns;
    JavaPlugin plugin;
    Player player;
    EntityHelper.ProjectileShootInfo shootInfo;
    int duration, attackInterval, currentTick;
    double projectileSpeed;

    // Constructor, contextual information such as plugin, target and shoot info are passed in here.
    public AttackManager(JavaPlugin plugin, Player target, EntityHelper.ProjectileShootInfo shootInfo, int duration, int attackInterval, AttackPattern... attackPatterns) {
        this.plugin = plugin;
        this.player = target;
        this.shootInfo = shootInfo;
        this.duration = duration;
        this.attackInterval = attackInterval;
        this.patterns = Arrays.asList(attackPatterns);

        this.currentTick = 0;
        this.projectileSpeed = shootInfo.velocity.length();
    }

    public void start() {
        // It can not be started twice
        if (currentTick != 0)
            return;
        tick();
    }

    private void tick() {
        currentTick += attackInterval;
        // Stop if duration exceeded or player becomes invalid
        if (currentTick > duration || !PlayerHelper.isProperlyPlaying(player)) {
            return;
        }

        // Select a random attack pattern and schedule it
        AttackPattern patternUsed = patterns.get(RANDOM.nextInt(patterns.size()));
        patternUsed.scheduleProjectiles(plugin, player, shootInfo, projectileSpeed);

        // Schedule the next tick
        Bukkit.getScheduler().runTaskLater(plugin, this::tick, attackInterval);
    }
}