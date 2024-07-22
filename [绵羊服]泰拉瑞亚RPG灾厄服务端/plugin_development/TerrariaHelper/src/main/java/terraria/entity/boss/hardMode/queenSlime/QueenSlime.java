package terraria.entity.boss.hardMode.queenSlime;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class QueenSlime extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.QUEEN_SLIME;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.HALLOW;
    public static final double BASIC_HEALTH = 66096 * 2, BASIC_HEALTH_BR = 367200 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final int MAX_SIZE = 16, MIN_SIZE = 12, SHRINK_TIME = 15, DISAPPEAR_TIME = 5;
    static final double LEAP_SPEED = 2, SMASH_SPEED_NORMAL = 1, SMASH_SPEED_ENRAGED = 1.75,
            FLY_SPEED = 5, FLY_ACC = 0.25, SLIME_SPAWN_RATE = 0.0075;
    static final HashMap<String, Double> attrMapRegalGel;
    static {
        attrMapRegalGel = new HashMap<>();
        attrMapRegalGel.put("damage", 420d);
        attrMapRegalGel.put("health", 1d);
        attrMapRegalGel.put("healthMax", 1d);
        attrMapRegalGel.put("knockback", 4d);
    }
    public EntityHelper.ProjectileShootInfo projectileProperty;
    enum SizeState {
        NORMAL, DISAPPEARED, SHRINKING, GROWING, LEAP, SMASH, FLY;
    }
    int indexAI = 0, jumpAmount = 0, sizeChangeTimeIndex = 0, normalSize = MAX_SIZE;
    boolean secondPhase = false;
    Vector flyVel = new Vector();
    SizeState sizeChangeState = SizeState.DISAPPEARED;
    private boolean isOnGround() {
        return ( (!secondPhase) && onGround) ||
                bukkitEntity.getLocation().subtract(0, 0.25, 0).getBlock().getType() != org.bukkit.Material.AIR;
    }
    private void shootGels() {
        projectileProperty.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        for (int i = 0; i < 20; i++) {
            projectileProperty.velocity = MathHelper.randomVector();
            EntityHelper.spawnProjectile(projectileProperty);
        }
    }
    private void spawnSlimes() {
        if (Math.random() < SLIME_SPAWN_RATE)
            MonsterHelper.spawnMob("水晶史莱姆", bukkitEntity.getLocation(), target);
        if (Math.random() < SLIME_SPAWN_RATE)
            MonsterHelper.spawnMob("飞翔史莱姆", bukkitEntity.getLocation(), target);
        if (Math.random() < SLIME_SPAWN_RATE)
            MonsterHelper.spawnMob("弹力史莱姆", bukkitEntity.getLocation(), target);
    }
    // return value: should other AI mechanics halt?
    private boolean handleTeleport() {
        setNoGravity(sizeChangeState == SizeState.DISAPPEARED);
        switch (sizeChangeState) {
            case NORMAL: {
                // if boss is on ground
                if (isOnGround()) {
                    switch (jumpAmount % 5) {
                        // leap attack
                        case 3:
                            sizeChangeState = SizeState.LEAP;
                            jumpAmount ++;
                            return true;
                        // shrink and teleport
                        case 4:
                            sizeChangeState = SizeState.SHRINKING;
                            addScoreboardTag("noDamage");
                            sizeChangeTimeIndex = 0;
                            jumpAmount ++;
                            return true;
                    }
                }
                // if the boss is not on ground, do not attempt to jump
                else
                    return true;
                break;
            }
            case SHRINKING: {
                EntityHelper.slimeResize((Slime) bukkitEntity,
                        (int) Math.ceil(normalSize * (double) (SHRINK_TIME - sizeChangeTimeIndex) / SHRINK_TIME) );
                sizeChangeTimeIndex ++;
                if (sizeChangeTimeIndex >= SHRINK_TIME) {
                    sizeChangeState = SizeState.DISAPPEARED;
                    Location teleportLoc = bukkitEntity.getLocation();
                    teleportLoc.setY(-1);
                    bukkitEntity.teleport(teleportLoc);
                    sizeChangeTimeIndex = 0;
                }
                return true;
            }
            case DISAPPEARED: {
                sizeChangeTimeIndex ++;
                if (sizeChangeTimeIndex >= DISAPPEAR_TIME) {
                    sizeChangeState = SizeState.GROWING;
                    sizeChangeTimeIndex = 1;
                    Location targetLoc = target.getWorld().getHighestBlockAt(target.getLocation()).getLocation().add(0, 1, 0);
                    bukkitEntity.teleport(targetLoc);
                }
                return true;
            }
            case GROWING: {
                EntityHelper.slimeResize((Slime) bukkitEntity,
                        (int) Math.ceil(normalSize * (double) sizeChangeTimeIndex / SHRINK_TIME) );
                sizeChangeTimeIndex ++;
                if (sizeChangeTimeIndex >= SHRINK_TIME) {
                    sizeChangeState = SizeState.NORMAL;
                    removeScoreboardTag("noDamage");
                    sizeChangeTimeIndex = 0;
                    EntityHelper.slimeResize((Slime) bukkitEntity, normalSize);
                }
                return true;
            }
            case LEAP: {
                noclip = true;
                Location targetLoc = target.getLocation().add(0, 8, 0);
                Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                double velLen = velocity.length();
                if (velLen > LEAP_SPEED) {
                    velocity.multiply(LEAP_SPEED / velLen);
                }
                bukkitEntity.setVelocity(velocity);
                if (velLen < LEAP_SPEED) {
                    sizeChangeState = SizeState.SMASH;
                    sizeChangeTimeIndex = 0;
                }
                return true;
            }
            case SMASH: {
                // begin smash
                if (sizeChangeTimeIndex == 0) {
                    EntityHelper.tweakAttribute(attrMap, "damageMeleeMulti", "0.2", true);
                }
                motX = 0;
                motY = -(secondPhase ? SMASH_SPEED_NORMAL : SMASH_SPEED_ENRAGED);
                motZ = 0;
                sizeChangeTimeIndex ++;
                // end smash
                if (locY <= 1 || (locY <= target.getLocation().getY() && isOnGround() )) {
                    EntityHelper.tweakAttribute(attrMap, "damageMeleeMulti", "0.2", false);
                    if (secondPhase) {
                        sizeChangeState = SizeState.FLY;
                    }
                    else {
                        sizeChangeState = SizeState.NORMAL;
                        noclip = false;
                    }
                    sizeChangeTimeIndex = 0;
                    motY = 0;
                }
                return true;
            }
            case FLY: {
                sizeChangeTimeIndex ++;
                // attempt to fly above the player
                Location targetLoc = target.getLocation().add(0, 12, 0);
                // regulate magnitude of acceleration
                Vector acc = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                double accLen = acc.length();
                if (accLen > FLY_ACC) {
                    acc.multiply(FLY_ACC / accLen);
                }
                // add acceleration to velocity
                flyVel.add(acc);
                double velLen = flyVel.length();
                if (velLen > FLY_SPEED) {
                    flyVel.multiply( FLY_SPEED / velLen );
                }
                try {
                    Vector velocity = MathHelper.vectorProjection(acc, flyVel);
                    velocity.setY( velocity.getY() * 0.5);
                    bukkitEntity.setVelocity(velocity);
                } catch (Exception ignored) {}
                // smash
                if (sizeChangeTimeIndex >= 50 && accLen < 2) {
                    bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                    sizeChangeState = SizeState.SMASH;
                    sizeChangeTimeIndex = 0;
                    flyVel = new Vector();
                }
                // shoot gelatin
                else if (sizeChangeTimeIndex % 50 == 35) {
                    shootGels();
                }
                return true;
            }
        }
        return false;
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
            // disappear if no target is available
            if (target == null) {
                for (LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                return;
            }
            // if target is valid, attack
            else {
                // increase player aggro duration
                targetMap.get(target.getUniqueId()).addAggressionTick();
                // AI
                if (secondPhase || ticksLived % 2 == 0) {
                    double healthRatio = getHealth() / getMaxHealth();
                    int lastNormalSize = normalSize;
                    normalSize = (int) Math.round(MIN_SIZE + (MAX_SIZE - MIN_SIZE) * healthRatio);
                    // teleport mechanism
                    if (!handleTeleport()) {
                        if (normalSize != lastNormalSize)
                            EntityHelper.slimeResize((Slime) bukkitEntity, normalSize);

                        // jump
                        if (indexAI % 5 == 0) {
                            Vector velocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                            velocity.setY(0);
                            velocity.normalize().multiply(1.75);
                            velocity.setY(1.35);
                            bukkitEntity.setVelocity(velocity);
                            jumpAmount++;
                        }
                        indexAI++;

                        // phase 2
                        if (!secondPhase && healthRatio < 0.5) {
                            secondPhase = true;
                            setNoGravity(true);
                            noclip = true;
                            sizeChangeState = SizeState.FLY;
                        }
                    }
                    spawnSlimes();
                }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        switch (sizeChangeState) {
            case SHRINKING:
            case DISAPPEARED:
            case GROWING:
                break;
            default:
                terraria.entity.boss.BossHelper.collisionDamage(this);
        }
    }
    // default constructor to handle chunk unload
    public QueenSlime(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public QueenSlime(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 30;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        spawnLoc.setY(-1);
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
        goalSelector.a(0, new PathfinderGoalFloat(this));
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 480d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 52d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.WALL_OF_FLESH.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(16, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = false;
            this.persistent = true;
        }
        // projectile info
        {
            projectileProperty = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapRegalGel,
                    EntityHelper.DamageType.ARROW, "挥发明胶");
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);

            // send loot
            terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
        }
    }
    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
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
