package terraria.entity.boss.profanedGuardians;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GuardianAttacker extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PROFANED_GUARDIANS;
    public static final double BASIC_HEALTH = 216000 * 2;
    public static final String DISPLAY_NAME = BOSS_TYPE.msgName + "·圣晶";
    HashMap<String, Double> attrMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapProjectile, attrMapLaser;
    static GenericHelper.StrikeLineOptions strikeOptionLaser;
    static {
        attrMapProjectile = new HashMap<>();
        attrMapProjectile.put("damage", 732d);
        attrMapProjectile.put("knockback", 1.5d);
        attrMapLaser = new HashMap<>();
        attrMapLaser.put("damage", 1145d);
        attrMapLaser.put("knockback", 5d);

        strikeOptionLaser = new GenericHelper.StrikeLineOptions()
                .setThruWall(true)
                .setParticleInfo(
                        new GenericHelper.ParticleLineOptions()
                                .setVanillaParticle(false)
                                .setTicksLinger(1)
                                .setParticleColor("255|255|150"));
    }
    // HORIZONTAL_POSITION should have value between 0 (directly above target) and 1 (directly above commander)
    static final double HORIZONTAL_POSITION = 0.75, VERTICAL_OFFSET = 10.0, HOVER_SPEED = 2.5;
    static final int PROJECTILE_INTERVAL = 20;
    // projectile angle etc.
    static final double ANGLE_INTERVAL = 10, SPREAD_DEGREE = 31, PROJECTILE_SPEED = 2.0;
    static final double[] LASER_THRESHOLDS = {0.7, 0.4};
    static final double LASER_LENGTH = 64.0, LASER_WIDTH = 1.5, LASER_GAP_START = 60.0, LASER_GAP_END = 17.5, LASER_SPEED_VERTICAL = 0.35;
    static final int LASER_DURATION = 60;

    GuardianCommander commander;
    EntityHelper.ProjectileShootInfo shootInfoProjectile;
    private Vector velocity = new Vector();
    int indexAI = 0, laserFired = 0;
    double laserPitch;
    boolean isLaserPhase = false;
    HashSet<Entity> laserDamaged = new HashSet<>();
private void attack() {
    // 1. Determine Hovering Location
    Location targetLoc = target.getLocation();
    Location commanderLoc = commander.getBukkitEntity().getLocation();
    // Vertically align the two locations
    commanderLoc.setY(targetLoc.getY());
    // Calculate the hover position
    Location hoverPosition = targetLoc.clone().multiply(1 - HORIZONTAL_POSITION).add(commanderLoc.clone().multiply(HORIZONTAL_POSITION));
    hoverPosition.add(0, VERTICAL_OFFSET, 0);

    // 2. Movement towards Hovering Location
    this.velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverPosition, HOVER_SPEED);
    if (isLaserPhase) {
        if (this.velocity.getY() > LASER_SPEED_VERTICAL)
            this.velocity.setY(LASER_SPEED_VERTICAL);
        else if (this.velocity.getY() < -LASER_SPEED_VERTICAL)
            this.velocity.setY(-LASER_SPEED_VERTICAL);
    }

    // 3. Attack
    if (isLaserPhase) {
        double pitchOffset = LASER_GAP_START + (LASER_GAP_END - LASER_GAP_START) * indexAI / LASER_DURATION;
        // Horizontally Locks the target
        double laserYaw = MathHelper.getVectorYaw( target.getEyeLocation().subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector() );
        // Shoot lasers
        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                laserYaw, laserPitch + pitchOffset, LASER_LENGTH, LASER_WIDTH, "", "",
                laserDamaged, attrMapLaser, strikeOptionLaser);
        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                laserYaw, laserPitch - pitchOffset, LASER_LENGTH, LASER_WIDTH, "", "",
                laserDamaged, attrMapLaser, strikeOptionLaser);
        // Transform back into regular phase
        if (indexAI >= LASER_DURATION) {
            isLaserPhase = false;
            indexAI = 0;
        }
    }
    else {
        // Transform into laser phase
        if (laserFired < LASER_THRESHOLDS.length && getHealth() / getMaxHealth() < LASER_THRESHOLDS[laserFired]) {
            laserFired ++;
            indexAI = 0;
            isLaserPhase = true;
            laserPitch = MathHelper.getVectorPitch( target.getEyeLocation().subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector() );
        }
        // Fire projectile
        if (ticksLived > 90 && indexAI % PROJECTILE_INTERVAL == 0) {
            shootInfoProjectile.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            ArrayList<Vector> projectileDirections = MathHelper.getEvenlySpacedProjectileDirections(
                    ANGLE_INTERVAL, SPREAD_DEGREE, target, shootInfoProjectile.shootLoc, PROJECTILE_SPEED);
            for (Vector direction : projectileDirections) {
                shootInfoProjectile.velocity = direction;
                EntityHelper.spawnProjectile(shootInfoProjectile);
            }
        }
    }
}
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
                attack();

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
        setCustomName(DISPLAY_NAME);
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
                    EntityHelper.DamageType.MAGIC, "神圣新星");
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