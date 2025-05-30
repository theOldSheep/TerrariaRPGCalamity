package terraria.entity.boss.hardMode.theTwins;

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
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Retinazer extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_TWINS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 50490 * 2, BASIC_HEALTH_BR = 289265 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static String bossName = "激光眼";
    Spazmatism twin;
    Vector dashVelocity = new Vector();
    boolean dashingPhase = false;
    int phaseAI = 1, indexAI = -40;
    static HashMap<String, Double> attrMapEyeLaser, attrMapDeathLaser, attrMapRapidFire, attrMapHomingMissile;
    static AimHelper.AimHelperOptions laserAimHelper;
    static final double SPEED_LASER_1 = 1.5, SPEED_LASER_2 = 1.85, SPEED_LASER_3 = 2, SPEED_MISSILE = 1;
    EntityHelper.ProjectileShootInfo shootInfoGenericLaser, shootInfoMissile;
    static {
        attrMapEyeLaser = new HashMap<>();
        attrMapEyeLaser.put("damage", 276d);
        attrMapEyeLaser.put("knockback", 2d);
        attrMapDeathLaser = new HashMap<>();
        attrMapDeathLaser.put("damage", 336d);
        attrMapDeathLaser.put("knockback", 2d);
        attrMapRapidFire = new HashMap<>();
        attrMapRapidFire.put("damage", 252d);
        attrMapRapidFire.put("knockback", 2d);
        attrMapHomingMissile = new HashMap<>();
        attrMapHomingMissile.put("damage", 420d);
        attrMapHomingMissile.put("knockback", 4d);

        laserAimHelper = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(SPEED_LASER_3);
    }
    private void changePhase() {
        phaseAI ++;
        indexAI = -40;
        bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
        if (phaseAI == 2) {
            // damage: 306 -> 458
            AttributeHelper.tweakAttribute(attrMap, "damage", "152", true);
            // defence: 20 -> 40
            AttributeHelper.tweakAttribute(attrMap, "defence", "20", true);
            addScoreboardTag("isMechanic");
            setCustomName(bossName + "§1");
        }
    }
    private void shootMissile() {
        shootInfoMissile.setLockedTarget(target);
        shootInfoMissile.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoMissile.velocity = MathHelper.getDirection(
                shootInfoMissile.shootLoc, target.getEyeLocation(), SPEED_MISSILE);
        EntityHelper.spawnProjectile(shootInfoMissile);
    }
    private void shootLaser(int state) {
        double laserSpeed = 0.75;
        Location targetLoc = target.getEyeLocation();
        switch (state) {
            case 1:
                shootInfoGenericLaser.attrMap = attrMapEyeLaser;
                laserSpeed = SPEED_LASER_1;
                break;
            case 2:
                shootInfoGenericLaser.attrMap = attrMapDeathLaser;
                laserSpeed = SPEED_LASER_2;
                break;
            case 3:
                shootInfoGenericLaser.attrMap = attrMapRapidFire;
                laserSpeed = SPEED_LASER_3;
                targetLoc = AimHelper.helperAimEntity(bukkitEntity, target, laserAimHelper);
                break;
        }
        shootInfoGenericLaser.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoGenericLaser.velocity = MathHelper.getDirection(
                shootInfoGenericLaser.shootLoc, targetLoc, laserSpeed);
        EntityHelper.spawnProjectile(shootInfoGenericLaser);
    }
    private void attackAI() {
        // if the twin is in an earlier phase, prevent damage
        if (ticksLived % 5 == 0) {
            if ( twin.phaseAI < phaseAI && twin.isAlive() ) {
                addScoreboardTag("noDamage");
            }
            else {
                removeScoreboardTag("noDamage");
            }
        }
        // phase change
        double healthRatio = getHealth() / getMaxHealth();
        switch (phaseAI) {
            case 1:
                if (healthRatio < 0.7) changePhase();
                break;
            case 2:
                if (healthRatio < 0.25 || !twin.isAlive()) changePhase();
                break;
        }
        // attack
        switch (phaseAI) {
            // phase 1: alternate between firing lasers and dashing
            case 1: {
                // hover and shoot laser
                if (indexAI < 60) {
                    dashingPhase = false;
                    // hover
                    Vector offset = bukkitEntity.getLocation().subtract(target.getLocation()).toVector();
                    offset.setY(0);
                    if (offset.lengthSquared() < 1e-5) {
                        offset = new Vector(1, 0, 0);
                    }
                    offset.normalize().multiply(16);
                    offset.setY(16);
                    Location targetLoc = target.getLocation().add(offset);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double velLen = velocity.length();
                    double maxSpeed = 2;
                    if (velLen > maxSpeed) {
                        velocity.multiply(maxSpeed / velLen);
                    }
                    bukkitEntity.setVelocity(velocity);
                    // shoot laser
                    if (indexAI >= 0 && indexAI % 5 == 0) {
                        shootLaser(1);
                    }
                }
                // quickly dash three times
                else {
                    dashingPhase = true;
                    if (indexAI >= 180)
                        indexAI = -1;
                    else {
                        if ((indexAI - 60) % 40 == 0) {
                            dashVelocity = MathHelper.getDirection(
                                    ((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(), 1.75);
                        }
                        // maintain dash velocity
                        bukkitEntity.setVelocity(dashVelocity);
                    }
                }
                break;
            }
            // phase 2: alternate between firing death lasers and rapid lasers
            case 2: {
                dashingPhase = false;
                // hover above and shoot death laser
                if (indexAI < 40) {
                    // hover above the player
                    Location targetLoc = target.getLocation().add(0, 12, 0);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double velLen = velocity.length();
                    double maxSpeed = 2;
                    if (velLen > maxSpeed) {
                        velocity.multiply(maxSpeed / velLen);
                    }
                    bukkitEntity.setVelocity(velocity);
                    // shoot laser
                    if (indexAI % 10 == 0) {
                        shootLaser(2);
                    }
                }
                // horizontally hover and rapid fire
                else {
                    // hover horizontally
                    Vector offset = bukkitEntity.getLocation().subtract(target.getLocation()).toVector();
                    offset.setY(0);
                    if (offset.lengthSquared() < 1e-5) {
                        offset = new Vector(1, 0, 0);
                    }
                    offset.normalize().multiply(16);
                    Location targetLoc = target.getLocation().add(offset);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double velLen = velocity.length();
                    double maxSpeed = 2;
                    if (velLen > maxSpeed) {
                        velocity.multiply(maxSpeed / velLen);
                    }
                    bukkitEntity.setVelocity(velocity);
                    // fire laser
                    if (indexAI >= 90)
                        indexAI = -1;
                    else if ((indexAI - 40) % 5 == 0) {
                        shootLaser(3);
                    }
                }
                break;
            }
            // phase 3: alternate between firing death / rapid lasers and quick dashes
            case 3: {
                // hover above and shoot death laser
                if (indexAI < 40) {
                    dashingPhase = false;
                    // hover above the player
                    Location targetLoc = target.getLocation().add(0, 12, 0);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double velLen = velocity.length();
                    double maxSpeed = 2;
                    if (velLen > maxSpeed) {
                        velocity.multiply(maxSpeed / velLen);
                    }
                    bukkitEntity.setVelocity(velocity);
                    // shoot laser
                    if (indexAI % 10 == 0) {
                        shootLaser(2);
                    }
                }
                // horizontally hover and rapid fire
                else if (indexAI < 90) {
                    dashingPhase = false;
                    // hover horizontally
                    Vector offset = bukkitEntity.getLocation().subtract(target.getLocation()).toVector();
                    offset.setY(0);
                    if (offset.lengthSquared() < 1e-5) {
                        offset = new Vector(1, 0, 0);
                    }
                    offset.normalize().multiply(16);
                    Location targetLoc = target.getLocation().add(offset);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double velLen = velocity.length();
                    double maxSpeed = 2;
                    if (velLen > maxSpeed) {
                        velocity.multiply(maxSpeed / velLen);
                    }
                    bukkitEntity.setVelocity(velocity);
                    // fire laser
                    if ((indexAI - 40) % 5 == 0) {
                        shootLaser(3);
                    }
                }
                // charges
                else {
                    dashingPhase = true;
                    if (indexAI >= 210)
                        indexAI = -1;
                    else {
                        if ((indexAI - 90) % 40 == 0) {
                            dashVelocity = MathHelper.getDirection(
                                    ((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(), 2);
                        }
                        // maintain dash velocity
                        bukkitEntity.setVelocity(dashVelocity);
                    }
                    // shoot homing projectiles
                    switch (indexAI) {
                        case 100:
                        case 105:
                        case 110:
                        case 115:
                        case 120:
                            shootMissile();
                    }
                }
                break;
            }
        }
        indexAI ++;
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
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

                attackAI();
            }
        }
        // face the player
        if (dashingPhase)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Retinazer(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.isDayTime(player.getWorld()) ) return false;
        return true;
    }
    // a constructor for actual spawning
    public Retinazer(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, -6, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(bossName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 306d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 20d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
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
        // spawn the twin
        this.twin = new Spazmatism(summonedPlayer, this);
        // shoot info's
        {
            shootInfoGenericLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapEyeLaser, "死亡激光");
            shootInfoMissile = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapHomingMissile, "追踪导弹");
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();

        if (!twin.isAlive() && bossbar.visible) {
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
