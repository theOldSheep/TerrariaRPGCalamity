package terraria.entity.boss.preHardMode.theSlimeGod;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class CrimulanSlime extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_SLIME_GOD;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    enum SlimeSize {
        BIG, MIDDLE, SMALL;
    }
    SlimeSize slimeSize;
    TheSlimeGod owner;
    int indexAI = 0, projectileAmount, jumpDelay;
    double jumpVelHor, jumpVelVer;
    boolean lastOnGround = true;
    Vector horVelocity = new Vector();
    EntityHelper.ProjectileShootInfo shootInfo;
    AimHelper.AimHelperOptions aimOption;
    static final HashMap<String, Double> ATTR_MAP_PROJECTILE;
    static {
        ATTR_MAP_PROJECTILE = new HashMap<>();
        ATTR_MAP_PROJECTILE.put("damage", 228d);
        ATTR_MAP_PROJECTILE.put("knockback", 4d);
    }
    private boolean isOnGround() {
        if (noclip) return false;
        return onGround ||
                bukkitEntity.getLocation().subtract(0, 0.25, 0).getBlock().getType() != org.bukkit.Material.AIR;
    }
    private void shootProjectiles() {
        for (int i = 0; i < projectileAmount; i ++) {
            Location targetLoc = AimHelper.helperAimEntity(bukkitEntity, target, aimOption);
            Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            Vector velocity = targetLoc.subtract(shootLoc).toVector();
            double velLen = velocity.length();
            if (velLen < 1e-5) {
                velocity = MathHelper.randomVector();
                velLen = 1;
            }
            velocity.multiply( TheSlimeGod.PROJECTILE_SPEED / velLen );
            shootInfo.shootLoc = shootLoc;
            shootInfo.velocity = velocity;
            EntityHelper.spawnProjectile(shootInfo);
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // spawn smaller slimes
        if (getHealth() / getMaxHealth() < 0.75 && slimeSize != SlimeSize.SMALL) {
            int spawnAmount = 2;
            double smallerSlimeHealth = getHealth() / spawnAmount;
            // remove and do not affect the boss bar's displayed health information
            getAttributeInstance(GenericAttributes.maxHealth).setValue(getMaxHealth() - getHealth());
            setHealth(0f);
            die();
            // spawn smaller slimes
            for (int i = 0; i < spawnAmount; i++) {
                new CrimulanSlime(owner,
                        slimeSize == SlimeSize.BIG ? SlimeSize.MIDDLE : SlimeSize.SMALL,
                        getBukkitEntity(), smallerSlimeHealth);
            }
        }
        // AI
        {
            // update target
            target = owner.target;
            // disappear if no target is available or core is defeated
            if (target == null || !owner.isAlive()) {
                super.die();
                return;
            }
            
            // if target is valid, attack
            int tickInterval = owner.ebonianDefeated ? 1 : 3;
            boolean currentlyOnGround = isOnGround();
            if (ticksLived % tickInterval == 0) {
                // if boss is on ground
                if (currentlyOnGround) {
                    // upon landing, shoot projectiles
                    if (!lastOnGround) {
                        shootProjectiles();
                        lastOnGround = true;
                    }
                    // jump
                    if (indexAI >= jumpDelay) {
                        indexAI = 0;
                        Vector velocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                        velocity.setY(0);
                        double velLen = velocity.length();
                        double horSpd = jumpVelHor;
                        // horizontal velocity is set to a high value when too far away
                        if (velLen > 64) {
                            horSpd = 2;
                        }
                        velocity.multiply( horSpd / velLen );
                        horVelocity = velocity.clone();
                        velocity.setY(jumpVelVer);
                        bukkitEntity.setVelocity(velocity);
                        noclip = true;
                        lastOnGround = false;
                    }
                    // add 1 to index
                    indexAI ++;
                }
                // go through walls as long as boss is above the target or moving upward
                else {
                    // determine whether the slime should go through walls
                    if (!lastOnGround) {
                        this.noclip = (this.motY > 0) || (this.locY > target.getEyeLocation().getY() + 5);
                    }
                    else {
                        this.noclip = false;
                    }
                }
            }
            if (!currentlyOnGround && motY > 0) {
                // regularize horizontal velocity
                horVelocity.setY(bukkitEntity.getVelocity().getY());
                bukkitEntity.setVelocity(horVelocity);
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }


    // default constructor to handle chunk unload
    public CrimulanSlime(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public CrimulanSlime(TheSlimeGod owner, double health) {
        this(owner, SlimeSize.BIG, owner.getBukkitEntity(), health);
    }
    public CrimulanSlime(TheSlimeGod owner, SlimeSize size, Entity spawner, double health) {
        super( owner.getWorld() );
        this.owner = owner;
        this.slimeSize = size;
        // spawn location
        double angle = Math.random() * 720d, dist = 15;
        Location spawnLoc = spawner.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("血化史莱姆");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            switch (slimeSize) {
                case BIG:
                    attrMap.put("damage", 342d);
                    break;
                case MIDDLE:
                    attrMap.put("damage", 300d);
                    break;
                case SMALL:
                    attrMap.put("damage", 240d);
                    break;
            }
            attrMap.put("damage", 270d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 0d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = owner.targetMap;
            target = owner.target;
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            switch (slimeSize) {
                case BIG:
                    setSize(16, false);
                    break;
                case MIDDLE:
                    setSize(8, false);
                    break;
                case SMALL:
                    setSize(4, false);
                    break;
            }
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            this.bossParts = owner.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.persistent = true;
        }
        // shoot info, aim helper and other size-specific values
        {
            shootInfo = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), ATTR_MAP_PROJECTILE, "血化深渊之球");
            aimOption = new AimHelper.AimHelperOptions("血化深渊之球")
                    .setAimMode(false)
                    .setProjectileSpeed(TheSlimeGod.PROJECTILE_SPEED);
            switch (slimeSize) {
                case BIG:
                    projectileAmount = 8;
                    jumpVelHor = TheSlimeGod.BIG_SLIME_HOR_VEL;
                    jumpVelVer = TheSlimeGod.BIG_SLIME_VER_VEL;
                    jumpDelay = TheSlimeGod.BIG_JUMP_DELAY;
                    aimOption
                            .setRandomOffsetRadius(4)
                            .setAccelerationMode(true)
                            .setIntensity(1d);
                    break;
                case MIDDLE:
                    projectileAmount = 3;
                    jumpVelHor = TheSlimeGod.MID_SLIME_HOR_VEL;
                    jumpVelVer = TheSlimeGod.MID_SLIME_VER_VEL;
                    jumpDelay = TheSlimeGod.MID_JUMP_DELAY;
                    aimOption
                            .setRandomOffsetRadius(2)
                            .setAccelerationMode(true)
                            .setIntensity(0.9 + Math.random() * 0.2);
                    break;
                case SMALL:
                    projectileAmount = 1;
                    jumpVelHor = TheSlimeGod.SMALL_SLIME_HOR_VEL;
                    jumpVelVer = TheSlimeGod.SMALL_SLIME_VER_VEL;
                    jumpDelay = TheSlimeGod.SMALL_JUMP_DELAY;
                    aimOption
                            .setIntensity(0.85 + Math.random() * 0.3);
                    break;
            }
            jumpVelVer *= Math.random() * 0.4 + 0.8;
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
