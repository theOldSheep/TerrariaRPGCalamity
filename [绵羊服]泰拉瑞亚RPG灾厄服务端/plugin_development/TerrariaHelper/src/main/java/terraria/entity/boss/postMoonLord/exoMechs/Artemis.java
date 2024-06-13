package terraria.entity.boss.postMoonLord.exoMechs;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class Artemis extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final double BASIC_HEALTH = 3588000 * 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    Draedon owner = null;
    Apollo apollo = null;
    // other variables and AI
    static final String LASER_TYPE = "橙色星流脉冲激光";
    static final double LASER_SPEED = 1, DASH_SPEED = 3,
            LASER_SPREAD_INTERVAL = 12.5, LASER_SPREAD_TOTAL = 51,
            FINAL_LASER_LENGTH = 128.0, FINAL_LASER_WIDTH = 2.25, FINAL_LASER_ROTATION = 3.0;
    static HashMap<String, Double> ATTR_MAP_LASER, ATTR_MAP_FINAL_LASER;
    static EntityHelper.AimHelperOptions
            AIM_HELPER_LASER, AIM_HELPER_LASER_INACCURATE, AIM_HELPER_LASER_OFFSET,
            AIM_HELPER_DASH, AIM_HELPER_DASH_INACCURATE;
    static GenericHelper.StrikeLineOptions STRIKE_OPTION_FINAL_LASER;
    EntityHelper.ProjectileShootInfo shootInfoLaser;
    static {
        ATTR_MAP_LASER = new HashMap<>();
        ATTR_MAP_LASER.put("damage", 1260d);
        ATTR_MAP_LASER.put("knockback", 1.5d);
        ATTR_MAP_FINAL_LASER = new HashMap<>();
        ATTR_MAP_FINAL_LASER.put("damage", 1560d);
        ATTR_MAP_FINAL_LASER.put("knockback", 2.25d);

        AIM_HELPER_LASER = new EntityHelper.AimHelperOptions(LASER_TYPE)
                .setProjectileSpeed(LASER_SPEED);
        AIM_HELPER_LASER_INACCURATE = new EntityHelper.AimHelperOptions(LASER_TYPE)
                .setProjectileSpeed(LASER_SPEED)
                .setEpoch(3)
                .setAccelerationMode(true);
        AIM_HELPER_LASER_OFFSET = new EntityHelper.AimHelperOptions(LASER_TYPE)
                .setProjectileSpeed(LASER_SPEED)
                .setRandomOffsetRadius(5);

        AIM_HELPER_DASH = new EntityHelper.AimHelperOptions()
                .setProjectileSpeed(DASH_SPEED);
        AIM_HELPER_DASH_INACCURATE = new EntityHelper.AimHelperOptions()
                .setProjectileSpeed(DASH_SPEED)
                .setEpoch(2);

        STRIKE_OPTION_FINAL_LASER = new GenericHelper.StrikeLineOptions()
                .setThruWall(true)
                .setParticleInfo(
                        new GenericHelper.ParticleLineOptions()
                                .setVanillaParticle(false)
                                .setTicksLinger(1)
                                .setParticleColor("236|122|16"));
    }

    protected boolean stageTransitionTriggered = false;
    protected Location twinHoverLocation, hoverLocation, pivot;
    private Vector dashVelocity = new Vector(), laserDir, laserRot;
    HashSet<Entity> finalLaserDamaged = new HashSet<>();
    private int phase = 1;
    private int phaseDurationCounter = 0;



    public void movementTick() {
        Location targetLocation = target.getLocation();

        // Calculate the midpoint of the boss's hover location and its twin's hover location
        Location midPoint = hoverLocation.clone().add(twinHoverLocation).multiply(0.5);

        // Calculate the yaw as between the target and the midpoint
        double yaw = MathHelper.getVectorYaw(midPoint.clone().subtract(targetLocation).toVector());

        // Calculate the direction vector from the target to the midpoint
        Vector direction;
        direction = MathHelper.vectorFromYawPitch_approx(yaw - 20, 0).multiply(32);

        // Calculate the hover location for the boss
        Location hoverLocation = targetLocation.clone().add(direction);

        // Update the hover location based on the sub-bosses' states
        updateHoverLocation(hoverLocation);

        // Calculate the direction vector for the twin
        direction = MathHelper.vectorFromYawPitch_approx(yaw + 20, 0).multiply(32);

        // Calculate the hover location for the twin
        twinHoverLocation = targetLocation.clone().add(direction);

        // Update the hover location based on the sub-bosses' states
        updateHoverLocation(twinHoverLocation);

        // Use velocity-based movement to move the sub-boss to its hover location
        Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverLocation, Draedon.MECHS_ALIGNMENT_SPEED, true);
        bukkitEntity.setVelocity(velocity);
    }
    private void updateHoverLocation(Location location) {
        if (owner.isSubBossActive(Draedon.SubBossType.ARTEMIS)) {
            if (owner.isSubBossActive(Draedon.SubBossType.ARES)) {
                location.setY(location.getY() - 10);
            }
        } else {
            location.setY(-30);
        }
    }

    private void attackTarget() {
        switch (phase) {
            case 1:
                phase1Attack();
                break;
            case 2:
                phase2Attack();
                break;
            case 3:
                phase3Attack();
                break;
            case 4:
                phase4Attack();
                break;
        }
        phaseDurationCounter++;
        checkPhaseTransition();
    }

    private void checkPhaseTransition() {
        switch (phase) {
            case 1:
                if (phaseDurationCounter >= 120) {
                    transitionToPhase(2);
                }
                break;
            case 2:
                if (phaseDurationCounter >= 120) {
                    if (stageTransitionTriggered) {
                        if (owner.getActiveBossCount() == 1 && Math.random() < 0.5) {
                            transitionToPhase(4);
                        } else {
                            transitionToPhase(3);
                        }
                    } else {
                        transitionToPhase(1);
                    }
                }
                break;
            case 3:
                if (phaseDurationCounter >= 60) {
                    transitionToPhase(4);
                }
                break;
            case 4:
                if (phaseDurationCounter >= 260) {
                    transitionToPhase(1);
                }
                break;
        }
    }

    private void transitionToPhase(int newPhase) {
        phase = newPhase;
        phaseDurationCounter = 0;
    }

    private void phase1Attack() {
        int shootInterval = getShootInterval();
        EntityHelper.AimHelperOptions aimHelper = getAimHelper();
        shootLaser(shootInterval, aimHelper);
    }

    private int getShootInterval() {
        if (owner.getActiveBossCount() == 3) {
            return 15;
        } else if (owner.getActiveBossCount() == 1 && stageTransitionTriggered) {
            return 5;
        } else {
            return 10;
        }
    }

    private EntityHelper.AimHelperOptions getAimHelper() {
        if (owner.getActiveBossCount() == 3) {
            return AIM_HELPER_LASER_INACCURATE;
        } else if (owner.getActiveBossCount() == 1 && stageTransitionTriggered) {
            return AIM_HELPER_LASER_OFFSET;
        } else {
            return AIM_HELPER_LASER;
        }
    }

    private void phase2Attack() {
        if (phaseDurationCounter < 75) {
            if (phaseDurationCounter % 25 == 0) {
                Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                ArrayList<Vector> directions = MathHelper.getEvenlySpacedProjectileDirections(LASER_SPREAD_INTERVAL, LASER_SPREAD_TOTAL, target, shootLoc, AIM_HELPER_LASER_INACCURATE, LASER_SPEED);
                for (Vector direction : directions) {
                    shootInfoLaser.shootLoc = shootLoc;
                    shootInfoLaser.velocity = direction;
                    EntityHelper.spawnProjectile(shootInfoLaser);
                }
            }
        } else if (phaseDurationCounter == 75) {
            // Dash towards the player
            Location aimLoc = EntityHelper.helperAimEntity(bukkitEntity, target, owner.getActiveBossCount() == 1 ? AIM_HELPER_DASH : AIM_HELPER_DASH_INACCURATE);
            dashVelocity = MathHelper.getDirection(bukkitEntity.getLocation(), aimLoc, DASH_SPEED);
            bukkitEntity.setVelocity(dashVelocity);
            // Unleash a roar
            owner.playWarningSound(bukkitEntity.getLocation());
        } else if (phaseDurationCounter < 100) {
            // Maintain the dash velocity
            bukkitEntity.setVelocity(dashVelocity);
        }
    }

    private void phase3Attack() {
        shootLaser(3, AIM_HELPER_LASER_OFFSET);
    }

    private void phase4Attack() {
        if (phaseDurationCounter < 10) {
            // Roar thrice for warning
            if (phaseDurationCounter % 3 == 0) {
                owner.playWarningSound(bukkitEntity.getLocation());
            }
        } else if (phaseDurationCounter < 20) {
            if (phaseDurationCounter == 10) {
                // Remember the player's current location
                pivot = target.getLocation();
                // Prevent getting too close to the ground
                double highestY = WorldHelper.getHighestBlockBelow(pivot).getY();
                pivot.setY( Math.max( pivot.getY(), highestY + 24 ) );
            }
        } else if (phaseDurationCounter < 240) {
            // Continue the laser rotation, 11 seconds
            handleFinalLaser(pivot);
        }
    }

    private void shootLaser(int shootInterval, EntityHelper.AimHelperOptions aimHelper) {
        if (phaseDurationCounter % shootInterval == 0) {
            Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            Location aimLoc = EntityHelper.helperAimEntity(shootLoc, target, aimHelper);
            shootInfoLaser.shootLoc = shootLoc;
            shootInfoLaser.velocity = MathHelper.getDirection(shootLoc, aimLoc, LASER_SPEED);
            EntityHelper.spawnProjectile(shootInfoLaser);
        }
    }

    private void handleFinalLaser(Location pivot) {
        if (phaseDurationCounter < 40) {
            Vector plyDir = target.getLocation().subtract(pivot).toVector();
            double yawAngle = MathHelper.getVectorYaw(plyDir);
            laserDir = MathHelper.vectorFromYawPitch(yawAngle, 0);
            laserRot = MathHelper.getNonZeroCrossProd(laserDir, new Vector(0, 1, 0));
            if (plyDir.getY() > 0) {
                laserRot.multiply(-1);
            }
        }
        else {
            // Rotate laserDir by laserRot
            laserDir = MathHelper.rotateAroundAxisDegree(laserDir, laserRot, FINAL_LASER_ROTATION);
        }

        // Calculate the new pivot location to contain the target's location in the laser's plane of rotation
        Vector pivotToTarget = target.getLocation().subtract(pivot).toVector();
        Vector projection = MathHelper.vectorProjection(laserRot, pivotToTarget);
        pivot.add(projection);

        // Calculate the teleport location and fire laser
        Location newLocation = pivot.clone().add(laserDir.clone().multiply(-32));
        Vector eyeToFootDir = bukkitEntity.getLocation().subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector();
        newLocation.add(eyeToFootDir);
        bukkitEntity.teleport(newLocation);
        bukkitEntity.setVelocity(new Vector(0, 0, 0));
        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                MathHelper.getVectorYaw(laserDir), MathHelper.getVectorPitch(laserDir), FINAL_LASER_LENGTH, FINAL_LASER_WIDTH, "", "",
                finalLaserDamaged, ATTR_MAP_FINAL_LASER, STRIKE_OPTION_FINAL_LASER);
    }

    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;

            if (target != null) {
                // movement
                movementTick();
                // attack
                if (owner.isSubBossActive(Draedon.SubBossType.ARTEMIS)) {
                    if (!stageTransitionTriggered && getHealth() / getMaxHealth() < 0.7) {
                        stageTransitionTriggered = true;
                        setCustomName(getCustomName() + "§1");
                        apollo.setCustomName(apollo.getCustomName() + "§1");
                    }
                    attackTarget();
                } else {
                    transitionToPhase(1);
                }
                // facing
                this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Artemis(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Artemis(Draedon draedon, Location spawnLoc) {
        super( draedon.getWorld() );
        owner = draedon;
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        hoverLocation = spawnLoc;
        twinHoverLocation = spawnLoc;
        // add to world
        draedon.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("XS-01“阿尔忒弥斯”");
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
            attrMap.put("damage", 1320d);
            attrMap.put("damageTakenMulti", 0.75d);
            attrMap.put("defence", 200d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
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
            setSize(15, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
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
        // shoot info's
        {
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), ATTR_MAP_LASER,
                    EntityHelper.DamageType.ARROW, LASER_TYPE);
        }
        // apollo
        apollo = new Apollo(owner, this, spawnLoc);
    }

    // rewrite AI
    @Override
    public void B_() {
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