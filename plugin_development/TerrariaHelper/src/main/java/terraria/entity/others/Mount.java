package terraria.entity.others;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.Setting;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/*
 * Note: do not use Player.dismount()
 * Use the method provided in PlayerHelper.
 * The player will be tricked into thinking it has dismounted (while the mount still has player as a passenger)
 * This is to prevent dismount when pressing shift/in water
 * Killing this mount can also release the player.
 * Be careful when teleporting the player: it will also force dismount
 */
public class Mount extends EntitySlime {
    public static final double DEFAULT_GRAVITY = 0.05, DEFAULT_VEL_IDLE_DECAY_MULTI = 0.95;
    protected double contactDmg = 0, entityHalfWidth = 0.25, entityHalfHeight = 0.25,
            horSpdMax = 0.5, verSpdMax = 1, horAcc = 0.1, verAcc = 0.2, gravityAcc = DEFAULT_GRAVITY, stepHeight = 1.01,
            speedMultiWater = 0.5, speedMultiGround = 0.75, velIdleDecayMulti = DEFAULT_VEL_IDLE_DECAY_MULTI;
    protected int flightIndex = 0, flightDuration = 0, slimeSize = 1;
    protected boolean hasGravity = true, isInfFlight = false;
    protected String mountType;

    protected HashMap<String, Double> attrMap;
    HashSet<org.bukkit.entity.Entity> damageCD = new HashSet<>();
    protected Player owner = null;
    protected EntityPlayer ownerNMS = null;

    public static HashMap<UUID, Mount> MOUNTS_MAP = new HashMap<>();

    // variables from Entity.class
    protected final double[] move_aJ = new double[]{0.0, 0.0, 0.0};
    protected float move_ay = 1.0F;

