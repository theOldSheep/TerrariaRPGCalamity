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
    public static final double BASIC_HEALTH = 3588000 * 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    Draedon owner = null;
    // other variables and AI
    static final double LASER_BEAM_LENGTH = 48, LASER_BEAM_WIDTH = 1;
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
                                .setTicksLinger(1)
                                .setParticleColor("RAINBOW"));
    }
    AresArm[] arms;

    HashSet<Entity> laserBeamDamaged = new HashSet<>();
    boolean isHandPlacementFlipped = false;
    boolean isLaserBeamAttackActive = false;
    private int laserBeamCounter = 0;





    public void movementTick() {
        // Calculate the hover center location
        Location hoverCenterLoc = owner.getHoverCenterLoc();

        // Update the hover location based on the sub-bosses' states
        updateHoverLocation(hoverCenterLoc);

        // Cycle the attack phase
        cycleAttackPhase();

        // Calculate the direction vector from the target to the boss
        double yaw = MathHelper.getVectorYaw(bukkitEntity.getLocation().subtract(target.getLocation()).toVector());

        // Check if the laser beam attack is active and the distance to the target is large enough
        if (isLaserBeamAttackActive) {
            double distance = bukkitEntity.getLocation().distance(target.getLocation());
            if (distance > Draedon.MECHS_ALIGN_DIST) {
                // Move the boss
                Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverCenterLoc, Draedon.MECHS_ALIGNMENT_SPEED, true);
                bukkitEntity.setVelocity(velocity);
            }
        } else {
            // Move the boss normally
            Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverCenterLoc, Draedon.MECHS_ALIGNMENT_SPEED, true);
            bukkitEntity.setVelocity(velocity);
        }

        // Calculate the desired locations for the boss's arms
        for (AresArm arm : arms) {
            Location armDesiredLocation = bukkitEntity.getLocation();

            // Calculate a vector that points sideways relative to the boss and player's direction
            Vector sidewaysVector = MathHelper.vectorFromYawPitch_approx(yaw - 90, 0);

            // Add the sideways vector to the hover location to get the desired location for the arm
            armDesiredLocation.add(sidewaysVector.multiply(arm.getArmType().getSidewaysOffset() * (isHandPlacementFlipped ? -1 : 1)));
            armDesiredLocation.add(0, arm.getArmType().getVerticalOffset(), 0);

            arm.setDesiredLocation(armDesiredLocation);
        }
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
            if (laserBeamCounter <= 160) {
                isLaserBeamAttackActive = false;
            } else if (laserBeamCounter <= 320) {
                // Laser beam attack phase
                isLaserBeamAttackActive = true;

                // Roar before the laser beam attack
                if (laserBeamCounter == 161) {
                    owner.playWarningSound();
                } else if (laserBeamCounter == 171) {
                    owner.playWarningSound();
                } else if (laserBeamCounter == 181) {
                    owner.playWarningSound();
                }

                // Apply the laser beam attack
                if (laserBeamCounter >= 200 && laserBeamCounter < 260) {
                    applyLaserBeamAttack(1);
                } else if (laserBeamCounter >= 260) {
                    applyLaserBeamAttack(2);
                }

                // Swap the arm placement after the laser beam attack
                if (laserBeamCounter == 320) {
                    isHandPlacementFlipped = !isHandPlacementFlipped;
                    laserBeamCounter = 0;
                }
            }
        } else {
            isLaserBeamAttackActive = false;
        }
    }

    private void applyLaserBeamAttack(int phase) {
        // Calculate the yaw and pitch
        double yaw = MathHelper.getVectorYaw(target.getLocation().clone().subtract(bukkitEntity.getLocation()).toVector());
        double pitch = (phase == 1 ? 90 - (laserBeamCounter - 200) / 60.0 * 70 : -20 - (320 - laserBeamCounter) / 60.0 * 70);
        double pitch2 = (phase == 1 ? -90 + (laserBeamCounter - 200) / 60.0 * 70 : 20 + (320 - laserBeamCounter) / 60.0 * 70);

        // Apply the laser beam attack
        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                yaw, pitch,
                LASER_BEAM_LENGTH, LASER_BEAM_WIDTH, "", "",
                laserBeamDamaged, ATTR_MAP_LASER_BEAM, STRIKE_OPTION_LASER_BEAM);
        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                yaw, pitch2,
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
                // TODO
//                if (isLaserBeamAttackActive) {
//                    double distance = bukkitEntity.getLocation().distance(target.getLocation());
//                    if (distance > Draedon.MECHS_ALIGN_DIST) {
//                        movementTick();
//                    }
//                } else {
//                    movementTick();
//                }
                movementTick();

                // facing
                this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
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