package terraria.entity.boss.preHardMode.wallOfFlesh;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class WallOfFleshEye extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.WALL_OF_FLESH;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 27417 * 2, BASIC_HEALTH_BR = 936900 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    WallOfFleshMouth mouth;
    static int minComboAmount = 3, fullComboAmount = 10;
    static double[] laserSpeeds = {1.25, 1.5, 2.25};
    static HashMap<String, Double> laser_attrMap, death_laser_attrMap;
    static {
        laser_attrMap = new HashMap<>();
        laser_attrMap.put("damage", 204d);
        laser_attrMap.put("damageMulti", 1d);
        death_laser_attrMap = new HashMap<>();
        death_laser_attrMap.put("damage", 264d);
        death_laser_attrMap.put("damageMulti", 1d);
    }
    private void shootLaser(int threatLevel) {
        Location targetedLoc = target.getEyeLocation();
        if (threatLevel >= 1) {
            AimHelper.AimHelperOptions aimHelper = new AimHelper.AimHelperOptions()
                    .setAimMode(false)
                    .setAccelerationMode(threatLevel >= 2)
                    .setIntensity(1d)
                    .setProjectileSpeed(laserSpeeds[threatLevel])
                    .setRandomOffsetRadius(2d);
            targetedLoc = AimHelper.helperAimEntity(bukkitEntity, target, aimHelper);
        }
        Vector velocity = targetedLoc.subtract( ((LivingEntity) bukkitEntity).getEyeLocation() ).toVector();
        double velLen = velocity.length();
        if (velLen < 1e-5) {
            velocity = mouth.horizontalMoveDirection.clone();
            velLen = 1;
        }
        velocity.multiply(laserSpeeds[threatLevel] / velLen);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, velocity, threatLevel == 0 ? laser_attrMap : death_laser_attrMap, "血肉激光");
        EntityHelper.spawnProjectile(shootInfo);
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d) {
            setNoGravity(false);
            return;
        }
        // AI
        {
            // update target
            target = mouth.target;
            // disappear if no target is available
            if (target == null) {
                getAttributeInstance(GenericAttributes.maxHealth).setValue(1);
                die();
                return;
            }
            
            // AI
            if (ticksLived % 3 == 0) {
                // shoot laser
                if (mouth.enragedCounter > 0) {
                    shootLaser(2);
                }
                double healthRatio = getHealth() / getMaxHealth();
                int comboShootAmount = (int) (minComboAmount + (1 - healthRatio) * (fullComboAmount - minComboAmount));
                if (mouth.indexAI % fullComboAmount < comboShootAmount) {
                    shootLaser(healthRatio > 0.5 ? 0 : 1);
                }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public WallOfFleshEye(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public WallOfFleshEye(Player summonedPlayer, ArrayList<LivingEntity> bossParts) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = bossParts.get(0).getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        this.mouth = (WallOfFleshMouth) ((CraftEntity) bossParts.get(0)).getHandle();
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 450d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 12d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = mouth.targetMap;
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
            this.bossParts = bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, mouth.getBukkitEntity());
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // update health
        setHealth(mouth.getHealth());
        // AI
        AI();
    }
}
