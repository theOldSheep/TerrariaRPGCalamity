package terraria.entity.boss.postMoonLord.calamitas;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Consumer;
import org.bukkit.util.Vector;
import terraria.entity.projectile.BulletHellProjectile;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class SupremeCalamitas extends EntitySlime {
    private static class BulletHellProjectileOption {
        String projectileType;
        BulletHellProjectile.ProjectileType shootType;
        HashMap<String, Double> attrMap;
        double speedMin, speedVariation;
        int ticksInterval, projectileLiveTime;
        private BulletHellProjectileOption(String projectileType, BulletHellProjectile.ProjectileType shootType, HashMap<String, Double> attrMap,
                                           double speedMin, double speedVariation, int ticksInterval, int projectileLiveTime) {
            this.projectileType = projectileType;
            this.shootType = shootType;
            this.attrMap = attrMap;
            this.speedMin = speedMin;
            this.speedVariation = speedVariation;
            this.ticksInterval = ticksInterval;
            this.projectileLiveTime = projectileLiveTime;
        }
    }
    private static class BulletHellPattern {
        double healthRatio;
        int duration;
        HashSet<BulletHellProjectileOption> projectileCandidates;
        Consumer<SupremeCalamitas> beginFunc, endFunc;
        private BulletHellPattern(double healthRatio, int duration) {
            this.healthRatio = healthRatio;
            this.duration = duration;
            projectileCandidates = new HashSet<>();
            beginFunc = null;
            endFunc = null;
        }
        BulletHellPattern addCandidate(BulletHellProjectileOption projectile) {
            projectileCandidates.add(projectile);
            return this;
        }
        BulletHellPattern setBeginFunc(Consumer<SupremeCalamitas> beginFunc) {
            this.beginFunc = beginFunc;
            return this;
        }
        BulletHellPattern setEndFunc(Consumer<SupremeCalamitas> endFunc) {
            this.endFunc = endFunc;
            return this;
        }
    }
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SUPREME_WITCH_CALAMITAS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 2760000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<String, EntityHelper.ProjectileShootInfo> bulletHellShootInfoMap = new HashMap<>();
    BulletHellProjectile.BulletHellDirectionInfo bulletHellDir = null;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapPrjLow, attrMapPrjNormal, attrMapPrjMid, attrMapPrjHigh;
    static BulletHellPattern[] bulletHellPatterns;
    static {
        attrMapPrjLow = new HashMap<>();
        attrMapPrjLow.put("damage", 1350d);
        attrMapPrjLow.put("knockback", 1.5d);
        attrMapPrjNormal = new HashMap<>();
        attrMapPrjNormal.put("damage", 1450d);
        attrMapPrjNormal.put("knockback", 2.0d);
        attrMapPrjMid = new HashMap<>();
        attrMapPrjMid.put("damage", 1560d);
        attrMapPrjMid.put("knockback", 2.5d);
        attrMapPrjHigh = new HashMap<>();
        attrMapPrjHigh.put("damage", 1850d);
        attrMapPrjHigh.put("knockback", 3.75d);

        bulletHellPatterns = new BulletHellPattern[]{
                // first bullet hell when summoned
                new BulletHellPattern(1.1, 300),
                // second bullet hell 75% health
                new BulletHellPattern(0.75, 325),
                // third bullet hell 50% health
                new BulletHellPattern(0.5, 350),
                // spawn brothers 45% health
                new BulletHellPattern(0.45, 50),
                // fourth bullet hell 30% health
                new BulletHellPattern(0.3, 375),
                // another sepulcher at 20% health
                new BulletHellPattern(0.2, 50),
                // final bullet hell 10% health
                new BulletHellPattern(0.1, 400),
                // final conversation
                new BulletHellPattern(0.005, 200)
                        .setEndFunc(SupremeCalamitas::die),
        };
    }

    EntityHelper.ProjectileShootInfo shootInfoDart, shootInfoHellBlast, shootInfoFireBlast, shootInfoGigaBlast;
    int bulletHellPatternIdx = 0;
    boolean bulletHellPatternActive = false;

    int indexAI = 0, AIPhase = 0;
    Vector velocity = new Vector();

    private void initBulletHellInfo() {
        bulletHellDir = new BulletHellProjectile.BulletHellDirectionInfo(target);
    }
    private void tickBulletHellRotation() {
        if (ticksLived % 25 == 0) {
            PlayerPOVHelper.getInstance().moveCamera(target, 32, 3000);
        }

        Location loc = target.getLocation();
        if (MathHelper.getAngleRadian(bulletHellDir.planeNormal, loc.getDirection()) > 1e-5) {
            loc.setDirection(bulletHellDir.planeNormal);
            target.teleport(loc);
        }

    }
    private void handleBulletHell() {
        bulletHellDir.target = target;
        // target rotation
        tickBulletHellRotation();
        // spawn projectiles
        for (BulletHellProjectileOption projOption : bulletHellPatterns[bulletHellPatternIdx].projectileCandidates) {
            if (ticksLived % projOption.ticksInterval == 0) {
                // prepare shoot info
                EntityHelper.ProjectileShootInfo shootInfo = bulletHellShootInfoMap.computeIfAbsent(projOption.projectileType,
                        (type) -> new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), projOption.attrMap,
                                EntityHelper.DamageType.MAGIC, type
                        ));
                // fire projectile
                shootInfo.setLockedTarget(target);
                double speed = projOption.speedMin + Math.random() * projOption.speedVariation;
                BulletHellProjectile projectile = new BulletHellProjectile(shootInfo, projOption.shootType, 48, speed, bulletHellDir);
                projectile.liveTime = projOption.projectileLiveTime;
            }
        }
    }
    private void normalAIPhase() {

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


                BulletHellPattern pattern;
                if (bulletHellPatternIdx < bulletHellPatterns.length)
                    pattern = bulletHellPatterns[bulletHellPatternIdx];
                else
                    pattern = null;
                // bullet hell
                if (bulletHellPatternActive) {
                    handleBulletHell();
                    // termination. "pattern" would not be null here, don't worry.
                    if (indexAI > pattern.duration) {
                        bulletHellPatternIdx++;
                        indexAI = -1;
                        bulletHellPatternActive = false;
                        removeScoreboardTag("noDamage");
                        if (pattern.endFunc != null)
                            pattern.endFunc.accept(this);
                        if (bulletHellPatternIdx < bulletHellPatterns.length) {
                            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT,
                                    (getMaxHealth() * bulletHellPatterns[bulletHellPatternIdx].healthRatio) - 10);
                        }
                    }
                }
                // normal behavior
                else {
                    normalAIPhase();
                    // bullet hell transition
                    if (pattern != null && getHealth() / getMaxHealth() < pattern.healthRatio) {
                        indexAI = -1;
                        bulletHellPatternActive = true;
                        addScoreboardTag("noDamage");
                        if (pattern.beginFunc != null)
                            pattern.beginFunc.accept(this);
                    }
                }

                // index increment & velocity maintenance
                indexAI ++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        // facing
        if (false)
            this.yaw = (float) MathHelper.getVectorYaw( velocity );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public SupremeCalamitas(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public SupremeCalamitas(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        spawnLoc.setY( spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
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
            attrMap.put("damage", 1400d);
            attrMap.put("damageTakenMulti", 0.75);
            attrMap.put("defence", 200d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MAGIC);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(12, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoDart = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrjLow,
                    EntityHelper.DamageType.MAGIC, "硫火飞弹");
            shootInfoHellBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrjNormal,
                    EntityHelper.DamageType.MAGIC, "深渊亡魂");
            shootInfoFireBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrjNormal,
                    EntityHelper.DamageType.MAGIC, "无际裂变球");
            shootInfoGigaBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrjMid,
                    EntityHelper.DamageType.MAGIC, "深渊炙炎弹");
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