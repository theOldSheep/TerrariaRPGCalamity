package terraria.entity.boss.preHardMode.desertScourge;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class DesertNuisance extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.DESERT_SCOURGE;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.DESERT;
    public static final double BASIC_HEALTH = 1680 * 2, BASIC_HEALTH_BR = 792000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    DesertNuisance head;
    DesertScourge owner;
    public static final int TOTAL_LENGTH = 14;
    public static final double
            HEAD_DMG = 180d, HEAD_DEF = 4d,
            BODY_DMG = 90d, BODY_DEF = 8d,
            TAIL_DMG = 60d, TAIL_DEF = 14d;
    public static final EntityHelper.WormSegmentMovementOptions FOLLOW_PROPERTY =
            new EntityHelper.WormSegmentMovementOptions()
                    .setFollowDistance(2)
                    .setFollowingMultiplier(1)
                    .setStraighteningMultiplier(0.1)
                    .setVelocityOrTeleport(false);
    boolean isFirst;
    int segmentIndex;
    int indexAI = 0;
    Vector dVec = null, bufferVec = new Vector(0, 0, 0);
    boolean charging = true;
    private void headRushEnemy() {
        if (ticksLived % 2 == 0) return;
        double distSqr = bukkitEntity.getLocation().distanceSquared(target.getLocation());
        if (dVec == null)
            indexAI = 0;
        indexAI %= 50;
        if (indexAI == 0) {
            charging = true;
            if (isFirst)
                indexAI = (int) (Math.random() * 10);
        }
        // set velocity
        boolean shouldIncreaseIdx = true;
        if (indexAI < 40) {
            if (charging) {
                // stop charging after getting close enough
                if (distSqr < 400 && dVec != null) {
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
                    dVec.multiply(0.5 / dVecLen);
                    shouldIncreaseIdx = false;
                }
            }
            else {
                dVec.multiply(0.98);
                dVec.setY(dVec.getY() - 0.02);
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
        if (bufferVecLen > 1.2) {
            bufferVec.multiply(1.2 / bufferVecLen);
            bufferVecLen = 1.2;
        }
        Vector actualVelocity = bufferVec.clone();
        double actualLen = bufferVecLen;
        if (actualLen < 0.01) {
            actualVelocity = new Vector(1, 0, 0);
            actualLen = 1;
        }
        double speed = 2 - 0.5 * (getHealth() / getMaxHealth());
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
            if (owner.isAlive())
                target = owner.target;
            else
                target = null;
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
                // attack
                if (segmentIndex == 0) {
                    // attack
                    headRushEnemy();
                    // face the charging direction
                    this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
                    // follow
                    EntityHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, segmentIndex);
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public DesertNuisance(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public DesertNuisance(Player summonedPlayer, ArrayList<LivingEntity> bossParts, DesertScourge owner, int segmentIndex, boolean isFirst) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // copy variable
        this.bossParts = bossParts;
        this.segmentIndex = segmentIndex;
        this.isFirst = isFirst;
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
        this.owner = owner;
        if (segmentIndex == 0) {
            setCustomName("黄沙恶虫头");
            this.head = this;
        }
        else {
            this.head = (DesertNuisance) ((CraftEntity) bossParts.get(0)).getHandle();
            if (segmentIndex + 1 < TOTAL_LENGTH)
                setCustomName("黄沙恶虫体节");
            else
                setCustomName("黄沙恶虫尾");
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
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageRangedMulti", 0.75d);
            attrMap.put("damageTakenMulti", 0.9d);
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
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) EntityHelper.getMetadata(owner.getBukkitEntity(), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
            target = summonedPlayer;
        }
        // init health and slime size
        {
            setSize(4, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;

            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
            // next segment
            if (segmentIndex + 1 < TOTAL_LENGTH)
                new DesertNuisance(summonedPlayer, bossParts, owner, segmentIndex + 1, isFirst);
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