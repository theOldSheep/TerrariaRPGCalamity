package terraria.entity.boss.profanedGuardians;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
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

public class GuardianCommander extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PROFANED_GUARDIANS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.HALLOW;
    public static final double BASIC_HEALTH = 288000 * 2, HEALTH_MULTI_PROVIDENCE_ALIVE = 0.35;
    public static final boolean IGNORE_DISTANCE = false;
    public static final String DISPLAY_NAME = BOSS_TYPE.msgName + "·统御";
    HashMap<String, Double> attrMap;
    // Made public for Providence access
    public HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    // Made public for Providence access
    public Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapProjectile;
    EntityHelper.ProjectileShootInfo shootInfoProjectile;
    static {
        attrMapProjectile = new HashMap<>();
        attrMapProjectile.put("damage", 732d);
        attrMapProjectile.put("knockback", 1.5d);
    }
    int indexAI = 0, AIPhase = 0;
    private boolean defenderAlive = true, summonedByProvidence;
    // In ticks
    private static final int
            PHASE_SWITCH_INTERVAL = 90,
            DASH_INTERVAL = 30, DASH_SLOWDOWN_BEGIN = DASH_INTERVAL - 12,
            SHOOT_INTERVAL_DEFENDER_ALIVE = 25, SHOOT_INTERVAL_DEFENDER_DEAD = 10;
    // in Blocks/tick
    private static final double
            VERTICAL_ALIGN_ACCELERATION = 0.35, HORIZONTAL_ADJUST_ACCELERATION = 0.5,
            DASH_SPEED = 3.0, MAX_SPEED_FOLLOW = 1.5, DASH_SLOWDOWN_FACTOR = 0.925,
            PROJECTILE_SPEED = 1.2;
    private EntityHelper.AimHelperOptions aimHelperOptionProjectile, aimHelperOptionDash;
    private Vector velocity = new Vector();
    private GuardianDefender defender;

    private void AIPhaseFollow() {
        Location bossLoc = bukkitEntity.getLocation();
        Location targetLoc = target.getLocation();
        // Horizontal & vertical distance calculation & printing for debugging
        double horizontalDistance = bossLoc.distance(new Location(targetLoc.getWorld(), targetLoc.getX(), bossLoc.getY(), targetLoc.getZ()));
        double verticalDistance = targetLoc.getY() - bossLoc.getY();

        Vector acceleration = new Vector();
        // Vertical alignment phase
        if (Math.abs(verticalDistance) > 1.0) {
            acceleration.setY(VERTICAL_ALIGN_ACCELERATION * Math.signum(verticalDistance));
        }

        // Maintain horizontal distance
        if (horizontalDistance < 30.0) {
            acceleration.add( MathHelper.getDirection(targetLoc, bossLoc, HORIZONTAL_ADJUST_ACCELERATION, true) ); // Move away
        } else if (horizontalDistance > 40.0) {
            acceleration.add( MathHelper.getDirection(bossLoc, targetLoc, HORIZONTAL_ADJUST_ACCELERATION, true) ); // Move closer
        }

        // Accelerate
        velocity.add(acceleration);
        int fireInterval = defenderAlive ? SHOOT_INTERVAL_DEFENDER_ALIVE : SHOOT_INTERVAL_DEFENDER_DEAD;
        if (indexAI % fireInterval == 0) {
            // Fire projectile
            Location aimLocation = EntityHelper.helperAimEntity(getBukkitEntity(), target, aimHelperOptionProjectile);
            Location newFireLocation = ((LivingEntity) getBukkitEntity()).getEyeLocation();
            Vector newFireVelocity = MathHelper.getDirection(newFireLocation, aimLocation, PROJECTILE_SPEED, false);

            shootInfoProjectile.setLockedTarget(target);
            shootInfoProjectile.shootLoc = newFireLocation;
            shootInfoProjectile.velocity = newFireVelocity;
            EntityHelper.spawnProjectile(shootInfoProjectile);
        }
        MathHelper.setVectorLength(velocity, MAX_SPEED_FOLLOW, true);
    }
    private void AIPhaseDash() {
        Location bossLoc = bukkitEntity.getLocation();
        Location targetLoc = EntityHelper.helperAimEntity(bukkitEntity, target, aimHelperOptionDash);
        // Dash phase

        if (indexAI % DASH_INTERVAL == 0) {
            velocity = MathHelper.getDirection(bossLoc, targetLoc, DASH_SPEED);

            // Play growl sound at dash start
            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
        } else if (indexAI % DASH_INTERVAL > DASH_SLOWDOWN_BEGIN) {
            // Slow down in the last 15 ticks of the dash
            velocity.multiply(DASH_SLOWDOWN_FACTOR);
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            if (!summonedByProvidence)
                target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                        IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
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
                // increase player aggro duration
                targetMap.get(target.getUniqueId()).addAggressionTick();
                // triggered once
                if (defenderAlive && !defender.isAlive()) {
                    defenderAlive = false;
                    removeScoreboardTag("noDamage");
                    shootInfoProjectile.projectileName = "圣之爆焱";
                }

                if (defenderAlive)
                    AIPhaseFollow();
                else {
                    // Phase switching logic
                    if (indexAI % PHASE_SWITCH_INTERVAL == 0) {
                        AIPhase = 1 - AIPhase;
                    }
                    if (AIPhase == 0)
                        AIPhaseFollow();
                    else
                        AIPhaseDash();
                }

                indexAI++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        // do not face the player when dashing
        if (AIPhase == 1)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );

        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public GuardianCommander(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    static boolean providenceAlive() {
        return BossHelper.bossMap.containsKey(BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS.msgName);
    }
    // Do not spawn when providence is present
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED && WorldHelper.isDayTime(player.getWorld()) && !providenceAlive();
    }
    // a constructor for actual spawning
    public GuardianCommander(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 48;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(DISPLAY_NAME);
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        addScoreboardTag("noDamage");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);

        summonedByProvidence = providenceAlive();
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 672d);
            attrMap.put("damageTakenMulti", 0.7);
            attrMap.put("defence", 80d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MAGIC);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer,
                    true, !summonedByProvidence, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(12, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            if (summonedByProvidence)
                healthMulti *= HEALTH_MULTI_PROVIDENCE_ALIVE;
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            if (!summonedByProvidence)
                BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoProjectile = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapProjectile,
                    EntityHelper.DamageType.MAGIC, "神圣之火");
            aimHelperOptionProjectile = new EntityHelper.AimHelperOptions()
                    .setAccelerationMode(false)
                    .setProjectileSpeed(PROJECTILE_SPEED);
            aimHelperOptionDash = new EntityHelper.AimHelperOptions()
                    .setAccelerationMode(true)
                    .setProjectileSpeed(DASH_SPEED);
        }
        // other parts
        defender = new GuardianDefender(this);
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);

            // send loot
            if (! providenceAlive())
                terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
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