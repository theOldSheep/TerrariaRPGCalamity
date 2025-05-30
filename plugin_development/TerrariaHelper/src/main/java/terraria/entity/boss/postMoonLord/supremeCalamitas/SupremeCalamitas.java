package terraria.entity.boss.postMoonLord.supremeCalamitas;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.BossProjectilesManager;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SupremeCalamitas extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SUPREME_WITCH_CALAMITAS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 2760000 * 2, BASIC_HEALTH_BR = 1656000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    public static final String MSG_PREFIX = "§#FFA500";
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static double DASH_SPEED = 4, HOVER_SPEED = 2.75, DART_SPEED = 3.25, HELL_BLAST_SPEED = 3.5, GIGA_BLAST_SPEED = 1.25,
            HOVER_DISTANCE = 32, DART_SPREAD_SINGLE = 5, DART_SPREAD_TOTAL = 16;
    static HashMap<String, Double> attrMapPrjLow, attrMapPrjMid, attrMapPrjHigh, attrMapPrjExtreme;
    static AimHelper.AimHelperOptions dashAimHelper, blastAimHelper;
    static {
        attrMapPrjLow = new HashMap<>();
        attrMapPrjLow.put("damage", 1500d);
        attrMapPrjLow.put("knockback", 1.5d);
        attrMapPrjMid = new HashMap<>();
        attrMapPrjMid.put("damage", 1680d);
        attrMapPrjMid.put("knockback", 2.0d);
        attrMapPrjHigh = new HashMap<>();
        attrMapPrjHigh.put("damage", 1800d);
        attrMapPrjHigh.put("knockback", 2.5d);
        attrMapPrjExtreme = new HashMap<>();
        attrMapPrjExtreme.put("damage", 2040d);
        attrMapPrjExtreme.put("knockback", 3.75d);

        dashAimHelper = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(DASH_SPEED)
                .setAccelerationMode(true);
        blastAimHelper = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(HELL_BLAST_SPEED)
                .setAccelerationMode(true);
    }

    EntityHelper.ProjectileShootInfo shootInfoDart, shootInfoHellBlast, shootInfoGigaBlast;
    int bulletHellPatternIdx = 0;
    boolean bulletHellPatternActive = false;
    ISupremeCalamitasBH[] bulletHellPatterns;
    ISupremeCalamitasBH bulletHell = null;

    // do not init indexAI as 0 to prevent a projectile fired before the first bullet hell
    int indexAI = -50, attackType = 0;
    Vector velocity = new Vector();
    BossProjectilesManager projectilesManager = new BossProjectilesManager();


    void generalMovement(double dist) {
        Vector offsetDir = MathHelper.vectorFromYawPitch_approx(
                MathHelper.getVectorYaw(bukkitEntity.getLocation().subtract(target.getLocation()).toVector()), 0);
        offsetDir.multiply(dist);
        Location eyeHoverLoc = target.getEyeLocation().add(offsetDir);
        velocity = MathHelper.getDirection( ((LivingEntity) bukkitEntity).getEyeLocation(), eyeHoverLoc, HOVER_SPEED, true);
    }
    void generalHover(double dist) {
        Vector offsetDir = new Vector(0, dist, 0);
        Location eyeHoverLoc = target.getEyeLocation().add(offsetDir);
        velocity = MathHelper.getDirection( ((LivingEntity) bukkitEntity).getEyeLocation(), eyeHoverLoc, HOVER_SPEED, true);
    }

    private void normalAIPhase() {
        // true pattern:
        // 1. darts
        // 2. hell blasts
        // 3. giga blasts
        // 4. dash
        LivingEntity livingEntity = (LivingEntity) bukkitEntity;

        boolean isPhase2 = getHealth() / getMaxHealth() < 0.44;
        int trueAttackPattern, attackInterval, attackTimes;
        switch (attackType % 21) {
            case 0:
            case 6:
            case 8:
            case 13:
            case 16:
            case 18:
            case 20:
                trueAttackPattern = 1;
                attackInterval = 15;
                attackTimes = isPhase2 ? 3 : 5;
                break;
            case 1:
            case 5:
            case 9:
            case 11:
                trueAttackPattern = 2;
                attackInterval = 1;
                attackTimes = 30;
                break;
            case 2:
            case 4:
                trueAttackPattern = 3;
                attackInterval = 10;
                attackTimes = 2;
                break;
            case 10:
            case 14:
                trueAttackPattern = 3;
                attackInterval = 10;
                attackTimes = 4;
                break;
            case 3:
            case 15:
                trueAttackPattern = 4;
                attackInterval = 25;
                attackTimes = isPhase2 ? 2 : 4;
                break;
            case 7:
            case 12:
            case 17:
            case 19:
                trueAttackPattern = 4;
                attackInterval = 25;
                attackTimes = isPhase2 ? 1 : 2;
                break;
            default:
                trueAttackPattern = 0;
                attackInterval = 0;
                attackTimes = 0;
        }

        // general movement
        if (trueAttackPattern != 4) {
            generalMovement(HOVER_DISTANCE);
        }
        // projectile
        if (indexAI % attackInterval == 0 && indexAI >= 0) {
            switch (trueAttackPattern) {
                // spread of darts
                case 1:
                    shootInfoDart.shootLoc = livingEntity.getEyeLocation();
                    shootInfoDart.setLockedTarget(target);
                    for (Vector projVel : MathHelper.getEvenlySpacedProjectileDirections(DART_SPREAD_SINGLE, DART_SPREAD_TOTAL, target, shootInfoDart.shootLoc, DART_SPEED)) {
                        shootInfoDart.velocity = projVel;
                        projectilesManager.handleProjectile( EntityHelper.spawnProjectile(shootInfoDart) );
                    }
                    break;
                // hell blasts
                case 2:
                    shootInfoHellBlast.shootLoc = livingEntity.getEyeLocation();
                    shootInfoHellBlast.setLockedTarget(target);
                    Location aimedLoc = AimHelper.helperAimEntity(shootInfoHellBlast.shootLoc, target, blastAimHelper);
                    shootInfoHellBlast.velocity = MathHelper.getDirection(shootInfoHellBlast.shootLoc, aimedLoc, HELL_BLAST_SPEED);
                    projectilesManager.handleProjectile( EntityHelper.spawnProjectile(shootInfoHellBlast) );
                    break;
                // giga blasts
                case 3:
                    shootInfoGigaBlast.shootLoc = livingEntity.getEyeLocation();
                    shootInfoGigaBlast.setLockedTarget(target);
                    shootInfoGigaBlast.velocity = MathHelper.getDirection(shootInfoGigaBlast.shootLoc, target.getEyeLocation(), GIGA_BLAST_SPEED);
                    projectilesManager.handleProjectile( EntityHelper.spawnProjectile(shootInfoGigaBlast) );
                    break;
                // dash
                case 4:
                    Location targetLoc = AimHelper.helperAimEntity(bukkitEntity, target, dashAimHelper);
                    velocity = MathHelper.getDirection(livingEntity.getEyeLocation(), targetLoc, DASH_SPEED);
                    break;
            }
        }
        // next phase
        if (indexAI + 1 >= attackTimes * attackInterval) {
            indexAI = -1;
            attackType ++;
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            Player lastTarget = target;
            target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
            // disappear if no target is available
            if (target == null) {
                for (LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                if (bulletHell != null)
                    bulletHell.finish();
                return;
            }
            // if target is valid, attack
            else {
                // increase player aggro duration
                targetMap.get(target.getUniqueId()).addAggressionTick();

                // bullet hell; movement / attack handled within bullet hell.
                bulletHellPatternActive = bulletHell != null && bulletHell.isStrict();
                if (bulletHell != null) {
                    bulletHell.tick();
                    // refresh duration when target changes
                    if (lastTarget != target) {
                        bulletHell.refresh();
                    }
                    if (! bulletHell.inProgress()) {
                        bulletHell.finish();
                        bulletHell = null;
                        // no attacks for the next 2.5 seconds out of the bullet hell
                        indexAI = -50;
                        attackType = 0;
                        bulletHellPatternIdx++;
                        if (bulletHellPatternIdx < bulletHellPatterns.length) {
                            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT,
                                    (getMaxHealth() * bulletHellPatterns[bulletHellPatternIdx].healthRatio()) - 10);
                        }
                    }
                }
                if (bulletHellPatternActive)
                    addScoreboardTag("noDamage");
                else {
                    removeScoreboardTag("noDamage");
                    normalAIPhase();

                    // bullet hell transition
                    ISupremeCalamitasBH pattern;
                    if (bulletHellPatternIdx < bulletHellPatterns.length)
                        pattern = bulletHellPatterns[bulletHellPatternIdx];
                    else
                        pattern = null;
                    if (pattern != null && getHealth() / getMaxHealth() < pattern.healthRatio()) {
                        bulletHell = pattern;
                        bulletHell.begin();
                    }

                    // index increment
                    indexAI++;
                }
            }
            // velocity maintenance
            bukkitEntity.setVelocity(velocity);
        }
        // facing
        if (false)
            this.yaw = (float) MathHelper.getVectorYaw( velocity );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        if (!bulletHellPatternActive)
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
            attrMap.put("damage", 1800d);
            attrMap.put("damageTakenMulti", 0.75);
            attrMap.put("defence", 200d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MAGIC);
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
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
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
                    DamageHelper.DamageType.MAGIC, "灾厄飞弹");
            shootInfoHellBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrjMid,
                    DamageHelper.DamageType.MAGIC, "灾厄亡魂");
            shootInfoGigaBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrjHigh,
                    DamageHelper.DamageType.MAGIC, "深渊炙炎弹");
        }
        // bullet hells
        {
            bulletHellPatterns = new ISupremeCalamitasBH[]{
                    // first bullet hell when summoned
                    new SupremeCalamitasBHFirework(this, 1.1),
                    new SupremeCalamitasBHSepulcher(this),
                    // second bullet hell 75% health
                    new SupremeCalamitasBHRing(this, 0.75),
                    // third bullet hell 50% health
                    new SupremeCalamitasBHBrimstone(this, 0.5),
                    // spawn brothers 45% health
                    new SupremeCalamitasBHBrother(this, 0.45),
                    // fourth bullet hell at 30% health
                    new SupremeCalamitasBHEvaporation(this, 0.3),
                    new SupremeCalamitasBHSepulcher(this),
                    // final bullet hell 10% health
                    new SupremeCalamitasBHAsh(this, 0.1),
                    // final conversation
                    new SupremeCalamitasBHFinalConversation(this, 0.005)
            };
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