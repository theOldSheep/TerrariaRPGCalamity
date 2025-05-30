package terraria.entity.boss.hardMode.dukeFishron;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Sharknado extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.DUKE_FISHRON;
    HashMap<String, Double> attrMap;
    Player target = null;
    Vector velocity;
    // other variables and AI
    ArrayList<Sharknado> sharknadoList;
    Sharknado base;
    boolean phase2;
    int index = 0;
    double angle = 0, horizontalOffset, verticalOffset;

    DukeFishron owner;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            if (base != null) {
                angle += 18;
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
            int maxLiveTime = phase2 ? 300 : 200;
            if (ticksLived > maxLiveTime)
                die();
        }
        // face the charge direction
        this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Sharknado(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Sharknado(DukeFishron owner, Location spawnLoc, ArrayList<Sharknado> sharknadoList, int currIndex, boolean phase2) {
        super( owner.getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.sharknadoList = sharknadoList;
        this.index = currIndex;
        this.phase2 = phase2;
        setCustomName("鲨鱼旋风");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("noDamage");
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", owner.phaseAI < 2 ? 540d: 696d);
            attrMap.put("defence", 200d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            // damage multiplier
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init slime size and offsets
        {
            horizontalOffset = index * 0.2;
            verticalOffset = index * 2;
            int slimeSize = (int) (4 + horizontalOffset);
            setSize(slimeSize, false);
        }
        // boss parts and other properties
        {
            sharknadoList.add(this);
            this.noclip = true;
            this.persistent = true;
        }
        // set last layer
        {
            if (index == 0)
                base = null;
            else
                base = sharknadoList.get(0);
        }
        // next layer of sharknado
        {
            int indexMax = phase2 ? 40 : 25;
            if (currIndex < indexMax)
                Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                    new Sharknado(owner, bukkitEntity.getLocation().add(new Vector(0, 1, 0)),
                            sharknadoList, currIndex + 1, phase2);
                }, 2);
        }
        // spawn sharkron
        {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                new Sharkron(owner, bukkitEntity.getLocation());
            }, index * 2L);
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // AI
        AI();
    }

}
