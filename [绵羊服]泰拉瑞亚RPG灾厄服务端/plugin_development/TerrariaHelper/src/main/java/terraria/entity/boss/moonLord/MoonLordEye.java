package terraria.entity.boss.moonLord;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
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
import java.util.HashSet;
import java.util.UUID;

public class MoonLordEye extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.MOON_LORD;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    enum EyeAttackMethod {
        PHANTASMAL_DEATH_RAY, PHANTASMAL_SPHERE, PHANTASMAL_EYE, PHANTASMAL_BOLT;
    }
    enum MoonLordEyeLocation {
        LEFT_HAND (57375 * 2,  80d, 5, -1, 0,  "月球领主手",
                MoonLordBackground.MoonLordBackgroundType.LEFT_HAND,
                new EyeAttackMethod[]{EyeAttackMethod.PHANTASMAL_EYE, EyeAttackMethod.PHANTASMAL_BOLT, EyeAttackMethod.PHANTASMAL_SPHERE}),
        RIGHT_HAND(57375 * 2,  80d, 5, -20, 120,"月球领主手",
                MoonLordBackground.MoonLordBackgroundType.RIGHT_HAND,
                new EyeAttackMethod[]{EyeAttackMethod.PHANTASMAL_EYE, EyeAttackMethod.PHANTASMAL_SPHERE, EyeAttackMethod.PHANTASMAL_BOLT}),
        HEAD      (103275 * 2, 100d,5, -50, 240, "月球领主"  ,
                MoonLordBackground.MoonLordBackgroundType.HEAD,
                new EyeAttackMethod[]{EyeAttackMethod.PHANTASMAL_BOLT, EyeAttackMethod.PHANTASMAL_DEATH_RAY, EyeAttackMethod.PHANTASMAL_BOLT});
        final double angleOffset, basicHealth, defence;
        final int eyeSize, initialIndexAI;
        final String eyeName;
        final EyeAttackMethod[] attackCycle;
        final MoonLordBackground.MoonLordBackgroundType backgroundType;
        MoonLordEyeLocation(double basicHealth, double defence, int eyeSize, int initialIndexAI, double angleOffset, String eyeName,
                                    MoonLordBackground.MoonLordBackgroundType backgroundType, EyeAttackMethod[] attackCycle) {
            this.basicHealth = basicHealth;
            this.defence = defence;
            this.eyeSize = eyeSize;
            this.initialIndexAI = initialIndexAI;
            this.angleOffset = angleOffset;
            this.eyeName = eyeName;
            this.backgroundType = backgroundType;
            this.attackCycle = attackCycle;
        }
    }
    static HashMap<String, Double> attrMapPhantasmalEye, attrMapDeathRay;
    static GenericHelper.StrikeLineOptions strikeOptionDeathRay;
    static {
        attrMapPhantasmalEye = new HashMap<>();
        attrMapPhantasmalEye.put("damage", 480d);
        attrMapPhantasmalEye.put("knockback", 1.5d);
        attrMapDeathRay = new HashMap<>();
        attrMapDeathRay.put("damage", 1140d);
        attrMapDeathRay.put("knockback", 1.5d);

        strikeOptionDeathRay = new GenericHelper.StrikeLineOptions()
                .setThruWall(true)
                .setParticleInfo(
                        new GenericHelper.ParticleLineOptions()
                                .setVanillaParticle(false)
                                .setTicksLinger(1)
                                .setParticleColor("100|255|255"));
    }

    int indexAI = -1, indexAnimation = 0, indexAttackMethod = -1;
    double deathRayYaw, deathRayPitch, deathRayPitchStep;
    boolean destroyed = false;
    MoonLordEyeLocation eyeLocation;
    MoonLordBackground background;
    MoonLord owner;
    EntityHelper.ProjectileShootInfo shootInfoPhantasmalEye;
    EyeAttackMethod attackMethod = EyeAttackMethod.PHANTASMAL_SPHERE;
    ArrayList<MoonLordPhantasmalSphere> allSpheres;
    HashSet<Entity> deathRayDamageCD = new HashSet<>();


    // returns true if the animation is finished
    private boolean openEyeAnimation() {
        if (! destroyed)
            removeScoreboardTag("noDamage");
        if (indexAnimation >= 15) {
            setCustomName(eyeLocation.eyeName);
            return true;
        }

        // 0~4, 5~9, 10~14
        int customNameIndex = 3 - (indexAnimation / 5);
        setCustomName(eyeLocation.eyeName + "§" + customNameIndex);
        indexAnimation ++;

        return false;
    }
    // returns true if the animation is finished
    private boolean closeEyeAnimation() {
        addScoreboardTag("noDamage");
        if (indexAnimation <= 0) {
            setCustomName(eyeLocation.eyeName + "§4");
            return true;
        }

        indexAnimation --;
        // 14~10, 9~5, 4~0
        int customNameIndex = 3 - (indexAnimation / 5);
        setCustomName(eyeLocation.eyeName + "§" + customNameIndex);

        return false;
    }
    private void attackSphere() {
        // open eye and init all spheres
        if (indexAI == 0) {
            if (openEyeAnimation())
                allSpheres = new ArrayList<>();
            else
                indexAI --;
        }
        // spawn sphere
        switch (indexAI) {
            case 10:
            case 20:
            case 30:
            case 40:
            case 50:
            case 60:
            case 70:
            case 80:
            case 90:
            case 100:
            case 110:
            case 120:
                MoonLordPhantasmalSphere sphere = new MoonLordPhantasmalSphere(target, getBukkitEntity().getLocation(), allSpheres);
                sphere.setVelocity(new Vector(0, 0.25, 0));
                allSpheres.add(sphere);
                break;
            case 150:
                MoonLordPhantasmalSphere center = allSpheres.get(allSpheres.size() / 2);
                Location aimLoc = EntityHelper.helperAimEntity(center.getBukkitEntity(), target, MoonLordPhantasmalSphere.aimHelper);
                Vector velocity = aimLoc.subtract( ((LivingEntity) center.getBukkitEntity()).getEyeLocation() ).toVector();
                velocity.multiply(1d / 15d);
                for (MoonLordPhantasmalSphere currSphere : allSpheres) {
                    currSphere.setVelocity(velocity);
                    currSphere.ticksRemaining = 75;
                }
        }
        // reach changes
        switch (eyeLocation) {
            case LEFT_HAND:
                if (indexAI <= 120)
                    owner.reachLeft += 0.2;
                else if (owner.reachLeft > 15)
                    owner.reachLeft -= 0.5;
                break;
            case RIGHT_HAND:
                if (indexAI <= 120)
                    owner.reachRight += 0.2;
                else if (owner.reachRight > 15)
                    owner.reachRight -= 0.5;
                break;
        }
        // close eye
        if (indexAI >= 185) {
            // next attack
            if (closeEyeAnimation())
                indexAI = -20;
        }
    }
    private void attackBolt() {
        // open eye
        if (indexAI == 0) {
            if (! openEyeAnimation())
                indexAI --;
        }
        // spawn bolt
        switch (indexAI) {
            case 25:
            case 32:
                new MoonLordPhantasmalBolt(target, ((LivingEntity) bukkitEntity).getEyeLocation());
        }
        // close eye
        if (indexAI >= 40) {
            // next attack
            if (closeEyeAnimation())
                indexAI = -20;
        }
    }
    private void attackEye() {
        // open eye
        if (indexAI == 0) {
            if (! openEyeAnimation())
                indexAI --;
        }
        // close eye
        if (indexAI >= 40) {
            // next attack
            if (closeEyeAnimation())
                indexAI = -20;
        }
        // spawn bolt
        else if (indexAI % 4 == 0) {
            shootInfoPhantasmalEye.setLockedTarget(target);
            shootInfoPhantasmalEye.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            shootInfoPhantasmalEye.velocity = MathHelper.randomVector();
            EntityHelper.spawnProjectile(shootInfoPhantasmalEye);
        }
    }
    private void attackDeathRay() {
        // open eye
        if (indexAI == 0) {
            if (! openEyeAnimation())
                indexAI --;
        }
        // close eye
        if (indexAI >= 120) {
            // next attack
            if (closeEyeAnimation())
                indexAI = -20;
        }
        // death ray
        else if (indexAI >= 10 && indexAI <= 110) {
            // init
            if (indexAI == 10) {
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
                    deathRayYaw, deathRayPitch, 96, 1, "", "",
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
            if (indexAI >= 0) {
                // get next attack method
                if (indexAI == 0) {
                    indexAttackMethod ++;
                    attackMethod = eyeLocation.attackCycle[indexAttackMethod % eyeLocation.attackCycle.length];
                }
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
            // handle destruction
            if (! destroyed && getHealth() < 10d) {
                destroyed = true;
                addScoreboardTag("noDamage");
                new MoonLordTrueEyeOfCthulhu(target, owner, bukkitEntity.getLocation(), eyeLocation.angleOffset);
            }

            indexAI++;
        }
        // no collision dmg
    }
    // default constructor to handle chunk unload
    public MoonLordEye(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public MoonLordEye(Player summonedPlayer, MoonLord owner, MoonLordEyeLocation eyeLocation) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.eyeLocation = eyeLocation;
        indexAI = eyeLocation.initialIndexAI;
        setCustomName(eyeLocation.eyeName + "§4");
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
            attrMap.put("defence", eyeLocation.defence);
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
            setSize(eyeLocation.eyeSize, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = eyeLocation.basicHealth * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
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
        // init background
        background = new MoonLordBackground(target, owner, eyeLocation.backgroundType);
    }

    // rewrite AI
    @Override
    public void die() {
        super.die();
        // remove the background
        background.die();
    }
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
        // update facing direction
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
    }
}
