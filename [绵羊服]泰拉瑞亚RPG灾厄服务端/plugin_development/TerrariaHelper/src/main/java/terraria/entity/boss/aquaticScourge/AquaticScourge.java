package terraria.entity.boss.aquaticScourge;

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
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class AquaticScourge extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.AQUATIC_SCOURGE;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.SULPHUROUS_OCEAN;
    public static final double BASIC_HEALTH = 220800 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    AquaticScourge head;
    public static final int TOTAL_LENGTH = 51;
    public static final double
            HEAD_DMG = 528d, HEAD_DEF = 20d, HEAD_DR = 0.05,
            BODY_DMG = 360d, BODY_DEF = 30d, BODY_DR = 0.1,
            TAIL_DMG = 312d, TAIL_DEF = 50d, TAIL_DR = 0.15;
    public static final EntityHelper.WormSegmentMovementOptions FOLLOW_PROPERTY =
            new EntityHelper.WormSegmentMovementOptions()
                    .setFollowDistance(3)
                    .setFollowingMultiplier(1)
                    .setStraighteningMultiplier(0.1)
                    .setVelocityOrTeleport(false);
    static HashMap<String, Double> attrMapSandTooth, attrMapSandPoisonCloud, attrMapToxicCloud;
    static {
        attrMapSandTooth = new HashMap<>();
        attrMapSandTooth.put("damage", 384d);
        attrMapSandTooth.put("knockback", 2d);
        attrMapSandPoisonCloud = new HashMap<>();
        attrMapSandPoisonCloud.put("damage", 420d);
        attrMapSandPoisonCloud.put("knockback", 2d);
        attrMapToxicCloud = new HashMap<>();
        attrMapToxicCloud.put("damage", 468d);
        attrMapToxicCloud.put("knockback", 2d);
    }
    public EntityHelper.ProjectileShootInfo projectilePropertySandTooth, projectilePropertySandPoisonCloud, projectilePropertyToxicCloud;
    int segmentIndex;
    int indexAI = 0;
    Vector dVec = null, bufferVec = new Vector(0, 0, 0);
    boolean charging = true;
    private void shootProjectiles(int attackMethod) {
        EntityHelper.ProjectileShootInfo shootInfo;
        switch (attackMethod) {
            case 1: {
                shootInfo = projectilePropertySandTooth;
                break;
            }
            case 2: {
                shootInfo = projectilePropertySandPoisonCloud;
                break;
            }
            case 3:
            default: {
                shootInfo = projectilePropertyToxicCloud;
                break;
            }
        }
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        switch (attackMethod) {
            // tooth
            case 1: {
                shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, target.getEyeLocation(), 1);
                EntityHelper.spawnProjectile(shootInfo);
                break;
            }
            // sand poison cloud
            case 2: {
                for (int i = 0; i < 10; i ++) {
                    shootInfo.velocity = MathHelper.randomVector();
                    EntityHelper.spawnProjectile(shootInfo);
                }
                break;
            }
            // toxic cloud
            case 3:
            default: {
                for (Vector direction : MathHelper.getCircularProjectileDirections(
                        6, 2, 180, target, shootInfo.shootLoc, 1)) {
                    shootInfo.velocity = direction;
                    EntityHelper.spawnProjectile(shootInfo);
                }
                break;
            }
        }
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
                    // fire a spread of sand clouds
                    shootProjectiles(2);
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
                if (indexAI % 4 == 1 && indexAI > 1)
                    shootProjectiles(3);
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
        double speed = 2.8 - 0.6 * healthRatio;
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
            target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
            // disappear if no target is available
            if (target == null) {
                for (LivingEntity segment : bossParts) {
                    segment.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    segment.setHealth(0d);
                    segment.remove();
                }
                return;
            }
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
                this.pitch = (float) MathHelper.getVectorPitch( bukkitEntity.getVelocity() );
                // follow
                EntityHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, segmentIndex);
            }
            // body
            else if (segmentIndex < TOTAL_LENGTH - 1) {
                if (++indexAI % 200 == segmentIndex * 2) {
                    shootProjectiles(1);
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public AquaticScourge(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public AquaticScourge(Player summonedPlayer, ArrayList<LivingEntity> bossParts, int segmentIndex) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // copy variable
        this.bossParts = bossParts;
        this.segmentIndex = segmentIndex;
        // spawn location
        Location spawnLoc;
        if (segmentIndex == 0) {
            double angle = Math.random() * 720d, dist = 40;
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
            setCustomName(BOSS_TYPE.msgName + "头");
            this.head = this;
        }
        else {
            this.head = (AquaticScourge) ((CraftEntity) bossParts.get(0)).getHandle();
            if (segmentIndex + 1 < TOTAL_LENGTH)
                setCustomName(BOSS_TYPE.msgName + "体节");
            else
                setCustomName(BOSS_TYPE.msgName + "尾");
        }
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
                attrMap.put("damageTakenMulti", 1 - HEAD_DR);
            }
            // tail
            else if (segmentIndex + 1 == TOTAL_LENGTH) {
                attrMap.put("damage", TAIL_DMG);
                attrMap.put("defence", TAIL_DEF);
                attrMap.put("damageTakenMulti", 1 - TAIL_DR);
            }
            // body
            else {
                attrMap.put("damage", BODY_DMG);
                attrMap.put("defence", BODY_DEF);
                attrMap.put("damageTakenMulti", 1 - BODY_DR);
            }
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        if (segmentIndex == 0) {
            bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                    BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        } else {
            bossbar = (BossBattleServer) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_BAR).value();
        }
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
            setSize(6, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
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
            projectilePropertySandTooth = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapSandTooth,
                    EntityHelper.DamageType.ARROW, "渊海灾虫毒牙");
            projectilePropertySandPoisonCloud = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapSandPoisonCloud,
                    EntityHelper.DamageType.ARROW, "渊海灾虫沙爆");
            projectilePropertyToxicCloud = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapToxicCloud,
                    EntityHelper.DamageType.ARROW, "渊海灾虫毒云");
            // segment settings
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
            // next segment
            if (segmentIndex + 1 < TOTAL_LENGTH)
                new AquaticScourge(summonedPlayer, bossParts, segmentIndex + 1);
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
        if (segmentIndex % 10 == 0) {
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
