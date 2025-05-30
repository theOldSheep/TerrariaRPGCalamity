package terraria.entity.boss.postMoonLord.exoMechs;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.*;

public class Thanatos extends EntitySlime {
    public enum SegmentType {
        HEAD("XM-05“塔纳托斯”激光头", "XM-05“塔纳托斯”头", 2232d),
        BODY("XM-05“塔纳托斯”散热体节", "XM-05“塔纳托斯”体节", 1920d),
        TAIL("XM-05“塔纳托斯”尾", "XM-05“塔纳托斯”尾", 1600d);

        private String openName, closedName;
        private double damage;

        SegmentType(String openName, String closedName, double damage) {
            this.openName = openName;
            this.closedName = closedName;
            this.damage = damage;
        }

        public double getDamage() {
            return damage;
        }
    }

    private enum AttackMethod {
        LASER_PROJECTILE,
        DASH,
        GAMMA_LASER_RAY
    }
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final double BASIC_HEALTH = 2760000 * 2, BASIC_HEALTH_BR = 1275000 * 2;
    public static final int TOTAL_LENGTH = 102, SLIME_SIZE = 8;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    Draedon owner = null;
    // other variables and AI
    static final double LASER_SPEED = 3.25,
            FINAL_LASER_MINIMUM_LENGTH = 24.0, FINAL_LASER_WIDTH = 0.5;
    private static final int ARMOR_CLOSE_COUNTDOWN = 60;
    private static final int LASER_DELAY = 1;
    static AimHelper.AimHelperOptions
            AIM_HELPER_LASER, AIM_HELPER_LASER_GENTLE, AIM_HELPER_LASER_ACC;
    static GenericHelper.StrikeLineOptions STRIKE_OPTION_GAMMA_LASER;
    static HashMap<String, Double> ATTR_MAP_LASER, ATTR_MAP_FINAL_LASER;
    EntityHelper.ProjectileShootInfo shootInfoLaser;
    static {
        ATTR_MAP_LASER = new HashMap<>();
        ATTR_MAP_LASER.put("damage", 1440d);
        ATTR_MAP_LASER.put("knockback", 1.5d);
        ATTR_MAP_FINAL_LASER = new HashMap<>();
        ATTR_MAP_FINAL_LASER.put("damage", 2100d);
        ATTR_MAP_FINAL_LASER.put("knockback", 2.25d);

        AIM_HELPER_LASER = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(LASER_SPEED);
        AIM_HELPER_LASER_GENTLE = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(LASER_SPEED)
                .setEpoch(1);
        AIM_HELPER_LASER_ACC = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(LASER_SPEED)
                .setAccelerationMode(true);

        STRIKE_OPTION_GAMMA_LASER = new GenericHelper.StrikeLineOptions()
                .setThruWall(true)
                .setParticleInfo(
                        new GenericHelper.ParticleLineOptions()
                                .setVanillaParticle(false)
                                .setParticleColor("b/tnt")
                                .setTicksLinger(2)
                                .setIntensityMulti(0.5));
    }
    static final EntityMovementHelper.WormSegmentMovementOptions followOption = new EntityMovementHelper.WormSegmentMovementOptions()
            .setStraighteningMultiplier(-0.1)
            .setFollowingMultiplier(1)
            .setFollowDistance(SLIME_SIZE * 0.5)
            .setVelocityOrTeleport(false);
    static final EntityMovementHelper.WormSegmentMovementOptions followOptionStationary = new EntityMovementHelper.WormSegmentMovementOptions()
            .setStraighteningMultiplier(0)
            .setFollowingMultiplier(1)
            .setFollowDistance(SLIME_SIZE * 0.5)
            .setVelocityOrTeleport(false);
    List<Thanatos> segments;

    List<LivingEntity> livingSegments;
    HashSet<Entity> finalLaserDamaged = new HashSet<>();
    Thanatos head;
    static final int LASER_INTERVAL_EASY = 5, LASER_INTERVAL_NORMAL = 3, LASER_INTERVAL_HARD = 1;
    protected int index, laserFireInterval = 3, armorCloseCountdown = 1;
    SegmentType segmentType;
    AttackMethod attackMethod = AttackMethod.LASER_PROJECTILE;
    int ticks = 0;
    Vector velocity = new Vector();
    double desiredSpeed = 2.5;

    public void setOpen(boolean open) {
        if (open && armorCloseCountdown <= 0) {
            AttributeHelper.tweakAttribute(attrMap, "defenceMulti", "0.1", false);
            addScoreboardTag("isMonster");
            setCustomName(segmentType.openName);
        } else if (!open && armorCloseCountdown <= 0) {
            AttributeHelper.tweakAttribute(attrMap, "defenceMulti", "0.1", true);
            removeScoreboardTag("isMonster");
            setCustomName(segmentType.closedName);
        }
        if (open) {
            armorCloseCountdown = ARMOR_CLOSE_COUNTDOWN;
        }
    }

