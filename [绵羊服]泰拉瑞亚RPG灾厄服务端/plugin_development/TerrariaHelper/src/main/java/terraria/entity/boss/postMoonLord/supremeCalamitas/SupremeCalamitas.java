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
import org.bukkit.util.Consumer;
import org.bukkit.util.Vector;
import terraria.entity.boss.BossProjectilesManager;
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
        boolean lockCamera;
        int duration;
        HashSet<BulletHellProjectileOption> projectileCandidates;
        Consumer<SupremeCalamitas> beginFunc, endFunc;
        private BulletHellPattern(double healthRatio, int duration, boolean lockCamera) {
            this.healthRatio = healthRatio;
            this.duration = duration;
            this.lockCamera = lockCamera;
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
    public static final double BASIC_HEALTH = 2760000 * 2, BASIC_HEALTH_BR = 1656000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<String, EntityHelper.ProjectileShootInfo> bulletHellShootInfoMap = new HashMap<>();
    BulletHellProjectile.BulletHellDirectionInfo bulletHellDir = null;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static double DASH_SPEED = 3.5, HOVER_SPEED = 2.75, DART_SPEED = 2.75, HELL_BLAST_SPEED = 3.25, GIGA_BLAST_SPEED = 1.25,
            HOVER_DISTANCE = 48, HOVER_DISTANCE_BROTHERS = 40, HOVER_DISTANCE_BROTHERS_OFFSET = 12,
            DART_SPREAD_SINGLE = 8.5, DART_SPREAD_TOTAL = 35;
    static HashMap<String, Double> attrMapPrjLow, attrMapPrjMid, attrMapPrjHigh, attrMapPrjExtreme;
    static BulletHellPattern[] bulletHellPatterns;
    static EntityHelper.AimHelperOptions dashAimHelper, blastAimHelper;
    static {
        attrMapPrjLow = new HashMap<>();
        attrMapPrjLow.put("damage", 1350d);
        attrMapPrjLow.put("knockback", 1.5d);
        attrMapPrjMid = new HashMap<>();
        attrMapPrjMid.put("damage", 1450d);
        attrMapPrjMid.put("knockback", 2.0d);
        attrMapPrjHigh = new HashMap<>();
        attrMapPrjHigh.put("damage", 1560d);
        attrMapPrjHigh.put("knockback", 2.5d);
        attrMapPrjExtreme = new HashMap<>();
        attrMapPrjExtreme.put("damage", 1850d);
        attrMapPrjExtreme.put("knockback", 3.75d);


        BulletHellProjectileOption optionBlastSurrounding = new BulletHellProjectileOption(
                "深渊亡魂", BulletHellProjectile.ProjectileType.SQUARE_BORDER,
                attrMapPrjMid, 0.4, 0.2, 2, 250);
        BulletHellProjectileOption optionBlastSurroundingEasy = new BulletHellProjectileOption(
                "深渊亡魂", BulletHellProjectile.ProjectileType.SQUARE_BORDER,
                attrMapPrjMid, 0.4, 0.2, 3, 250);
        BulletHellProjectileOption optionFlameSkull = new BulletHellProjectileOption(
                "深渊炙颅", BulletHellProjectile.ProjectileType.SQUARE_BORDER_SIDES,
                attrMapPrjHigh, 0.4, 0.2, 3, 250);
        BulletHellProjectileOption optionHellBlast = new BulletHellProjectileOption(
                "无际裂变", BulletHellProjectile.ProjectileType.CIRCUMFERENCE,
                attrMapPrjHigh, 0.3, 0.1, 20, 60);
        BulletHellProjectileOption optionHellBlastEasy = new BulletHellProjectileOption(
                "无际裂变", BulletHellProjectile.ProjectileType.CIRCUMFERENCE,
                attrMapPrjHigh, 0.3, 0.1, 30, 60);
        BulletHellProjectileOption optionGigaBlast = new BulletHellProjectileOption(
                "深渊炙炎", BulletHellProjectile.ProjectileType.CIRCUMFERENCE,
                attrMapPrjHigh, 0.35, 0.15, 35, 75);

        final String bossProgress = BOSS_TYPE.msgName;
        final String MSG_PREFIX = "§#FFA500";
        bulletHellPatterns = new BulletHellPattern[]{
                // first bullet hell when summoned
                new BulletHellPattern(1.1, 400, true)
                        .addCandidate(optionBlastSurrounding)
                        .addCandidate(optionBlastSurrounding)
                        .setBeginFunc(
                                (boss) -> {
                                    String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                            new String[]{("如果你想感受一下四级烫伤的话，你可算是找对人了。")} :
                                            new String[]{("你享受地狱之旅么？")};
                                    terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                                })
                        .setEndFunc(
                        (boss) -> {
                            String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                    new String[]{("他日若你魂销魄散，你会介意我将你的骨头和血肉融入我的造物中吗？")} :
                                    new String[]{("真奇怪，你应该已经死了才对......")};
                            terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                            // spawn first sepulcher
                            new Sepulcher(boss);
                        }),
                // second bullet hell 75% health
                new BulletHellPattern(0.75, 400, true)
                        .addCandidate(optionBlastSurrounding)
                        .addCandidate(optionHellBlast)
                        .setBeginFunc(
                        (boss) -> {
                            String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                    new String[]{("你离胜利还差得远着呢。")} :
                                    new String[]{("距离你上次勉强才击败我的克隆体也没过多久。那玩意就是个失败品，不是么？")};
                            terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                        }),
                // third bullet hell 50% health
                new BulletHellPattern(0.5, 400, true)
                        .addCandidate(optionBlastSurroundingEasy)
                        .addCandidate(optionHellBlastEasy)
                        .addCandidate(optionGigaBlast)
                        .setBeginFunc(
                        (boss) -> {
                            String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                    new String[]{("自我上一次能在如此有趣的靶子假人身上测试我的魔法，已经过了很久了。")} :
                                    new String[]{("你驾驭着强大的力量，但你使用这股力量只为了自己的私欲。")};
                            terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                        }),
                // spawn brothers 45% health
                new BulletHellPattern(0.45, 50, false)
                        .setBeginFunc(
                        (boss) -> {
                            String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                    new String[]{("只是单有过去形态的空壳罢了，或许在其中依然残存他们的些许灵魂也说不定。")} :
                                    new String[]{("你想见见我的家人吗？听上去挺可怕，不是么？")};
                            terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                            boss.brothers.add( new SupremeCalamitasBrother(boss, 0) );
                            boss.brothers.add( new SupremeCalamitasBrother(boss, 1) );
                            boss.addScoreboardTag("noDamage");
                        }),
                // fourth bullet hell 30% health
                new BulletHellPattern(0.3, 400, true)
                        .addCandidate(optionBlastSurrounding)
                        .addCandidate(optionHellBlastEasy)
                        .addCandidate(optionGigaBlast)
                        .setBeginFunc(
                        (boss) -> {
                            String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                    new String[]{("我挺好奇，自我们第一次交手后，你是否有在梦魇中见到过这些？")} :
                                    new String[]{("别想着逃跑。只要你还活着，痛苦就不会离你而去。")};
                            terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                        }),
                // another sepulcher at 20% health
                new BulletHellPattern(0.2, 50, false)
                        .setBeginFunc(
                        (boss) -> {
                            String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                    new String[]{("注意一下，那个会自己爬的坟墓来了，这是最后一次。")} :
                                    new String[]{("一个后起之人，只识杀戮与偷窃，但却以此得到力量。我想想，这让我想起了谁...？")};
                            terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                            // spawn second sepulcher
                            new Sepulcher(boss);
                        }),
                // final bullet hell 10% health
                new BulletHellPattern(0.1, 400, true)
                        .addCandidate(optionBlastSurroundingEasy)
                        .addCandidate(optionFlameSkull)
                        .addCandidate(optionHellBlastEasy)
                        .addCandidate(optionGigaBlast)
                        .setBeginFunc(
                        (boss) -> {
                            String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                    new String[]{("这难道不令人激动么？")} :
                                    new String[]{
                                            ("给我停下！"),
                                            ("如果我在这里失败，我就再无未来可言。"),
                                            ("一旦你战胜了我，你就只剩下一条道路。"),
                                            ("而那条道路......同样也无未来可言。"),
                                            ("这场战斗的输赢对你而言毫无意义！那你又有什么理由干涉这一切！"),};
                            terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                        }),
                // final conversation
                new BulletHellPattern(0.005, 300, false)
                        .setBeginFunc(
                                (boss) -> {
                                    String[] messages = PlayerHelper.hasDefeated( boss.target, bossProgress ) ?
                                            new String[]{
                                                    ("了不起的表现，我认可你的胜利。"),
                                                    ("毫无疑问，你会遇见比我更加强大的敌人。"),
                                                    ("我相信你不会犯下和他一样的错误。"),
                                                    ("至于你的未来会变成什么样子，我很期待。"),} :
                                            new String[]{
                                                    ("哪怕他抛弃了一切，他的力量也不会消失。"),
                                                    ("我已没有余力去怨恨他了，对你也是如此......"),
                                                    ("现在，一切都取决于你。"),};
                                    terraria.entity.boss.BossHelper.sendBossMessages(20, 0, boss.bukkitEntity, MSG_PREFIX, messages);
                                })
                        .setEndFunc(SupremeCalamitas::die),
        };

        dashAimHelper = new EntityHelper.AimHelperOptions().setProjectileSpeed(DASH_SPEED);
        blastAimHelper = new EntityHelper.AimHelperOptions().setProjectileSpeed(HELL_BLAST_SPEED);
    }

    EntityHelper.ProjectileShootInfo shootInfoDart, shootInfoHellBlast, shootInfoGigaBlast;
    int bulletHellPatternIdx = 0;
    boolean bulletHellPatternActive = false;

    // do not init indexAI as 0 to prevent a projectile fired before the first bullet hell
    int indexAI = -50, attackType = 0;
    Vector velocity = new Vector();
    ArrayList<SupremeCalamitasBrother> brothers = new ArrayList<>();
    BossProjectilesManager projectilesManager = new BossProjectilesManager();


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
    private void handleBulletHell() {
        // init bullet hell dir & kill other projectiles
        if (indexAI == 0) {
            bulletHellDir = new BulletHellProjectile.BulletHellDirectionInfo(target);
            projectilesManager.killAll();
        }
        // velocity
        Location hoverLoc = target.getLocation();
        hoverLoc.add(hoverLoc.getDirection().multiply(HOVER_DISTANCE));
        velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverLoc, HOVER_SPEED, true);

        BulletHellPattern pattern = bulletHellPatterns[bulletHellPatternIdx];

        bulletHellDir.target = target;
        // target rotation
        if (pattern.lockCamera) {
            tickBulletHellRotation();
        }
        // spawn projectiles
        for (BulletHellProjectileOption projOption : pattern.projectileCandidates) {
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
                projectilesManager.handleProjectile(projectile.bukkitEntity);
            }
        }
    }
    private void generalMovement() {
        Vector offsetDir = MathHelper.vectorFromYawPitch_approx(
                MathHelper.getVectorYaw(bukkitEntity.getLocation().subtract(target.getLocation()).toVector()), 0);
        offsetDir.multiply(HOVER_DISTANCE);
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
                attackInterval = 25;
                attackTimes = isPhase2 ? 2 : 3;
                break;
            case 1:
            case 5:
            case 9:
            case 11:
                trueAttackPattern = 2;
                attackInterval = 1;
                attackTimes = 40;
                break;
            case 2:
            case 4:
                trueAttackPattern = 3;
                attackInterval = 25;
                attackTimes = 2;
                break;
            case 10:
            case 14:
                trueAttackPattern = 3;
                attackInterval = 30;
                attackTimes = 4;
                break;
            case 3:
            case 15:
                trueAttackPattern = 4;
                attackInterval = 30;
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
            generalMovement();
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
                    Location aimedLoc = EntityHelper.helperAimEntity(shootInfoHellBlast.shootLoc, target, blastAimHelper);
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
                    Location targetLoc = EntityHelper.helperAimEntity(bukkitEntity, target, dashAimHelper);
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
    private void tickBrothers() {
        // remove dead brother
        for (int i = 0; i < brothers.size(); i ++) {
            if (brothers.get(i).isAlive())
                continue;
            brothers.remove(i);
            i --;
            if (brothers.isEmpty()) {
                removeScoreboardTag("noDamage");
                return;
            }
        }
        double yaw = MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        Vector directDir = MathHelper.vectorFromYawPitch_approx(yaw, 0)
                .multiply(HOVER_DISTANCE - HOVER_DISTANCE_BROTHERS);
        Location hoverLoc = bukkitEntity.getLocation().add(directDir);
        if (brothers.size() == 1) {
            brothers.get(0).desiredLoc = hoverLoc;
        }
        else {
            Vector offsetDir = MathHelper.vectorFromYawPitch_approx(yaw + 90, 0)
                    .multiply(HOVER_DISTANCE_BROTHERS_OFFSET);
            brothers.get(0).desiredLoc = hoverLoc.clone().add(offsetDir);
            brothers.get(1).desiredLoc = hoverLoc.clone().subtract(offsetDir);
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
                return;
            }
            // if target is valid, attack
            else {
                // increase player aggro duration
                targetMap.get(target.getUniqueId()).addAggressionTick();

                // manage projectiles
                if (indexAI % 20 == 0)
                    projectilesManager.dropOutdated();

                BulletHellPattern pattern;
                if (bulletHellPatternIdx < bulletHellPatterns.length)
                    pattern = bulletHellPatterns[bulletHellPatternIdx];
                else
                    pattern = null;
                // brothers
                if (brothers.size() > 0) {
                    generalMovement();
                    tickBrothers();
                }
                // bullet hell
                else if (bulletHellPatternActive) {
                    // retarget: reset bullet hell duration
                    if (target != lastTarget)
                        indexAI = 0;

                    handleBulletHell();
                    // termination. "pattern" would not be null here, don't worry.
                    if (indexAI > pattern.duration) {
                        projectilesManager.killAll();

                        bulletHellPatternIdx++;
                        indexAI = -51;
                        attackType = 0;
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
                    EntityHelper.DamageType.MAGIC, "硫火飞弹");
            shootInfoHellBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrjMid,
                    EntityHelper.DamageType.MAGIC, "深渊亡魂");
            shootInfoGigaBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrjHigh,
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