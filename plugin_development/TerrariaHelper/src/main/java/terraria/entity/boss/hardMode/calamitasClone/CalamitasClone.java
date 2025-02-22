package terraria.entity.boss.hardMode.calamitasClone;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.BossProjectilesManager;
import terraria.entity.projectile.BulletHellProjectile;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class CalamitasClone extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CALAMITAS_CLONE;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 108000 * 2, BASIC_HEALTH_BR = 746000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final String BULLET_HELL_WARNING = "§6§l弹幕炼狱开始，请小心躲避！", BROTHER_REBORN = "§6§l兄弟重生!";
    static final double FINAL_DASH_SPEED = 3, SPEED = 2, BULLET_HELL_RADIUS = 32, DART_SPEED = 0.5, DISPLAY_OFFSET = 32;
    static final HashMap<String, Double> attrMapBrimstoneDart, attrMapHellFireball;
    static final AimHelper.AimHelperOptions dashAimHelper;
    static {
        dashAimHelper = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(FINAL_DASH_SPEED);

        attrMapBrimstoneDart = new HashMap<>();
        attrMapBrimstoneDart.put("damage", 352d);
        attrMapBrimstoneDart.put("knockback", 2d);
        attrMapHellFireball = new HashMap<>();
        attrMapHellFireball.put("damage", 468d);
        attrMapHellFireball.put("knockback", 2d);
    }
    BossProjectilesManager projectilesManager = new BossProjectilesManager();
    public EntityHelper.ProjectileShootInfo psiFireBlast, psiDart, psiFireball, psiHellBlast;
    Vector dashVelocity = new Vector();
    boolean brothersAlive = false;
    int indexAI = -40, attackMethod = 1, bulletHellTicksLeft = -1, lastBulletHellDuration = 300, healthLockProgress = 1;
    HashSet<Entity> bulletHellProjectiles = new HashSet<>();
    BulletHellProjectile.BulletHellDirectionInfo bulletHellDir = null;
    // 1: fireball   2: hell blast
    private void beginBulletHell(int ticksDuration) {
        lastBulletHellDuration = ticksDuration;
        bulletHellTicksLeft = ticksDuration;
        addScoreboardTag("noDamage");
        Bukkit.broadcastMessage(BULLET_HELL_WARNING);
        bulletHellDir = new BulletHellProjectile.BulletHellDirectionInfo(target);
        PlayerPOVHelper.setPOVState(target, true);
        projectilesManager.killAll();
    }
    private void tickBulletHellRotation() {
        if (ticksLived % 25 == 0) {
            PlayerPOVHelper.setPOVState(target, true);
        }

        Location loc = target.getLocation();
        if (MathHelper.getAngleRadian(bulletHellDir.planeNormal, loc.getDirection()) > 1e-5) {
            loc.setDirection(bulletHellDir.planeNormal);
            target.teleport(loc);
        }
    }
    private void spawnBulletHellProjectile(BulletHellProjectile.ProjectileType projectileType, int ticksLive, double speed) {
        EntityHelper.ProjectileShootInfo shootInfo;
        switch (projectileType) {
            case SQUARE_BORDER:
                shootInfo = psiHellBlast;
                break;
            case CIRCUMFERENCE:
                shootInfo = psiFireBlast;
                break;
            default:
                return;
        }
        shootInfo.setLockedTarget(target);
        BulletHellProjectile projectile = new BulletHellProjectile(shootInfo, projectileType, 32, speed, bulletHellDir);
        projectilesManager.handleProjectile(projectile.bukkitEntity);
        projectile.liveTime = ticksLive;
    }
    private void handleBulletHell() {
        bulletHellDir.target = target;
        // target rotation
        tickBulletHellRotation();
        // spawn projectiles
        if (--bulletHellTicksLeft > 0) {
            // hell blast
            boolean secondBulletHell = healthLockProgress == 4;
            double hellBlastProbability = secondBulletHell ? 0.6 : 0.4;
            if (Math.random() < hellBlastProbability) {
                double projectileSpeed = 0.35 + Math.random() * 0.15;
                int ticksLive = (int) (BULLET_HELL_RADIUS * 3 / projectileSpeed);
                // spawn projectile
                spawnBulletHellProjectile(BulletHellProjectile.ProjectileType.SQUARE_BORDER, ticksLive, projectileSpeed);
            }
            // fire blasts
            if (bulletHellTicksLeft % 50 == 0 && secondBulletHell) {
                spawnBulletHellProjectile(BulletHellProjectile.ProjectileType.CIRCUMFERENCE, 50, 0.75);
            }
        }
        // remove bullet hell projectiles
        else {
            projectilesManager.killAll();
        }
    }
    private void shootProjectile(int type) {
        switch (type) {
            case 1: {
                psiFireball.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                psiFireball.velocity = MathHelper.getDirection(psiFireball.shootLoc, target.getEyeLocation(), 2.25);
                projectilesManager.handleProjectile( EntityHelper.spawnProjectile(psiFireball) );
                break;
            }
            case 2: {
                psiHellBlast.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                psiHellBlast.velocity = MathHelper.getDirection(psiHellBlast.shootLoc, target.getEyeLocation(), 2);
                projectilesManager.handleProjectile( EntityHelper.spawnProjectile(psiHellBlast) );
                break;
            }
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
                    IGNORE_DISTANCE, terraria.entity.boss.BossHelper.TimeRequirement.NIGHT, BIOME_REQUIRED, targetMap.keySet());
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
                // calculate brothers alive
                if (brothersAlive) {
                    if (bossParts.get(1).isDead() && bossParts.get(2).isDead())
                        brothersAlive = false;
                }
                boolean duringBulletHell = bulletHellTicksLeft > 0 || bulletHellProjectiles.size() > 0;
                if (duringBulletHell || brothersAlive)
                    addScoreboardTag("noDamage");
                else
                    removeScoreboardTag("noDamage");
                // occasionally manage the projectiles
                if (indexAI % 20 == 0)
                    projectilesManager.dropOutdated();
                // bullet hell
                if ( duringBulletHell ) {
                    // spawn and handle bullet hell projectiles
                    handleBulletHell();
                    // on player kill, restore bullet hell duration
                    if (lastTarget != target)
                        beginBulletHell(lastBulletHellDuration);
                    // stay far above the player
                    bukkitEntity.setVelocity(MathHelper.getDirection(
                            bukkitEntity.getLocation(), target.getEyeLocation().add(0, 24, 0),
                            4, true));
                    // give player some time to react after finishing bullet hell
                    indexAI = -10;
                }
                // normal attack
                else {
                    // get health ratio
                    double healthRatio = getHealth() / getMaxHealth();
                    // health lock and phase switch handling
                    switch (healthLockProgress) {
                        // 70%
                        case 1:
                            if (healthRatio < 0.7) {
                                beginBulletHell(300);
                                EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, getMaxHealth() * 0.39);
                                healthLockProgress = 2;
                            }
                            break;
                        // 40%
                        case 2:
                            if (healthRatio < 0.4) {
                                new Catastrophe(target, this);
                                new Cataclysm(target, this);
                                Bukkit.broadcastMessage(BROTHER_REBORN);
                                brothersAlive = true;
                                EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, getMaxHealth() * 0.09);
                                addScoreboardTag("noDamage");
                                healthLockProgress = 3;
                            }
                            break;
                        // 10%
                        case 3:
                            if (healthRatio < 0.1) {
                                beginBulletHell(400);
                                EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, null);
                                healthLockProgress = 4;
                            }
                            break;
                    }
                    if (indexAI >= 0) {
                        // forced attack methods
                        if (brothersAlive)
                            attackMethod = 1;
                        else if (healthRatio < 0.4)
                            attackMethod = 3;
                        switch (attackMethod) {
                            case 1: {
                                // set velocity
                                bukkitEntity.setVelocity( MathHelper.getDirection(
                                        bukkitEntity.getLocation(), target.getEyeLocation().add(0, 12, 0),
                                        SPEED, true));
                                // shoot projectile
                                if (indexAI % 10 == 0) {
                                    shootProjectile(1);
                                }
                                // switch phase
                                if (indexAI >= 80) {
                                    indexAI = -1;
                                    attackMethod = 2;
                                }
                                break;
                            }
                            case 2: {
                                // set velocity
                                Vector offset = ((LivingEntity) bukkitEntity).getEyeLocation().subtract( target.getEyeLocation() ).toVector();
                                offset.setY(0);
                                double velLen = offset.length();
                                if (velLen < 1e-5) {
                                    velLen = 1;
                                    offset = new Vector(1, 0, 0);
                                }
                                offset.multiply(12 / velLen);
                                bukkitEntity.setVelocity( MathHelper.getDirection(
                                        bukkitEntity.getLocation(), target.getEyeLocation().add(offset),
                                        SPEED, true));
                                // shoot projectile
                                if (indexAI % 8 == 0) {
                                    shootProjectile(2);
                                }
                                // switch phase
                                if (indexAI >= 80) {
                                    indexAI = -1;
                                    attackMethod = 1;
                                }
                                break;
                            }
                            case 3: {
                                // end of dash, only enter next dash if far away enough
                                if (indexAI >= 30) {
                                    if (bukkitEntity.getLocation().distanceSquared(target.getLocation()) > 1600 || indexAI > 60)
                                        indexAI = -1;
                                }
                                else {
                                    // dash
                                    if (indexAI == 0) {
                                        Location targetLoc = AimHelper.helperAimEntity(bukkitEntity, target, dashAimHelper);
                                        dashVelocity = MathHelper.getDirection(
                                                ((LivingEntity) bukkitEntity).getEyeLocation(), targetLoc, FINAL_DASH_SPEED);
                                    }
                                    // shoot hell blasts
                                    else if (indexAI % 5 == 0)
                                        shootProjectile(2);
                                    // reset dash velocity to cached
                                    bukkitEntity.setVelocity(dashVelocity);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            indexAI ++;
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public CalamitasClone(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return !WorldHelper.isDayTime(player.getWorld());
    }
    // a constructor for actual spawning
    public CalamitasClone(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 30;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        addScoreboardTag("isMechanic");
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 480d);
            attrMap.put("damageTakenMulti", 0.85d);
            attrMap.put("defence", 50d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
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
            setSize(8, false);
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
        // projectile info
        {
            psiFireBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(0, DART_SPEED, 0), attrMapBrimstoneDart,
                    DamageHelper.DamageType.MAGIC, "无际裂变");
            psiDart = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(0, DART_SPEED, 0), attrMapBrimstoneDart,
                    DamageHelper.DamageType.MAGIC, "硫火飞弹");
            psiFireball = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(0, DART_SPEED, 0), attrMapHellFireball,
                    DamageHelper.DamageType.MAGIC, "炼狱硫火球");
            psiHellBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(0, DART_SPEED, 0), attrMapHellFireball,
                    DamageHelper.DamageType.MAGIC, "深渊亡魂");
        }
        // health lock info
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, getMaxHealth() * 0.69);
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
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
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
