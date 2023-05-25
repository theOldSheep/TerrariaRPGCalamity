package terraria.entity.boss.thePlaguebringerGoliath;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
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

public class PlagueMine extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_PLAGUEBRINGER_GOLIATH;
    public static final double BASIC_HEALTH = 3000 * 2;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    Player target = null;
    // other variables and AI
    static String NAME = "瘟疫之雷";
    static double FLIGHT_SPEED = 1.5, ACCELERATION_MIN = 0.75, ACCELERATION_MAX = 0.75, DETONATE_RADIUS_SQR = 6;
    ThePlaguebringerGoliath owner;
    int indexAI = 0;

    private double getAcc() {
        return ACCELERATION_MIN + (ACCELERATION_MAX - ACCELERATION_MIN) * (1 - owner.healthRatio);
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            // disappear if no target is available
            if (owner.dead || owner.getHealth() < 1e-5 || target == null) {
                die();
                return;
            }
            // if target is valid, attack
            Location eyeLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            if (ticksLived % 3 == 0) {
                // move towards enemy
                {
                    Vector velocity = bukkitEntity.getVelocity();
                    Vector acc = MathHelper.getDirection(eyeLoc, target.getEyeLocation(), getAcc());
                    velocity.add(acc);
                    double velLen = velocity.length();
                    if (velLen > FLIGHT_SPEED)
                        velocity.multiply(FLIGHT_SPEED / velLen);
                    bukkitEntity.setVelocity(velocity);
                }
                // add 1 to index
                indexAI ++;
            }
            // detonate if close enough
            if (eyeLoc.distanceSquared(target.getEyeLocation()) < DETONATE_RADIUS_SQR) {
                EntityHelper.handleEntityExplode(bukkitEntity, 3, new ArrayList<>(), eyeLoc);
                die();
            }
        }
    }
    // default constructor to handle chunk unload
    public PlagueMine(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.isDayTime(player.getWorld()) ) return false;
        return true;
    }
    // a constructor for actual spawning
    public PlagueMine(ThePlaguebringerGoliath owner) {
        super( owner.getWorld() );
        this.owner = owner;
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(NAME);
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
            attrMap.put("damage", 660d);
            attrMap.put("defence", 40d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = (HashMap<Player, Double>) owner.targetMap.clone();
            target = owner.target;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(3, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
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
        // AI
        AI();
    }
}
