package terraria.entity.boss.postMoonLord.supremeCalamitas;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
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
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Sepulcher extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SUPREME_WITCH_CALAMITAS;
    // it can follow the player out of the space
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 1000000 * 2, BASIC_HEALTH_BR = 875000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    public static final int TOTAL_LENGTH = 51;
    static final String[] NAME_SUFFIXES = {"头", "体节", "尾"};
    HashMap<String, Double> attrMap;
    public HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    public Player target = null;
    // other variables and AI
    Sepulcher head;
    static final double[][]
            SEGMENT_DAMAGE = {{1800d}, {1400d}, {1000d}};
    static final double DART_SPEED = 2.5;


    static final int SLIME_SIZE = 8;
    static HashMap<String, Double> attrMapDart;
    static {
        attrMapDart = new HashMap<>();
        attrMapDart.put("damage", 1400d);
        attrMapDart.put("knockback", 1.5d);
    }
    static final EntityHelper.WormSegmentMovementOptions
            FOLLOW_PROPERTY =
                    new EntityHelper.WormSegmentMovementOptions()
                            .setFollowDistance(SLIME_SIZE * 0.5)
                            .setFollowingMultiplier(1)
                            .setStraighteningMultiplier(-0.1)
                            .setVelocityOrTeleport(false);
    EntityHelper.ProjectileShootInfo shootInfoDart;
    int segmentIndex, segmentTypeIndex;

    static final int DASH_INTERVAL = 30;
    static final double DASH_ACCELERATION = 0.25, DASH_SPEED = 3.25, DASH_MIN_DISTANCE = 22.5;
    Vector lastVelocity = new Vector();
    private int dashTimer = 0;

    SupremeCalamitas owner;


    private void scheduleDartForSegment(int index) {
        if (index < 0 || index >= bossParts.size())
            return;
        Entity nextFireSegmentNMS = ((CraftEntity) bossParts.get(index)).getHandle();
        Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), ((Sepulcher) nextFireSegmentNMS)::fireDart, 1);
    }
    private void fireDart() {
        shootInfoDart.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoDart.velocity = MathHelper.getDirection(shootInfoDart.shootLoc, target.getEyeLocation(), DART_SPEED);
        EntityHelper.spawnProjectile(shootInfoDart);

        scheduleDartForSegment(segmentIndex + 1);
    }
    private void dashTowardsPlayer() {
        // dashTimer = 0 denotes the boss is accelerating towards the player
        if (dashTimer == 0) {
            Vector movementVector = MathHelper.getDirection( ((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(), DASH_ACCELERATION);

            // Use stored lastVelocity to avoid being slowed down by liquid
            lastVelocity.add(movementVector);

            double distanceToTarget = bukkitEntity.getLocation().distance(target.getLocation());
            // The dash begins when the boss is close to the player
            if (distanceToTarget <= DASH_MIN_DISTANCE) {
                // The velocity is maintained during the dash
                lastVelocity = MathHelper.setVectorLength(movementVector, DASH_SPEED, false);
                dashTimer = 1;
            }
            else {
                MathHelper.setVectorLength(lastVelocity, DASH_SPEED, true);
            }
        }
        // The boss maintains its speed for the dash duration, and get prepared for the next dash.
        else if (++dashTimer > DASH_INTERVAL) {
            dashTimer = 0;
            scheduleDartForSegment(1);
        }
        // Update the entity's velocity and facing
        this.getBukkitEntity().setVelocity(lastVelocity);
        this.setYawPitch(bukkitEntity.getLocation().setDirection(lastVelocity));
    }
    private void setYawPitch(Location loc) {
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            if (owner.isAlive()) {
                target = owner.target;
                
            }
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
                // head
                if (segmentIndex == 0) {
                    // increase player aggro duration
                    targetMap.get(target.getUniqueId()).addAggressionTick();

                    dashTowardsPlayer();

                    // follow
                    EntityHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, segmentIndex);
                }
                // update facing direction from handleSegmentsFollow
                {
                    MetadataValue valYaw = EntityHelper.getMetadata(bukkitEntity, "yaw");
                    if (valYaw != null) this.yaw = valYaw.asFloat();
                    MetadataValue valPitch = EntityHelper.getMetadata(bukkitEntity, "pitch");
                    if (valPitch != null) this.pitch = valPitch.asFloat();
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Sepulcher(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Sepulcher(SupremeCalamitas owner) {
        this(owner, new ArrayList<>(), 0);
    }
    public Sepulcher(SupremeCalamitas owner, ArrayList<LivingEntity> bossParts, int segmentIndex) {
        super( owner.getWorld() );
        // copy variable
        this.bossParts = bossParts;
        this.segmentIndex = segmentIndex;
        this.owner = owner;
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY() + segmentIndex, spawnLoc.getZ(), 0, 0);
        // add to world
        owner.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        if (segmentIndex == 0) {
            this.head = this;
            segmentTypeIndex = 0;
        }
        else {
            this.head = (Sepulcher) ((CraftEntity) bossParts.get(0)).getHandle();
            segmentTypeIndex = (segmentIndex + 1 < TOTAL_LENGTH) ? 1 : 2;
        }
        setCustomName("灾坟魔物" + NAME_SUFFIXES[segmentTypeIndex]);
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
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("damage", SEGMENT_DAMAGE[segmentTypeIndex][0]);
            attrMap.put("defence", 0d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MAGIC);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // clone target map
        {
            targetMap = (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) owner.targetMap.clone();
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(SLIME_SIZE, false);
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
            // projectile info
            shootInfoDart = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapDart,
                    EntityHelper.DamageType.MAGIC, "硫火飞弹");
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
            // next segment
            if (segmentTypeIndex != 2)
                new Sepulcher(owner, bossParts, segmentIndex + 1);
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
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
