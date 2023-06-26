package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;
import terraria.util.ItemUseHelper;
import terraria.util.MathHelper;
import terraria.util.PlayerHelper;

public class TerrariaFlail extends TerrariaPotionProjectile {
    Player owner;
    Location spawnedLoc;
    boolean returning = false, spinning = true;
    double maxDistanceSquared, useTime, speed;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public TerrariaFlail(World world) {
        super(world);
        owner = null;
        die();
    }
    public TerrariaFlail(EntityHelper.ProjectileShootInfo shootInfo, double maxDistance, double useTime) {
        super(shootInfo);
        // initialize variables
        owner = (Player) shootInfo.shooter;
        spawnedLoc = bukkitEntity.getLocation();
        this.maxDistanceSquared = maxDistance * maxDistance;
        this.useTime = useTime;
        this.speed = bukkitEntity.getVelocity().length();
        // make the projectile return on block hit
        super.projectileRadius = 0.5;
        super.gravity = 0;
        super.penetration = 999999;
        super.liveTime = 999999;
        super.blockHitAction = "thru";
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
    public void B_() {
        super.B_();
        // owner is offline
        if (! PlayerHelper.isProperlyPlaying(owner) ) {
            die();
            return;
        }
        // returns if too far away
        if (returning) {
            super.blockHitAction = "thru";
            bukkitEntity.setVelocity(MathHelper.getDirection(
                    bukkitEntity.getLocation(), owner.getEyeLocation(), this.speed) );
            if (bukkitEntity.getLocation().distanceSquared(owner.getEyeLocation()) < this.speed * this.speed)
                die();
        }
        // if the player is spinning the flail before throwing out
        else if (spinning) {
            // end of spinning
            if (! owner.getScoreboardTags().contains("temp_autoSwing")) {
                bukkitEntity.teleport(owner.getEyeLocation());
                spinning = false;
                super.blockHitAction = "stick";
                // update velocity
                Vector newVelocity = MathHelper.vectorFromYawPitch_quick(owner.getLocation().getYaw(), owner.getLocation().getPitch());
                newVelocity.multiply(speed);
                bukkitEntity.setVelocity(newVelocity);
            }
            // rotate around the player when spinning
            else {
                double angle = ticksLived * 40;
                Vector horizontalOffset = MathHelper.vectorFromYawPitch_quick(angle, 0);
                horizontalOffset.multiply(2);
                Location targetLoc = owner.getLocation().add(0, 1, 0).add(horizontalOffset);
                bukkitEntity.setVelocity(
                        targetLoc.subtract( bukkitEntity.getLocation() ).toVector()  );
            }
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
    }
}
