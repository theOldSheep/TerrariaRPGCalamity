package terraria.entity.boss.postMoonLord.yharon;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.hardMode.dukeFishron.DukeFishron;
import terraria.entity.boss.hardMode.dukeFishron.Sharkron;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Infernado extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.YHARON_DRAGON_OF_REBIRTH;
    HashMap<String, Double> attrMap;
    // other variables and AI
    ArrayList<Infernado> sharknadoList;
    Infernado base;
    boolean phase2;
    int index = 0;
    double angle = 0, horizontalOffset, verticalOffset;

    Yharon owner;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            angle += 18;
            if (base != null) {
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
            int maxLiveTime = phase2 ? 600 : 400;
            if (ticksLived > maxLiveTime)
                die();
        }
        // face the charge direction
        this.yaw = (float) (angle + 90) % 360;
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Infernado(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Infernado(Yharon owner, Location targetLoc, ArrayList<Infernado> sharknadoList, int currIndex) {
        super( owner.getWorld() );
        // spawn location
        this.phase2 = owner.phase >= 3;
        setLocation(targetLoc.getX(), targetLoc.getY() + (phase2 ? -30 : -20), targetLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.sharknadoList = sharknadoList;
        this.index = currIndex;
        setCustomName("烈焰龙卷");
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
            attrMap.put("damage", 1392d);
            attrMap.put("knockback", 4d);
            // damage multiplier
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init slime size and offsets
        {
            double verticalOffsetMulti = phase2 ? 6 : 3;
            horizontalOffset = index * (phase2 ? 0.6 : 0.4);
            verticalOffset = index * verticalOffsetMulti;
            int slimeSize = (int) (2 * verticalOffsetMulti + horizontalOffset);
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
        // next layer
        {
            int indexMax = 40;
            if (currIndex < indexMax)
                Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                    new Infernado(owner, bukkitEntity.getLocation().add(new Vector(0, 1, 0)),
                            sharknadoList, currIndex + 1);
                }, 2);
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        // AI
        AI();
    }

}
