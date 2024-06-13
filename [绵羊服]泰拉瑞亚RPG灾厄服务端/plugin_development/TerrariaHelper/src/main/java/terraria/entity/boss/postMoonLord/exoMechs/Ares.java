package terraria.entity.boss.postMoonLord.exoMechs;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.postMoonLord.exoMechs.Draedon;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
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
    AresArm[] arms;





    public void movementTick() {
        Location targetLocation = target.getLocation();

        // Calculate the direction vector from the target to the boss
        double yaw = MathHelper.getVectorYaw(bukkitEntity.getLocation().clone().subtract(targetLocation).toVector());
        Vector direction = MathHelper.vectorFromYawPitch_approx(yaw, 0).multiply(32);

        // Calculate the hover location for the boss
        Location hoverLocation = targetLocation.clone().add(direction);

        // Update the hover location based on the sub-bosses' states
        updateHoverLocation(hoverLocation);

        // Use velocity-based movement to move the boss to its hover location
        Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverLocation, Draedon.MECHS_ALIGNMENT_SPEED, true);
        bukkitEntity.setVelocity(velocity);

        // Calculate the desired locations for the boss's arms
        for (AresArm arm : arms) {
            Location armDesiredLocation = hoverLocation.clone();

            // Calculate a vector that points sideways relative to the boss and player's direction
            Vector sidewaysVector = MathHelper.vectorFromYawPitch_approx(yaw - 90, 0);

            // Add the sideways vector to the hover location to get the desired location for the arm
            switch (arm.getHandType()) {
                case LEFT_TOP:
                    armDesiredLocation.add(sidewaysVector.multiply(-20));
                    break;
                case LEFT_BOTTOM:
                    armDesiredLocation.add(sidewaysVector.multiply(-15));
                    break;
                case RIGHT_TOP:
                    armDesiredLocation.add(sidewaysVector.multiply(20));
                    break;
                case RIGHT_BOTTOM:
                    armDesiredLocation.add(sidewaysVector.multiply(15));
                    break;
            }

            // You can add arm-specific offset or rotation here based on the arm type
            switch (arm.getHandType()) {
                case LEFT_TOP:
                case RIGHT_TOP:
                    armDesiredLocation.add(0, 6, 0);
                    break;
                case LEFT_BOTTOM:
                case RIGHT_BOTTOM:
                    armDesiredLocation.add(0, -10, 0);
                    break;
            }

            arm.setDesiredLocation(armDesiredLocation);
        }
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
                movementTick();
                // TODO

                // facing
                this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
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
            attrMap.put("damageTakenMulti", 1d);
            attrMap.put("defence", 0d);
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