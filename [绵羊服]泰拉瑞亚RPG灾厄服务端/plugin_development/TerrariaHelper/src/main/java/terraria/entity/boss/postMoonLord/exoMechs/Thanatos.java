package terraria.entity.boss.postMoonLord.exoMechs;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.postMoonLord.exoMechs.Draedon;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Thanatos extends EntitySlime {
    public enum SegmentType {
        HEAD("Thanatos, the Devouring Worm's Head", 1500d),
        BODY("Thanatos, the Devouring Worm's Body", 1320d),
        TAIL("Thanatos, the Devouring Worm's Tail", 1100d);

        private String customName;
        private double damage;

        SegmentType(String customName, double damage) {
            this.customName = customName;
            this.damage = damage;
        }

        public String getCustomName() {
            return customName;
        }

        public double getDamage() {
            return damage;
        }
    }
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final double BASIC_HEALTH = 2760000 * 2;
    public static final int TOTAL_LENGTH = 82, SLIME_SIZE = 8;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    Draedon owner = null;
    // other variables and AI
    static final EntityHelper.WormSegmentMovementOptions followOption = new EntityHelper.WormSegmentMovementOptions()
            .setStraighteningMultiplier(-0.1)
            .setFollowingMultiplier(1)
            .setFollowDistance(SLIME_SIZE * 0.5)
            .setVelocityOrTeleport(false);
    List<Thanatos> segments;

    List<LivingEntity> livingSegments;
    Thanatos head;
    int index;
    SegmentType segmentType;


    private void tick() {
        if (segmentType == SegmentType.HEAD) {
            // Handle movement
            Location targetLocation = target.getLocation();
            if (! owner.isSubBossActive(Draedon.SubBossType.THANATOS)) {
                targetLocation.setY(-50);
            }

            // Calculate the target velocity
            Vector targetVelocity = targetLocation.toVector().subtract(getBukkitEntity().getLocation().toVector()).normalize().multiply(2.5);

            // Smoothly update the velocity
            Vector velocity = getBukkitEntity().getVelocity();
            velocity.setX(velocity.getX() * 0.9 + targetVelocity.getX() * 0.1);
            velocity.setY(velocity.getY() * 0.9 + targetVelocity.getY() * 0.1);
            velocity.setZ(velocity.getZ() * 0.9 + targetVelocity.getZ() * 0.1);
            getBukkitEntity().setVelocity(velocity);


            // Let the remaining segments follow the head
            EntityHelper.handleSegmentsFollow(segments.stream().map((e) -> (LivingEntity) (e.bukkitEntity)).collect(Collectors.toList()), followOption);
        } else {
            // Set the health of subsequent entities to the health of the head
            setHealth(head.getHealth());
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            // attack
            if (target != null) {
                // TODO
                tick();

                // facing
                this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Thanatos(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Thanatos(Draedon draedon, Location spawnLoc) {
        this(draedon, spawnLoc, new ArrayList<>(), new ArrayList<>(), null, 0);
    }
    private Thanatos(Draedon draedon, Location spawnLoc, List<Thanatos> segments, List<LivingEntity> livingSegments, Thanatos head, int index) {
        super( draedon.getWorld() );
        owner = draedon;
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        draedon.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.head = head;
        this.index = index;
        if (index == 0) {
            segmentType = SegmentType.HEAD;
        } else if (index == TOTAL_LENGTH - 1) {
            segmentType = SegmentType.TAIL;
        } else {
            segmentType = SegmentType.BODY;
        }

        setCustomName(segmentType.getCustomName());
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        if (index > 0) {
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.bukkitEntity);
        }
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", segmentType.getDamage());
            attrMap.put("defence", 200d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = draedon.bossbar;
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = owner.targetMap;
            target = owner.target;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(SLIME_SIZE, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = owner.bossParts;
            if (index == 0)
                bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // segment info
        {
            this.segments = segments;
            this.livingSegments = livingSegments;
            this.segments.add(this);
            this.livingSegments.add((LivingEntity) bukkitEntity);

            if (index < TOTAL_LENGTH - 1) {
                Location nextSpawnLoc = spawnLoc.subtract(0, 1, 0);
                new Thanatos(draedon, nextSpawnLoc, segments, livingSegments, head == null ? this : head, index + 1);
            }
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