    // synchronize attribute, such as knockback resistance, from owner
    protected void synchronizeAttribute() {
        attrMap.put("knockbackResistance", AttributeHelper.getAttrMap(owner).getOrDefault("knockbackResistance", 0d));
    }
    // default constructor accounting for default behaviour (removal)
    public Mount(World world) {
        super(world);
        super.die();
    }
    public Mount(Player owner, ConfigurationSection mountSection) {
        super(((CraftPlayer) owner).getHandle().getWorld());
        // init position
        Location loc = owner.getLocation();
        setLocation(loc.getX(), loc.getY() + 0.01, loc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // init variables
        this.owner = owner;
        this.ownerNMS = ((CraftPlayer) owner).getHandle();

        flightDuration = mountSection.getInt("flightDuration", flightDuration);
        slimeSize = mountSection.getInt("slimeSize", slimeSize);

        contactDmg = mountSection.getDouble("damage", contactDmg);
        entityHalfWidth = mountSection.getDouble("entityHalfWidth", entityHalfWidth * slimeSize);
        entityHalfHeight = mountSection.getDouble("entityHalfHeight", entityHalfHeight * slimeSize);
        horSpdMax = mountSection.getDouble("horSpdMax", horSpdMax);
        verSpdMax = mountSection.getDouble("verSpdMax", verSpdMax);
        horAcc = mountSection.getDouble("horAcc", horAcc);
        verAcc = mountSection.getDouble("verAcc", verAcc);
        gravityAcc = mountSection.getDouble("gravityAcc", gravityAcc);
        stepHeight = mountSection.getDouble("stepHeight", stepHeight);
        speedMultiWater = mountSection.getDouble("speedMultiWater", speedMultiWater);
        speedMultiGround = mountSection.getDouble("speedMultiGround", speedMultiGround);
        velIdleDecayMulti = mountSection.getDouble("velIdleDecayMulti", velIdleDecayMulti);
        // for non-inf flight, the player needs to reset the flight time (hit the ground)
        isInfFlight = flightDuration > 10000;
        flightIndex = isInfFlight ? 0 : flightDuration;

        mountType = mountSection.getString("type", "坐骑");

        hasGravity = mountSection.getBoolean("gravity", true);
        // no goal selector / target selector
        this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        this.targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute
        attrMap = new HashMap<>(5);
        attrMap.put("damage", contactDmg);
        attrMap.put("knockback", mountSection.getDouble("knockback", 2d));
        synchronizeAttribute();
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        // init properties
        setNoGravity(! hasGravity);
        setSize(slimeSize, false);
        addScoreboardTag("isMount");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_SOURCE, owner);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, owner);
        setCustomName(mountType);
        setCustomNameVisible(false);
        getAttributeInstance(GenericAttributes.maxHealth).setValue(444);
        setHealth(444f);
        initBoundingBox();
//        bukkitEntity.setVelocity(owner.getVelocity());
        // step height
        super.P = (float) this.stepHeight;
        // disguise (not working?)
        // this seems to create some lag and glitch
//        if (mountType.equals("独角兽坐骑")) {
//            MobDisguise disguise = new MobDisguise(DisguiseType.SKELETON_HORSE);
//
//            disguise.setReplaceSounds(true);
//            disguise.setEntity(bukkitEntity);
//            DisguiseAPI.disguiseEntity(bukkitEntity, disguise);
//        }
        // make player mount
        bukkitEntity.addPassenger(owner);
        // prevent later glitch; the internal representation is left as single-sided.
        owner.leaveVehicle();
        MOUNTS_MAP.put(owner.getUniqueId(), this);
        // prevent unexpected dismount
        owner.setVelocity(new Vector());
    }

    protected void initBoundingBox() {
        a(new AxisAlignedBB(
                locX + entityHalfWidth, locY + entityHalfHeight * 2, locZ + entityHalfWidth,
                locX - entityHalfWidth, locY, locZ - entityHalfWidth) );
    }

    protected void updatePlyLoc() {
        ownerNMS.locX = this.locX;
        ownerNMS.locY = this.locY;
        ownerNMS.locZ = this.locZ;
        ownerNMS.motX = this.motX;
        ownerNMS.motY = this.motY;
        ownerNMS.motZ = this.motZ;
    }
    protected void movementTicking() {
        // undo the horizontal resistance done
        Vector prevVel = bukkitEntity.getVelocity();
        super.B_();
        Vector aftVel = bukkitEntity.getVelocity();

        double yComp = aftVel.getY();
        aftVel.setY(0);

        double aVLS = aftVel.lengthSquared();
        if (aVLS > 1e-9) {
            Vector projected = MathHelper.vectorProjection(aftVel, prevVel);
            double factor = Math.sqrt(projected.lengthSquared() / aVLS);

            aftVel.multiply(factor);
            aftVel.setY(yComp);
            bukkitEntity.setVelocity(aftVel);
        }
        // update player location
        updatePlyLoc();
    }

    // override dismount-related function
    @Override
    public void die() {
        MOUNTS_MAP.remove(owner.getUniqueId());
        super.die();
    }

    @Override
    protected void p(Entity entity) {
        boolean dismountCancelled = PlayerHelper.isProperlyPlaying(owner) && this.isAlive();
        if (dismountCancelled) {
            // DO NOT USE addPassenger. This will create a terrible amount of twitching.
            return;
        }
        MOUNTS_MAP.remove(owner.getUniqueId());
        super.p(entity);
    }
    @Override
    public void B_() {
        // remove when dismounted for some reason OR the owner is not properly playing
        boolean shouldRemove = ! (
                    bukkitEntity.getPassengers().contains(owner) &&
                    MOUNTS_MAP.get(owner.getUniqueId()) == this &&
                    PlayerHelper.isProperlyPlaying(owner));
        if (shouldRemove) {
            die();
            return;
        }

        // basic tick
        synchronizeAttribute();
        movementTicking();

        // handle contact damage
        handleCollisionDamage();
        // movement
        Set<String> playerKeyPressed = PlayerHelper.getPlayerKeyPressed(owner);
        Set<String> ownerTags = owner.getScoreboardTags();
        double horSpdLmt = horSpdMax;
        double verSpdLmt = verSpdMax;
        boolean isOnGround = bukkitEntity.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid();
        if (isInWater()) {
            horSpdLmt *= speedMultiWater;
            verSpdLmt *= speedMultiWater;
        }
        else if (isOnGround) {
            horSpdLmt *= speedMultiGround;
        }
        // slow down
        if (mountType.equals("星流飞椅") &&
                playerKeyPressed.contains( Setting.getOptionString(owner, Setting.Options.CONTROL_INSIGNIA) )) {
            horSpdLmt *= 0.35;
            verSpdLmt *= 0.35;
        }
        // horizontal movement
        Vector finalHorComp = new Vector(motX, 0, motZ);
        {
            double plyHorMoveDir = PlayerHelper.getPlayerMoveYaw(owner);
            if (plyHorMoveDir < 1e5) {
                Vector horMoveDir = MathHelper.vectorFromYawPitch_approx(plyHorMoveDir, 0);
                horMoveDir.multiply(horAcc);
                finalHorComp.add(horMoveDir);
            }
            else {
                finalHorComp.multiply((isOnGround && hasGravity) ? Math.pow(velIdleDecayMulti, 5) : velIdleDecayMulti);
            }
            MathHelper.setVectorLength(finalHorComp, horSpdLmt, true);
        }
        // vertical movement
        double verticalVel = motY;
        {
            // reset flight duration
            if (isOnGround || super.onGround)
                flightIndex = 0;
            // flight handling
            boolean canFly = flightIndex < flightDuration || isOnGround;
            if (ownerTags.contains("temp_thrusting") && canFly) {
                verticalVel += verAcc;
                verticalVel = Math.min(verticalVel, verSpdLmt);
                flightIndex ++;
            }
            else if (playerKeyPressed.contains( Setting.getOptionString(owner, Setting.Options.CONTROL_MOUNT_DESCEND) )
                    && isInfFlight && canFly) {
                verticalVel -= verAcc;
                verticalVel = Math.max(verticalVel, -verSpdLmt);
                flightIndex ++;
            }
            // other mechanics
            else {
                // vertical velocity decay
                verticalVel *= isInfFlight ? velIdleDecayMulti : DEFAULT_VEL_IDLE_DECAY_MULTI;

                boolean shouldHandleGravity = hasGravity;
                switch (mountType) {
                    // slimes float when on water
                    case "史莱姆坐骑":
                    case "羽翼史莱姆坐骑": {
                        if (isInWater()) {
                            verticalVel += 0.1;
                            verticalVel = Math.min(verticalVel, 1);
                            shouldHandleGravity = false;
                        }
                        break;
                    }
                }
                // gravity if applicable
                if (shouldHandleGravity) {
                    // if entity is moving upward, slime quick falling will not work
                    verticalVel -= verticalVel > 0 ? DEFAULT_GRAVITY : gravityAcc;
                    verticalVel = Math.max(verticalVel, gravityAcc * -30);
                }
                // otherwise, reset fall distance
                else
                    fallDistance = 0f;
            }
        }
        // combine speed
        finalHorComp.setY(verticalVel);
        bukkitEntity.setVelocity(finalHorComp);
        // update facing dir
        yaw = ownerNMS.yaw;
        // update owner in-world and internal velocity
        EntityMovementHelper.setVelocity(owner, bukkitEntity.getVelocity());
    }
    public void handleCollisionDamage() {
        if (contactDmg <= 0d)
            return;
        // tweak damage based on mount type
        double currDmg = contactDmg;
        switch (mountType) {
            case "史莱姆坐骑":
            case "羽翼史莱姆坐骑":
                // only deal damage when sitting onto enemy
                if (motY > -gravityAcc)
                    return;
                break;
            case "独角兽坐骑":
                // damage proportional to speed
                Vector temp = bukkitEntity.getVelocity();
                temp.setY(0);
                double dmgMulti = temp.length() / horSpdMax;
                if (dmgMulti < 0.2)
                    return;
                currDmg *= dmgMulti;
                break;
        }
        // collision damage also includes the player's height
        Vector initLoc = new Vector(locX, locY + entityHalfHeight, locZ);
        Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(bukkitEntity.getWorld(),
                initLoc, initLoc.clone().add(bukkitEntity.getVelocity()),
                entityHalfWidth, entityHalfHeight, entityHalfWidth,
                (Entity entity) -> DamageHelper.checkCanDamage(bukkitEntity, entity.getBukkitEntity(), false));
        for (HitEntityInfo hitEntityInfo : toDamage) {
            org.bukkit.entity.Entity victimBukkit = hitEntityInfo.getHitEntity().getBukkitEntity();
            // do not collide with passenger or itself
            if (bukkitEntity.getPassengers().contains(victimBukkit))
                continue;
            if (bukkitEntity == victimBukkit)
                continue;
            if (!damageCD.contains(victimBukkit)) {
                DamageHelper.damageCD(damageCD, victimBukkit, 5);
                DamageHelper.handleDamage(bukkitEntity, victimBukkit,
                        currDmg, DamageHelper.DamageReason.CONTACT_DAMAGE);
                // slimes also bounce upward
                switch (mountType) {
                    case "史莱姆坐骑":
                    case "羽翼史莱姆坐骑":
                        motY = 2;
                        break;
                }
            }
        }
    }
}
