package terraria.entity.boss.moonLord;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
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

import java.util.ArrayList;
import java.util.HashMap;

public class MoonLordPhantasmalBolt extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.MOON_LORD;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    static EntityHelper.AimHelperOptions aimHelper;
    static {
        aimHelper = new EntityHelper.AimHelperOptions()
                .setAimMode(true)
                .setTicksOffset(8);
    }
    Vector velocity = new Vector();

    private void setVelocity(Vector velocity) {
        this.velocity = velocity;
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // regulate velocity
            bukkitEntity.setVelocity(velocity);
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( velocity );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public MoonLordPhantasmalBolt(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public MoonLordPhantasmalBolt(Player summonedPlayer, Location spawnLoc) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.target = summonedPlayer;
        setCustomName("幻影矢");
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
            attrMap.put("damage", 480d);
            attrMap.put("defence", 0d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init health and slime size
        {
            setSize(6, false);
        }
        // boss parts and other properties
        {
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // init velocity
        Vector velocity = EntityHelper.helperAimEntity(bukkitEntity.getLocation(), target, aimHelper)
                .subtract(bukkitEntity.getLocation()).toVector();
        velocity.multiply(1d / 8);
        setVelocity(velocity);
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
