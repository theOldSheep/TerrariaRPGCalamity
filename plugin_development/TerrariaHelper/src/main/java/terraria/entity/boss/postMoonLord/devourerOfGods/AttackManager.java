package terraria.entity.boss.postMoonLord.devourerOfGods;


import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

import java.util.ArrayList;
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
    int duration, attackInterval, currentTick, simultaneousAttacks;
    double projectileSpeed;

    // Constructor, contextual information such as plugin, target and shoot info are passed in here.
    public AttackManager(JavaPlugin plugin, Player target, EntityHelper.ProjectileShootInfo shootInfo, int duration, int attackInterval, int simultaneousAttacks, AttackPattern... attackPatterns) {
        this.plugin = plugin;
        this.player = target;
        this.shootInfo = shootInfo;
        this.duration = duration;
        this.attackInterval = attackInterval;
        this.simultaneousAttacks = simultaneousAttacks;
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

        // Select a specified number of random attack patterns and schedule them
        List<AttackPattern> patternsToUse = new ArrayList<>();
        for (int i = 0; i < simultaneousAttacks; i++) {
            AttackPattern pattern = patterns.get(RANDOM.nextInt(patterns.size()));
            if (!patternsToUse.contains(pattern) || patternsToUse.size() == patterns.size()) {
                patternsToUse.add(pattern);
            } else {
                // If we've already added all unique patterns, just add a random one
                patternsToUse.add(patterns.get(RANDOM.nextInt(patterns.size())));
            }
        }
        for (AttackPattern pattern : patternsToUse) {
            pattern.scheduleProjectiles(plugin, player, shootInfo, projectileSpeed);
        }

        // Schedule the next tick
        Bukkit.getScheduler().runTaskLater(plugin, this::tick, attackInterval);
    }
}