package terraria.entity.boss.postMoonLord.profanedGuardians;

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

import java.util.ArrayList;
import java.util.HashMap;

public class GuardianDefender extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PROFANED_GUARDIANS;
    public static final double BASIC_HEALTH = 144000 * 2, BASIC_HEALTH_BR = 216000 * 2;
    public static final String DISPLAY_NAME = BOSS_TYPE.msgName + "·神石";
    HashMap<String, Double> attrMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapSpear, attrMapBlast;
    static {
        attrMapSpear = new HashMap<>();
        attrMapSpear.put("damage", 865d);
        attrMapSpear.put("knockback", 1.5d);
        attrMapBlast = new HashMap<>();
        attrMapBlast.put("damage", 732d);
        attrMapBlast.put("knockback", 1.5d);
    }
    protected enum AttackMode {
        PROJECTILE,
        DASH
    }
    static final double HORIZONTAL_ROTATION_SPEED = 0.05, HORIZONTAL_ROTATION_DIST = 12;
    static final int SPEAR_ATTACK_INTERVAL = 30, SPEAR_ATTACK_INTERVAL_SECOND_PHASE = 15;
    static final double SPEAR_SPEED = 2.5, BLAST_SPEED = 1.25, BLAST_CHANCE = 0.15;
    static final Vector SPEAR_SHOOT_LOC_OFFSET = new Vector(0, 1, 0);
    static final int DASH_COUNT = 3;
    static final double REGULAR_MOVE_SPEED = 1.0, DASH_SPEED = 2.0;
    static final int DASH_DURATION_SINGLE = 20, PROJECTILE_DURATION = 160, PROJECTILE_MIN_INDEX = 50;
    static final int ROCK_SUMMON_INTERVAL = 10;
    GuardianCommander commander;
    GuardianAttacker attacker;
    EntityHelper.ProjectileShootInfo shootInfoSpear, shootInfoBlast;
    EntityHelper.AimHelperOptions aimHelperSpear, aimHelperBlast, aimHelperDash;

    protected AttackMode currentAttackMode = AttackMode.PROJECTILE;
    int indexAI = 0;
    private boolean secondPhase = false;
    private Vector velocity = new Vector();

    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = commander.target;
            terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
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
                checkPhaseTransition();
                updateAttackMode();
                summonRock();

                switch (currentAttackMode) {
                    case PROJECTILE:
                        handleProjectileAttack();
                        break;
                    case DASH:
                        handleDashAttack();
                        break;
                }

                indexAI++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );

        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    private void summonRock() {
        if (indexAI % ROCK_SUMMON_INTERVAL == 0) {
            new GuardianRock(this);
        }
    }
    private void checkPhaseTransition() {
        if (! secondPhase && ! attacker.isAlive()) {
            secondPhase = true;
            removeScoreboardTag("noDamage");
        }
    }
    private void updateAttackMode() {
        if (currentAttackMode == AttackMode.DASH && indexAI >= DASH_COUNT * DASH_DURATION_SINGLE - 1 ) {
            currentAttackMode = AttackMode.PROJECTILE;
            indexAI = 0; // Reset index for timing
        }
        else if (currentAttackMode == AttackMode.PROJECTILE && !attacker.isAlive() && indexAI >= PROJECTILE_DURATION) {
            currentAttackMode = AttackMode.DASH;
            indexAI = 0; // Reset index for timing
        }
    }

    private void handleProjectileAttack() {
        handleHorizontalRotation();

        boolean shouldFire = indexAI >= PROJECTILE_MIN_INDEX &&
                indexAI % (secondPhase ? SPEAR_ATTACK_INTERVAL_SECOND_PHASE : SPEAR_ATTACK_INTERVAL) == 0;
        if (shouldFire) {
            boolean blastOrSpear = secondPhase && Math.random() < BLAST_CHANCE;
            // Setup projectile features
            EntityHelper.AimHelperOptions aimOption = blastOrSpear ? aimHelperBlast : aimHelperSpear;
            EntityHelper.ProjectileShootInfo shootInfo = blastOrSpear ? shootInfoBlast : shootInfoSpear;
            double speed = blastOrSpear ? BLAST_SPEED : SPEAR_SPEED;
            // Fire projectile
            Location predictedTargetLoc = EntityHelper.helperAimEntity(
                    bukkitEntity.getLocation(), target, aimOption);
            shootInfo.shootLoc = bukkitEntity.getLocation().add(SPEAR_SHOOT_LOC_OFFSET);
            shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, predictedTargetLoc, speed, false);
            EntityHelper.spawnProjectile(shootInfo);
        }
    }

    private void handleDashAttack() {
        // Dash towards the player
        if (indexAI % DASH_DURATION_SINGLE == 0) {
            Location targetLoc = EntityHelper.helperAimEntity(bukkitEntity.getLocation(), target, aimHelperDash);
            velocity = MathHelper.getDirection( ((LivingEntity) bukkitEntity).getEyeLocation(), targetLoc, DASH_SPEED, false);
            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
        }
    }

    private void handleHorizontalRotation() {
        double angle = ticksLived * HORIZONTAL_ROTATION_SPEED;
        Vector dir = new Vector(Math.sin(angle), 0, Math.cos(angle));
        dir.multiply(HORIZONTAL_ROTATION_DIST);
        Location desiredPosition = commander.getBukkitEntity().getLocation().add(dir);
        velocity = MathHelper.getDirection(getBukkitEntity().getLocation(), desiredPosition, REGULAR_MOVE_SPEED, true);
    }
    // default constructor to handle chunk unload
    public GuardianDefender(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public GuardianDefender(GuardianCommander commander) {
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
        addScoreboardTag("noDamage");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 672d);
            attrMap.put("damageTakenMulti", 0.6);
            attrMap.put("defence", 100d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
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
            if (BossHelper.bossMap.containsKey(BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS.msgName))
                healthMulti *= GuardianCommander.HEALTH_MULTI_PROVIDENCE_ALIVE;
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
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
            shootInfoSpear = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapSpear,
                    EntityHelper.DamageType.ARROW, "神圣之矛");
            shootInfoBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapBlast,
                    EntityHelper.DamageType.MAGIC, "闪耀之弹");
            aimHelperSpear = new EntityHelper.AimHelperOptions()
                    .setProjectileSpeed(SPEAR_SPEED)
                    .setAccelerationMode(true);
            aimHelperBlast = new EntityHelper.AimHelperOptions()
                    .setProjectileSpeed(BLAST_SPEED);
            aimHelperDash = new EntityHelper.AimHelperOptions()
                    .setProjectileSpeed(DASH_SPEED)
                    .setAccelerationMode(true);
        }
        attacker = new GuardianAttacker(commander);
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