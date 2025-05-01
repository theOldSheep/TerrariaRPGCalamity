package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import net.minecraft.server.v1_12_R1.Vec3D;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.*;

public class Flail extends GenericProjectile {
    Player owner;
    Location spawnedLoc;
    boolean returning = false, spinning = true, shouldUpdateSpeed = true, returnOnHitBlock, canRotate, strict;
    double maxDist, maxDistanceSquared, useTime, speed;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public Flail(World world) {
        super(world);
        owner = null;
        vanillaDie();
    }
    public Flail(EntityHelper.ProjectileShootInfo shootInfo, double maxDistance, double useTime,
                 boolean strict, boolean canRotate, boolean returnOnHitBlock) {
        super(shootInfo);
        // initialize variables
        owner = (Player) shootInfo.shooter;
        spawnedLoc = bukkitEntity.getLocation();
        this.maxDist = maxDistance;
        this.maxDistanceSquared = maxDistance * maxDistance;
        this.useTime = useTime;
        this.speed = bukkitEntity.getVelocity().length();
        this.strict = strict;
        // if it is strict, more properties of the projectile will be overridden
        // and the player will be given a hardcore cool down until it returns
        boolean shouldApplyCD = !owner.getScoreboardTags().contains("temp_useCD");
        if (this.strict) {
            super.penetration = 999999;
            super.liveTime = 999999;
            super.canBeReflected = false;
            // give infinite use CD temporarily; if this projectile is somehow spawned during CD
            // don't recognize it as a strict projectile starting from here
            if ( shouldApplyCD ) {
                ItemUseHelper.applyCD(owner, -1);
            } else {
                this.strict = false;
            }
        }
        // normally give use CD if it is positive
        else if (useTime > 0 && shouldApplyCD) {
            ItemUseHelper.applyCD(owner, useTime);
        }
        // it needs to pass through blocks during spinning phase.
        // if needed, turns into "stick" on casting out, to make the projectile return on block hit.
        super.blockHitAction = "thru";
        // see if the flail supports rotation
        this.canRotate = canRotate;
        // see if the flail goes through wall or returns on hitting blocks
        this.returnOnHitBlock = returnOnHitBlock;
    }
    @Override
    public void die() {
        super.die();
        if (strict && useTime > 0 && owner != null && owner.isOnline()) {
            ItemUseHelper.applyCD(owner, useTime);
        }
    }
    @Override
    protected void vanillaDie() {
        super.vanillaDie();
        if (strict && useTime > 0 && owner != null && owner.isOnline()) {
            ItemUseHelper.applyCD(owner, useTime);
        }
    }
    @Override
    public Vec3D hitEntity(Entity e, MovingObjectPosition position, Vec3D futureLoc, Vector velocityHolder) {
        Vec3D result = super.hitEntity(e, position, futureLoc, velocityHolder);
        // flails that returns on first hit
        switch (super.projectileType) {
            case "海蚌锤":
                returning = true;
                break;
        }
        // frees the flail on hit
        if (super.projectileType.equals("风滚草") && shouldUpdateSpeed) {
            shouldUpdateSpeed = false;
            super.gravity = 0.05;
            super.liveTime = ticksLived + 100;
            super.blockHitAction = "slide";
            super.canBeReflected = true;
            ItemUseHelper.applyCD(owner, useTime);
            // preventing item use cool down reset on death again
            useTime = -1;
        }
        return result;
    }
    @Override
    protected void extraTicking() {
        switch (projectileType) {
            case "海蚌锤": {
                if (ticksLived == noGravityTicks) {
                    attrMap.put("damage", attrMap.get("damage") * 4);
                }
                break;
            }
        }
    }
    @Override
    public void B_() {
        super.B_();
        if (shouldUpdateSpeed) {
            // owner is offline
            if (!PlayerHelper.isProperlyPlaying(owner)) {
                vanillaDie();
                return;
            }
            // returns if too far away
            if (returning) {
                super.blockHitAction = "thru";
                bukkitEntity.setVelocity(MathHelper.getDirection(
                        bukkitEntity.getLocation(), owner.getEyeLocation(), this.speed));
                if (bukkitEntity.getLocation().distanceSquared(owner.getEyeLocation()) < this.speed * this.speed)
                    vanillaDie();
            }
            // if the player is spinning the flail before throwing out
            else if (spinning) {
                // end of spinning
                if ( ! (canRotate && owner.getScoreboardTags().contains("temp_autoSwing")) ) {
                    bukkitEntity.teleport(owner.getEyeLocation());
                    spinning = false;
                    if (returnOnHitBlock) {
                        super.blockHitAction = "stick";
                    }
                    // update velocity
                    AimHelper.AimHelperOptions aimHelper = new AimHelper.AimHelperOptions(projectileType)
                            .setProjectileSpeed(speed);
                    Vector newVelocity = MathHelper.getDirection(
                            owner.getEyeLocation(),
                            AimHelper.getPlayerTargetLoc(new AimHelper.PlyTargetLocInfo(owner, aimHelper, true)
                                    .setTraceDist(maxDist)),
                            speed);
                    newVelocity.normalize();
                    newVelocity.multiply(speed);
                    bukkitEntity.setVelocity(newVelocity);
                    switch (projectileType) {
                        case "海胆链枷": {
                            if (ticksLived > 20) {
                                newVelocity.multiply(1.5);
                                EntityHelper.spawnProjectile(owner, newVelocity, attrMap, "涡流");
                            }
                            break;
                        }
                    }
                }
                // rotate around the player when spinning
                else {
                    double angle = ticksLived * 40;
                    Vector horizontalOffset = MathHelper.vectorFromYawPitch_approx(angle, 0);
                    horizontalOffset.multiply(2);
                    Location targetLoc = owner.getLocation().add(0, 1, 0).add(horizontalOffset);
                    bukkitEntity.setVelocity(
                            targetLoc.subtract(bukkitEntity.getLocation()).toVector());
                }
            }
            // validate if the projectile should return to owner
            else {
                if (spawnedLoc.distanceSquared(bukkitEntity.getLocation()) > maxDistanceSquared) {
                    this.returning = true;
                }
                // if the projectile should return for hitting a block
                if (bukkitEntity.getVelocity().lengthSquared() < 1e-5)
                    this.returning = true;
            }
        }
    }
}
