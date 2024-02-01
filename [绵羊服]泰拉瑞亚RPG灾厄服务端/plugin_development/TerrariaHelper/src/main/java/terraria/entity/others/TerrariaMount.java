package terraria.entity.others;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.PlayerHelper;

import java.util.HashMap;
import java.util.Set;

public class TerrariaMount extends EntitySlime {
    protected double entityHalfWidth = 0.25, entityHalfHeight = 0.25,
            horSpdMax = 0.5, verSpdMax = 1, horAcc = 0.1, verAcc = 0.2, gravityAcc = 0.1,
            speedMultiWater = 0.5, speedMultiGround = 0.75;
    protected int flightIndex = 0, flightDuration = 1, slimeSize = 1;
    protected boolean hasGravity = true;
    protected String minionType;

    protected HashMap<String, Double> attrMap;
    protected AxisAlignedBB boundingBox = null;
    protected Player owner = null;

    // default constructor accounting for default behaviour (removal)
    public TerrariaMount(World world) {
        super(world);
        super.die();
    }
    public TerrariaMount(Player owner, ConfigurationSection mountSection) {
        super(((CraftPlayer) owner).getHandle().getWorld());
        // init position
        Location loc = owner.getLocation();
        setLocation(loc.getX(), loc.getY(), loc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // init variables
        this.owner = owner;

        entityHalfWidth = mountSection.getDouble("entityHalfWidth", entityHalfWidth * slimeSize);
        entityHalfHeight = mountSection.getDouble("entityHalfHeight", entityHalfHeight * slimeSize);
        horSpdMax = mountSection.getDouble("horSpdMax", horSpdMax);
        verSpdMax = mountSection.getDouble("verSpdMax", verSpdMax);
        horAcc = mountSection.getDouble("horAcc", horAcc);
        verAcc = mountSection.getDouble("verAcc", verAcc);
        gravityAcc = mountSection.getDouble("gravityAcc", gravityAcc);
        speedMultiWater = mountSection.getDouble("speedMultiWater", speedMultiWater);
        speedMultiGround = mountSection.getDouble("speedMultiGround", speedMultiGround);

        flightDuration = mountSection.getInt("flightDuration", flightDuration);
        slimeSize = mountSection.getInt("slimeSize", slimeSize);
        flightIndex = flightDuration;

        minionType = mountSection.getString("type", "坐骑");

        hasGravity = mountSection.getBoolean("gravity", true);
        // no goal selector / target selector
        this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        this.targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute
        attrMap = new HashMap<>(5);
        attrMap.put("damage", mountSection.getDouble("damage", 0d));
        attrMap.put("knockback", 2d);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        // init properties
        setNoGravity(! hasGravity);
        setSize(slimeSize, false);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, owner);
        setCustomName(minionType);
        setCustomNameVisible(false);
        getAttributeInstance(GenericAttributes.maxHealth).setValue(444);
        setHealth(444f);
        // init AABB
        updateBoundingBox();
        // make player mount
        bukkitEntity.addPassenger(owner);
    }

    protected void updateBoundingBox() {
//        this.boundingBox = new AxisAlignedBB(
//                locX + entityHalfWidth, locY + entityHalfHeight, locZ + entityHalfWidth,
//                locX - entityHalfWidth, locY - entityHalfHeight, locZ - entityHalfWidth);
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return super.getBoundingBox();
//        return this.boundingBox;
    }

    @Override
    public void B_() {
        Bukkit.broadcastMessage("-1");
        // remove when dismounted
        if (! bukkitEntity.getPassengers().contains(owner)) {
            die();
            return;
        }
        // TODO: press "SHIFT" to fly downward; contact dmg
        // TODO: mount disappear in water fixture (the player gets teleported?)
        super.B_();

        Bukkit.broadcastMessage("0");
        // update AABB
        updateBoundingBox();
        // movement
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
        // horizontal movement
        Vector finalHorComp = new Vector(motX, 0, motZ);
        {
            double plyHorMoveDir = PlayerHelper.getPlayerMoveYaw(owner);
            if (plyHorMoveDir < 1e5) {
                Vector horMoveDir = MathHelper.vectorFromYawPitch_quick(plyHorMoveDir, 0);
                horMoveDir.multiply(horAcc);
                finalHorComp.add(horMoveDir);
            }
            else {
                finalHorComp.multiply((isOnGround && hasGravity) ? 0.75 : 0.95);
            }
            MathHelper.setVectorLength(finalHorComp, horSpdLmt, true);
        }
        // vertical movement
        Bukkit.broadcastMessage("1");
        double verticalVel = motY;
        {
            // reset flight duration
            if (isOnGround || super.onGround)
                flightIndex = 0;
            // flight handling
            if (ownerTags.contains("temp_thrusting") && flightIndex < flightDuration) {
                verticalVel += verAcc;
                verticalVel = Math.min(verticalVel, verSpdLmt);
                flightIndex ++;
            }
            // other mechanics
            else {
                // vertical velocity decay
                verticalVel *= 0.95;

                boolean shouldHandleGravity = hasGravity;
                switch (minionType) {
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
                    verticalVel -= gravityAcc;
                    verticalVel = Math.max(verticalVel, gravityAcc * -30);
                }
                // otherwise, reset fall distance
                else
                    fallDistance = 0f;
                Bukkit.broadcastMessage("2");
            }
        }
        // combine speed
        finalHorComp.setY(verticalVel);
        bukkitEntity.setVelocity(finalHorComp);
        Bukkit.broadcastMessage("3");
    }
}
