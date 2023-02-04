package terraria.entity;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class HitEntityInfo {
    private double distance;
    private Entity hitEntity;
    private MovingObjectPosition hitLocation;

    public HitEntityInfo(double distance, Entity hitEntity, MovingObjectPosition hitLocation) {
        // add a tiny offset so that it would not have accidental collision
        this.distance = distance + Math.random() * 1e-9;
        this.hitEntity = hitEntity;
        this.hitLocation = hitLocation;
    }

    public double getDistance() {
        return this.distance;
    }

    public Entity getHitEntity() {
        return this.hitEntity;
    }
    public MovingObjectPosition getHitLocation() {
        return this.hitLocation;
    }
    // helper functions for projectiles etc.
    public static MovingObjectPosition rayTraceBlocks(org.bukkit.World world, Vector startLoc, Vector terminalLoc) {
        return rayTraceBlocks(((CraftWorld) world).getHandle(), MathHelper.toNMSVector(startLoc), MathHelper.toNMSVector(terminalLoc));
    }
    public static MovingObjectPosition rayTraceBlocks(World world, Vec3D startLoc, Vec3D terminalLoc) {
        // do not care about grass etc.
        MovingObjectPosition movingobjectposition = world.rayTrace(startLoc, terminalLoc, false, true, false);
        return movingobjectposition;
    }

    public static TreeSet<HitEntityInfo> getEntitiesHit(org.bukkit.World world, Vector startLoc, Vector terminalLoc, double radius, com.google.common.base.Predicate<? super Entity> predication) {
        return getEntitiesHit(((CraftWorld) world).getHandle(),
                MathHelper.toNMSVector(startLoc), MathHelper.toNMSVector(terminalLoc), radius, predication);
    }
    public static TreeSet<HitEntityInfo> getEntitiesHit(org.bukkit.World world, Vector startLoc, Vector terminalLoc,
                                                        double radiusX, double radiusY, double radiusZ, com.google.common.base.Predicate<? super Entity> predication) {
        return getEntitiesHit(((CraftWorld) world).getHandle(),
                MathHelper.toNMSVector(startLoc), MathHelper.toNMSVector(terminalLoc), radiusX, radiusY, radiusZ, predication);
    }
    public static TreeSet<HitEntityInfo> getEntitiesHit(World world, Vec3D startLoc, Vec3D terminalLoc, double radius, com.google.common.base.Predicate<? super Entity> predication) {
        return getEntitiesHit(world, startLoc, terminalLoc, radius, radius, radius, predication);
    }
    public static TreeSet<HitEntityInfo> getEntitiesHit(World world, Vec3D startLoc, Vec3D terminalLoc,
                                                        double radiusX, double radiusY, double radiusZ, com.google.common.base.Predicate<? super Entity> predication) {
        AxisAlignedBB boundingBox = new AxisAlignedBB(startLoc.x, startLoc.y, startLoc.z, terminalLoc.x, terminalLoc.y, terminalLoc.z)
                .grow(radiusX, radiusY, radiusZ);
        List<Entity> list = world.getEntities(null, boundingBox, predication);
        TreeSet<HitEntityInfo> hitCandidates = new TreeSet<>(Comparator.comparingDouble(HitEntityInfo::getDistance));
        for (Entity toCheck : list) {
            AxisAlignedBB axisalignedbb = toCheck.getBoundingBox().grow(radiusX, radiusY, radiusZ);
            MovingObjectPosition hitPosition = null;
            // if the initial location is already within the collision box
            if (axisalignedbb.b(startLoc)) hitPosition = new MovingObjectPosition(startLoc, EnumDirection.DOWN);
            // otherwise, check for the first hit location
            if (hitPosition == null) hitPosition = axisalignedbb.b(startLoc, terminalLoc);
            // if the entity is being hit
            if (hitPosition != null) {
                double distanceSquared = startLoc.distanceSquared(hitPosition.pos);
                hitCandidates.add(new HitEntityInfo(distanceSquared, toCheck, hitPosition));
            }
        }
        return hitCandidates;
    }
}