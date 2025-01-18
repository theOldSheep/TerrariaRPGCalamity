package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

public class Boomerang extends GenericProjectile {
    Player owner;
    Location spawnedLoc;
    boolean returning = false, strict;
    double maxDistanceSquared, useTime;
    int hitTimes = 0;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public Boomerang(World world) {
        super(world);
        owner = null;
        vanillaDie();
    }
    public Boomerang(EntityHelper.ProjectileShootInfo shootInfo, double maxDistance, double useTime,
                     boolean strict, boolean returnOnHitBlock) {
        super(shootInfo);
        // initialize variables
        owner = (Player) shootInfo.shooter;
        spawnedLoc = bukkitEntity.getLocation();
        this.maxDistanceSquared = maxDistance * maxDistance;
        this.useTime = useTime;
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
        // make the projectile return on block
        super.blockHitAction = returnOnHitBlock ? "stick" : "thru";
    }
    @Override
    public void die() {
        super.die();
        if (strict && owner != null && owner.isOnline()) {
            ItemUseHelper.applyCD(owner, useTime);
        }
    }
    @Override
    protected void vanillaDie() {
        super.vanillaDie();
        if (strict && owner != null && owner.isOnline()) {
            ItemUseHelper.applyCD(owner, useTime);
        }
    }

    @Override
    public Vec3D hitEntity(Entity e, MovingObjectPosition position, Vec3D futureLoc, Vector velocityHolder) {
        Vec3D result = super.hitEntity(e, position, futureLoc, velocityHolder);
        switch (projectileType) {
            case "鱼骨回旋镖Ex": {
                if (hitTimes++ == 0)
                    ricochet(14.5, futureLoc, velocityHolder);
                else
                    this.returning = true;
                break;
            }
            default:
                this.returning = true;
        }
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
                vanillaDie();
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
            vanillaDie();
    }
}
