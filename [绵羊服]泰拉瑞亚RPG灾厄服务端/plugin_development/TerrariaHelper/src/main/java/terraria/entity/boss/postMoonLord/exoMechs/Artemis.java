package terraria.entity.boss.postMoonLord.exoMechs;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Artemis extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final double BASIC_HEALTH = 3588000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    Draedon owner = null;
    Apollo apollo = null;
    // other variables and AI
    static HashMap<String, Double> attrMapLaser;
    EntityHelper.ProjectileShootInfo shootInfoLaser;
    static {
        attrMapLaser = new HashMap<>();
        attrMapLaser.put("damage", 1260d);
        attrMapLaser.put("knockback", 1.5d);
    }

    boolean dashing = false;
    protected Location twinHoverLocation, hoverLocation;




    public void movementTick() {
        Location targetLocation = target.getLocation();

        // Calculate the midpoint of the boss's hover location and its twin's hover location
        Location midPoint = hoverLocation.clone().add(twinHoverLocation).multiply(0.5);

        // Calculate the yaw as between the target and the midpoint
        double yaw = MathHelper.getVectorYaw(midPoint.clone().subtract(targetLocation).toVector());

        // Calculate the direction vector from the target to the midpoint
        Vector direction;
        direction = MathHelper.vectorFromYawPitch_approx(yaw - 20, 0).multiply(32);

        // Calculate the hover location for the boss
        Location hoverLocation = targetLocation.clone().add(direction);

        // Update the hover location based on the sub-bosses' states
        updateHoverLocation(hoverLocation);

        // Calculate the direction vector for the twin
        direction = MathHelper.vectorFromYawPitch_approx(yaw + 20, 0).multiply(32);

        // Calculate the hover location for the twin
        twinHoverLocation = targetLocation.clone().add(direction);

        // Update the hover location based on the sub-bosses' states
        updateHoverLocation(twinHoverLocation);

        // Use velocity-based movement to move the sub-boss to its hover location
        Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverLocation, Draedon.MECHS_ALIGNMENT_SPEED, true);
        bukkitEntity.setVelocity(velocity);
    }
    private void updateHoverLocation(Location location) {
        if (owner.isSubBossActive(0)) {
            if (owner.isSubBossActive(2)) {
                location.setY(location.getY() - 15);
            }
        } else {
            location.setY(-30);
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
                movementTick();
            }
        }
        // facing
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Artemis(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Artemis(Draedon draedon, Location spawnLoc) {
        super( draedon.getWorld() );
        owner = draedon;
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        hoverLocation = spawnLoc;
        twinHoverLocation = spawnLoc;
        // add to world
        draedon.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("XS-01“阿尔忒弥斯”");
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
            attrMap.put("damage", 1d);
            attrMap.put("damageTakenMulti", 1d);
            attrMap.put("defence", 0d);
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
            setSize(15, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = owner.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapLaser,
                    EntityHelper.DamageType.ARROW, "橙色星流脉冲激光");
        }
        // apollo
        apollo = new Apollo(owner, this, spawnLoc);
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