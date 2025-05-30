package terraria.entity.boss.postMoonLord.exoMechs;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Apollo extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final double BASIC_HEALTH = 3588000 * 2, BASIC_HEALTH_BR = 1646500 * 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    Draedon owner = null;
    Artemis twin = null;
    // other variables and AI
    static final double DASH_SPEED = 4.5;
    static HashMap<String, Double> ATTR_MAP_PLASMA, ATTR_MAP_ROCKET;
    static AimHelper.AimHelperOptions AIM_HELPER_DASH;
    EntityHelper.ProjectileShootInfo shootInfoPlasma, shootInfoRocket;
    static {
        ATTR_MAP_PLASMA = new HashMap<>();
        ATTR_MAP_PLASMA.put("damage", 1440d);
        ATTR_MAP_PLASMA.put("knockback", 1.5d);
        ATTR_MAP_ROCKET = new HashMap<>();
        ATTR_MAP_ROCKET.put("damage", 1680d);
        ATTR_MAP_ROCKET.put("knockback", 3.5d);

        AIM_HELPER_DASH = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(DASH_SPEED);
    }
    Vector dashVel = null;


    private void attack() {
        if (twin != null) {
            switch (twin.phase) {
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
        }
    }

    // dash when difficulty is high and otherwise fire plasma
    private void phase1Attack() {
        Draedon.Difficulty difficulty = owner.calculateDifficulty(this);
        if (difficulty == Draedon.Difficulty.HIGH) {
            if (twin.phaseDurationCounter % 30 == 0) {
                owner.playWarningSound(false);
                dashVel = getDashDirection(false); // Direct dash
            }
            if (dashVel != null) {
                bukkitEntity.setVelocity(dashVel);
                this.yaw = (float) MathHelper.getVectorYaw(dashVel);
            }
        } else {
            int interval = difficulty == Draedon.Difficulty.LOW ? 15 : 10;
            if (twin.phaseDurationCounter % interval == 0) {
                fireProjectile(false); // Fire plasma
            }
        }
    }
    // fire rockets
    private void phase2Attack() {
        int interval = owner.calculateDifficulty(this) == Draedon.Difficulty.HIGH ? 5 : 10;
        if (twin.phaseDurationCounter % interval == 0) {
            fireProjectile(true); // Fire rocket
        }
    }
    // fire plasma; perform a dash first if difficulty is high
    private void phase3Attack() {
        Draedon.Difficulty difficulty = owner.calculateDifficulty(this);
        if (difficulty == Draedon.Difficulty.HIGH && twin.phaseDurationCounter < 30) {
            if (twin.phaseDurationCounter == 0) {
                owner.playWarningSound(false);
                dashVel = getDashDirection(true); // Aimed dash
            }
            if (dashVel != null) {
                bukkitEntity.setVelocity(dashVel);
                this.yaw = (float) MathHelper.getVectorYaw(dashVel);
            }
        } else {
            int interval = difficulty == Draedon.Difficulty.HIGH ? 8 : 15;
            if (twin.phaseDurationCounter % interval == 0) {
                fireProjectile(false); // Fire plasma
            }
        }
    }
    // fire rockets
    private void phase4Attack() {
        if (twin.phaseDurationCounter % 5 == 0) {
            fireProjectile(true); // Fire rocket
        }
    }

    private void fireProjectile(boolean rocketOrPlasma) {
        EntityHelper.ProjectileShootInfo shootInfo = rocketOrPlasma ? shootInfoRocket : shootInfoPlasma;
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc,
                target.getEyeLocation().add(MathHelper.randomVector().multiply(5)),
                rocketOrPlasma ? 1.25 : 1.35);
        shootInfo.setLockedTarget(target);
        EntityHelper.spawnProjectile(shootInfo);
    }
    private Vector getDashDirection(boolean aimedOrDirect) {
        Location eyeLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        Location targetLoc = aimedOrDirect ? AimHelper.helperAimEntity(eyeLoc, target, AIM_HELPER_DASH) : target.getEyeLocation();
        return MathHelper.getDirection(eyeLoc, targetLoc, DASH_SPEED);
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
                // hover
                twin.movementTick(20, bukkitEntity);
                // facing
                this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
                // attack
                if (owner.isSubBossActive(Draedon.SubBossType.ARTEMIS))
                    attack();
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Apollo(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Apollo(Draedon draedon, Artemis artemis, Location spawnLoc) {
        super( draedon.getWorld() );
        owner = draedon;
        twin = artemis;
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        draedon.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("XS-03“阿波罗”");
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, artemis.getBukkitEntity());
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 2040d);
            attrMap.put("defence", 200d);
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
            setSize(15, false);
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
        // shoot info's
        {
            shootInfoPlasma = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), ATTR_MAP_PLASMA,
                    DamageHelper.DamageType.ARROW, "巨大挥发性等离子光球");
            shootInfoRocket = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), ATTR_MAP_ROCKET,
                    DamageHelper.DamageType.ROCKET, "高爆等离子火箭");
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        this.setHealth(twin.getHealth());
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