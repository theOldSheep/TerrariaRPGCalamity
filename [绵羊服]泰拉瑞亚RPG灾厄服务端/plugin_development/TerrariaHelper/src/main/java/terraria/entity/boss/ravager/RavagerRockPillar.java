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

import java.util.ArrayList;
import java.util.HashMap;

public class RavagerRockPillar extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.RAVAGER;
    public static final double BASIC_HEALTH = 7500 * 2, BASIC_HEALTH_POST_PROVIDENCE = 30000 * 2;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    static final String name = "石元柱";

    Ravager owner;
    RavagerRockPillar base;
    int heightIndex;


    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update health etc.
            if (base != null) {
                setHealth(base.getHealth());
                bukkitEntity.setVelocity(base.bukkitEntity.getVelocity());
                bukkitEntity.teleport(base.bukkitEntity.getLocation().add(0, 2 * heightIndex, 0));
            }
            // remove on timeout
            if (ticksLived > 200)
                die();
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public RavagerRockPillar(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public RavagerRockPillar(Ravager owner, Location spawnLoc) {
        this(owner.target, owner, 0, owner.postProvidence, null, spawnLoc);
    }
    public RavagerRockPillar(Player summonedPlayer, Ravager owner, int index, boolean postProvidence, RavagerRockPillar base, Location spawnLoc) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.base = base;
        this.heightIndex = index;
        setCustomName(name);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isBOSS");
        if (base != null)
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, base);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = (HashMap<String, Double>) owner.attrMap.clone();
            attrMap.put("damageTakenMulti", 0.7);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        HashMap<Player, Double> targetMap = (HashMap<Player, Double>) owner.targetMap.clone();
        {
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(4, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = (postProvidence ? BASIC_HEALTH_POST_PROVIDENCE : BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            this.persistent = true;
        }
        // next layer
        if (index < 7)
            new RavagerRockPillar(summonedPlayer, owner, index + 1, postProvidence,
                    (index == 0 ? this : base), spawnLoc);
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
