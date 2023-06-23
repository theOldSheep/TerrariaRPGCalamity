package terraria.entity.projectile;

import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.util.*;

public class TerrariaYoyo extends TerrariaPotionProjectile {
    static EntityHelper.AimHelperOptions aimHelper;
    static {
        aimHelper = new EntityHelper.AimHelperOptions()
                .setAimMode(true)
                .setTicksOffset(1);
    }
    Player owner;
    Location spawnedLoc;
    Vector recoilPool = new Vector();
    double maxDistance, maxDistanceSquared, useTime, speed, acceleration;
    int ticksDuration, indexAI = 0;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public TerrariaYoyo(World world) {
        super(world);
        owner = null;
        die();
    }
    public TerrariaYoyo(EntityHelper.ProjectileShootInfo shootInfo, double maxDistance, double useTime, int ticksDuration) {
        super(shootInfo);
        // initialize variables
        owner = (Player) shootInfo.shooter;
        spawnedLoc = bukkitEntity.getLocation();
        this.maxDistance = maxDistance;
        this.maxDistanceSquared = maxDistance * maxDistance;
        this.useTime = useTime;
        this.speed = bukkitEntity.getVelocity().length();
        this.ticksDuration = ticksDuration;
        // make the projectile return on block hit
        super.gravity = 0;
        super.penetration = 999999;
        super.liveTime = 999999;
        super.blockHitAction = "slide";
        // give infinite use CD temporarily
        ItemUseHelper.applyCD(owner, -1);
    }
    @Override
    public void die() {
        super.die();
        if (owner != null && PlayerHelper.isProperlyPlaying(owner)) {
            ItemUseHelper.applyCD(owner, useTime);
        }
    }

    @Override
    public void hitEntity(Entity e, MovingObjectPosition position) {
        super.hitEntity(e, position);
        // tweak the on-hit recoil
        Location hitLoc = new Location(bukkitEntity.getWorld(), position.pos.x, position.pos.y, position.pos.z);
        Vector recoilDir = MathHelper.getDirection(hitLoc, bukkitEntity.getLocation(), speed * 0.02);
        recoilPool.add(recoilDir);
        // hit increases the index significantly
        indexAI += 4;
    }

    @Override
    public void B_() {
        super.B_();
        // returns if too far away
        boolean isReturning = owner.isSneaking() || ticksLived >= ticksDuration;
        if (isReturning) {
            super.blockHitAction = "thru";
            bukkitEntity.setVelocity(MathHelper.getDirection(
                    bukkitEntity.getLocation(), owner.getEyeLocation(), this.speed) );
            if (bukkitEntity.getLocation().distanceSquared(owner.getEyeLocation()) < this.speed * this.speed)
                die();
        }
        // update velocity
        else {
            Location targetLoc = ItemUseHelper.getPlayerTargetLoc(owner, maxDistance, 5, aimHelper, true);
            Vector acc = MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, acceleration);
            Vector velocity = bukkitEntity.getVelocity();
            // tweak velocity
            velocity.add(acc);
            velocity.add(recoilPool);
            recoilPool.multiply(0.5);
            if (velocity.lengthSquared() < 1e-5)
                velocity = MathHelper.randomVector();
            velocity.normalize().multiply(speed);
            bukkitEntity.setVelocity(velocity);
        }
        // owner is offline
        if (! PlayerHelper.isProperlyPlaying(owner) )
            die();
        // add 1 to index per tick
        indexAI ++;
    }
}
