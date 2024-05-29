package terraria.entity.boss.postMoonLord.devourerOfGods;

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

public class DevourerOfGods extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_DEVOURER_OF_GODS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 2556000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    public static final int TOTAL_LENGTH = 82;
    static final String[] NAME_SUFFIXES = {"头", "体节", "尾"};
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    DevourerOfGods head;
    static final double[]
            SEGMENT_DAMAGE = {1760d, 1100d, 920d}, SEGMENT_DEFENCE = {100d, 140d, 100d}, SEGMENT_DAMAGE_TAKEN = {1d, 0.075d, 1d};
    static final int SLIME_SIZE = 10;
    static HashMap<String, Double> attrMapFireball, attrMapLaser;
    static {
        attrMapFireball = new HashMap<>();
        attrMapFireball.put("damage", 1128d);
        attrMapFireball.put("knockback", 3.5d);

        attrMapLaser = new HashMap<>();
        attrMapLaser.put("damage", 992d);
        attrMapLaser.put("knockback", 1.75d);
    }
    static final EntityHelper.WormSegmentMovementOptions
            FOLLOW_PROPERTY =
                    new EntityHelper.WormSegmentMovementOptions()
                            .setFollowDistance(SLIME_SIZE * 0.5)
                            .setFollowingMultiplier(1)
                            .setStraighteningMultiplier(-0.1)
                            .setVelocityOrTeleport(false);
    EntityHelper.ProjectileShootInfo shootInfoFireball, shootInfoLaser;
    int segmentIndex, segmentTypeIndex;
    Vector velocityPool = new Vector();







    private double circleAngle = 0.0, rotationDirection = 1.0;
    private int phase = 1; // 0 - flying, 1 - dashing
    private int phaseTicks = 0; // ticks spent in current phase
    private int dashCount = 0; // number of dashes in current dash phase
    private int dashTicks = -1; // ticks spent in current dash
    private boolean isRamming = false; // whether the boss is currently ramming the player

    private void headMovement() {
        if (phase == 0) {
            flyingPhase();
        } else {
            dashingPhase();
        }
        bukkitEntity.setVelocity(velocityPool);
    }

    private void flyingPhase() {
        phaseTicks++;
        if (phaseTicks > 200) { // switch to dash phase after 10 seconds
            phase = 1;
            phaseTicks = 0;
            dashCount = 0;
            dashTicks = -1;

            bossbar.color = BossBattle.BarColor.PURPLE;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
        }

        Location headLocation = getBukkitEntity().getLocation();
        Location targetLocation = target.getLocation();

        if (phaseTicks > 60 && !target.isOnGround()) {
            isRamming = true;
        }
        if (isRamming) {
            ramPlayer(headLocation, targetLocation);
        } else {
            circleAbovePlayer(headLocation, targetLocation);
        }
    }
    private void circleAbovePlayer(Location headLocation, Location targetLocation) {
        double radius = 50.0; // circling radius
        double speed = 2.5; // circling speed
        double amplitude = 8.0; // amplitude of the y-offset fluctuation
        double frequency = 3; // frequency of the y-offset fluctuation

        if (phaseTicks == 1) {
            // Calculate the initial angle based on the player's position
            Vector direction = MathHelper.getDirection(headLocation, targetLocation, 1);
            circleAngle = Math.atan2(direction.getZ(), direction.getX()) - Math.PI; // adjusted angle calculation

            // Determine the rotation direction
            Vector bossVelocity = getBukkitEntity().getVelocity();
            double dotProduct = direction.getX() * bossVelocity.getZ() - direction.getZ() * bossVelocity.getX();
            if (dotProduct > 0) {
                rotationDirection = -1; // counterclockwise
            } else {
                rotationDirection = 1; // clockwise
            }
        } else {
            circleAngle += rotationDirection * speed / radius; // update the angle
        }

        double xOffset = Math.cos(circleAngle) * radius;
        double zOffset = Math.sin(circleAngle) * radius;
        double yOffset = amplitude * Math.sin(circleAngle * frequency); // fluctuating y-offset
        Location alignLoc = targetLocation.clone().add(xOffset, 15 + yOffset, zOffset);
        velocityPool = MathHelper.getDirection(headLocation, alignLoc, speed);
    }

    private void ramPlayer(Location headLocation, Location targetLocation) {
        double speed = 3.25; // ramming speed
        velocityPool = MathHelper.getDirection(headLocation, targetLocation, speed);
    }
    private void dashingPhase() {
        double acceleration = 2.0;
        double maxSpeed = 3.0;
        double dashStartDist = 12.0;
        double minAccelerationAngle = Math.PI / 10;
        double maxAccelerationAngle = Math.PI;

        if (dashCount < 5) { // dash 5 times
            Location headLocation = getBukkitEntity().getLocation();
            Location targetLocation = target.getLocation();
            Vector direction = MathHelper.getDirection(headLocation, targetLocation, 1);

            if (dashTicks == -1) { // acceleration phase
                if (velocityPool.lengthSquared() < 1e-5) {
                    velocityPool = direction.clone().multiply(0.1); // initialize velocityPool if it's zero
                }
                Vector currentDirection = velocityPool.clone().normalize();
                double angleBetweenDirections = Math.acos(currentDirection.dot(direction));

                // Limit the maximum acceleration angle
                double accelerationAngle = Math.min(maxAccelerationAngle,
                        minAccelerationAngle + (maxAccelerationAngle - minAccelerationAngle) * phaseTicks / 25);
                if (angleBetweenDirections > accelerationAngle) {
                    direction = MathHelper.rotateAroundAxisRadian(currentDirection,
                            MathHelper.getNonZeroCrossProd(currentDirection, direction), accelerationAngle);
                }

                // Use a more gradual acceleration curve
                velocityPool.add(direction.multiply(acceleration));
                MathHelper.setVectorLength(velocityPool, maxSpeed, true);

                if (headLocation.distance(targetLocation) <= dashStartDist) {
                    dashTicks = 0; // start straight dash phase
                    // Calculate the direction towards the target and multiply it by the maximum speed
                    velocityPool = MathHelper.getDirection(headLocation, targetLocation, maxSpeed);
                }
            }
            else { // straight dash phase
                dashTicks++;
                if (dashTicks > 20) {
                    dashCount++;
                    dashTicks = -1; // reset to acceleration phase
                }
            }
        } else {
            phase = 0; // switch back to flying phase
            phaseTicks = 0;
            isRamming = false;

            bossbar.color = BossBattle.BarColor.BLUE;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
        }
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

                    // debuff
                    for (UUID uid : targetMap.keySet())
                        EntityHelper.applyEffect(Bukkit.getPlayer(uid), "极限重力", 310);

                    headMovement();

                    // follow
                    EntityHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, segmentIndex);
                    // rotation
                    this.yaw = (float) MathHelper.getVectorYaw( velocityPool );
                    this.pitch = (float) MathHelper.getVectorPitch( velocityPool );
                }
                // update facing direction from handleSegmentsFollow
                {
                    MetadataValue valYaw = EntityHelper.getMetadata(bukkitEntity, "yaw");
                    if (valYaw != null) this.yaw = valYaw.asFloat();
                    MetadataValue valPitch = EntityHelper.getMetadata(bukkitEntity, "pitch");
                    if (valPitch != null) this.pitch = valPitch.asFloat();
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public DevourerOfGods(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if (BossHelper.bossMap.containsKey(BossHelper.BossType.STORM_WEAVER.msgName) )
            return false;
        if (BossHelper.bossMap.containsKey(BossHelper.BossType.CEASELESS_VOID.msgName) )
            return false;
        if (BossHelper.bossMap.containsKey(BossHelper.BossType.SIGNUS_ENVOY_OF_THE_DEVOURER.msgName) )
            return false;
        return true;
    }
    // a constructor for actual spawning
    public DevourerOfGods(Player summonedPlayer, ArrayList<LivingEntity> bossParts, int segmentIndex) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // copy variable
        this.bossParts = bossParts;
        this.segmentIndex = segmentIndex;
        // spawn location
        Location spawnLoc;
        if (segmentIndex == 0) {
            double angle = Math.random() * 720d, dist = 64;
            spawnLoc = summonedPlayer.getLocation().add(
                    MathHelper.xsin_degree(angle) * dist, 32, MathHelper.xcos_degree(angle) * dist);
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
            this.head = (DevourerOfGods) ((CraftEntity) bossParts.get(0)).getHandle();
            segmentTypeIndex = (segmentIndex + 1 < TOTAL_LENGTH) ? 1 : 2;
        }
        setCustomName("装甲" + BOSS_TYPE.msgName + NAME_SUFFIXES[segmentTypeIndex]);
        setCustomNameVisible(true);
        if (segmentTypeIndex != 1) {
            addScoreboardTag("isMonster");
        }
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("damage", SEGMENT_DAMAGE[segmentTypeIndex]);
            attrMap.put("defence", SEGMENT_DEFENCE[segmentTypeIndex]);
            attrMap.put("damageTakenMulti", SEGMENT_DAMAGE_TAKEN[segmentTypeIndex]);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        if (segmentIndex == 0) {
            bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                    BossBattle.BarColor.PURPLE, BossBattle.BarStyle.PROGRESS);
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
            setSize(SLIME_SIZE, false);
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
            shootInfoFireball = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFireball,
                    EntityHelper.DamageType.MAGIC, "弑神火球");
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapLaser,
                    EntityHelper.DamageType.BULLET, "弑神激光");
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
            // next segment
            if (segmentTypeIndex != 2)
                new DevourerOfGods(summonedPlayer, bossParts, segmentIndex + 1);
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
