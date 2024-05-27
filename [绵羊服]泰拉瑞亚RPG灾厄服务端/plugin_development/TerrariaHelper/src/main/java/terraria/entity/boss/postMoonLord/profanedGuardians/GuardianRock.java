package terraria.entity.boss.postMoonLord.profanedGuardians;

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

import java.util.HashMap;

public class GuardianRock extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PROFANED_GUARDIANS;
    public static final double BASIC_HEALTH = 4000;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    static final int ROCK_LIFETIME = 60;
    static final int PREPARATION_TIME_FOR_LAUNCH = 200;
    static final double ROCK_ORBIT_RADIUS_MIN = 5.0, ROCK_ORBIT_RADIUS_MAX = 8.5, ROCK_ORBIT_SPEED = 0.05;
    static final double DASH_SPEED = 2.25;

    EntityHelper.AimHelperOptions aimHelperDash;


    double angle = Math.random() * 2 * Math.PI;
    private Vector orbitX, orbitZ;
    private int ticksSinceLaunch = -1;

    GuardianDefender owner;
    GuardianCommander commander;
    int indexAI = 0;
    private Vector velocity = new Vector();

    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = commander.target;
            // disappear if no target is available / commander is dead
            if (target == null || ! commander.isAlive()) {
                die();
                return;
            }
            // if target is valid, attack
            else {
                if (ticksSinceLaunch > 0) { // Launched towards target
                    ticksSinceLaunch++;
                    if (ticksSinceLaunch >= ROCK_LIFETIME) {
                        die();
                    }
                } else { // Orbiting
                    angle += ROCK_ORBIT_SPEED;

                    // Calculate new position based on orbit
                    Vector offsetDirection =
                            orbitX.clone().multiply(MathHelper.xsin_radian(angle)).add(
                                    orbitZ.clone().multiply(MathHelper.xcos_radian(angle)));

                    // Update velocity to move towards new position
                    velocity = commander.getBukkitEntity().getLocation().add(offsetDirection).subtract(bukkitEntity.getLocation()).toVector();

                    // Check for launch condition
                    if (owner.isAlive() && owner.currentAttackMode != GuardianDefender.AttackMode.DASH && indexAI > PREPARATION_TIME_FOR_LAUNCH) { // Delay before launch
                        launchTowardsTarget();
                    }
                }

                indexAI++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );

        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    private void launchTowardsTarget() {
        ticksSinceLaunch = 1;
        Location predictedTargetLoc = EntityHelper.helperAimEntity(
                bukkitEntity.getLocation(), target, aimHelperDash);
        velocity = MathHelper.getDirection(bukkitEntity.getLocation(), predictedTargetLoc, DASH_SPEED, false);
    }
    // default constructor to handle chunk unload
    public GuardianRock(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public GuardianRock(GuardianDefender defender) {
        super( defender.getWorld() );
        this.owner = defender;
        this.commander = owner.commander;
        // spawn location
        Location spawnLoc = ((LivingEntity) commander.getBukkitEntity()).getEyeLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        commander.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("亵渎岩");
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
            attrMap.put("damageTakenMulti", 1d);
            attrMap.put("defence", 100d);
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
            setSize(5, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(commander.targetMap.size());
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
        // init orbit
        {
            // Randomly initialize the orbit x and z; these will be the "e1" and "e2" multiplied with sin and cos to yield a circle.
            this.orbitX = MathHelper.randomVector();
            // The edge case for this cross product will yield a random orthogonal vector
            this.orbitZ = MathHelper.getNonZeroCrossProd(orbitX, orbitX).normalize();
            double rdm = ROCK_ORBIT_RADIUS_MIN + Math.random() * (ROCK_ORBIT_RADIUS_MAX - ROCK_ORBIT_RADIUS_MIN);
            this.orbitX.multiply(rdm);
            this.orbitZ.multiply(rdm);
        }
        aimHelperDash = new EntityHelper.AimHelperOptions().setProjectileSpeed(DASH_SPEED).setAccelerationMode(true);
    }

    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // AI
        AI();
    }
}