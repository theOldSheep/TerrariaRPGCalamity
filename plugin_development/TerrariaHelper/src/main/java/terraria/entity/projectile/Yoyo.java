package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import net.minecraft.server.v1_12_R1.Vec3D;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.*;

public class Yoyo extends GenericProjectile {
    static AimHelper.AimHelperOptions aimHelper;
    static {
        aimHelper = new AimHelper.AimHelperOptions()
                .setAimMode(true)
                .setTicksTotal(1);
    }
    Player owner;
    Vector recoilPool = new Vector();
    double maxDistance, maxDistanceSquared, useTime, speed, recoilPoolMultiplier,
            // microwave would not recoil when hitting with enlarged bound box
            actualRadiusSqr;
    int ticksDuration, indexAI = 0;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public Yoyo(World world) {
        super(world);
        owner = null;
        vanillaDie();
    }
    public Yoyo(EntityHelper.ProjectileShootInfo shootInfo,
                double maxDistance, double useTime, double recoilPoolMultiplier, int ticksDuration) {
        super(shootInfo);
        // initialize variables
        owner = (Player) shootInfo.shooter;
        this.maxDistance = maxDistance;
        this.maxDistanceSquared = maxDistance * maxDistance;
        this.useTime = useTime;
        this.recoilPoolMultiplier = recoilPoolMultiplier;
        this.speed = bukkitEntity.getVelocity().length();
        this.ticksDuration = ticksDuration;
        // make the projectile return on block hit
        super.gravity = 0;
        super.penetration = 999999;
        super.liveTime = 999999;
        super.blockHitAction = "thru";
        super.canBeReflected = false;
        actualRadiusSqr = projectileRadius * projectileRadius + 1e-5;
        if (projectileType.equals("微波辐照"))
            actualRadiusSqr = 0.125 * 0.125 + 1e-5;
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
    protected void vanillaDie() {
        super.vanillaDie();
        if (owner != null && owner.isOnline()) {
            ItemUseHelper.applyCD(owner, useTime);
        }
    }

    @Override
    public Vec3D hitEntity(Entity e, MovingObjectPosition position, Vec3D futureLoc, Vector velocityHolder) {
        Vec3D result = super.hitEntity(e, position, futureLoc, velocityHolder);
        // tweak the on-hit recoil
        Location hitLoc = new Location(bukkitEntity.getWorld(), position.pos.x, position.pos.y, position.pos.z);
        if (hitLoc.distanceSquared(bukkitEntity.getLocation()) <= actualRadiusSqr) {
            recoilPool = MathHelper.getDirection(hitLoc, bukkitEntity.getLocation(), speed + 0.75);
            // hit increases the index significantly
            indexAI += 6;
        }
        return result;
    }

    @Override
    public void B_() {
        super.B_();
        // returns if too far away
        boolean isReturning = owner.isSneaking() ||
                ticksLived >= ticksDuration ||
                bukkitEntity.getLocation().distanceSquared(owner.getEyeLocation()) > maxDistanceSquared;
        if (isReturning) {
            bukkitEntity.setVelocity(MathHelper.getDirection(
                    bukkitEntity.getLocation(), owner.getEyeLocation(), this.speed) );
            if (bukkitEntity.getLocation().distanceSquared(owner.getEyeLocation()) < this.speed * this.speed)
                vanillaDie();
        }
        // update velocity
        else {
            Location targetLoc = AimHelper.getPlayerTargetLoc(
                    new AimHelper.PlyTargetLocInfo(owner, aimHelper, true)
                            .setTraceDist(maxDistance));
            Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, speed, true);
            // tweak velocity
            velocity.add(recoilPool);
            recoilPool.multiply(recoilPoolMultiplier);
            bukkitEntity.setVelocity(velocity);
        }
        // owner is offline
        if (! PlayerHelper.isProperlyPlaying(owner) )
            vanillaDie();
        // add 1 to index per tick
        indexAI ++;
    }
}
