package terraria.entity.boss.event.pumpking;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;

import java.util.HashMap;

public class PumpkingScythe extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PUMPKING;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    int indexAI = 0;
    Vector velocityNormalized = null, velocity = null;
    private void AI() {
        indexAI ++;
        // remove on timeout
        if (indexAI > 100) {
            die();
            return;
        }
        // initialize direction
        if (indexAI == 20) {
            Location targetLoc = target.getEyeLocation();
            velocityNormalized = MathHelper.getDirection( ((LivingEntity) bukkitEntity).getEyeLocation(), targetLoc, 1);
        }
        // velocity
        else if (indexAI > 20) {
            // accelerates
            if (indexAI < 30) {
                double x = (indexAI - 20) / 10d;
                double speed = x * x * 3;
                velocity = velocityNormalized.clone().multiply(speed);
            }
            bukkitEntity.setVelocity(velocity);
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public PumpkingScythe(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public PumpkingScythe(Player summonedPlayer, Location spawnLoc) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.target = summonedPlayer;
        setCustomName("火焰锄刀");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("noDamage");
        bukkitEntity.addScoreboardTag("isMonster");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 720d);
            attrMap.put("defence", 0d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init health and slime size
        {
            setSize(5, false);
            double health = 1d;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // other properties
        {
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
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
