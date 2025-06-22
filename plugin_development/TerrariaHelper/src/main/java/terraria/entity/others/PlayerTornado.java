package terraria.entity.others;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.MathHelper;
import terraria.util.MetadataHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class PlayerTornado extends EntitySlime {
    // basic variables
    HashMap<String, Double> attrMap;
    ArrayList<PlayerTornado> tornadoList;
    PlayerTornado base;
    HashSet<Entity> damageCD;
    int index = 0, rotationAngleOffsetPerLayer = 18, lastTime = 300;
    double angle = 0, horizontalOffset, verticalOffset;
    Player owner;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            if (base != null) {
                angle += rotationAngleOffsetPerLayer;
                Vector offsetDir = MathHelper.vectorFromYawPitch_approx(angle, 0);
                offsetDir.multiply(horizontalOffset);
                offsetDir.setY(verticalOffset);
                // go to new location
                Location targetLoc = base.getBukkitEntity().getLocation().add(offsetDir);
                bukkitEntity.setVelocity(targetLoc.subtract( bukkitEntity.getLocation() )
                        .toVector());
            }
            // this is the base, remain stationary
            else {
                bukkitEntity.setVelocity(new Vector());
            }
            // handle max live time
            if (ticksLived > lastTime)
                die();
        }
        // face the charge direction
        this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this, damageCD, 8);
    }
    // default constructor to handle chunk unload
    public PlayerTornado(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public PlayerTornado(Player owner, Location spawnLoc, ArrayList<PlayerTornado> tornadoList,
                         int currIndex, int rotationAngleOffsetPerLayer, int lastTime, int maxHeight,
                         int layerExpandInterval, double layerHeight,
                         String name, HashMap<String, Double> attrMap) {
        super( ((CraftPlayer) owner).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftPlayer) owner).getHandle().getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.tornadoList = tornadoList;
        this.index = currIndex;
        this.rotationAngleOffsetPerLayer = rotationAngleOffsetPerLayer;
        this.lastTime = lastTime;
        this.attrMap = (HashMap<String, Double>) attrMap.clone();
        // damage CD is shared for every layer
        this.damageCD = index == 0 ? new HashSet<>() : tornadoList.get(0).damageCD;
        setCustomName(name);
        setCustomNameVisible(false);
        bukkitEntity.addScoreboardTag("noDamage");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, this.attrMap);
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.DAMAGE_SOURCE, owner);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init slime size and offsets
        {
            horizontalOffset = index * 0.4;
            verticalOffset = index * 2;
            int slimeSize = (int) (4 + horizontalOffset);
            setSize(slimeSize, false);
        }
        // parts and other properties
        {
            tornadoList.add(this);
            this.noclip = true;
            this.persistent = true;
        }
        // set last layer
        {
            if (index == 0)
                base = null;
            else
                base = tornadoList.get(0);
        }
        // next layer of sharknado
        {
            if (currIndex < maxHeight)
                Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                    new PlayerTornado(owner, bukkitEntity.getLocation().add(new Vector(0, layerHeight, 0)),
                            tornadoList, currIndex + 1,
                            rotationAngleOffsetPerLayer, lastTime, maxHeight,
                            layerExpandInterval, layerHeight,
                            name, attrMap);
                }, layerExpandInterval);
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // AI
        AI();
    }

}
