package terraria.entity.boss.postMoonLord.supremeCalamitas;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.HashMap;
import java.util.UUID;

public class SupremeCalamitasBrother extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SUPREME_WITCH_CALAMITAS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 414000 * 2, BASIC_HEALTH_BR = 576800 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    static final String[] NAMES = {"至尊灾难", "至尊灾祸"}, PROJECTILE_TYPES = {"斩魂幻锋", "硫火碎魂拳"};
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    Player target = null;
    // other variables and AI
    static final double[] PROJECTILE_SPEEDS = {0.5, 2.0};
    static HashMap<String, Double> attrMapProjectile;
    static AimHelper.AimHelperOptions[] aimHelpers;
    EntityHelper.ProjectileShootInfo shootInfo;
    static {
        attrMapProjectile = new HashMap<>();
        attrMapProjectile.put("damage", 1450d);
        attrMapProjectile.put("knockback", 2.5d);

        aimHelpers = new AimHelper.AimHelperOptions[]{
                new AimHelper.AimHelperOptions(PROJECTILE_TYPES[0]).setProjectileSpeed(PROJECTILE_SPEEDS[0]),
                new AimHelper.AimHelperOptions(PROJECTILE_TYPES[1]).setProjectileSpeed(PROJECTILE_SPEEDS[1])};
    }
    int typeIdx;
    int indexAI = 0;
    Vector velocity = new Vector();
    Location desiredLoc;
    SupremeCalamitas owner;


    private void attack() {
        double healthRatio = getHealth() / getMaxHealth();
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();

        boolean fireDirectProj = false;
        if (healthRatio > 0.4) {
            int fireInterval = healthRatio > 0.7 ? 8 : 5;
            if (indexAI % fireInterval == 0) {
                fireDirectProj = true;
            }
        }
        else {
            if (indexAI % 4 == 0) {
                if (indexAI % 20 == 0) {
                    for (Vector projVel : MathHelper.getEvenlySpacedProjectileDirections(
                            11.5, 35,
                            target, shootInfo.shootLoc, aimHelpers[typeIdx], PROJECTILE_SPEEDS[typeIdx])) {
                        shootInfo.velocity = projVel;
                        EntityHelper.spawnProjectile(shootInfo);
                    }
                }
                else {
                    fireDirectProj = true;
                }
            }
        }
        if (fireDirectProj) {
            Location aimLoc = AimHelper.helperAimEntity(shootInfo.shootLoc, target, aimHelpers[typeIdx]);
            shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, aimLoc, PROJECTILE_SPEEDS[typeIdx]);
            EntityHelper.spawnProjectile(shootInfo);
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            if (owner.isAlive()) {
                target = owner.target;
                
            }
            else
                target = null;
            // disappear if no target is available
            if (target == null) {
                die();
                return;
            }
            // if target is valid, attack
            else {
                attack();

                // index increment & velocity maintenance
                indexAI ++;
                velocity = MathHelper.getDirection(bukkitEntity.getLocation(), desiredLoc, 3, true);
                bukkitEntity.setVelocity(velocity);
            }
        }
        // facing
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // no collision dmg
    }
    // default constructor to handle chunk unload
    public SupremeCalamitasBrother(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public SupremeCalamitasBrother(SupremeCalamitas owner, int typeIdx) {
        super( owner.getWorld() );
        // spawn location
        desiredLoc = owner.getBukkitEntity().getLocation().add(MathHelper.randomVector().multiply(10));
        setLocation(desiredLoc.getX(), desiredLoc.getY(), desiredLoc.getZ(), 0, 0);
        this.owner = owner;
        this.typeIdx = typeIdx;
        // add to world
        owner.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(NAMES[typeIdx]);
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
            attrMap.put("damage", 0d);
            attrMap.put("damageTakenMulti", 0.75);
            attrMap.put("defence", 160d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) owner.targetMap.clone();
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
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfo = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapProjectile,
                    DamageHelper.DamageType.ARROW, PROJECTILE_TYPES[typeIdx]);
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
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