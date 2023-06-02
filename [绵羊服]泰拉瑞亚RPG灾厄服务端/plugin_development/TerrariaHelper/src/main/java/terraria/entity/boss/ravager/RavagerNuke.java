package terraria.entity.boss.ravager;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class RavagerNuke extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.RAVAGER;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    static final String name = "毁灭魔像导弹";

    Ravager owner;
    Vector cachedVelocity = new Vector();

    private void initSpeed() {
        cachedVelocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(), 1.5);
    }
    private void AI() {
        // update target
        if (owner.isAlive())
            target = owner.target;
        else
            target = null;
        // disappear if no target is available
        if (target == null) {
            die();
            return;
        }
        // if target is valid, attack
        else {
            bukkitEntity.setVelocity(cachedVelocity);
            // init speed when far away
            // 20 ^ 2 = 400
            if (bukkitEntity.getLocation().distanceSquared(target.getLocation()) > 400)
                initSpeed();
            // explode after timeout
            if (ticksLived > 200) {
                EntityHelper.handleEntityExplode(bukkitEntity, 3, new ArrayList<>(),
                        ((LivingEntity) bukkitEntity).getEyeLocation());
                die();
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public RavagerNuke(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public RavagerNuke(Player summonedPlayer, Ravager owner, boolean postProvidence) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = summonedPlayer.getLocation().add(0, 25, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.target = owner.target;
        setCustomName(name);
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
            attrMap.put("damage", postProvidence ? 864d : 576d);
            attrMap.put("knockback", 4d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init health and slime size
        {
            setSize(5, false);
        }
        // other properties
        {
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        initSpeed();
    }

    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // AI
        AI();
    }
}
