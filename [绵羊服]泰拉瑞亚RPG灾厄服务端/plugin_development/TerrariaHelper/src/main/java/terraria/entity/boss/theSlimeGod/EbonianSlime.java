package terraria.entity.boss.theSlimeGod;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class EbonianSlime extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_SLIME_GOD;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 15552 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
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
    EntityHelper.AimHelperOptions aimOption;
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
            Location targetLoc = EntityHelper.helperAimEntity(bukkitEntity, target, aimOption);
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
            int tickInterval = owner.crimulanDefeated ? 1 : 3;
            boolean currentlyOnGround = isOnGround();
            if (ticksLived % tickInterval == 0) {
                // if boss is on ground
                if (isOnGround()) {
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
    public EbonianSlime(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public EbonianSlime(TheSlimeGod owner) {
        this(owner, SlimeSize.BIG, owner.getBukkitEntity());
    }
    public EbonianSlime(TheSlimeGod owner, SlimeSize size, Entity spawner) {
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
        setCustomName("黑檀史莱姆");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
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
            attrMap.put("defence", 12d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            EntityHelper.setDamageType(bukkitEntity, "Melee");
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init target map
        {
            targetMap = owner.targetMap;
            target = owner.target;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
        }
        // init health and slime size
        {
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            switch (slimeSize) {
                case BIG:
                    setSize(16, false);
                    break;
                case MIDDLE:
                    setSize(8, false);
                    healthMulti *= 0.33;
                    break;
                case SMALL:
                    setSize(4, false);
                    healthMulti *= 0.05;
                    break;
            }
            double health = BASIC_HEALTH * healthMulti;
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
            shootInfo = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), ATTR_MAP_PROJECTILE, "黑檀深渊之球");
            aimOption = new EntityHelper.AimHelperOptions()
                    .setAimMode(false)
                    .setProjectileSpeed(TheSlimeGod.PROJECTILE_SPEED);
            switch (slimeSize) {
                case BIG:
                    projectileAmount = 8;
                    jumpVelHor = TheSlimeGod.BIG_SLIME_HOR_VEL;
                    jumpVelVer = TheSlimeGod.BIG_SLIME_VER_VEL;
                    jumpDelay = TheSlimeGod.BIG_JUMP_DELAY;
                    aimOption
                            .setIntensity(1d)
                            .setRandomOffsetRadius(4d);
                    break;
                case MIDDLE:
                    projectileAmount = 3;
                    jumpVelHor = TheSlimeGod.MID_SLIME_HOR_VEL;
                    jumpVelVer = TheSlimeGod.MID_SLIME_VER_VEL;
                    jumpDelay = TheSlimeGod.MID_JUMP_DELAY;
                    aimOption
                            .setIntensity(0.9 + Math.random() * 0.2)
                            .setRandomOffsetRadius(2.5d);
                    break;
                case SMALL:
                    projectileAmount = 1;
                    jumpVelHor = TheSlimeGod.SMALL_SLIME_HOR_VEL;
                    jumpVelVer = TheSlimeGod.SMALL_SLIME_VER_VEL;
                    jumpDelay = TheSlimeGod.SMALL_JUMP_DELAY;
                    aimOption
                            .setIntensity(0.5 + Math.random())
                            .setRandomOffsetRadius(1d);
                    break;
            }
            jumpVelVer *= Math.random() * 0.4 + 0.8;
        }
    }

    @Override
    public void die() {
        super.die();
        // spawn smaller slimes
        switch (slimeSize) {
            case BIG:
                for (int i = 0; i < 4; i ++) {
                    new EbonianSlime(owner, SlimeSize.MIDDLE, getBukkitEntity());
                }
                break;
            case MIDDLE:
                for (int i = 0; i < 5; i ++) {
                    new EbonianSlime(owner, SlimeSize.SMALL, getBukkitEntity());
                }
                break;
        }
    }
    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // load nearby chunks
        {
            for (int i = -2; i <= 2; i ++)
                for (int j = -2; j <= 2; j ++) {
                    org.bukkit.Chunk currChunk = bukkitEntity.getLocation().add(i << 4, 0, j << 4).getChunk();
                    currChunk.load();
                }
        }
        // AI
        AI();
    }
}
