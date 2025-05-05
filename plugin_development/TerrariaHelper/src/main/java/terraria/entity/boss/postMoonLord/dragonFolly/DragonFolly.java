package terraria.entity.boss.postMoonLord.dragonFolly;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class DragonFolly extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_DRAGONFOLLY;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.JUNGLE;
    public static final double BASIC_HEALTH = 540000 * 2, BASIC_HEALTH_BR = 720000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    private static final double[] DASH_SPEEDS = {2.0, 2.25, 2.5};
    // DASH_INTERVAL, DASH_DURATION, slow for x ticks
    private final int[][] DASH_TIMINGS = {
        {10, 40, 10},
        {8, 30, 5},
        {5, 25, 3}
    };
    // ticks before firing, ticks after firing
    private final int[][] PROJECTILE_TIMINGS = {
            {20, 10},
            {15, 8},
            {10, 5},
    };
    private static final double[] PHASE_THRESHOLDS = {0.75, 0.4};
    private static final int PARTICLE_INTERVAL = 100;
    private static final double HORIZONTAL_LIMIT = 32.0, ALIGNMENT_DIST = 20, ALIGNMENT_SPEED = 0.8;
    private static final double PROJECTILE_SPEED = 1.0;
    private static final int PROJECTILE_TICKS_OFFSET = 30;
    static HashMap<String, Double> attrMapFeather;
    static AimHelper.AimHelperOptions aimHelperFeather;
    static AimHelper.AimHelperOptions[] aimHelperDashes;
    static {
        attrMapFeather = new HashMap<>();
        attrMapFeather.put("damage", 732d);
        attrMapFeather.put("knockback", 1.5d);

        aimHelperFeather = new AimHelper.AimHelperOptions()
                .setAimMode(true).setAccelerationMode(false)
                .setProjectileSpeed(PROJECTILE_SPEED).setTicksTotal(PROJECTILE_TICKS_OFFSET);
        aimHelperDashes = new AimHelper.AimHelperOptions[]{
                new AimHelper.AimHelperOptions().setAccelerationMode(true).setProjectileSpeed(DASH_SPEEDS[0]),
                new AimHelper.AimHelperOptions().setAccelerationMode(false).setProjectileSpeed(DASH_SPEEDS[1]),
                new AimHelper.AimHelperOptions().setAccelerationMode(false).setProjectileSpeed(DASH_SPEEDS[2]),
        };
    }
    EntityHelper.ProjectileShootInfo shootInfoFeather;
    int indexAI = 0, AIPhase = 0;
    Vector velocity = new Vector();
    private Location spawnPosition;
    private boolean isDashPhase = true, isDashing = false;
    private int particleTicks = PARTICLE_INTERVAL;
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
                for (UUID uid : targetMap.keySet()) {
                    Player ply = Bukkit.getPlayer(uid);
                    if (ply == null)
                        continue;
                    if (isOutOfBoundary(ply)) {
                        EntityHelper.applyEffect(ply, "龙焰", 100);
                    }
                }
                // Fire particles
                if (++particleTicks > PARTICLE_INTERVAL) {
                    visualizeBoundary();
                    particleTicks = 0;
                }

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

                isDashing = false;
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
            isDashing = true;
            // Initialize dash direction
            if (indexAI == 0) {
                velocity = MathHelper.getDirection(bukkitEntity.getLocation(),
                        AimHelper.helperAimEntity(bukkitEntity, target, aimHelperDashes[AIPhase]),
                        DASH_SPEEDS[AIPhase], false);
                bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1.0f);
            }
            // Slow down towards the end of the dash
            if (indexAI >= DASH_TIMINGS[AIPhase][1] - DASH_TIMINGS[AIPhase][2]) {
                velocity.multiply(0.925);
            }
        }
        // Between dashes
        else {
            Location targetLocClone = target.getLocation();
            targetLocClone.setY(bukkitEntity.getLocation().getY());
            Vector horDir = MathHelper.getDirection(targetLocClone, bukkitEntity.getLocation(), ALIGNMENT_DIST, true);

            velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation().add(horDir), ALIGNMENT_SPEED);
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
        shootInfoFeather.setLockedTarget(target);
        for (Vector dir : MathHelper.getCircularProjectileDirections(
                9, 3, 90, target, bukkitEntity.getLocation(), 1)) {
            shootInfoFeather.shootLoc = target.getEyeLocation().add(dir.clone().multiply(PROJECTILE_SPEED * PROJECTILE_TICKS_OFFSET));
            shootInfoFeather.velocity = dir.multiply(-PROJECTILE_SPEED);
            EntityHelper.spawnProjectile(shootInfoFeather);
        }
    }
    private boolean isOutOfBoundary(Player ply) {
        Location targetHorizontalLocation = ply.getLocation();
        targetHorizontalLocation.setY(spawnPosition.getY());
        double horizontalDistance = spawnPosition.distanceSquared(targetHorizontalLocation);
        return horizontalDistance > HORIZONTAL_LIMIT * HORIZONTAL_LIMIT;
    }
    private void visualizeBoundary() {
        for (UUID uid : targetMap.keySet()) {
            Player ply = Bukkit.getPlayer(uid);
            if (ply == null)
                continue;
            DragoncoreHelper.displaySnowStormParticle(ply,
                    new DragoncoreHelper.DragonCoreParticleInfo("b/dfl", spawnPosition),
                    PARTICLE_INTERVAL);
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
            attrMap.put("damageTakenMulti", 0.9d);
            attrMap.put("defence", 80d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
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
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
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
                    DamageHelper.DamageType.ARROW, "闪耀红羽");
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
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        // update boss bar and dynamic DR
        terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, BOSS_TYPE);
        // load nearby chunks
        {
            for (int i = -1; i <= 1; i ++)
                for (int j = -1; j <= 1; j ++) {
                    org.bukkit.Chunk currChunk = bukkitEntity.getLocation().add(i << 4, 0, j << 4).getChunk();
                    currChunk.load();
                }
        }
        // AI
        AI();
    }
}