    private void tick() {
        // for all segments, the open & close mechanism should apply.
        if (armorCloseCountdown > 0) {
            armorCloseCountdown--;
            if (armorCloseCountdown <= 0) {
                setOpen(false);
            }
        }
        if (segmentType == SegmentType.HEAD) {
            if (!owner.isSubBossActive(Draedon.SubBossType.THANATOS)) {
                handleHeadIdleMovement();
                attackMethod = AttackMethod.LASER_PROJECTILE;
                ticks = 0;
                // Let the remaining segments follow the head
                EntityMovementHelper.handleSegmentsFollow(livingSegments, followOptionStationary);
            }
            else {
                Draedon.Difficulty difficulty = owner.calculateDifficulty(this);

                switch (attackMethod) {
                    case LASER_PROJECTILE:
                        handleLaserProjectileAttack(difficulty);
                        break;
                    case DASH:
                        handleDashAttack(difficulty);
                        break;
                    case GAMMA_LASER_RAY:
                        handleGammaLaserAttack();
                        break;
                }
                adjustLength();
                ticks++;
                // Let the remaining segments follow the head
                EntityMovementHelper.handleSegmentsFollow(livingSegments, followOption);
            }
            // Movement
            bukkitEntity.setVelocity(velocity);
        } else {
            // Set the health of subsequent entities to the health of the head
            setHealth(head.getHealth());
            checkLaserChain();
        }
    }

