package terraria.entity.boss.postMoonLord.devourerOfGods;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.postMoonLord.ceaselessVoid.CeaselessVoid;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

import java.util.HashMap;

public class GodSlayerPortal extends EntitySlime {
    // default constructor to handle chunk unload
    public GodSlayerPortal(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public GodSlayerPortal(DevourerOfGods owner, Location spawnLoc) {
        super( owner.getWorld() );
        setLocation(spawnLoc.getX(), spawnLoc.getY() - 2, spawnLoc.getZ(), spawnLoc.getYaw(), spawnLoc.getPitch());
        // add to world
        owner.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("弑神传送门");
        setCustomNameVisible(false);
        addScoreboardTag("noDamage");
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);

        setSize(18, false);
        this.noclip = true;
        this.setNoGravity(true);
        this.persistent = true;
    }
}