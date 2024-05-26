package terraria.entity.boss.postMoonLord.ceaselessVoid;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
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
import java.util.List;
import java.util.UUID;

public class CeaselessVoid extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CEASELESS_VOID;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.DUNGEON;
    public static final double BASIC_HEALTH = 187200 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapProjectile;
    EntityHelper.ProjectileShootInfo shootInfoProjectile;
    static {
        attrMapProjectile = new HashMap<>();
        attrMapProjectile.put("damage", 792d);
        attrMapProjectile.put("knockback", 1.5d);
    }
    int indexAI = 0;
    Vector velocity = new Vector();
    static final double[] TRANSITION_HEALTH_RATIO = {0.8, 0.6, 0.4, -1}; // Health % for phase transitions
    static final int[] PHASE_ARC_AMOUNTS = {6, 7, 8, 9}; // Number of arcs per phase, spawned at once
    static final double[][] ARC_PROPERTY_SETS = {
            {0.02, 0.8},    // Set 1: Low acceleration, medium max speed
            {0.03, 1.0},    // Set 2: Medium acceleration, medium max speed
            {0.05, 1.2},    // Set 3: High acceleration, high max speed
    };
    static final double BOSS_MOVEMENT_SPEED = 0.5; // Speed of movement towards the player
    static final double BOSS_DECELERATION = 0.95;  // Deceleration rate in projectile phase

    static final double PROJECTILE_INTERVAL_DEGREES = 10, PROJECTILE_SPREAD_DEGREES = 35, PROJECTILE_SPEED = 1.75;
    static final double SUCTION_FORCE_DISTANCE_FACTOR_MIN = 0.000075, SUCTION_FORCE_DISTANCE_FACTOR_MAX = 0.000125;


    // Phase tracking
    int currentPhase = 0;
    boolean isArcPhase = true; // Start with arc phase

    // The data structure used to organize independent arcs
    static class DarkEnergyArc {
        Vector rotationAxis = MathHelper.randomVector(); // Random initial rotation axis
        double rotationAngle; // Angle around the rotation axis
        double rotationSpeed = 1d / 20d; // Speed at which the arc rotates around its axis


        double radiusSpeed = 0; // Initialized as 0, modified as time elapses
        double maxRadiusSpeed; // Maximum radius change
        double radiusAcceleration; // Adjust for acceleration speed
        double angleSeparation;
        double angleStart = Math.random() * Math.PI * 2, radius = 0d;

        CeaselessVoid owner;
        List<DarkEnergy> darkEnergies = new ArrayList<>();

        DarkEnergyArc(CeaselessVoid owner, int numEntities, int propertySetIndex) {
            this.owner = owner;
            this.angleSeparation = Math.PI * 2 / numEntities;

            // Use pre-defined properties
            this.radiusAcceleration = ARC_PROPERTY_SETS[propertySetIndex][0];
            this.maxRadiusSpeed = ARC_PROPERTY_SETS[propertySetIndex][1];
            this.rotationAngle = Math.random() * Math.PI * 2;

            for (int i = 0; i < numEntities; i++) {
                DarkEnergy darkEnergy = new DarkEnergy(owner);
                darkEnergy.getBukkitEntity().teleport(owner.getBukkitEntity().getLocation());
                darkEnergies.add(darkEnergy);
            }
        }

        void tick() {

            Location playerLocation = owner.target.getLocation();
            Location centerLocation = owner.getBukkitEntity().getLocation();

            // Update arc's angle (rotation)
            angleStart += rotationSpeed;

            // Update arc's radius, accelerating towards the player
            double distanceToPlayer = centerLocation.distance(playerLocation);
            radius = Math.max(radius + radiusSpeed, 0);
            if (distanceToPlayer > radius) {
                radiusSpeed = Math.min(radiusSpeed + radiusAcceleration, maxRadiusSpeed);
            }
            else {
                radiusSpeed = Math.max(radiusSpeed - radiusAcceleration, -maxRadiusSpeed);
            }

            // Update all dark energies' position based on the updated arc properties
            double currAngle = angleStart;
            for (DarkEnergy darkEnergy : darkEnergies) {
                if (darkEnergy.isAlive()) {
                    darkEnergy.updateArcPosition(centerLocation, currAngle, radius,
                            rotationAxis, rotationAngle + owner.indexAI / 100d);
                }
                currAngle += angleSeparation;
            }
        }
    }
    List<DarkEnergyArc> darkEnergyArcs = new ArrayList<>(); // List to hold arcs

    private void attackArcPhase() {
        // Spawn arcs only on phase start or boss spawn
        if (indexAI == 0) {
            spawnArcs(PHASE_ARC_AMOUNTS[currentPhase]); // Spawn the configured number of arcs
            getBukkitEntity().getScoreboardTags().add("noDamage"); // Make the boss invulnerable
        }
        // Remove invulnerability if 80% dark energies are defeated
        if (darkEnergyArcs.stream()
                .flatMap(arc -> arc.darkEnergies.stream())
                .filter((e) -> !e.isAlive() ).count()
                >= 0.75 * darkEnergyArcs.stream()
                .mapToInt(arc -> arc.darkEnergies.size()).sum())
        {
            getBukkitEntity().getScoreboardTags().remove("noDamage");
            isArcPhase = false;
            indexAI = -1; // This is immediately incremented after the function
            // Add metadata lock to prevent instant killing by stacking damage
            if (currentPhase < TRANSITION_HEALTH_RATIO.length) {
                EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT,
                        getMaxHealth() * PHASE_ARC_AMOUNTS[currentPhase] - 1);
            }
        }
        // Update each arc's position
        for (DarkEnergyArc arc : darkEnergyArcs) {
            arc.tick();
        }
        // Move towards player
        velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), BOSS_MOVEMENT_SPEED);
    }

    // Arc spawning logic
    private void spawnArcs(int numArcs) {
        // Clear existing arcs and spawn new ones
        darkEnergyArcs.clear();
        int numEntitiesPerArc = 4;
        for (int i = 0; i < numArcs; i++) {
            int propertySetIndex = i % ARC_PROPERTY_SETS.length; // Cycle through property sets
            darkEnergyArcs.add(new DarkEnergyArc(this, numEntitiesPerArc, propertySetIndex));
        }
    }

    // Stub function for the projectile attack phase
    private void attackProjectilePhase() {
        // Phase transition
        double healthRatio = getHealth() / getMaxHealth();
        if (healthRatio < TRANSITION_HEALTH_RATIO[currentPhase]) {
            currentPhase ++;
            isArcPhase = true;
            indexAI = -1; // This is immediately incremented after the function
            // Kill remaining dark energies
            for (DarkEnergyArc arc : darkEnergyArcs) {
                arc.darkEnergies.forEach(DarkEnergy::die); // Kill each DarkEnergy in each arc
            }
            darkEnergyArcs.clear(); // Remove arcs for cleanliness
        }
        // Decelerate gradually
        velocity.multiply(BOSS_DECELERATION);
        // Suction!
        for (UUID uid : targetMap.keySet()) {
            Player ply = Bukkit.getPlayer(uid);
            double distSqr = bukkitEntity.getLocation().distanceSquared(ply.getLocation());
            double suctionForce = (SUCTION_FORCE_DISTANCE_FACTOR_MAX +
                    (SUCTION_FORCE_DISTANCE_FACTOR_MIN - SUCTION_FORCE_DISTANCE_FACTOR_MAX) * healthRatio) * distSqr;
            Vector suctionDirection = MathHelper.getDirection(ply.getLocation(), bukkitEntity.getLocation(), suctionForce);
            EntityHelper.knockback(ply, suctionDirection, true, -1, true); // Apply suction
        }
        // Projectile attack logic
        if (indexAI % 20 == 0) { // Shoot every 20 ticks
            // Calculate projectile directions using helper function
            ArrayList<Vector> projectileDirections = MathHelper.getEvenlySpacedProjectileDirections(
                    PROJECTILE_INTERVAL_DEGREES, PROJECTILE_SPREAD_DEGREES, target, bukkitEntity.getLocation(), PROJECTILE_SPEED
            );

            // Shoot projectiles in calculated directions
            shootInfoProjectile.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            shootInfoProjectile.setLockedTarget(target); // Optional: make projectiles track target
            for (Vector dir : projectileDirections) {
                shootInfoProjectile.velocity = dir;
                EntityHelper.spawnProjectile(shootInfoProjectile);
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

                if (isArcPhase) {
                    attackArcPhase();
                } else {
                    attackProjectilePhase();
                }

                indexAI ++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public CeaselessVoid(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public CeaselessVoid(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = - ((CraftPlayer) summonedPlayer).getHandle().yaw, dist = 24;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
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
            attrMap.put("damage", 1440d);
            attrMap.put("damageTakenMulti", 0.5);
            attrMap.put("defence", 0d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
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
            setSize(16, false);
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
            shootInfoProjectile = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapProjectile,
                    EntityHelper.DamageType.MAGIC, "暗能量球");
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