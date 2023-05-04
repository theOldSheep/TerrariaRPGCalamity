package terraria.entity.boss.calamitasClone;

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
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class CalamitasClone extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CALAMITAS_CLONE;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    // TODO
//    public static final double BASIC_HEALTH = 108000 * 2;
    public static final double BASIC_HEALTH = 10800 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final double FINAL_DASH_SPEED = 3, SPEED = 2, BULLET_HELL_RADIUS = 32, DART_SPEED = 1;
    static final HashMap<String, Double> attrMapBrimstoneDart, attrMapHellFireball;
    static final EntityHelper.AimHelperOptions dashAimHelper;
    static {
        dashAimHelper = new EntityHelper.AimHelperOptions()
                .setProjectileSpeed(FINAL_DASH_SPEED);

        attrMapBrimstoneDart = new HashMap<>();
        attrMapBrimstoneDart.put("damage", 352d);
        attrMapBrimstoneDart.put("knockback", 2d);
        attrMapHellFireball = new HashMap<>();
        attrMapHellFireball.put("damage", 468d);
        attrMapHellFireball.put("knockback", 2d);
    }
    public EntityHelper.ProjectileShootInfo psiFireBlast, psiDart, psiFireball, psiHellBlast;
    Vector bullet_hell_dir1 = MathHelper.vectorFromYawPitch_quick(Math.random() * 360, 0),
            bullet_hell_dir2 = new Vector(0, 1, 0),
            bullet_hell_orth_dir = bullet_hell_dir1.getCrossProduct(bullet_hell_dir2);
    // TODO
    int indexAI = -40, attackMethod = 1, bulletHellTicksLeft = 1145140, healthLockProgress = 1;
    HashSet<Entity> bulletHellProjectiles = new HashSet<>();
    // 1: fireball   2: hell blast
    private void shootProjectile(int type) {
        switch (type) {
            case 1: {
                psiFireball.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                psiFireball.velocity = MathHelper.getDirection(psiFireball.shootLoc, target.getEyeLocation(), 2.25);
                EntityHelper.spawnProjectile(psiFireball);
                break;
            }
            case 2: {
                psiHellBlast.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                psiHellBlast.velocity = MathHelper.getDirection(psiHellBlast.shootLoc, target.getEyeLocation(), 2);
                EntityHelper.spawnProjectile(psiHellBlast);
                break;
            }
        }
    }
    private void handleBulletHell() {
        // spawn projectiles
        if (--bulletHellTicksLeft > 0) {
            // hell blast
            {
                boolean direction = Math.random() < 0.5;
                Vector velocity = direction ? bullet_hell_dir1.clone() : bullet_hell_dir2.clone();
                Vector spawnLocOffset = !direction ? bullet_hell_dir1.clone() : bullet_hell_dir2.clone();
                // projectiles may come from either direction, up/down/left/right
                if (Math.random() < 0.5)
                    velocity.multiply(-1);
                spawnLocOffset.multiply((Math.random() * 2 - 1) * BULLET_HELL_RADIUS)
                        .add(velocity.clone().multiply(-BULLET_HELL_RADIUS));
                Location spawnLoc = target.getEyeLocation().add(spawnLocOffset);
                double projectileSpeed = 0.75 + Math.random() * 0.25;
                velocity.multiply(projectileSpeed);
                int ticksLive = (int) (BULLET_HELL_RADIUS * 2 / projectileSpeed);
                // spawn projectile
                psiHellBlast.velocity = velocity;
                psiHellBlast.shootLoc = spawnLoc;
                Entity projectileSpawned = EntityHelper.spawnProjectile(psiHellBlast);
                EntityHelper.setMetadata(
                        projectileSpawned, "ticksLive", ticksLive);
                bulletHellProjectiles.add(projectileSpawned);
            }
            // fire blasts
            if (bulletHellTicksLeft % 50 == 0) {
                double angle = Math.random() * 360;
                double sinVal = MathHelper.xsin_degree(angle) * BULLET_HELL_RADIUS * 1.5;
                double cosVal = MathHelper.xcos_degree(angle) * BULLET_HELL_RADIUS * 1.5;
                Vector shootLocOffset = bullet_hell_dir1.clone().multiply(sinVal)
                        .add(bullet_hell_dir2.clone().multiply(cosVal));
                psiFireBlast.shootLoc = target.getEyeLocation().add(shootLocOffset);
                psiFireBlast.velocity = MathHelper.getDirection(psiFireBlast.shootLoc, target.getEyeLocation(), 1);
                Entity projectileSpawned = EntityHelper.spawnProjectile(psiFireBlast);
                EntityHelper.setMetadata(
                        projectileSpawned, "ticksLive", 50);
                bulletHellProjectiles.add(projectileSpawned);
            }
        }
        // remove outdated projectiles
        ArrayList<Entity> projectilesToRemove = new ArrayList<>();
        // record outdated to prevent co-modification error
        for (Entity projectile : bulletHellProjectiles) {
            int ticksLive = EntityHelper.getMetadata(projectile, "ticksLive").asInt();
            if (projectile.isDead() || projectile.getTicksLived() >= ticksLive) {
                projectilesToRemove.add(projectile);
            }
            // make sure the projectiles stay within the same plane as the player
            //
            else if (projectile.getTicksLived() > 1) {
                Vector correctionDir = MathHelper.vectorProjection(
                        bullet_hell_orth_dir, target.getLocation().subtract(projectile.getLocation()).toVector());
//                Bukkit.broadcastMessage(correctionDir + "");
//                Bukkit.broadcastMessage(projectile.getLocation() + "");
//                Bukkit.broadcastMessage(projectile.getLocation().add(correctionDir) + "");
//                Bukkit.broadcastMessage("");
                projectile.teleport(projectile.getLocation().add(correctionDir));
            }
        }
        // remove projectiles
        for (Entity projectile : projectilesToRemove) {
            // shoot a spread of brimstone darts
            if (projectile.getScoreboardTags().contains("isFireBlast")) {
                psiDart.shootLoc = projectile.getLocation();
                int shootAmount = 8;
                for (int i = 0; i < 8; i ++) {
                    double angle = (double)i / shootAmount * 360;
                    double sinVal = MathHelper.xsin_degree(angle) * DART_SPEED;
                    double cosVal = MathHelper.xcos_degree(angle) * DART_SPEED;
                    psiDart.velocity = bullet_hell_dir1.clone().multiply(sinVal)
                            .add(bullet_hell_dir2.clone().multiply(cosVal));
                    Entity projectileSpawned = EntityHelper.spawnProjectile(psiDart);
                    EntityHelper.setMetadata(
                            projectileSpawned, "ticksLive", BULLET_HELL_RADIUS * 3 / DART_SPEED);
                    bulletHellProjectiles.add(projectileSpawned);
                }
            }
            // delete the projectile
            bulletHellProjectiles.remove(projectile);
            projectile.remove();
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
            if (WorldHelper.isDayTime(bukkitEntity.getWorld()))
                target = null;
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
                boolean brothersAlive = bossParts.size() > 1;
                if (brothersAlive) {
                    if (bossParts.get(1).isDead() && bossParts.get(2).isDead())
                        brothersAlive = false;
                }
                if (bulletHellTicksLeft > 0 || brothersAlive)
                    addScoreboardTag("noDamage");
                else
                    removeScoreboardTag("noDamage");
                // bullet hell
                if (bulletHellTicksLeft > 0 || bulletHellProjectiles.size() > 0) {
                    // spawn and handle bullet hell projectiles
                    handleBulletHell();
                    // on player kill, restore 15 seconds of bullet hell duration
                    if (lastTarget != target)
                        bulletHellTicksLeft += 300;
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
                                bulletHellTicksLeft = 400;
                                EntityHelper.setMetadata(bukkitEntity, "healthLock", getMaxHealth() * 0.39);
                                healthLockProgress = 2;
                            }
                            break;
                        // 40%
                        case 2:
                            if (healthRatio < 0.4) {
                                new Catastrophe(target, this);
                                new Cataclysm(target, this);
                                EntityHelper.setMetadata(bukkitEntity, "healthLock", getMaxHealth() * 0.09);
                                healthLockProgress = 3;
                            }
                            break;
                        // 10%
                        case 3:
                            if (healthRatio < 0.1) {
                                bulletHellTicksLeft = 500;
                                EntityHelper.setMetadata(bukkitEntity, "healthLock", null);
                                healthLockProgress = 4;
                            }
                            break;
                    }
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
                            indexAI %= 30;
                            // dash
                            if (indexAI == 0) {
                                Location targetLoc = EntityHelper.helperAimEntity(bukkitEntity, target, dashAimHelper);
                                Vector direction = MathHelper.getDirection(
                                        ((LivingEntity) bukkitEntity).getEyeLocation(), targetLoc, FINAL_DASH_SPEED );
                                bukkitEntity.setVelocity(direction);
                            }
                            // shoot hell blasts
                            else if (indexAI % 5 == 0)
                                shootProjectile(2);
                            break;
                        }
                    }
                    indexAI ++;
                }
            }
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
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
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
            EntityHelper.setDamageType(bukkitEntity, "Melee");
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, "bossbar", targetMap);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), "血肉之墙", summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
        }
        // init health and slime size
        {
            setSize(8, false);
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
        // projectile info
        {
            psiFireBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapBrimstoneDart, "Magic", "无际裂变");
            psiDart = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapBrimstoneDart, "Magic", "硫火飞弹");
            psiFireball = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapHellFireball, "Magic", "炼狱硫火球");
            psiHellBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapHellFireball, "Magic", "深渊亡魂");
        }
        // health lock info
        EntityHelper.setMetadata(bukkitEntity, "healthLock", getMaxHealth() * 0.69);
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
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // update boss bar and dynamic DR
        terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, BOSS_TYPE);
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
