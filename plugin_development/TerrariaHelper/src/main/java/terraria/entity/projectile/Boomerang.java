package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

public class Boomerang extends GenericProjectile {
    Player owner;
    Location spawnedLoc;
    boolean returning = false;
    double maxDistanceSquared, useTime;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public Boomerang(World world) {
        super(world);
        owner = null;
        die();
    }
    public Boomerang(EntityHelper.ProjectileShootInfo shootInfo, double maxDistance, double useTime) {
        super(shootInfo);
        // initialize variables
        owner = (Player) shootInfo.shooter;
        spawnedLoc = bukkitEntity.getLocation();
        this.maxDistanceSquared = maxDistance * maxDistance;
        this.useTime = useTime;
        // make the projectile return on block hit
        super.projectileRadius = 0.25;
        super.gravity = 0;
        super.penetration = 999999;
        super.liveTime = 999999;
        super.blockHitAction = "stick";
        super.canBeReflected = false;
        // give infinite use CD temporarily
        ItemUseHelper.applyCD(owner, -1);
    }
    @Override
    public void die() {
        super.die();
        if (owner != null && owner.isOnline()) {
            ItemUseHelper.applyCD(owner, useTime);
        }
    }

    @Override
    public Vec3D hitEntity(Entity e, MovingObjectPosition position, Vec3D futureLoc, Vector velocityHolder) {
        Vec3D result = super.hitEntity(e, position, futureLoc, velocityHolder);
        this.returning = true;
        return result;
    }

    @Override
    public void B_() {
        super.B_();
        // returns if too far away
        if (returning) {
            super.blockHitAction = "thru";
            bukkitEntity.setVelocity(MathHelper.getDirection(
                    bukkitEntity.getLocation(), owner.getEyeLocation(), super.speed) );
            if (bukkitEntity.getLocation().distanceSquared(owner.getEyeLocation()) < super.speed * super.speed)
                die();
        }
        // validate if the projectile should return to owner
        else {
            if (spawnedLoc.distanceSquared(bukkitEntity.getLocation()) > maxDistanceSquared) {
                this.returning = true;
            }
            // hit ground
            if (bukkitEntity.getVelocity().lengthSquared() < 1e-5)
                this.returning = true;
        }
        // owner is offline
        if (! PlayerHelper.isProperlyPlaying(owner) )
            die();
    }
}