    private void adjustLength() {
        double velLen = velocity.length();
        double diff = desiredSpeed - velLen;
        double diffAbs = Math.abs(diff);
        velLen += Math.min(0.05, diffAbs) * Math.signum(diff);
        MathHelper.setVectorLength(velocity, velLen);
    }
    // the head uses this to initiate laser beam pattern
    private void handleLaserProjectileAttack(Draedon.Difficulty difficulty) {
        switch (difficulty) {
            case LOW:
                laserFireInterval = LASER_INTERVAL_EASY;
                break;
            case MEDIUM:
                laserFireInterval = LASER_INTERVAL_NORMAL;
                break;
            case HIGH:
                laserFireInterval = LASER_INTERVAL_HARD;
                break;
        }
        int completeFireDuration = TOTAL_LENGTH * LASER_DELAY / laserFireInterval + ARMOR_CLOSE_COUNTDOWN;
        if (ticks % completeFireDuration == ARMOR_CLOSE_COUNTDOWN / 2) {
            int randomIndex = new Random().nextInt(laserFireInterval) + 1;
            segments.get(randomIndex).fireLaser();
        }
        if (ticks >= completeFireDuration && difficulty != Draedon.Difficulty.LOW) {
            attackMethod = AttackMethod.DASH;
            ticks = 0;
        }
        desiredSpeed = 1.75;
        Vector idealVelocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(), desiredSpeed);
        velocity = MathHelper.rotationInterpolateDegree(velocity, idealVelocity, 6);
    }

    private void handleDashAttack(Draedon.Difficulty difficulty) {
        int dashDuration = difficulty == Draedon.Difficulty.HIGH ? 10 : 15;
        // Windup
        if (ticks < 40) {
            desiredSpeed = 2.5;

            double angle = 25 * ticks / 40.0;
            Location targetLocation = target.getEyeLocation();
            if (!owner.isSubBossActive(Draedon.SubBossType.THANATOS)) {
                targetLocation.setY(-50);
            }
            Vector idealVelocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), targetLocation, desiredSpeed);
            velocity = MathHelper.rotationInterpolateDegree(velocity, idealVelocity, angle);
            if (bukkitEntity.getLocation().distanceSquared(targetLocation) < 400) {
                ticks = 39;
            }
        }
        // Dash
        else if (ticks == 40) {
            desiredSpeed = difficulty == Draedon.Difficulty.HIGH ? 5.0 : 3.5;
            Location targetLocation = target.getEyeLocation();
            if (!owner.isSubBossActive(Draedon.SubBossType.THANATOS)) {
                targetLocation.setY(-50);
            }
            else {
                owner.playWarningSound(false);
            }
            velocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), targetLocation, desiredSpeed);
        }
        // Slow
        else if (ticks == 40 + dashDuration) {
            desiredSpeed = 3.5;
        }
        // Next phase
        else if (ticks >= 40 + dashDuration) {
            if (difficulty == Draedon.Difficulty.HIGH) {
                attackMethod = AttackMethod.GAMMA_LASER_RAY;

                bossbar.color = BossBattle.BarColor.RED;
                bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
            } else {
                attackMethod = AttackMethod.LASER_PROJECTILE;
            }
            ticks = 0;
        }
    }

    private void handleGammaLaserAttack() {
        Location targetLocation = target.getLocation().add(target.getEyeLocation()).multiply(0.5);
        if (!owner.isSubBossActive(Draedon.SubBossType.THANATOS)) {
            targetLocation.setY(-50);
        }
        // do not start the attack until getting real close
        double turningRate = ticks >= 5 ? 3 : 12;
        if (ticks == 1) {
            if (targetLocation.distanceSquared(bukkitEntity.getLocation()) < FINAL_LASER_MINIMUM_LENGTH * FINAL_LASER_MINIMUM_LENGTH) {
                desiredSpeed = 0.75;
            }
            else {
                ticks = -1;
                desiredSpeed = 3;
            }
        }
        Vector idealVelocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), targetLocation, desiredSpeed);
        velocity = MathHelper.rotationInterpolateDegree(velocity, idealVelocity, turningRate);

        // warning sound
        if (ticks == 1) {
            owner.playWarningSound(true);
        }
        // laser
        else if (ticks >= 40 && ticks <= 140) {
            setOpen(true);
            tickGammaLaser(velocity);
        }
        // next attack
        else if (ticks >= 160) {
            attackMethod = AttackMethod.LASER_PROJECTILE;
            ticks = 0;

            bossbar.color = BossBattle.BarColor.GREEN;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
        }
    }

    private void handleHeadIdleMovement() {
        // Handle movement
        Location targetLocation = target.getEyeLocation();
        targetLocation.setY(-50);

        // Calculate the target velocity
        Vector targetVelocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), targetLocation, 2.5);

        // Smoothly update the velocity
        double intensity = 0.05;
        velocity.multiply(1 - intensity)
                .add(targetVelocity.multiply(intensity));
    }

    public void fireLaser() {
        if (armorCloseCountdown <= 0) {
            setOpen(true);
            Draedon.Difficulty difficulty = owner.calculateDifficulty(this);
            switch (difficulty) {
                case LOW:
                    spawnSingleLaser(AIM_HELPER_LASER_GENTLE);
                    break;
                case MEDIUM:
                    spawnSingleLaser(AIM_HELPER_LASER_GENTLE);
                    spawnSingleLaser(AIM_HELPER_LASER);
                    break;
                case HIGH:
                    spawnSingleLaser(AIM_HELPER_LASER);
                    spawnSingleLaser(AIM_HELPER_LASER_ACC);
                    break;
            }
        }
    }
    private void spawnSingleLaser(AimHelper.AimHelperOptions aimHelper) {
        shootInfoLaser.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        Location targetLoc = aimHelper == null ? target.getEyeLocation() :
                AimHelper.helperAimEntity(shootInfoLaser.shootLoc, target, aimHelper);
        shootInfoLaser.velocity = MathHelper.getDirection(shootInfoLaser.shootLoc, targetLoc, LASER_SPEED);
        EntityHelper.spawnProjectile(shootInfoLaser);
    }
    public void checkLaserChain() {
        if (armorCloseCountdown <= 0 && index > head.laserFireInterval) {
            Thanatos previousSegment = segments.get(index - head.laserFireInterval);
            if (previousSegment.armorCloseCountdown == ARMOR_CLOSE_COUNTDOWN - LASER_DELAY) {
                fireLaser();
            }
        }
    }
    public void tickGammaLaser(Vector laserDir) {
        double laserLen = target.getLocation().distance(bukkitEntity.getLocation()) + 4;
        laserLen = Math.max(laserLen, FINAL_LASER_MINIMUM_LENGTH);
        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                MathHelper.getVectorYaw(laserDir), MathHelper.getVectorPitch(laserDir),
                laserLen, FINAL_LASER_WIDTH, "", "",
                finalLaserDamaged, ATTR_MAP_FINAL_LASER, STRIKE_OPTION_GAMMA_LASER);
    }

    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            
            // attack
            if (target != null) {
                tick();

                // facing
                MetadataValue valYaw = EntityHelper.getMetadata(bukkitEntity, "yaw");
                if (valYaw != null) this.yaw = valYaw.asFloat();
                else this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Thanatos(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Thanatos(Draedon draedon, Location spawnLoc) {
        this(draedon, spawnLoc, new ArrayList<>(), new ArrayList<>(), null, 0);
    }
    private Thanatos(Draedon draedon, Location spawnLoc, List<Thanatos> segments, List<LivingEntity> livingSegments, Thanatos head, int index) {
        super( draedon.getWorld() );
        owner = draedon;
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        draedon.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.head = head;
        this.index = index;
        if (index == 0) {
            segmentType = SegmentType.HEAD;
        } else if (index == TOTAL_LENGTH - 1) {
            segmentType = SegmentType.TAIL;
        } else {
            segmentType = SegmentType.BODY;
        }

        setCustomName(segmentType.closedName);
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        if (index > 0) {
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.bukkitEntity);
        }
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", segmentType.getDamage());
            attrMap.put("defence", 300d);
            attrMap.put("damageTakenMulti", 0.25d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.BULLET);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = draedon.bossbar;
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = owner.targetMap;
            target = owner.target;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(SLIME_SIZE, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = owner.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // segment info
        {
            this.segments = segments;
            this.livingSegments = livingSegments;
            this.segments.add(this);
            this.livingSegments.add((LivingEntity) bukkitEntity);

            if (index < TOTAL_LENGTH - 1) {
                Location nextSpawnLoc = spawnLoc.subtract(0, 1, 0);
                new Thanatos(draedon, nextSpawnLoc, segments, livingSegments, head == null ? this : head, index + 1);
            }
        }
        // shoot info's
        {
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), ATTR_MAP_LASER,
                    DamageHelper.DamageType.ARROW, "红色星流脉冲激光");
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