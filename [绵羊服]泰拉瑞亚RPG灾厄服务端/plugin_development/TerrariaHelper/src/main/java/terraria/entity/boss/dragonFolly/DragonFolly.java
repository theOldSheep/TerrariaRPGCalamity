package terraria.entity.boss.dragonFolly;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class DragonFolly extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_DRAGONFOLLY;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.JUNGLE;
    public static final double BASIC_HEALTH = 540000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    private static final double[] DASH_SPEEDS = {1.5, 1.75, 2.0};
    private final int[][] DASH_TIMINGS = {
        {30, 20, 5},  // Phase 0: DASH_INTERVAL = 30, DASH_DURATION = 20, slow for 5 ticks
        {25, 15, 3},  // Phase 1: DASH_INTERVAL = 25, DASH_DURATION = 15, slow for 3 ticks
        {20, 10, 2}   // Phase 2: DASH_INTERVAL = 20, DASH_DURATION = 10, slow for 2 ticks
    };
private final int[][] PROJECTILE_TIMINGS = {
        {20, 10},  // Phase 0: ticks before firing = 20, ticks after firing = 10
        {15, 9},  // Phase 1: ticks before firing = 15, ticks after firing = 9
        {10, 8},  // Phase 2: ticks before firing = 10, ticks after firing = 8
};
    private static final double[] PHASE_THRESHOLDS = {0.75, 0.4};
    private static final double PARTICLE_INTERVAL = 2;
    private static final double HORIZONTAL_LIMIT = 32.0, ALIGNMENT_SPEED = 0.8;
    private static final double PROJECTILE_SPEED = 1.0;
    private static final int PROJECTILE_TICKS_OFFSET = 10;
    static HashMap<String, Double> attrMapFeather;
    static EntityHelper.AimHelperOptions aimHelperFeather;
    static {
        attrMapFeather = new HashMap<>();
        attrMapFeather.put("damage", 732d);
        attrMapFeather.put("knockback", 1.5d);
        aimHelperFeather = new EntityHelper.AimHelperOptions()
                .setAimMode(true).setAccelerationMode(false)
                .setProjectileSpeed(PROJECTILE_SPEED).setTicksTotal(PROJECTILE_TICKS_OFFSET);
    }
    EntityHelper.ProjectileShootInfo shootInfoFeather;
    int indexAI = 0, AIPhase = 0;
    Vector velocity = new Vector();
    private Location spawnPosition;
    private boolean isDashPhase = true, isDashing = false;
    private int particleTicks = 0;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
            // disappear if no target is available
            if (target == null) {
                for (LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                return;
            }
            // if target is valid, attack
            else {
                // increase player aggro duration
                targetMap.get(target.getUniqueId()).addAggressionTick();
                // Check if the player has moved too far horizontally
                if (isOutOfBoundary()) {
                    target.setFireTicks(40); // Set on fire for 2 seconds
                }
                // Fire particles
                if (++particleTicks > PARTICLE_INTERVAL) {
                    visualizeBoundary();
                    particleTicks = 0;
                }

                // TODO
                // Phase transition
                double healthRatio = getHealth() / getMaxHealth();
                if (AIPhase < PHASE_THRESHOLDS.length && healthRatio < PHASE_THRESHOLDS[AIPhase]) {
                    AIPhase ++;
                    // Update bossbar color
                    switch (AIPhase) {
                        case 1:
                            bossbar.color = BossBattle.BarColor.YELLOW;
                            break;
                        case 2:
                            bossbar.color = BossBattle.BarColor.RED;
                            break;
                    }
                    bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
                }

                if (isDashPhase)
                    dashAttack();
                else
                    projectileAttack();
                indexAI ++;

                // Update velocity
                bukkitEntity.setVelocity(velocity);
            }
        }
        // face the player
        if (isDashing)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    private void dashAttack() {
        // During dashes
        if (indexAI <= DASH_TIMINGS[AIPhase][1]) {
            // Initialize dash direction
            if (indexAI == 0) {
                velocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                if (velocity.lengthSquared() > 1e-9) {
                    velocity.normalize().multiply(DASH_SPEEDS[AIPhase]);
                }
            }
            // Slow down towards the end of the dash
            if (indexAI >= DASH_TIMINGS[AIPhase][1] - DASH_TIMINGS[AIPhase][2]) {
                velocity.multiply(0.9);
            }
        }
        // Between dashes
        else {
            velocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
            // Move much faster vertically than horizontally
            velocity.setY(velocity.getY() * 5);
            if (velocity.lengthSquared() > 1e-9) {
                velocity.normalize().multiply(ALIGNMENT_SPEED);
            }
            // Next AI Phase
            if (indexAI >= DASH_TIMINGS[AIPhase][0] + DASH_TIMINGS[AIPhase][1]) {
                indexAI = -1;
                if (shouldUseProjectileAttack())
                    isDashPhase = false;
            }
        }
    }
    private void projectileAttack() {
        if (indexAI == PROJECTILE_TIMINGS[AIPhase][0])
            fireProjectiles();
        if (indexAI >= PROJECTILE_TIMINGS[AIPhase][0] + PROJECTILE_TIMINGS[AIPhase][1]) {
            indexAI = -1;
            isDashPhase = true;
        }
    }
    private boolean shouldUseProjectileAttack() {
        switch (AIPhase) {
            case 0:
                return Math.random() < 0.1;
            case 1:
                return Math.random() < 0.2;
            case 2:
            default:
                return Math.random() < 0.3;
        }
    }
    private void fireProjectiles() {
        for (Vector dir : MathHelper.getCircularProjectileDirections(
                9, 3, 90, target, bukkitEntity.getLocation(), 1)) {
            shootInfoFeather.shootLoc = target.getEyeLocation().add(dir.clone().multiply(PROJECTILE_SPEED * PROJECTILE_TICKS_OFFSET));
            shootInfoFeather.velocity = dir.multiply(-PROJECTILE_SPEED);
            EntityHelper.spawnProjectile(shootInfoFeather);
        }
    }
    private boolean isOutOfBoundary() {
        Location targetHorizontalLocation = target.getLocation();
        targetHorizontalLocation.setY(spawnPosition.getY());
        double horizontalDistance = spawnPosition.distanceSquared(targetHorizontalLocation);
        return horizontalDistance > HORIZONTAL_LIMIT * HORIZONTAL_LIMIT;
    }
    private void visualizeBoundary() {
        Location center = spawnPosition;
        Location targetPos = target.getLocation();
        double angleToPlayer = Math.atan2(targetPos.getZ() - center.getZ(), targetPos.getX() - center.getX());
        int numVisualizedParticles = isOutOfBoundary() ? 16 : 8;
        // Center particles around the player, and only visualize half of the circle.
        for (int i = -numVisualizedParticles/2; i < numVisualizedParticles/2; ++i) {
            double angle = angleToPlayer + i * Math.PI / 16;
            double x = center.getX() + HORIZONTAL_LIMIT * Math.cos(angle);
            double z = center.getZ() + HORIZONTAL_LIMIT * Math.sin(angle);
            spawnPosition.getWorld().spawnParticle(Particle.REDSTONE, x, targetPos.getY() + 2, z, 1, 0, 0, 0, 0);
        }
    }
    // default constructor to handle chunk unload
    public DragonFolly(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public DragonFolly(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        spawnLoc.setY( spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 768d);
            attrMap.put("damageTakenMulti", 0.9);
            attrMap.put("defence", 80d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(12, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoFeather = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFeather,
                    EntityHelper.DamageType.ARROW, "闪耀红羽");
        }
        // center of arena
        spawnPosition = target.getLocation();
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);

            // send loot
            terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
        }
    }
    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // update boss bar and dynamic DR
        terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, BOSS_TYPE);
        // load nearby chunks
        {
            for (int i = -2; i <= 2; i ++)
                for (int j = -2; j <= 2; j ++) {
                    org.bukkit.Chunk currChunk = bukkitEntity.getLocation().add(i << 4, 0, j << 4).getChunk();
                    currChunk.load();
                }
        }
        // AI
        AI();
    }
}