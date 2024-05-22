package terraria.entity.boss.stormWeaver;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class StormWeaver extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.STORM_WEAVER;
    // it can follow the player out of the space
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 1980000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    StormWeaver head;
    static final int TOTAL_LENGTH = 61;
    static final String[] NAME_SUFFIXES = {"头", "体节", "尾"};
    static final double[][]
            SEGMENT_DAMAGE = {{1080d}, {660d}, {540d}}, SEGMENT_DEFENCE = {{99999d, 40d}, {99999d, 60d}, {0d, 80d}};
    static final double FROST_WAVE_SPEED = 2.75, LASER_SPEED = 3.0;

    static final double ACC_TIER_1 = 0.2, SPEED_TIER_1 = 2, DIST_ACCELERATE_TIER_1 = 16.0;
    static final double ACC_TIER_2 = 0.8, SPEED_TIER_2 = 3.75, DIST_ACCELERATE_TIER_2 = 24.0,
            CIRCLE_HEIGHT = 40.0, CIRCLE_RADIUS = 40.0,
            ANGLE_PER_TICK = SPEED_TIER_2 / CIRCLE_RADIUS;
    static final int PROJECTILE_INTERVAL = 20, PROJECTILE_ROUNDS = 4, DASHES = 3;

    static final int SLIME_SIZE_ARMORED = 10, SLIME_SIZE_UNARMORED = 9;
    static HashMap<String, Double> attrMapFrostWaveLightning, attrMapLaser;
    static {
        attrMapFrostWaveLightning = new HashMap<>();
        attrMapFrostWaveLightning.put("damage", 864d);
        attrMapFrostWaveLightning.put("knockback", 2.5d);

        attrMapLaser = new HashMap<>();
        attrMapLaser.put("damage", 792d);
        attrMapLaser.put("knockback", 1.5d);
    }
    static final EntityHelper.WormSegmentMovementOptions FOLLOW_PROPERTY =
            new EntityHelper.WormSegmentMovementOptions()
                    .setFollowDistance(5)
                    .setFollowingMultiplier(1)
                    .setStraighteningMultiplier(-0.1)
                    .setVelocityOrTeleport(false);
    EntityHelper.ProjectileShootInfo shootInfoFrostWave, shootInfoLaser;
    int segmentIndex, segmentTypeIndex;

    Vector lastVelocity = new Vector(); // Store the last velocity
    private int phase = 1;  // Initialize the phase to 1
    private int circleTimer = 0; // Timer to track circular movement duration
    private int dashTimer = 0; // Timer to track dash duration
    private int dashCount = 0; // Counter for dash attempts
    private void headMovement() {
        if (phase == 1) {
            dashTowardsPlayer(ACC_TIER_1, SPEED_TIER_1, DIST_ACCELERATE_TIER_1);
        } else {
            // Phase 2: Circular movement and projectile firing, then dashing
            if (circleTimer < PROJECTILE_ROUNDS * PROJECTILE_INTERVAL) {
                circleAbovePlayer();
                if (circleTimer % PROJECTILE_INTERVAL == 0) {
                     fireProjectile();
                }
                circleTimer++;
            } else if (dashCount < DASHES) {
                dashTowardsPlayer(ACC_TIER_2, SPEED_TIER_2, DIST_ACCELERATE_TIER_2);
            } else {
                // Reset timers and switch back to circular movement
                circleTimer = 0;
                dashCount = 0;
            }
        }
    }
    private void circleAbovePlayer() {
        // Calculate circular movement parameters
        double angle = ticksLived * ANGLE_PER_TICK; // Adjust the speed as needed
        double x = target.getLocation().getX() + CIRCLE_RADIUS * Math.cos(angle);
        double z = target.getLocation().getZ() + CIRCLE_RADIUS * Math.sin(angle);
        double y = target.getLocation().getY() + CIRCLE_HEIGHT;

        // Move the head towards the calculated position
        Vector movementVector = MathHelper.getDirection(bukkitEntity.getLocation(), new Location(bukkitEntity.getWorld(), x, y, z), ACC_TIER_2);
        lastVelocity.add(movementVector);
        MathHelper.setVectorLength(lastVelocity, SPEED_TIER_2, true);// Cap the speed in phase 2

        this.getBukkitEntity().setVelocity(lastVelocity);
        this.setYawPitch(bukkitEntity.getLocation().setDirection(lastVelocity));
    }
    // TODO: Implement your projectile firing logic here
    private void fireProjectile() {
        Bukkit.broadcastMessage("StormWeaver Attempted to Fire Projectiles!");
    }
    private void dashTowardsPlayer(double acceleration, double maxSpeed, double minDistance) {
        double distanceToTarget = bukkitEntity.getLocation().distance(target.getLocation());
        // Accelerate to change movement direction when far enough
        if (distanceToTarget > minDistance) {
            Vector movementVector = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), acceleration);

            // Use stored lastVelocity to avoid being slowed down by liquid
            lastVelocity.add(movementVector);
            MathHelper.setVectorLength(lastVelocity, maxSpeed, true);

            if (phase == 2) {
                dashTimer++; // Update the timer
            }
        }
        // If the boss dashes into the minDistance, record this as one completed dash.
        else if (dashTimer > 0 && phase == 2) {
            dashCount ++;
            dashTimer = 0;
        }
        // Update the entity's velocity and facing
        this.getBukkitEntity().setVelocity(lastVelocity);
        this.setYawPitch(bukkitEntity.getLocation().setDirection(lastVelocity));
    }
    private void setYawPitch(Location loc) {
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }
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
                for (LivingEntity segment : bossParts) {
                    segment.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    segment.setHealth(0d);
                    segment.remove();
                }
                return;
            }
            // if target is valid, attack
            else {
                // head
                if (segmentIndex == 0) {
                    // increase player aggro duration
                    targetMap.get(target.getUniqueId()).addAggressionTick();

                    // TODO
                    headMovement();

                    // follow
                    EntityHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, segmentIndex);
                }
                // update facing direction from handleSegmentsFollow
                {
                    MetadataValue valYaw = EntityHelper.getMetadata(bukkitEntity, "yaw");
                    if (valYaw != null) this.yaw = valYaw.asFloat();
                    MetadataValue valPitch = EntityHelper.getMetadata(bukkitEntity, "pitch");
                    if (valPitch != null) this.pitch = valPitch.asFloat();
                }

                updatePhase();
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    private void updatePhase() {
        double currentHealthPercentage = getHealth() / getAttributeInstance(GenericAttributes.maxHealth).getValue() * 100;

        if (currentHealthPercentage <= 80 && phase == 1) {
            phase = 2;
            System.out.println("StormWeaver changed to phase " + phase);
//            onPhaseChange(1, 2); // Call the helper function when phase changes
        } else if (currentHealthPercentage > 80 && phase == 2) {
            phase = 1;
            System.out.println("StormWeaver changed to phase " + 1);
//            onPhaseChange(2, 1);
        }
    }
    // default constructor to handle chunk unload
    public StormWeaver(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == WorldHelper.BiomeType.SPACE;
    }
    // a constructor for actual spawning
    public StormWeaver(Player summonedPlayer, ArrayList<LivingEntity> bossParts, int segmentIndex) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // copy variable
        this.bossParts = bossParts;
        this.segmentIndex = segmentIndex;
        // spawn location
        Location spawnLoc;
        if (segmentIndex == 0) {
            double angle = Math.random() * 720d, dist = 40;
            spawnLoc = summonedPlayer.getLocation().add(
                    MathHelper.xsin_degree(angle) * dist, 40, MathHelper.xcos_degree(angle) * dist);
        } else {
            spawnLoc = bossParts.get(segmentIndex - 1).getLocation().add(0, -1, 0);
        }
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        if (segmentIndex == 0) {
            this.head = this;
            segmentTypeIndex = 0;
        }
        else {
            this.head = (StormWeaver) ((CraftEntity) bossParts.get(0)).getHandle();
            segmentTypeIndex = (segmentIndex + 1 < TOTAL_LENGTH) ? 1 : 2;
        }
        setCustomName(BOSS_TYPE.msgName + "装甲" + NAME_SUFFIXES[segmentTypeIndex]);
        setCustomNameVisible(true);
        if (segmentIndex == 2) {
            addScoreboardTag("isMonster");
            addScoreboardTag("isBOSS");
        }
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("damage", SEGMENT_DAMAGE[segmentTypeIndex][0]);
            attrMap.put("defence", SEGMENT_DEFENCE[segmentTypeIndex][0]);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        if (segmentIndex == 0) {
            bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                    BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        } else {
            bossbar = (BossBattleServer) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_BAR).value();
        }
        // init target map
        {
            if (segmentIndex == 0) {
                targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                        getBukkitEntity(), BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS.msgName,
                        summonedPlayer, true, bossbar);
            } else {
                targetMap = (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
            }
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
            target = summonedPlayer;
        }
        // init health and slime size
        {
            setSize(SLIME_SIZE_ARMORED, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts.add((LivingEntity) bukkitEntity);
            if (segmentIndex == 0)
                BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
            // projectile info
            shootInfoFrostWave = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFrostWaveLightning,
                    EntityHelper.DamageType.MAGIC, "寒霜波");
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapLaser,
                    EntityHelper.DamageType.BULLET, "电击激光");
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
            // next segment
            if (segmentTypeIndex != 2)
                new StormWeaver(summonedPlayer, bossParts, segmentIndex + 1);
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        if (segmentIndex > 0) return;
        // drop loot
        if (getMaxHealth() > 10) {
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);
        }
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // send loot
            terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
        }
    }
    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // update boss bar and dynamic DR
        if (segmentIndex == 0)
            terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, ticksLived, BOSS_TYPE);
        // update health
        setHealth(head.getHealth());
        // load nearby chunks
        if (segmentIndex % 10 == 0) {
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
