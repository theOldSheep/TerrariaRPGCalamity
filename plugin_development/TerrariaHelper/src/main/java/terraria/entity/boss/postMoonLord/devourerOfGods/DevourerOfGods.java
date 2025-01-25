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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.postMoonLord.ceaselessVoid.CeaselessVoid;
import terraria.entity.boss.postMoonLord.signus.Signus;
import terraria.entity.boss.postMoonLord.stormWeaver.StormWeaver;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.*;

public class DevourerOfGods extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_DEVOURER_OF_GODS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 2556000 * 2, BASIC_HEALTH_BR = 3600000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    public static final int TOTAL_LENGTH = 82;
    static final String[] NAME_SUFFIXES = {"头", "体节", "尾"};
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    public String COLOR_PREFIX = "§#00FFFF", MSG_SPAWN = "你不是神……但你的灵魂仍是我的盛宴！",
            MSG_FINAL_STAGE = "还没完呢！", MSG_KILL = "致命失误！";
    DevourerOfGods head;
    StormWeaver stormWeaver = null;
    CeaselessVoid ceaselessVoid = null;
    Signus signus = null;
    static final double[]
            SEGMENT_DAMAGE = {1760d, 1100d, 920d}, SEGMENT_DEFENCE = {100d, 140d, 100d}, SEGMENT_DAMAGE_TAKEN = {0.9d, 0.15d, 1d};
    static final int SLIME_SIZE = 10, DAMAGE_TAKEN_INTERPOLATE_SEGMENTS = 15;

    static final int FLYING_TOTAL_DURATION = 300, FLYING_START_INDEX = 60, FLYING_END_INDEX = 240;
    static final double SEGMENT_RADIUS = SLIME_SIZE * 0.5, DASH_DISTANCE_INITIAL = 15.0, DASH_DISTANCE_FINAL = 12.0,
            HIDE_Y_COORD = -100;
    static HashMap<String, Double> attrMapFireball, attrMapLaser;
    static {
        attrMapFireball = new HashMap<>();
        attrMapFireball.put("damage", 1128d);
        attrMapFireball.put("knockback", 3.5d);

        attrMapLaser = new HashMap<>();
        attrMapLaser.put("damage", 992d);
        attrMapLaser.put("knockback", 1.75d);
    }
    static final EntityMovementHelper.WormSegmentMovementOptions
            FOLLOW_PROPERTY =
            new EntityMovementHelper.WormSegmentMovementOptions()
                    .setFollowDistance(SEGMENT_RADIUS)
                    .setFollowingMultiplier(1)
                    .setStraighteningMultiplier(-0.1)
                    .setVelocityOrTeleport(false);
    EntityHelper.ProjectileShootInfo shootInfoFireball, shootInfoLaser;
    int segmentIndex, segmentTypeIndex;
    Vector velocityPool = new Vector();

    double circleAngle = 0.0, rotationDirection = 1.0;
    int phase = 1; // 0 - flying, 1 - dashing
    int phaseTicks = 0; // ticks spent in current phase
    int dashCount = 0; // number of dashes in current dash phase
    int dashTicks = -1; // ticks spent in current dash
    boolean isRamming = false; // whether the boss is currently ramming the player
    boolean hasRetargeted = false;
    int stage = 0; // 0 - initial, 1 - minion phase, 2 - final
    int minionIndex = 0; // 0 - storm weaver, 1 - ceaseless void, 2 - signus, 3 - delay before the next phase
    int finalPhaseDelayTicks = 200; // delay before starting the final phase
    double dashDistance = DASH_DISTANCE_INITIAL; // Distance for dashing based on the current stage
    private Queue<Wormhole> wormholes = new ArrayDeque<>(); // Queue to hold multiple wormholes
    private class Wormhole {
        Location in, out;
        int segmentBehindIndex; // Index of segment to teleport next
        GodSlayerPortal inPortal, outPortal;

        public Wormhole(DevourerOfGods dog, Location in, Location out) {
            this.in = in;
            this.out = out;
            this.segmentBehindIndex = 1;

            inPortal = new GodSlayerPortal(dog, in);
            outPortal = new GodSlayerPortal(dog, out);
        }
        // Call this to help you remove the wormhole visualization!
        void remove() {
            inPortal.die();
            outPortal.die();
        }
    }

    private void toPhase(int newPhase) {
        phase = newPhase;
        phaseTicks = 0;
        dashCount = 0;
        dashTicks = -1;
        isRamming = false;

        bossbar.color = newPhase == 1 ? BossBattle.BarColor.PURPLE : BossBattle.BarColor.BLUE;
        bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
    }
    private void headMovement() {
        phaseTicks ++;
        if (stage == 1) {
            Location targetLoc = target.getLocation();
            targetLoc.setY(HIDE_Y_COORD);
            circleAboveLocation(bukkitEntity.getLocation(), targetLoc);
        }
        else {
            if (phase == 0) {
                flyingPhase();
            } else {
                dashingPhase();
            }
        }
        bukkitEntity.setVelocity(velocityPool);
    }
    private void flyingPhase() {
        if (phaseTicks > FLYING_TOTAL_DURATION) { // switch to dash phase
            toPhase(1);
        }

        Location headLocation = getBukkitEntity().getLocation();
        Location targetLocation = target.getLocation();

        // Schedule the projectile attack
        if (phaseTicks == FLYING_START_INDEX)
            startBulletHell();
        if (!isRamming) {
            // Wormhole logic. Only applicable to non-ramming situations.
            if (stage == 2 && phaseTicks < FLYING_END_INDEX) {
                // Circle at y = HIDE_Y_COORD in the last stage
                targetLocation.setY(HIDE_Y_COORD);
                // Open wormhole
                if (phaseTicks == 1) {
                    openWormhole(targetLocation);
                }
            }
            // Enter ramming phase
            if (phaseTicks > FLYING_START_INDEX && phaseTicks < FLYING_END_INDEX && !target.isOnGround()) {
                isRamming = true;
                // Open wormhole in front of target
                if (stage == 2) {
                    Location wormholeDestination = target.getEyeLocation().add(
                            MathHelper.setVectorLength(target.getLocation().getDirection(), 16) );
                    openWormhole(wormholeDestination);
                }
            }
        }

        if (isRamming) {
            // ramming speed
            velocityPool = MathHelper.getDirection(headLocation, targetLocation, 3.25);
        } else {
            circleAboveLocation(headLocation, targetLocation);
            if (stage == 2 && phaseTicks == FLYING_END_INDEX) {
                // Open wormhole above the player at the end of the circling phase
                Location wormholeDestination = target.getLocation().add(0, 16, 0);
                openWormhole(wormholeDestination);
            }
        }
    }
    private void startBulletHell() {
        JavaPlugin plugin = TerrariaHelper.getInstance();
        AttackManager attackManager;
        if (stage == 0) {
            attackManager = new AttackManager(plugin, target, shootInfoLaser,
                    FLYING_END_INDEX - FLYING_START_INDEX, 30, 2,
                    new DelayedWallAttackPattern(45, 5, 12, 0, DelayedWallAttackPattern.GRID),
                    new DelayedWallAttackPattern(45, 5, 12, 0, DelayedWallAttackPattern.GRID_SLANTED),
                    new CircleAttackPattern(16, 6, 0, 120)
            );
        }
        else {
            attackManager = new AttackManager(plugin, target, shootInfoFireball,
                    FLYING_END_INDEX - FLYING_START_INDEX, 60, 3,
                    new DelayedWallAttackPattern(45, 5, 12, 2, DelayedWallAttackPattern.RANDOM),
                    new DelayedWallAttackPattern(45, 5, 12, 0, DelayedWallAttackPattern.GRID),
                    new DelayedWallAttackPattern(45, 5, 12, 0, DelayedWallAttackPattern.GRID_SLANTED),
                    new CircleAttackPattern(20, 6, 2, 120),
                    new SwingingArcAttackPattern(20, 6, 2, 120, 0)
            );

            AttackManager attackManagerExtra = new AttackManager(plugin, target, shootInfoFireball,
                    FLYING_END_INDEX - FLYING_START_INDEX - 30, 60, 1,
                    new ScatteringCircleAttackPattern(40, 16, 0, 16)
            );
            Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), attackManagerExtra::start, 30);
        }
        attackManager.start();
    }
    private void circleAboveLocation(Location headLocation, Location targetLocation) {
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
    private void dashingPhase() {
        double acceleration = 2.0;
        double maxSpeed = 3.0;
        double dashStartDist = dashDistance;
        double minAccelerationAngle = Math.PI / 10;
        double maxAccelerationAngle = Math.PI / 10;

        if (dashCount < 5) { // dash 5 times
            Location headLocation = getBukkitEntity().getLocation();
            Location targetLocation = target.getLocation();
            Vector direction = MathHelper.getDirection(headLocation, targetLocation, 1);

            if (dashTicks == -1) { // acceleration phase
                if (velocityPool.lengthSquared() < 1e-5) {
                    velocityPool = direction.clone().multiply(0.1); // initialize velocityPool if it's zero
                }
                Vector currentDirection = velocityPool.clone();
                MathHelper.setVectorLength(currentDirection, 1d);
                double angleBetweenDirections = Math.acos(currentDirection.dot(direction));

                // Limit the maximum acceleration angle
                double accelerationAngle = Math.min(maxAccelerationAngle,
                        minAccelerationAngle + (maxAccelerationAngle - minAccelerationAngle) * phaseTicks / 100);
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
            // switch back to flying phase
            toPhase(0);
        }
    }
    private void handleMinionPhaseAndRetarget() {
        if (segmentIndex == 0 && wormholes.size() == 0) {
            // NOTE: the minions do not have a common interface/class defining the variables used. Optimizing this section would take unreasonably long.
            switch (minionIndex) {
                case 0:
                    // Summon the minion
                    if (stormWeaver == null) {
                        stormWeaver = new StormWeaver(target);
                        stormWeaver.targetMap.clear();
                        stormWeaver.targetMap.putAll(targetMap);
                    }
                    // Go to the next minion
                    if (!stormWeaver.isAlive()) {
                        minionIndex++;
                    } else {
                        target = stormWeaver.target;
                        hasRetargeted = true;
                    }
                    break;
                case 1:
                    // Summon the minion
                    if (ceaselessVoid == null) {
                        ceaselessVoid = new CeaselessVoid(target);
                        ceaselessVoid.targetMap.clear();
                        ceaselessVoid.targetMap.putAll(targetMap);
                    }
                    // Go to the next minion
                    if (!ceaselessVoid.isAlive()) {
                        minionIndex++;
                    } else {
                        target = ceaselessVoid.target;
                        hasRetargeted = true;
                    }
                    break;
                case 2:
                    // Summon the minion
                    if (signus == null) {
                        signus = new Signus(target);
                        signus.targetMap.clear();
                        signus.targetMap.putAll(targetMap);
                    }
                    // Start the delay
                    if (!signus.isAlive()) {
                        minionIndex++;
                    } else {
                        target = signus.target;
                        hasRetargeted = true;
                    }
                    break;
                case 3:
                    setHealth(getMaxHealth() * 0.599f);
                    // Go to the next stage
                    if (--finalPhaseDelayTicks <= 0) {
                        Bukkit.broadcastMessage(COLOR_PREFIX + MSG_FINAL_STAGE);
                        stage = 2;
                        // reset to dashing phase
                        toPhase(1);

                        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, null);
                        dashDistance = DASH_DISTANCE_FINAL;

                        Location wormholeDestination = target.getEyeLocation().add(
                                MathHelper.setVectorLength(target.getLocation().getDirection(), 32) );
                        openWormhole(wormholeDestination);
                    }
                    break;
            }
        }
    }
    // This should only be called by the head segment!
    private void openWormhole(Location destination) {
        Wormhole newWormhole = new Wormhole(this, bukkitEntity.getLocation(), destination.clone());
        wormholes.add(newWormhole);
        teleportWithWormhole((LivingEntity) bukkitEntity, newWormhole); // Teleport the head to the new wormhole
    }
    private void teleportWithWormhole(LivingEntity entity, Wormhole wormhole) {
        entity.teleport(wormhole.out);
    }
    private void handleFollowAndWormholes() {
        int endIndex = TOTAL_LENGTH;
        // Iterate through each wormhole
        for (Wormhole wormhole : wormholes) {
            // Check if there are segments left to teleport for this wormhole
            if (wormhole.segmentBehindIndex < TOTAL_LENGTH) {
                double prevDist = bossParts.get(wormhole.segmentBehindIndex - 1).getLocation().distance(wormhole.out);
                LivingEntity effectiveHead = bossParts.get(wormhole.segmentBehindIndex);

                // Check if previous segment is far enough away from the out portal for this wormhole
                if (prevDist >= SEGMENT_RADIUS) {
                    teleportWithWormhole(effectiveHead, wormhole);
                    wormhole.segmentBehindIndex++;
                    prevDist = 0;
                }

                // The effective head's velocity and segments following it, if applicable
                if (wormhole.segmentBehindIndex < TOTAL_LENGTH) {
                    effectiveHead = bossParts.get(wormhole.segmentBehindIndex);
                } else {
                    effectiveHead = null;
                }
                if (effectiveHead != null) {
                    // Adjust the next segment's position relative to the wormhole entrance
                    double distanceToInPortal = SEGMENT_RADIUS - prevDist;
                    Vector directionToInPortal = MathHelper.getDirection(effectiveHead.getLocation(), wormhole.in, 1);
                    Location targetLocation = wormhole.in.clone().subtract(directionToInPortal.multiply(distanceToInPortal));
                    effectiveHead.teleport(targetLocation);

                    // Make the segment face the wormhole
                    EntityHelper.setMetadata(effectiveHead, "yaw", (float) MathHelper.getVectorYaw(directionToInPortal));
                    EntityHelper.setMetadata(effectiveHead, "pitch", (float) MathHelper.getVectorPitch(directionToInPortal));

                    EntityMovementHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, wormhole.segmentBehindIndex, endIndex);
                    endIndex = wormhole.segmentBehindIndex;
                }
            }
        }

        // Pop out finished wormholes
        while (!wormholes.isEmpty() && wormholes.peek().segmentBehindIndex >= TOTAL_LENGTH)
            wormholes.poll().remove();

        // segments that should follow the head
        EntityMovementHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, segmentIndex, endIndex);
        this.yaw = (float) MathHelper.getVectorYaw(velocityPool);
        this.pitch = (float) MathHelper.getVectorPitch(velocityPool);
    }
    private void AI() {
        // No AI after death
        if (getHealth() <= 0d) return;

        // body & tail segment status update for the last phase
        if (head.stage == 1 && this.stage != head.stage) {
            this.stage = head.stage;
            setCustomName(BOSS_TYPE.msgName + NAME_SUFFIXES[segmentTypeIndex]);
        }

        // head
        if (segmentIndex == 0) {
            // Update stage based on health
            if (stage == 0 && getHealth() < getMaxHealth() * 0.605) {
                stage = 1;
                setCustomName(BOSS_TYPE.msgName + NAME_SUFFIXES[segmentTypeIndex]);

                Location wormholePos = target.getLocation();
                wormholePos.setY(-20);
                openWormhole(wormholePos);
            }

            // Target handling
            hasRetargeted = false;
            if (stage == 1) {
                handleMinionPhaseAndRetarget();
            }
            if (!hasRetargeted) {
                Player previousTarget = target;
                target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                        IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
                // Target changed
                if (target != previousTarget) {
                    Bukkit.broadcastMessage(COLOR_PREFIX + MSG_KILL);
                    // Reset flying phase duration
                    if (stage != 1 && phase == 0) {
                        toPhase(0);
                    }
                }
            }

            // Disappear if no target
            if (target == null) {
                for (LivingEntity segment : bossParts) {
                    segment.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    segment.setHealth(0d);
                    segment.remove();
                }
                return;
            }

            // debuff
            for (UUID uid : targetMap.keySet()) {
                Player ply = Bukkit.getPlayer(uid);
                if (ply == null)
                    continue;
                EntityHelper.applyEffect(ply, "极限重力", 310);
            }

            // increase player aggro duration
            targetMap.get(target.getUniqueId()).addAggressionTick();

            // Attack
            headMovement();

            // following & rotations
            handleFollowAndWormholes();
        }
        // update facing direction
        {
            MetadataValue valYaw = EntityHelper.getMetadata(bukkitEntity, "yaw");
            if (valYaw != null) this.yaw = valYaw.asFloat();
            MetadataValue valPitch = EntityHelper.getMetadata(bukkitEntity, "pitch");
            if (valPitch != null) this.pitch = valPitch.asFloat();
        }

        // Collision damage
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
        addScoreboardTag("isMonster");
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
            // Calculate damageTakenMulti using quadratic interpolation
            if (segmentTypeIndex == 1) { // Middle segments
                int distFromHead = segmentIndex;
                int distFromTail = (TOTAL_LENGTH - 1) - segmentIndex;
                // Use the smaller of the two distances to ensure the most vulnerable points are near the middle
                double interpolationDistance = Math.min(distFromHead, distFromTail);
                // Larger means closer to head / tail, zero means middle body segment
                double interpolationFactor = Math.max(1 - interpolationDistance / DAMAGE_TAKEN_INTERPOLATE_SEGMENTS, 0d);
                // Quadratic interpolation for quicker decay
                interpolationFactor *= interpolationFactor;
                double endSegmentDamageTakenMulti = SEGMENT_DAMAGE_TAKEN[distFromHead < distFromTail ? 0 : 2];
                double damageTakenMulti = SEGMENT_DAMAGE_TAKEN[1] + interpolationFactor * (endSegmentDamageTakenMulti - SEGMENT_DAMAGE_TAKEN[1]);
                attrMap.put("damageTakenMulti", damageTakenMulti);
            } else {
                attrMap.put("damageTakenMulti", SEGMENT_DAMAGE_TAKEN[segmentTypeIndex]); // Head or tail
            }
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
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
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
            // lock health at 60.1%
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, health * 0.601);
        }
        // boss parts and other properties
        {
            bossParts.add((LivingEntity) bukkitEntity);
            if (segmentIndex == 0)
                BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
            // projectile info; the vector specified the projectile speed!
            shootInfoFireball = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(2, 0, 0), attrMapFireball,
                    DamageHelper.DamageType.MAGIC, "弑神火球");
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(2, 0, 0), attrMapLaser,
                    DamageHelper.DamageType.BULLET, "弑神激光");
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
            // next segment
            if (segmentTypeIndex != 2)
                new DevourerOfGods(summonedPlayer, bossParts, segmentIndex + 1);
        }
        // summon message
        if (segmentIndex == 0)
            Bukkit.broadcastMessage(COLOR_PREFIX + MSG_SPAWN);
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        for (Wormhole wormhole : wormholes)
            wormhole.remove();
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
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        // update boss bar and dynamic DR
        if (segmentIndex == 0)
            terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, ticksLived, BOSS_TYPE);
        // update health
        setHealth(head.getHealth());
        // load nearby chunks
        if (segmentIndex % TerrariaHelper.Constants.WORM_BOSS_CHUNK_LOAD_SEGMENT_INTERVAL == 0) {
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
