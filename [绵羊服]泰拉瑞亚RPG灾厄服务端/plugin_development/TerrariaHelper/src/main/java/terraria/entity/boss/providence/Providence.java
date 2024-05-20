package terraria.entity.boss.providence;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.profanedGuardians.GuardianCommander;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.*;

public class Providence extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.HALLOW;
    public static final double BASIC_HEALTH = 900000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI


    // phase 1-2
    static final double TARGET_HEIGHT_PHASE_0 = 15.0, HORIZONTAL_DIST_PHASE_0 = 35.0;
    static final double HOVER_HEIGHT_PHASE_1 = 30.0;
    static final double MAX_SPEED = 2.5, MAX_ACCELERATION = 0.25;
    static final double BOMB_SPEED = 1.35, BOMB_SPEED_EXHAUST = 1.0, BLAST_SPEED = 0.9;
    static final double PHASE_2_DECELERATION = 0.9; // Rapid slow down
    // phase 3
    static final double[] STAR_INTERVAL_DEGREE = {10, 11, 12}, STAR_SPREAD_ANGLE = {41, 45, 50}, STAR_SPEED = {2.0, 2.5, 3.0};
    static final int[] PROJECTILE_RAIN_FIRE_TIME = {0, 8, 16};
    static final int PROJECTILE_RAIN_INTERVAL = 24;
    static final int
            PROJECTILE_RAIN_FULL_DURATION = PROJECTILE_RAIN_INTERVAL * 10 - 1,
            PROJECTILE_RAIN_MARGIN_DURATION = PROJECTILE_RAIN_INTERVAL * 2;
    static final int ACTION_BAR_UPDATE_INTERVAL = 3, ACTION_BAR_LENGTH = 30;
    static final double SPECIAL_EFFECT_DISTANCE = 64.0, TARGET_VERTICAL_OFFSET = SPECIAL_EFFECT_DISTANCE * 0.65, VERTICAL_ALIGNMENT_SPEED = 2.25;
    // phase 4
    static final double LASER_LENGTH = SPECIAL_EFFECT_DISTANCE + 16.0, LASER_WIDTH = 1.5,
            LASER_GAP_END = 10, LASER_AIM_PITCH = 35;
    static final int LASER_DURATION = 120, LASER_MARGIN = 40;


    static HashMap<String, Double> attrMapDmgLow, attrMapDmgMid, attrMapDmgHigh;
    static EntityHelper.AimHelperOptions aimBomb, aimBlast;
    static GenericHelper.StrikeLineOptions strikeOptionLaser;
    static {
        // majority of projectiles
        attrMapDmgLow = new HashMap<>();
        attrMapDmgLow.put("damage", 732d);
        attrMapDmgLow.put("knockback", 1.5d);
        // star
        attrMapDmgMid = new HashMap<>();
        attrMapDmgMid.put("damage", 864d);
        attrMapDmgMid.put("knockback", 2.0d);
        // laser
        attrMapDmgHigh = new HashMap<>();
        attrMapDmgHigh.put("damage", 1320d);
        attrMapDmgHigh.put("knockback", 3d);


        strikeOptionLaser = new GenericHelper.StrikeLineOptions()
                .setThruWall(true)
                .setParticleInfo(
                        new GenericHelper.ParticleLineOptions()
                                .setVanillaParticle(false)
                                .setTicksLinger(1)
                                .setParticleColor("255|255|150"));

        aimBomb = new EntityHelper.AimHelperOptions()
                .setAccelerationMode(true)
                .setProjectileSpeed(BOMB_SPEED);
        aimBlast = new EntityHelper.AimHelperOptions()
                .setAccelerationMode(false)
                .setProjectileSpeed(BLAST_SPEED);
    }
    EntityHelper.ProjectileShootInfo shootInfoBlast, shootInfoBomb, shootInfoStar;

    int indexAI = -100, AIPhase = 0;
    Vector velocity = new Vector();
    GuardianCommander commanderMinion = null;
    HashSet<Entity> laserDamaged = new HashSet<>();
    int commanderState = 0; // 0: not spawned, 1: spawned, 2: defeated
    static final int NUM_PHASES = 4;
    int[] phaseCounts = new int[NUM_PHASES];

    public int weightedTransition(int currentPhase) {
        HashMap<Integer, Double> weights = new HashMap<>();

        for (int phase = 0; phase < NUM_PHASES; phase++) {
            if (phase != currentPhase) {
                // Calculate weight inversely proportional to frequency
                double weight = 1.0 / (phaseCounts[phase] + 1); // Avoid division by zero
                weights.put(phase, weight);
            }
        }

        int newPhase = MathHelper.selectWeighedRandom(weights, currentPhase);
        phaseCounts[newPhase]++; // Increment count for the chosen phase
        return newPhase;
    }
    private void projectilePhase() {
        Location hoverLoc;
        double speed, acceleration, projectileSpeed;
        int projectileInterval, phaseDuration;
        // Modify this to your type
        EntityHelper.ProjectileShootInfo projectileType;
        EntityHelper.AimHelperOptions aimHelper;
        if (AIPhase == 0) {
            if (commanderState == 1) {
                hoverLoc = commanderMinion.getBukkitEntity().getLocation().add(0, 10, 0);
                projectileInterval = 35;
                projectileSpeed = BOMB_SPEED_EXHAUST;
            }
            else {
                double angle = System.currentTimeMillis() / 5000d; // Adjust for desired speed
                hoverLoc = target.getLocation().clone().add(
                        HORIZONTAL_DIST_PHASE_0 * Math.cos(angle),
                        TARGET_HEIGHT_PHASE_0,
                        HORIZONTAL_DIST_PHASE_0 * Math.sin(angle)
                );
                projectileInterval = 10;
                projectileSpeed = BOMB_SPEED;
            }
            speed = MAX_SPEED;
            acceleration = MAX_ACCELERATION;
            projectileType = shootInfoBomb;
            aimHelper = aimBomb;
            phaseDuration = 115;
        }
        else {
            hoverLoc = target.getLocation().clone().add(0, HOVER_HEIGHT_PHASE_1, 0);
            speed = MAX_SPEED * 0.8;
            acceleration = MAX_ACCELERATION * 1.25;
            projectileSpeed = BLAST_SPEED;
            projectileType = shootInfoBlast;
            aimHelper = aimBlast;
            projectileInterval = 10;
            phaseDuration = 85;
        }
        Vector accelerationVec = MathHelper.getDirection(bukkitEntity.getLocation(), hoverLoc, acceleration, true);
        velocity.add(accelerationVec);
        MathHelper.setVectorLength(velocity, speed, true); // Limit max speed
        if (indexAI > 0 && indexAI % projectileInterval == 0) {
            projectileType.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            projectileType.velocity = MathHelper.getDirection( projectileType.shootLoc,
                    EntityHelper.helperAimEntity(projectileType.shootLoc, this.target, aimHelper), projectileSpeed, false );
            EntityHelper.spawnProjectile(projectileType);
        }
        // Do not transition out of this phase if guardians are alive!
        if (indexAI >= phaseDuration && commanderState != 1) {
            indexAI = -1;
            AIPhase = weightedTransition(AIPhase);
        }
    }
    private void stopAndBurn(boolean shouldDamage) {
        Location highestBlockLoc = bukkitEntity.getWorld().getHighestBlockAt(bukkitEntity.getLocation()).getLocation();
        double targetHeight = highestBlockLoc.getY() + TARGET_VERTICAL_OFFSET; // Add a slight offset for the boss

        // Decelerate and vertical adjustment
        velocity.multiply(PHASE_2_DECELERATION);
        double yDiff = targetHeight - bukkitEntity.getLocation().getY();
        if (yDiff > 4)
            velocity.setY(Math.max(Math.abs(velocity.getY()), VERTICAL_ALIGNMENT_SPEED) * Math.signum(yDiff) );
        else
            velocity.setY(0);

        // Send the distance info action bar, conditionally burn the player
        if (indexAI % ACTION_BAR_UPDATE_INTERVAL == 0) {
            for (UUID plyID : targetMap.keySet()) {
                Player ply = Bukkit.getPlayer(plyID);
                double distanceToPlayer = bukkitEntity.getLocation().distance(ply.getLocation());
                double distRatio = Math.min(distanceToPlayer / SPECIAL_EFFECT_DISTANCE, 1);
                StringBuilder distanceMsgBuilder = new StringBuilder();
                // Different color for different distance
                if (distRatio < 0.5)
                    distanceMsgBuilder.append(ChatColor.GREEN);
                else if (distRatio < 0.75)
                    distanceMsgBuilder.append(ChatColor.GOLD);
                else
                    distanceMsgBuilder.append(ChatColor.RED);
                // The distance meter
                distanceMsgBuilder.append("[");
                for (int i = 0; i < ACTION_BAR_LENGTH; i++) {
                    distanceMsgBuilder.append((double) i / ACTION_BAR_LENGTH < distRatio ? "=" : " ");
                }
                distanceMsgBuilder.append("]");
                // Send message
                PlayerHelper.sendActionBar(ply, distanceMsgBuilder.toString());

                // Burn, only applicable after a brief moment into this phase!
                if (distanceToPlayer >= SPECIAL_EFFECT_DISTANCE && shouldDamage) {
                    EntityHelper.applyEffect(target, "圣狱神火", 100);
                }
            }
        }
    }
    private void rainProjectilePhase() {
        if (indexAI == 0) {
            tweakDamageReduction(true);
        }

        // Do not fire projectiles when the phase has just began or is about to end
        boolean withinTimeMargin = indexAI >= PROJECTILE_RAIN_MARGIN_DURATION && indexAI <= PROJECTILE_RAIN_FULL_DURATION - PROJECTILE_RAIN_MARGIN_DURATION;
        if (withinTimeMargin) {
            int starProjectileIndex = -1; // -1 denotes not applicable
            int indexWithinFireRound = indexAI % PROJECTILE_RAIN_INTERVAL;
            for (int i = 0; i < PROJECTILE_RAIN_FIRE_TIME.length; i ++) {
                if (indexWithinFireRound == PROJECTILE_RAIN_FIRE_TIME[i]) {
                    starProjectileIndex = i;
                    break;
                }
            }
            // If the index exists, fire a burst of projectiles.
            if (starProjectileIndex != -1) {
                shootInfoStar.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                ArrayList<Vector> projectileDirections = MathHelper.getEvenlySpacedProjectileDirections(
                        STAR_INTERVAL_DEGREE[starProjectileIndex], STAR_SPREAD_ANGLE[starProjectileIndex],
                        this.target, shootInfoStar.shootLoc, STAR_SPEED[starProjectileIndex]
                );

                for (Vector dir : projectileDirections) {
                    shootInfoStar.velocity = dir;
                    EntityHelper.spawnProjectile(shootInfoStar);
                }
            }
        }

        stopAndBurn(withinTimeMargin);

        if (indexAI >= PROJECTILE_RAIN_FULL_DURATION) {
            tweakDamageReduction(false);
            indexAI = -1; // This is incremented immediately afterwards
            AIPhase = weightedTransition(AIPhase);
        }
    }
    private void laserPhase() {
        boolean withinTimeMargin = indexAI >= LASER_MARGIN && indexAI <= LASER_DURATION - LASER_MARGIN;

        stopAndBurn(withinTimeMargin);

        // laser
        if (withinTimeMargin) {
            double progress = (double) (indexAI - LASER_MARGIN) / (LASER_DURATION - LASER_MARGIN * 2);
            double pitchOffsetTop = (90 + LASER_AIM_PITCH) * (1 - progress) + LASER_GAP_END * progress;
            double pitchOffsetBottom = (90 - LASER_AIM_PITCH) * (1 - progress) + LASER_GAP_END * progress;
            for (UUID plyID : targetMap.keySet()) {
                Player ply = Bukkit.getPlayer(plyID);
                double laserYaw = MathHelper.getVectorYaw( ply.getEyeLocation().subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector() );
                // Shoot lasers
                GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                        laserYaw, LASER_AIM_PITCH - pitchOffsetTop, LASER_LENGTH, LASER_WIDTH, "", "",
                        laserDamaged, attrMapDmgHigh, strikeOptionLaser);
                GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                        laserYaw, LASER_AIM_PITCH + pitchOffsetBottom, LASER_LENGTH, LASER_WIDTH, "", "",
                        laserDamaged, attrMapDmgHigh, strikeOptionLaser);
            }
        }

        if (indexAI >= LASER_DURATION) {
            indexAI = -1; // This is incremented immediately afterwards
            AIPhase = weightedTransition(AIPhase);
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
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

                // On low health, summon guardians
                if (getHealth() / getMaxHealth() < 0.33 && commanderState == 0) {
                    commanderMinion = new GuardianCommander(target);
                    addScoreboardTag("noDamage");
                    //
                    if (AIPhase == 2)
                        tweakDamageReduction(false);
                    AIPhase = 0;
                    indexAI = 0;
                    commanderState = 1;
                }
                // The boss can heal beyond low health when guardians are alive
                if (commanderState == 1) {
                    heal(300);
                    // Revert damage reduction if guardians defeated
                    if (!commanderMinion.isAlive()) {
                        removeScoreboardTag("noDamage");
                        // Continue with AI Phase 0
                        AIPhase = 0;
                        indexAI = 0;
                        commanderState = 2;
                    }
                }
                switch (AIPhase) {
                    case 0:
                    case 1:
                        projectilePhase();
                        break;
                    case 2:
                        rainProjectilePhase();
                        break;
                    case 3:
                        laserPhase();
                        break;
                    default:
                        AIPhase = weightedTransition(AIPhase);
                }
                indexAI ++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // no collision dmg
    }
    private void tweakDamageReduction(boolean addOrRemove) {
        EntityHelper.tweakAttribute(attrMap, "damageTakenMulti", "-6", addOrRemove);
    }
    // default constructor to handle chunk unload
    public Providence(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED && WorldHelper.isDayTime(player.getWorld()) &&
                !BossHelper.bossMap.containsKey(BossHelper.BossType.PROFANED_GUARDIANS.msgName);
    }
    // a constructor for actual spawning
    public Providence(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        spawnLoc.setY( spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
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
            attrMap.put("damage", 1d);
            attrMap.put("damageTakenMulti", 0.7);
            attrMap.put("defence", 100d);
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
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(24, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapDmgLow,
                    EntityHelper.DamageType.MAGIC, "闪耀之弹");
            shootInfoBomb = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapDmgLow,
                    EntityHelper.DamageType.MAGIC, "圣之爆焱");
            shootInfoStar = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapDmgMid,
                    EntityHelper.DamageType.MAGIC, "神圣新星");
        }
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