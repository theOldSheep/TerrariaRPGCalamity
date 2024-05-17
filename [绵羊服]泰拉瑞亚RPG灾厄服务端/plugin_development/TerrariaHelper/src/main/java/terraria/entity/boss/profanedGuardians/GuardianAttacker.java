package terraria.entity.boss.profanedGuardians;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
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

public class GuardianAttacker extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PROFANED_GUARDIANS;
    public static final double BASIC_HEALTH = 216000 * 2;
    HashMap<String, Double> attrMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapProjectile;
    EntityHelper.ProjectileShootInfo shootInfoProjectile;
    static {
        attrMapProjectile = new HashMap<>();
        attrMapProjectile.put("damage", 732d);
        attrMapProjectile.put("knockback", 1.5d);
    }
    static final double FLIGHT_SPEED = 2.75, FLIGHT_ACCELERATION = 0.35;
    GuardianCommander commander;
    int indexAI = 0, AIPhase = 0;
    private Vector velocity = new Vector();

    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = commander.target;
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
                // TODO
                Location targetLoc = target.getLocation().add(0, 20, 0);
                velocity.add( MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, FLIGHT_ACCELERATION) );
                MathHelper.setVectorLength(velocity, FLIGHT_SPEED, true);

                indexAI++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );

        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public GuardianAttacker(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public GuardianAttacker(GuardianCommander commander) {
        super( commander.getWorld() );
        this.commander = commander;
        // spawn location
        Location spawnLoc = commander.getBukkitEntity().getLocation().add( MathHelper.randomVector().multiply(10) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        commander.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
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
            attrMap.put("damage", 540d);
            attrMap.put("damageTakenMulti", 0.8);
            attrMap.put("defence", 60d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.ARROW);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target
        {
            target = commander.target;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, commander.targetMap);
        }
        // init health and slime size
        {
            setSize(12, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(commander.targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = commander.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoProjectile = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapProjectile,
                    EntityHelper.DamageType.ARROW, "projectile");
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);
        }
    }
    // rewrite AI
    @Override
    public void B_() {
        super.B_();
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