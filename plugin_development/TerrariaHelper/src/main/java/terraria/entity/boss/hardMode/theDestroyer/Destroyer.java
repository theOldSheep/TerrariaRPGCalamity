package terraria.entity.boss.hardMode.theDestroyer;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Destroyer extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_DESTROYER;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 230400 * 2, BASIC_HEALTH_BR = 573750 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    Destroyer head;
    public static final int TOTAL_LENGTH = 82;
    public static final double
            HEAD_DMG = 840d, HEAD_DEF = 0d,
            BODY_DMG = 408d, BODY_DEF = 80d,
            TAIL_DMG = 306d, TAIL_DEF = 70d;
    public static final EntityMovementHelper.WormSegmentMovementOptions FOLLOW_PROPERTY =
            new EntityMovementHelper.WormSegmentMovementOptions()
                    .setFollowDistance(4)
                    .setFollowingMultiplier(2)
                    .setStraighteningMultiplier(0.1)
                    .setVelocityOrTeleport(false);
    static double laserSpeed = 2.5;
    static HashMap<String, Double> attrMapDeathLaser, attrMapCursedLaser, attrMapElectricLaser;
    static AimHelper.AimHelperOptions laserAimHelper;
    static {
        attrMapDeathLaser = new HashMap<>();
        attrMapDeathLaser.put("damage", 384d);
        attrMapDeathLaser.put("knockback", 2d);
        attrMapCursedLaser = new HashMap<>();
        attrMapCursedLaser.put("damage", 420d);
        attrMapCursedLaser.put("knockback", 2d);
        attrMapElectricLaser = new HashMap<>();
        attrMapElectricLaser.put("damage", 468d);
        attrMapElectricLaser.put("knockback", 2d);

        laserAimHelper = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(laserSpeed);
    }
    public EntityHelper.ProjectileShootInfo projectilePropertyDeathLaser, projectilePropertyCursedLaser, projectilePropertyElectricLaser;
    int segmentIndex;
    int indexAI = 0;
    Vector dVec = null, bufferVec = new Vector(0, 0, 0);
    boolean charging = true;
    private void shootProjectiles(int attackMethod) {
        EntityHelper.ProjectileShootInfo shootInfo;
        switch (attackMethod) {
            case 1: {
                shootInfo = projectilePropertyDeathLaser;
                break;
            }
            case 2: {
                shootInfo = projectilePropertyCursedLaser;
                break;
            }
            case 3: {
                shootInfo = projectilePropertyElectricLaser;
                break;
            }
            default:
                return;
        }
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfo.velocity = MathHelper.getDirection(
                shootInfo.shootLoc,
                AimHelper.helperAimEntity(
                        bukkitEntity, target, laserAimHelper),
                laserSpeed);
        EntityHelper.spawnProjectile(shootInfo);
    }
    private void headRushEnemy() {
        if (ticksLived % 2 == 0) return;
        double healthRatio = getHealth() / getMaxHealth();
        double distSqr = bukkitEntity.getLocation().distanceSquared(target.getLocation());
        if (dVec == null)
            indexAI = 0;
        indexAI %= 60;
        if (indexAI == 0) {
            charging = true;
            indexAI = 1;
        }
        // set velocity
        boolean shouldIncreaseIdx = true;
        if (indexAI < 25) {
            if (charging) {
                // stop charging after getting close enough
                double chargeDist = 12;
                if (distSqr < chargeDist * chargeDist && dVec != null) {
                    charging = false;
                }
                else {
                    Location targetLoc = target.getEyeLocation();
                    dVec = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double dVecLen = dVec.length();
                    if (dVecLen < 0.01) {
                        dVec = new Vector(0, -1, 0);
                        dVecLen = 1;
                    }
                    dVec.multiply(1 / dVecLen);
                    shouldIncreaseIdx = false;
                }
            }
            else {
                dVec.multiply(0.975);
                dVec.setY(dVec.getY() - 0.05);
            }
        }
        else {
            if (locY > target.getLocation().getY())
                shouldIncreaseIdx = false;
            else {
                Location targetLoc = target.getLocation().subtract(0, 20, 0);
                dVec = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                double dVecLen = dVec.length();
                if (dVecLen < 0.01) {
                    dVec = new Vector(0, -1, 0);
                    dVecLen = 1;
                }
                dVec.multiply(0.35 / dVecLen);
            }
            dVec.multiply(0.95);
            dVec.setY(dVec.getY() - 0.05);
        }
        // tweak buffer vector, cloned and scaled to produce actual velocity
        bufferVec.add(dVec);
        double bufferVecLen = bufferVec.length();
        double maxBufferVecLen = 2.25;
        if (bufferVecLen > maxBufferVecLen) {
            bufferVec.multiply(maxBufferVecLen / bufferVecLen);
            bufferVecLen = maxBufferVecLen;
        }
        Vector actualVelocity = bufferVec.clone();
        double actualLen = bufferVecLen;
        if (actualLen < 0.01) {
            actualVelocity = new Vector(1, 0, 0);
            actualLen = 1;
        }
        double speed = 3 - healthRatio;
        actualVelocity.multiply( speed / actualLen);
        bukkitEntity.setVelocity(actualVelocity);
        // increase indexAI
        if (shouldIncreaseIdx)
            indexAI ++;
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            if (segmentIndex == 0)
                target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                        IGNORE_DISTANCE, terraria.entity.boss.BossHelper.TimeRequirement.NIGHT, BIOME_REQUIRED, targetMap.keySet());
            else {
                target = head.target;
                
            }
            // disappear if no target is available
            if (target == null) {
                for (LivingEntity segment : bossParts) {
                    segment.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    segment.setHealth(0d);
                    segment.remove();
                }
                return;
            }
            // if target is valid, attack
            else {
                // update facing direction
                {
                    MetadataValue valYaw = EntityHelper.getMetadata(bukkitEntity, "yaw");
                    if (valYaw != null) this.yaw = valYaw.asFloat();
                    MetadataValue valPitch = EntityHelper.getMetadata(bukkitEntity, "pitch");
                    if (valPitch != null) this.pitch = valPitch.asFloat();
                }
                // head
                if (segmentIndex == 0) {
                    // increase player aggro duration
                    targetMap.get(target.getUniqueId()).addAggressionTick();
                    // attack
                    headRushEnemy();
                    // face the charging direction
                    this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
                    // follow
                    EntityMovementHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, segmentIndex);
                }
                // body, shoot laser
                else if (segmentIndex < TOTAL_LENGTH - 1) {
                    if (++indexAI % 250 == segmentIndex * 2) {
                        double healthRatio = getHealth() / getMaxHealth();
                        if (getScoreboardTags().contains("hasProbe") || healthRatio < 0.5) {
                            if (healthRatio < 0.7)
                                shootProjectiles(3);
                            else if (healthRatio < 0.85)
                                shootProjectiles(2);
                            else
                                shootProjectiles(1);
                        }
                    }
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Destroyer(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return !WorldHelper.isDayTime(player.getWorld());
    }
    // a constructor for actual spawning
    public Destroyer(Player summonedPlayer, ArrayList<LivingEntity> bossParts, int segmentIndex) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // copy variable
        this.bossParts = bossParts;
        this.segmentIndex = segmentIndex;
        // spawn location
        Location spawnLoc;
        if (segmentIndex == 0) {
            double angle = Math.random() * 720d, dist = 48;
            spawnLoc = summonedPlayer.getLocation().add(
                    MathHelper.xsin_degree(angle) * dist, -40, MathHelper.xcos_degree(angle) * dist);
        } else {
            spawnLoc = bossParts.get(segmentIndex - 1).getLocation().add(0, -1, 0);
        }
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        if (segmentIndex == 0) {
            setCustomName(BOSS_TYPE.msgName + "§1");
            this.head = this;
        }
        else {
            this.head = (Destroyer) ((CraftEntity) bossParts.get(0)).getHandle();
            if (segmentIndex + 1 < TOTAL_LENGTH) {
                setCustomName(BOSS_TYPE.msgName + "§2");
                addScoreboardTag("hasProbe");
            }
            else
                setCustomName(BOSS_TYPE.msgName + "§3");
        }
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
            attrMap.put("damageMulti", 1d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            // head
            if (segmentIndex == 0) {
                attrMap.put("damage", HEAD_DMG);
                attrMap.put("defence", HEAD_DEF);
            }
            // tail
            else if (segmentIndex + 1 == TOTAL_LENGTH) {
                attrMap.put("damage", TAIL_DMG);
                attrMap.put("defence", TAIL_DEF);
            }
            // body
            else {
                attrMap.put("damage", BODY_DMG);
                attrMap.put("defence", BODY_DEF);
            }
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        if (segmentIndex == 0) {
            bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                    BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        } else {
            bossbar = (BossBattleServer) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_BAR).value();
        }
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            if (segmentIndex == 0) {
                targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                        getBukkitEntity(), BossHelper.BossType.WALL_OF_FLESH.msgName, summonedPlayer, true, bossbar);
            } else {
                targetMap = (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
            }
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
            target = summonedPlayer;
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
            bossParts.add((LivingEntity) bukkitEntity);
            if (segmentIndex == 0)
                BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
            // projectile info
            projectilePropertyDeathLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapDeathLaser,
                    DamageHelper.DamageType.MAGIC, "死亡激光");
            projectilePropertyCursedLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapCursedLaser,
                    DamageHelper.DamageType.MAGIC, "诅咒激光");
            projectilePropertyElectricLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapElectricLaser,
                    DamageHelper.DamageType.MAGIC, "电击激光");
            // segment settings
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
            // next segment
            if (segmentIndex + 1 < TOTAL_LENGTH)
                new Destroyer(summonedPlayer, bossParts, segmentIndex + 1);
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        if (segmentIndex > 0) return;
        // drop loot
        if (getMaxHealth() > 10) {
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);
        }
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
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
        if (segmentIndex == 0)
            terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, ticksLived, BOSS_TYPE);
        // update health
        setHealth(head.getHealth());
        // load nearby chunks
        if (segmentIndex % TerrariaHelper.Constants.WORM_BOSS_CHUNK_LOAD_SEGMENT_INTERVAL == 0) {
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
