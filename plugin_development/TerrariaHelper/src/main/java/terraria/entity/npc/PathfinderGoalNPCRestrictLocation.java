package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.EntityCreature;
import net.minecraft.server.v1_12_R1.PathfinderGoal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import terraria.util.WorldHelper;

public class PathfinderGoalNPCRestrictLocation extends PathfinderGoal {
    protected final EntityCreature entity;
    public double targetX, targetY, targetZ;
    public int ticksWalked;
    public static final int LOCATION_RESTRICTION_DAY = 32, LOCATION_RESTRICTION_NIGHT = 16;
    public PathfinderGoalNPCRestrictLocation(EntityCreature entity) {
        this.entity = entity;
        targetX = entity.locX;
        targetY = entity.locY;
        targetZ = entity.locZ;
    }

    protected boolean updateWanderTargetLoc() {
        boolean isDay = WorldHelper.isDayTime( entity.getBukkitEntity().getWorld() );
        double wanderDiameter = isDay ? 40 : 20;
        World wld = entity.getBukkitEntity().getWorld();
        Block blk = wld.getHighestBlockAt( (int) (wanderDiameter * (Math.random() - 0.5)),
                (int) (wanderDiameter * (Math.random() - 0.5)) );
        Location targetLoc = blk.getLocation();
        targetX = targetLoc.getX();
        targetY = targetLoc.getY();
        targetZ = targetLoc.getZ();
        return true;
    }

    protected boolean isInProperLocation() {
        boolean isDay = WorldHelper.isDayTime( entity.getBukkitEntity().getWorld() );
        double loc_restriction = isDay ? LOCATION_RESTRICTION_DAY : LOCATION_RESTRICTION_NIGHT;
        return Math.abs(entity.locX) < loc_restriction && Math.abs(entity.locZ) < loc_restriction;
    }
    @Override
    public boolean a() {
        return ! isInProperLocation() && updateWanderTargetLoc();
    }

    @Override
    public boolean b() {
        return ! isInProperLocation();
    }

    // start
    @Override
    public void c() {
        ticksWalked = 0;
    }
    // tick
    @Override
    public void e() {
        // 10 seconds timeout
        if (++ ticksWalked > 200) {
            ticksWalked = 0;
            updateWanderTargetLoc();
        }
        if (ticksWalked % 20 == 0) {
            this.entity.getNavigation().a(targetX, targetY, targetZ, 1);
        }
    }
}
