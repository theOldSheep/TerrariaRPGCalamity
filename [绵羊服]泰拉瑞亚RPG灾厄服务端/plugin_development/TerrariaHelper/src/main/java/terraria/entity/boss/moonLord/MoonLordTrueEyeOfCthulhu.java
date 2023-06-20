package terraria.entity.boss.moonLord;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
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

public class MoonLordTrueEyeOfCthulhu extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.MOON_LORD;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    enum EyeAttackMethod {
        PHANTASMAL_DEATH_RAY, PHANTASMAL_SPHERE, PHANTASMAL_EYE, PHANTASMAL_BOLT;
    }
    static EyeAttackMethod[] attackMethodCycle = new EyeAttackMethod[] {
            EyeAttackMethod.PHANTASMAL_SPHERE,
            EyeAttackMethod.PHANTASMAL_BOLT,
            EyeAttackMethod.PHANTASMAL_EYE,
            EyeAttackMethod.PHANTASMAL_DEATH_RAY,
    };
    static EyeAttackMethod attackMethod = attackMethodCycle[0];
    static int indexAttackMethod = -1;
    static HashMap<String, Double> attrMapPhantasmalEye, attrMapDeathRay;
    static GenericHelper.StrikeLineOptions strikeOptionDeathRay;
    static {
        attrMapPhantasmalEye = new HashMap<>();
        attrMapPhantasmalEye.put("damage", 420d);
        attrMapPhantasmalEye.put("knockback", 1.5d);
        attrMapDeathRay = new HashMap<>();
        attrMapDeathRay.put("damage", 750d);
        attrMapDeathRay.put("knockback", 1.5d);

        strikeOptionDeathRay = new GenericHelper.StrikeLineOptions()
                .setThruWall(true)
                .setParticleInfo(
                        new GenericHelper.ParticleLineOptions()
                                .setTicksLinger(1)
                                .setParticleColor("100|255|255"));
    }

    double deathRayYaw, deathRayPitch, deathRayPitchStep, angleOffset;
    boolean startedAttack = false;
    MoonLord owner;
    EntityHelper.ProjectileShootInfo shootInfoPhantasmalEye;
    ArrayList<MoonLordPhantasmalSphere> allSpheres;
    ArrayList<Entity> deathRayDamageCD = new ArrayList<>();


    protected static void initializeAttackPattern() {
        attackMethod = attackMethodCycle[0];
        indexAttackMethod = -1;
    }
    protected static void nextAttackPattern() {
        indexAttackMethod = (indexAttackMethod + 1) % attackMethodCycle.length;
        attackMethod = attackMethodCycle[indexAttackMethod];
    }
    private void teleportToLocation() {
        Vector offset = MathHelper.vectorFromYawPitch_quick(angleOffset + (owner.ticksLived * 0.5), 0);
        offset.multiply(24);
        offset.setY(8);
        Location teleportLoc = target.getLocation().add(offset);
        bukkitEntity.teleport(teleportLoc);
    }
    private void attackSphere() {
        // open eye and init all spheres
        if (owner.trueEyeIndexAI == 0) {
            allSpheres = new ArrayList<>();
        }
        // spawn sphere
        switch (owner.trueEyeIndexAI) {
            case 20:
            case 25:
            case 30:
            case 35:
            case 40:
            case 45:
                Location spawnLoc = getBukkitEntity().getLocation();
                switch (owner.trueEyeIndexAI) {
                    case 20:
                        spawnLoc.add(5, 0, 0);
                        break;
                    case 25:
                        spawnLoc.add(0, 5, 0);
                        break;
                    case 30:
                        spawnLoc.add(0, 0, 5);
                        break;
                    case 35:
                        spawnLoc.add(-5, 0, 0);
                        break;
                    case 40:
                        spawnLoc.add(0, -5, 0);
                        break;
                    case 45:
                        spawnLoc.add(0, 0, -5);
                        break;
                }
                MoonLordPhantasmalSphere sphere = new MoonLordPhantasmalSphere(target, spawnLoc, allSpheres);
                sphere.setVelocity(new Vector());
                allSpheres.add(sphere);
                break;
            case 70:
                Location aimLoc = EntityHelper.helperAimEntity(getBukkitEntity(), target, MoonLordPhantasmalSphere.aimHelper);
                Vector velocity = aimLoc.subtract( ((LivingEntity) getBukkitEntity()).getEyeLocation() ).toVector();
                velocity.multiply(1d / 15d);
                for (MoonLordPhantasmalSphere currSphere : allSpheres) {
                    currSphere.setVelocity(velocity);
                    currSphere.ticksRemaining = 75;
                }
                bukkitEntity.setVelocity(velocity);
                break;
        }
        // next attack
        if (owner.trueEyeIndexAI == 90)
            bukkitEntity.setVelocity(new Vector());
        else if (owner.trueEyeIndexAI >= 95) {
            owner.trueEyeIndexAI = -20;
            nextAttackPattern();
        }
    }
    private void attackBolt() {
        // spawn bolt
        switch (owner.trueEyeIndexAI) {
            case 25:
            case 32:
                new MoonLordPhantasmalBolt(target, ((LivingEntity) bukkitEntity).getEyeLocation());
        }
        // next attack
        if (owner.trueEyeIndexAI >= 40) {
            owner.trueEyeIndexAI = -20;
            nextAttackPattern();
        }
    }
    private void attackEye() {
        // next attack
        if (owner.trueEyeIndexAI >= 40) {
            owner.trueEyeIndexAI = -20;
            nextAttackPattern();
        }
        // spawn bolt
        else if (owner.trueEyeIndexAI % 6 == 0) {
            shootInfoPhantasmalEye.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            shootInfoPhantasmalEye.velocity = MathHelper.randomVector();
            EntityHelper.spawnProjectile(shootInfoPhantasmalEye);
        }
    }
    private void attackDeathRay() {
        // next attack
        if (owner.trueEyeIndexAI >= 120) {
            owner.trueEyeIndexAI = -20;
            nextAttackPattern();
        }
        // death ray
        else if (owner.trueEyeIndexAI >= 10 && owner.trueEyeIndexAI <= 110) {
            // init
            if (owner.trueEyeIndexAI == 10) {
                Vector targetDir = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                deathRayYaw = MathHelper.getVectorYaw(targetDir);
                deathRayPitch = MathHelper.getVectorPitch(targetDir);
                if (deathRayPitch < 0) {
                    deathRayPitch = Math.max(deathRayPitch - 40, -90);
                    deathRayPitchStep = 1.25;
                }
                else {
                    deathRayPitch = Math.min(deathRayPitch + 40, 90);
                    deathRayPitchStep = -1.25;
                }
            }
            // ticking
            Vector targetDir = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
            double[] directionInterpolation = GenericHelper.getDirectionInterpolateOffset(
                    deathRayYaw, deathRayPitch, MathHelper.getVectorYaw(targetDir), deathRayPitch, 0.1);
            deathRayYaw += directionInterpolation[0];
            deathRayPitch += deathRayPitchStep;
            GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                    deathRayYaw, deathRayPitch, 64, 0.75, "", "",
                    deathRayDamageCD, attrMapDeathRay, strikeOptionDeathRay);
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // remove when owner is defeated
            if (! owner.isAlive()) {
                die();
                return;
            }
            // update target
            target = owner.target;
            // if target is valid, attack
            if (owner.trueEyeIndexAI >= 0) {
                // get next attack method
                if (owner.trueEyeIndexAI == 0) {
                    startedAttack = true;
                    teleportToLocation();
                }
                if (startedAttack)
                    switch (attackMethod) {
                        case PHANTASMAL_SPHERE:
                            attackSphere();
                            break;
                        case PHANTASMAL_BOLT:
                            attackBolt();
                            break;
                        case PHANTASMAL_EYE:
                            attackEye();
                            break;
                        case PHANTASMAL_DEATH_RAY:
                            attackDeathRay();
                            break;
                    }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // no collision dmg
    }
    // default constructor to handle chunk unload
    public MoonLordTrueEyeOfCthulhu(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public MoonLordTrueEyeOfCthulhu(Player summonedPlayer, MoonLord owner, Location spawnLoc, double angleOffset) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.angleOffset = angleOffset;
        owner.trueEyeSpawned ++;
        setCustomName("克苏鲁真眼");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("noDamage");
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, 1);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 1d);
            attrMap.put("damageTakenMulti", 0.95);
            attrMap.put("defence", 0d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = owner.targetMap;
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(5, false);
        }
        // boss parts and other properties
        {
            bossParts = owner.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // init shoot info
        {
            shootInfoPhantasmalEye = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPhantasmalEye,
                    EntityHelper.DamageType.MAGIC, "幻影眼");
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
