package terraria.entity.boss.postMoonLord.exoMechs;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class AresArm extends EntitySlime {
    public enum ArmType {
        LEFT_TOP("XF-09“阿瑞斯”镭射加农炮", -20, 6, ArmAttackPattern.LASER_CANNON),
        LEFT_BOTTOM("XF-09“阿瑞斯”特斯拉加农炮", -15, -10, ArmAttackPattern.TESLA_CANNON),
        RIGHT_TOP("XF-09“阿瑞斯”高斯核弹发射井", 20, 6, ArmAttackPattern.NUKE_LAUNCHER),
        RIGHT_BOTTOM("XF-09“阿瑞斯”离子加农炮", 15, -10, ArmAttackPattern.PLASMA_CANNON);

        private final String name;
        private final double sidewaysOffset;
        private final double verticalOffset;
        private final ArmAttackPattern attackPattern;

        ArmType(String name, double sidewaysOffset, double verticalOffset, ArmAttackPattern attackPattern) {
            this.name = name;
            this.sidewaysOffset = sidewaysOffset;
            this.verticalOffset = verticalOffset;
            this.attackPattern = attackPattern;
        }

        public String getName() {
            return name;
        }

        public double getSidewaysOffset() {
            return sidewaysOffset;
        }

        public double getVerticalOffset() {
            return verticalOffset;
        }

        public ArmAttackPattern getAttackPattern() {
            return attackPattern;
        }
    }
    public enum ArmAttackPattern {
        LASER_CANNON("红色星流脉冲激光", new int[]{10, 8, 5, 10}, new int[]{1, 1, 1, 1}, new int[]{10, 8, 5, 10},
                2.0, new double[]{0.5, 0.75, 1, 0.25}, new double[]{0.25, 0.5, 1, 0}, createAttributeMap(1560, 1.5)),
        TESLA_CANNON("特斯拉星流闪电", new int[]{60, 40, 0, 50}, new int[]{4, 5, 6, 1}, new int[]{30, 25, 20, 0},
                1.8, new double[]{0.6, 0.85, 1, 1}, new double[]{0, 0, 0, 0}, createAttributeMap(1260, 1)),
        NUKE_LAUNCHER("星流高斯核弹", new int[]{200, 100, 60, 200}, new int[]{0, 1, 1, 0}, new int[]{0, 0, 0, 0},
                1.5, new double[]{0, 0.5, 1, 0}, new double[]{0, 0, 0, 0}, createAttributeMap(1920, 6)),
        PLASMA_CANNON("巨大挥发性等离子光球", new int[]{75, 60, 50, 80}, new int[]{2, 3, 5, 2}, new int[]{20, 15, 10, 10},
                1.2, new double[]{0.5, 0.9, 1, 0}, new double[]{0.75, 1.25, 2, 0}, createAttributeMap(1260, 2.5));

        private final String projectileType;
        private final int[] loadTimes;
        private final int[] loadAmounts;
        private final int[] fireIntervals;
        private final double projectileSpeed;
        private final double[] intensities;
        private final double[] randomOffsetRadii;
        private final HashMap<String, Double> attributeMap;

        ArmAttackPattern(String projectileType, int[] loadTimes, int[] loadAmounts, int[] fireIntervals, double projectileSpeed, double[] intensities, double[] randomOffsetRadii, HashMap<String, Double> attributeMap) {
            this.projectileType = projectileType;
            this.loadTimes = loadTimes;
            this.loadAmounts = loadAmounts;
            this.fireIntervals = fireIntervals;
            this.projectileSpeed = projectileSpeed;
            this.intensities = intensities;
            this.randomOffsetRadii = randomOffsetRadii;
            this.attributeMap = attributeMap;
        }

        public String getProjectileType() {
            return projectileType;
        }

        public int getLoadTime(int difficulty) {
            return loadTimes[difficulty];
        }

        public int getLoadAmount(int difficulty) {
            return loadAmounts[difficulty];
        }

        public int getFireInterval(int difficulty) {
            return fireIntervals[difficulty];
        }

        public double getProjectileSpeed() {
            return projectileSpeed;
        }

        public EntityHelper.AimHelperOptions getAimHelperOptions(int difficulty) {
            return new EntityHelper.AimHelperOptions(projectileType)
                    .setProjectileSpeed(projectileSpeed)
                    .setIntensity(intensities[difficulty])
                    .setRandomOffsetRadius(randomOffsetRadii[difficulty]);
        }

        public HashMap<String, Double> getAttributeMap() {
            return attributeMap;
        }

        private static HashMap<String, Double> createAttributeMap(double damage, double knockback) {
            HashMap<String, Double> attributeMap = new HashMap<>();
            attributeMap.put("damage", damage);
            attributeMap.put("knockback", knockback);
            return attributeMap;
        }
    }
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final double BASIC_HEALTH = 3588000 * 2, BASIC_HEALTH_BR = 1646500 * 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    Ares owner = null;
    // other variables and AI
    ArmType armType;
    public ArmType getArmType() {
        return armType;
    }
    Location desiredLocation;
    EntityHelper.ProjectileShootInfo projectileShootInfo;
    private int chargeTicks = 0;
    private int projectilesFired = 0;

    private int getDifficulty() {
        return owner.isLaserBeamAttackActive ? 3 : owner.owner.calculateDifficulty(this).ordinal();
    }



    public void handleProjectileFiring() {
        if (target == null) return;

        ArmAttackPattern attackPattern = armType.getAttackPattern();
        int difficulty = getDifficulty(); // Replace with your method to get the difficulty level

        if (chargeTicks < attackPattern.getLoadTime(difficulty)) {
            chargeTicks++;
        } else {
            if (projectilesFired < attackPattern.getLoadAmount(difficulty)) {
                int fireInterval = attackPattern.getFireInterval(difficulty);
                if (fireInterval == 0 || chargeTicks % fireInterval == 0) {
                    Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                    EntityHelper.AimHelperOptions aimHelperOptions = attackPattern.getAimHelperOptions(difficulty);

                    Location aimLocation = EntityHelper.helperAimEntity(shootLoc, target, aimHelperOptions);

                    Vector direction = aimLocation.toVector().subtract(shootLoc.toVector());
                    if (direction.lengthSquared() == 0) {
                        direction = new Vector(1, 0, 0);
                    } else {
                        direction.normalize();
                    }
                    Vector velocity = direction.multiply(attackPattern.getProjectileSpeed());

                    projectileShootInfo.setLockedTarget(target);
                    projectileShootInfo.shootLoc = shootLoc;
                    projectileShootInfo.velocity = velocity;

                    EntityHelper.spawnProjectile(projectileShootInfo);

                    projectilesFired++;
                }
                chargeTicks++;
            } else {
                chargeTicks = 0;
                projectilesFired = 0;
            }
        }
    }



    public void setDesiredLocation(Location location) {
        this.desiredLocation = location;
    }

    public void movementTick() {
        // Use velocity-based movement to move the arm to its desired location
        Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), desiredLocation, Draedon.MECHS_ALIGNMENT_SPEED, true);
        bukkitEntity.setVelocity(velocity);
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
                movementTick();
                if (owner.owner.isSubBossActive(Draedon.SubBossType.ARES)) {
                    handleProjectileFiring();
                }

                // facing
                this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
            }
        }
    }
    // default constructor to handle chunk unload
    public AresArm(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public AresArm(Ares head, Location spawnLoc, ArmType armType) {
        super( head.getWorld() );
        owner = head;
        this.armType = armType;
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        head.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(this.armType.getName());
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 1d);
            attrMap.put("damageTakenMulti", 0.65d);
            attrMap.put("defence", 200d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = head.bossbar;
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = owner.targetMap;
            target = owner.target;
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
            bossParts = owner.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info
        projectileShootInfo = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(),
                armType.getAttackPattern().getAttributeMap(), EntityHelper.DamageType.ARROW,
                armType.getAttackPattern().getProjectileType());
    }

    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        this.setHealth(owner.getHealth());
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