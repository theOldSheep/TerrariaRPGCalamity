package terraria.entity.boss.postMoonLord.yharon;

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
import terraria.entity.projectile.RotatingRingProjectile;
import terraria.entity.projectile.YharonTornado;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Yharon extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.YHARON_DRAGON_OF_REBIRTH;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 3744000 * 2, BASIC_HEALTH_BR = 1776000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    static String MSG_COLOR = "§#FFA500";
    static String PHASE_MSG = "你周围的空气变得灼热起来......";
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapFireball, attrMapFlareTornado;
    EntityHelper.ProjectileShootInfo shootInfoFireballRegular, shootInfoFireballHoming, shootInfoFlareTornado;

    static {
        attrMapFireball = new HashMap<>();
        attrMapFireball.put("damage", 1200d);
        attrMapFireball.put("knockback", 2.5d);
        attrMapFlareTornado = new HashMap<>();
        attrMapFlareTornado.put("damage", 0d);
        attrMapFlareTornado.put("knockback", 0d);
    }
    static final int PARTICLE_INTERVAL = 400;
    static final double HORIZONTAL_LIMIT = 64.0;

    int phase = 1;
    Vector velocity = new Vector(0, 0, 0);
    Location teleportTarget, spawnPosition;
    LivingEntity entity = (LivingEntity) getBukkitEntity();
    private int phaseTick = 0;
    private int phaseStep = 1;
    private int dashType;
    private int particleTicks = PARTICLE_INTERVAL;

    private void updatePhaseStep() {
        updatePhaseStep(this.phaseStep + 1);
    }
    private void updatePhaseStep(int nextStep) {
        this.phaseStep = nextStep;
        this.phaseTick = 0;
    }
    private void charge() {
        if (this.phaseTick % 30 == 1) {
            this.velocity = MathHelper.getDirection(entity.getLocation(), this.target.getLocation(), 2);
        }
    }

    private void chargeQuickly() {
        if (this.phaseTick % 20 == 1) {
            this.velocity = MathHelper.getDirection(entity.getLocation(), this.target.getLocation(), 3);
        }
    }
    private void randomCharge() {
        if (this.phaseTick == 1) {
            if (Math.random() < 0.5) {
                dashType = 1; // Quick dash
            } else {
                dashType = 0; // Normal dash
            }
        }
        if (dashType == 0) {
            this.charge();
            if (this.phaseTick > 30) {
                updatePhaseStep();
            }
        } else {
            this.chargeQuickly();
            if (this.phaseTick > 20) {
                updatePhaseStep();
            }
        }
    }
    private void flyLoop() {
        double angle = Math.toRadians(this.phaseTick * 18);
        this.velocity = new Vector(Math.cos(angle), 0, Math.sin(angle)).multiply(1.5);
    }
    private void fireball() {
        Location loc = entity.getEyeLocation();
        Vector dir = this.velocity.clone();
        MathHelper.setVectorLength(dir, 1.5);
        shootInfoFireballHoming.shootLoc = loc;
        shootInfoFireballHoming.velocity = dir;
        shootInfoFireballHoming.setLockedTarget(target);
        EntityHelper.spawnProjectile(shootInfoFireballHoming);
    }
    private void flyHorizontal() {
        if (this.phaseTick == 1) {
            Location loc = entity.getLocation();
            Vector dir = this.target.getLocation().toVector().subtract(loc.toVector());
            dir.setY(0);
            this.velocity = MathHelper.setVectorLength(dir,1.5);
        }
    }
    private void spawnTornado() {
        Location loc = entity.getEyeLocation();
        Vector dir = MathHelper.getDirection(entity.getLocation(), this.target.getLocation(), 1.5);
        shootInfoFlareTornado.shootLoc = loc;
        shootInfoFlareTornado.velocity = dir;
        shootInfoFlareTornado.setLockedTarget(target);
        new YharonTornado(shootInfoFlareTornado, this);
    }

    private boolean clockwise = true;

    public void summonRingOfProjectilesSphere(EntityHelper.ProjectileShootInfo shootInfo, int idx, int idxMax, boolean alternate) {
        summonRingOfProjectilesSphere(shootInfo, idx, idxMax, alternate, 32);
    }
    public void summonRingOfProjectilesSphere(EntityHelper.ProjectileShootInfo shootInfo, int idx, int idxMax, boolean alternate, int numProjectiles) {
        Location spawnLocation = ((LivingEntity) bukkitEntity).getEyeLocation();
        double initialAngle = Math.random() * 360;
        double angleChange = 0.4;
        double radiusMultiplier = 1.5;

        double initialRotationDegrees = (double) idx / idxMax * 180;

        RotatingRingProjectile.RingProperties ringProperties = new RotatingRingProjectile.RingProperties.Builder()
                .withCenterLocation(spawnLocation)
                .withRotationDirection(RotatingRingProjectile.RotationDirection.fromBoolean(clockwise))
                .withAngleChange(angleChange)
                .withInitialRotationDegrees(initialRotationDegrees)
                .withRadiusMultiplier(radiusMultiplier)
                .withPlayer(target)
                .build();

        RotatingRingProjectile.summonRingOfProjectiles(shootInfo, target, ringProperties, initialAngle, numProjectiles);

        if (alternate) {
            clockwise = !clockwise;
        }
    }



    public void phase1() {
        switch (this.phaseStep) {
            case 1:
            case 2:
            case 6:
            case 7:
                this.charge();
                if (this.phaseTick > 30) {
                    this.updatePhaseStep();
                }
                break;
            case 3:
                this.chargeQuickly();
                if (this.phaseTick > 20) {
                    this.updatePhaseStep();
                }
                break;
            case 4:
                if (this.phaseTick == 1) {
                    this.teleportTarget = this.target.getLocation().add(0, 32, 0);
                    this.velocity = MathHelper.getDirection(entity.getLocation(), this.teleportTarget, 2);
                }
                if (this.phaseTick > 10) {
                    if (this.phaseTick == 11) {
                        bukkitEntity.teleport(this.teleportTarget);
                        this.velocity.zero();
                    }
                    if (this.phaseTick % 10 == 0)
                        summonRingOfProjectilesSphere(shootInfoFireballRegular, 0, 1, false);
                }
                if (this.phaseTick > 60) {
                    this.updatePhaseStep();
                }
                break;
            case 5:
                if (this.phaseTick > 40) {
                    this.updatePhaseStep();
                }
                break;
            case 8:
                this.flyLoop();
                this.fireball();
                if (this.phaseTick > 40) {
                    this.updatePhaseStep();
                }
                break;
            case 9:
                if (this.phaseTick == 1) {
                    spawnTornado();
                    this.velocity.zero();
                }
                if (this.phaseTick > 20) {
                    this.updatePhaseStep(1);
                }
                break;
        }
    }
    public void phase2() {
        switch (this.phaseStep) {
            case 1:
            case 7:
                this.charge();
                if (this.phaseTick > 30) {
                    updatePhaseStep();
                }
                break;
            case 2:
            case 3:
            case 6:
                this.chargeQuickly();
                if (this.phaseTick > 20) {
                    updatePhaseStep();
                }
                break;
            case 4:
                this.flyLoop();
                this.fireball();
                if (this.phaseTick > 40) {
                    updatePhaseStep();
                }
                break;
            case 5:
                this.flyHorizontal();
                this.fireball();
                if (this.phaseTick > 20) {
                    updatePhaseStep();
                }
                break;
            case 8:
                if (this.phaseTick == 1) {
                    spawnTornado();
                    this.velocity.zero();
                }
                if (this.phaseTick > 20) {
                    updatePhaseStep();
                }
                break;
            case 9:
                if (this.phaseTick == 1) {
                    Location loc = target.getLocation().add(target.getLocation().getDirection().multiply(32));
                    bukkitEntity.teleport(loc);
                    this.velocity.zero();
                }
                if (this.phaseTick % 10 == 1)
                    summonRingOfProjectilesSphere(shootInfoFireballRegular, 0, 1, true, 24);
                if (this.phaseTick > 60) {
                    updatePhaseStep(1);
                }
                break;
        }
    }
    public void phase3() {
        switch (this.phaseStep) {
            case 1:
                if (this.phaseTick == 1) {
                    spawnTornado();
                    this.velocity.zero();
                }
                if (this.phaseTick > 15) {
                    updatePhaseStep();
                }
                break;
            case 2:
                if (this.phaseTick < 40) {
                    this.chargeQuickly();
                } else {
                    updatePhaseStep();
                }
                break;
            case 3:
                if (this.phaseTick < 20) {
                    this.flyHorizontal();
                    this.fireball();
                } else {
                    updatePhaseStep();
                }
                break;
            case 4:
                this.charge();
                if (this.phaseTick > 30) {
                    if (Math.random() < 0.5) {
                        updatePhaseStep();
                    } else {
                        updatePhaseStep(this.phaseStep + 2);
                    }
                }
                break;
            case 5:
                this.charge();
                if (this.phaseTick > 30) {
                    updatePhaseStep();
                }
                break;
            case 6:
                if (this.phaseTick < 40) {
                    this.chargeQuickly();
                } else {
                    this.velocity.zero();
                    updatePhaseStep();
                }
                break;
            case 7:
                if (this.phaseTick % 15 == 0) {
                    summonRingOfProjectilesSphere(shootInfoFireballRegular, phaseTick / 15, 2, true);
                }
                if (this.phaseTick > 60) {
                    updatePhaseStep();
                }
                break;
            case 8:
                if (this.phaseTick > 30) {
                    updatePhaseStep(1);
                }
                break;
        }
    }
    public void phase4() {
        switch (this.phaseStep) {
            case 1:
            case 2:
            case 3:
            case 5:
            case 6:
            case 7:
                randomCharge();
                break;
            case 4:
                this.flyHorizontal();
                this.fireball();
                if (this.phaseTick > 20) {
                    updatePhaseStep();
                }
                break;
            case 8:
                this.velocity = new Vector(0, 0, 0); // Ensure the entity does not move
                if (this.phaseTick % 12 == 0)
                    summonRingOfProjectilesSphere(shootInfoFireballRegular, phaseTick / 12, 5, false, 26);
                if (this.phaseTick > 60) {
                    updatePhaseStep();
                }
                break;
            case 9:
                if (this.phaseTick > 20) {
                    updatePhaseStep(1);
                }
                break;
        }
    }
    public void phase5() {
        switch (this.phaseStep) {
            case 1:
                if (this.phaseTick == 1) {
                    Location loc = this.target.getLocation();
                    Vector dir;
                    do {
                        dir = MathHelper.randomVector();
                    } while (MathHelper.getAngleRadian(loc.getDirection(), dir) > Math.toRadians(45));
                    dir.multiply(32);
                    loc.add(dir);
                    bukkitEntity.teleport(loc);
                    this.chargeQuickly();
                }
                if (this.phaseTick > 20) {
                    updatePhaseStep();
                }
                break;
            case 2:
            case 3:
                randomCharge();
                break;
            case 4:
                if (this.phaseTick == 1) {
                    Location loc = this.target.getLocation();
                    bukkitEntity.teleport(loc.add(loc.getDirection().multiply(32)));
                    this.velocity.zero();
                }
                if (this.phaseTick % 10 == 0)
                    summonRingOfProjectilesSphere(shootInfoFireballRegular, phaseTick / 10, 3, true, 20);
                if (this.phaseTick > 60) {
                    updatePhaseStep();
                }
                break;
            case 5:
                if (this.phaseTick > 40) {
                    updatePhaseStep(1);
                }
                break;
        }
    }
    private void updatePhase() {
        double healthPercentage = (this.getHealth() / this.getMaxHealth()) * 100;
        int newPhase = this.phase;
        if (healthPercentage < 80 && this.phase == 1) {
            newPhase = 2;
        } else if (healthPercentage < 55 && this.phase == 2) {
            newPhase = 3;
        } else if (healthPercentage < 35 && this.phase == 3) {
            newPhase = 4;
        } else if (healthPercentage < 16 && this.phase == 4) {
            newPhase = 5;
        }
        if (newPhase != this.phase) {
            this.phase = newPhase;
            this.phaseStep = 1;
            this.phaseTick = 0;

            Location loc = bukkitEntity.getLocation();
            loc.getWorld().playSound(loc, "entity.yharon.yharon_roar",
                    org.bukkit.SoundCategory.HOSTILE, 25f, 1f);
            if (newPhase == 3) {
                terraria.entity.boss.BossHelper.sendBossMessages(20, 0, entity,
                        MSG_COLOR, PHASE_MSG);
            }
        }
    }
    private void executePhase() {
        switch (this.phase) {
            case 1:
                this.phase1();
                break;
            case 2:
                this.phase2();
                break;
            case 3:
                this.phase3();
                break;
            case 4:
                this.phase4();
                break;
            case 5:
                this.phase5();
                break;
        }
        this.phaseTick++;
    }
    public void tick() {
        // Update velocity
        this.setPosition(this.locX + this.velocity.getX(), this.locY + this.velocity.getY(), this.locZ + this.velocity.getZ());

        // Update phase
        updatePhase();

        // AI details
        executePhase();

        // Check if the player has moved too far horizontally
        for (UUID uid : targetMap.keySet()) {
            Player ply = Bukkit.getPlayer(uid);
            if (ply == null)
                continue;
            if (isOutOfBoundary(ply)) {
                EntityHelper.applyEffect(ply, "龙焰", 200);
            }
        }
        // Fire particles
        if (++particleTicks > PARTICLE_INTERVAL) {
            visualizeBoundary();
            particleTicks = 0;
        }
    }
    private boolean isOutOfBoundary(Player ply) {
        Location targetHorizontalLocation = ply.getLocation();
        targetHorizontalLocation.setY(spawnPosition.getY());
        double horizontalDistance = spawnPosition.distanceSquared(targetHorizontalLocation);
        return horizontalDistance > HORIZONTAL_LIMIT * HORIZONTAL_LIMIT;
    }
    private void visualizeBoundary() {
        for (UUID uid : targetMap.keySet()) {
            Player ply = Bukkit.getPlayer(uid);
            if (ply == null)
                continue;
            DragoncoreHelper.displaySnowStormParticle(ply,
                    new DragoncoreHelper.DragonCoreParticleInfo("b/yrn", spawnPosition),
                    PARTICLE_INTERVAL);
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

                tick();
            }
        }
        // facing
        if (velocity.lengthSquared() > 1e-5)
            this.yaw = (float) MathHelper.getVectorYaw( velocity );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Yharon(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public Yharon(Player summonedPlayer) {
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
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 1248d);
            attrMap.put("damageTakenMulti", 0.75);
            attrMap.put("defence", 180d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(16, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
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
            shootInfoFireballRegular = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFireball,
                    DamageHelper.DamageType.MAGIC, "灼焱余烬");
            shootInfoFireballHoming = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFireball,
                    DamageHelper.DamageType.MAGIC, "追踪灼焱余烬");
            shootInfoFlareTornado = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFlareTornado,
                    DamageHelper.DamageType.MAGIC, "大型火焰龙卷");
        }
        // center of arena
        spawnPosition = target.getLocation();
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
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
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

    public Player getTarget() {
        return target;
    }
}