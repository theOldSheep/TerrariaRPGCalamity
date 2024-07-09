package terraria.entity.boss.postMoonLord.exoMechs;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
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

public class Ares extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final double BASIC_HEALTH = 3588000 * 2, BASIC_HEALTH_BR = 1646500 * 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    Draedon owner = null;
    // other variables and AI
    static final double LASER_BEAM_LENGTH = 56, LASER_MAX_TARGET_DIST = 48, LASER_BEAM_WIDTH = 1;
    static final int LASER_BEAM_INITIAL_PITCH = 90, LASER_BEAM_PITCH_CHANGE_RATE = 90 - 15;
    static final int REGULAR_ATTACK_PHASE_TICK_DURATION = 160,
            ROAR_INTERVAL = 10, ROAR_COUNT = 3,
            LASER_BEAM_DURATION = 60, LASER_BEAM_COUNT = 3;
    static HashMap<String, Double> ATTR_MAP_LASER_BEAM;
    static GenericHelper.StrikeLineOptions STRIKE_OPTION_LASER_BEAM;

    static {
        ATTR_MAP_LASER_BEAM = new HashMap<>();
        ATTR_MAP_LASER_BEAM.put("damage", 1560d);
        ATTR_MAP_LASER_BEAM.put("knockback", 3.5d);

        STRIKE_OPTION_LASER_BEAM = new GenericHelper.StrikeLineOptions()
                .setThruWall(true)
                .setParticleInfo(
                        new GenericHelper.ParticleLineOptions()
                                .setVanillaParticle(false)
                                .setTicksLinger(2)
                                .setParticleColor("255|255|255", "191|246|145"));
    }
    AresArm[] arms;
    HashSet<Entity> laserBeamDamaged = new HashSet<>();
    boolean isHandPlacementFlipped = false;
    protected boolean isLaserBeamAttackActive = false;
    private int laserBeamCounter = 0;
    private int currentLaserBeam = 0;


    public void movementTick() {
        // Calculate the hover center location
        Location hoverCenterLoc = owner.getHoverCenterLoc();

        // Update the hover location based on the sub-bosses' states
        updateHoverLocation(hoverCenterLoc);

        // Calculate the direction vector from the target to the boss
        double yaw = MathHelper.getVectorYaw(bukkitEntity.getLocation().subtract(target.getLocation()).toVector());

        // Move the boss
        Vector velocity;
        if (isLaserBeamAttackActive &&
                bukkitEntity.getLocation().distanceSquared(target.getLocation()) < LASER_MAX_TARGET_DIST * LASER_MAX_TARGET_DIST) {
            velocity = bukkitEntity.getVelocity().multiply(0.5);
        } else {
            velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverCenterLoc, Draedon.MECHS_ALIGNMENT_SPEED, true);
        }
        bukkitEntity.setVelocity(velocity);

        // Update the arm locations
        for (AresArm arm : arms) {
            arm.setDesiredLocation(calculateArmDesiredLocation(arm, yaw));
        }
    }

    private Location calculateArmDesiredLocation(AresArm arm, double yaw) {
        Location armDesiredLocation = bukkitEntity.getLocation();

        // Calculate a vector that points sideways relative to the boss and player's direction
        Vector sidewaysVector = MathHelper.vectorFromYawPitch_approx(yaw - 90, 0);

        // Add the sideways vector to the hover location to get the desired location for the arm
        armDesiredLocation.add(sidewaysVector.multiply(arm.getArmType().getSidewaysOffset() * (isHandPlacementFlipped ? -1 : 1)));
        armDesiredLocation.add(0, arm.getArmType().getVerticalOffset(), 0);

        return armDesiredLocation;
    }

    public void cycleAttackPhase() {
        // Check if the sub-boss is active
        if (!owner.isSubBossActive(Draedon.SubBossType.ARES)) {
            isLaserBeamAttackActive = false;
        }

        // Check the difficulty level
        if (owner.calculateDifficulty(this) == Draedon.Difficulty.HIGH) {
            // Increment the laser counter
            laserBeamCounter++;

            // Regular attack phase
            if (laserBeamCounter <= REGULAR_ATTACK_PHASE_TICK_DURATION) {
                isLaserBeamAttackActive = false;
            } else if (laserBeamCounter <= REGULAR_ATTACK_PHASE_TICK_DURATION + ROAR_INTERVAL * ROAR_COUNT + LASER_BEAM_DURATION * LASER_BEAM_COUNT) {
                // Roar before the laser beam attack
                if (laserBeamCounter <= REGULAR_ATTACK_PHASE_TICK_DURATION + ROAR_INTERVAL * ROAR_COUNT) {
                    if ((laserBeamCounter - REGULAR_ATTACK_PHASE_TICK_DURATION) % ROAR_INTERVAL == 1) {
                        owner.playWarningSound();
                    }
                }

                // Laser beam attack phase
                if (laserBeamCounter > REGULAR_ATTACK_PHASE_TICK_DURATION + ROAR_INTERVAL * ROAR_COUNT) {
                    isLaserBeamAttackActive = true;

                    // Apply the laser beam attack
                    if (currentLaserBeam < LASER_BEAM_COUNT) {
                        int initialTick = REGULAR_ATTACK_PHASE_TICK_DURATION + ROAR_INTERVAL * ROAR_COUNT + LASER_BEAM_DURATION * currentLaserBeam;
                        applyLaserBeamAttack(initialTick);
                        if (laserBeamCounter >= initialTick + LASER_BEAM_DURATION) {
                            currentLaserBeam++;
                        }
                    }

                    // Swap the arm placement after the laser beam attack
                    if (currentLaserBeam == LASER_BEAM_COUNT) {
                        isHandPlacementFlipped = !isHandPlacementFlipped;
                        laserBeamCounter = 0;
                        currentLaserBeam = 0;
                    }
                }
            }
        } else {
            isLaserBeamAttackActive = false;
        }
    }


    private void applyLaserBeamAttack(int initialTick) {
        // Calculate the yaw and pitch
        double yaw = MathHelper.getVectorYaw(target.getLocation().clone().subtract(bukkitEntity.getLocation()).toVector());
        double pitch = LASER_BEAM_INITIAL_PITCH - (laserBeamCounter - initialTick) / (double) LASER_BEAM_DURATION * LASER_BEAM_PITCH_CHANGE_RATE;
        double pitch2 = -pitch; // Opposite direction

        // Apply the laser beam attack
        applyLaserBeam(yaw, pitch);
        applyLaserBeam(yaw, pitch2);
    }

    private void applyLaserBeam(double yaw, double pitch) {
        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                yaw, pitch,
                LASER_BEAM_LENGTH, LASER_BEAM_WIDTH, "", "",
                laserBeamDamaged, ATTR_MAP_LASER_BEAM, STRIKE_OPTION_LASER_BEAM);
    }

    private void updateHoverLocation(Location hoverLocation) {
        if (owner.isSubBossActive(Draedon.SubBossType.ARES)) {
            hoverLocation.setY(hoverLocation.getY() + 15);
        } else {
            hoverLocation.setY(-15);
        }
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
                // facing
                this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );

                movementTick();
                cycleAttackPhase();
            }
        }
    }
    // default constructor to handle chunk unload
    public Ares(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Ares(Draedon draedon, Location spawnLoc) {
        super( draedon.getWorld() );
        owner = draedon;
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        draedon.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("XF-09“阿瑞斯”");
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
            attrMap.put("damage", 1d);
            attrMap.put("damageTakenMulti", 0.65d);
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
            setSize(20, false);
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
            // arms
            arms = new AresArm[AresArm.ArmType.values().length];
            int idx = 0;
            for (AresArm.ArmType type : AresArm.ArmType.values())
                arms[idx++] = new AresArm(this, ((LivingEntity) bukkitEntity).getEyeLocation(), type);
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