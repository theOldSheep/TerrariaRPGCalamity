package terraria.entity.boss.dukeFishron;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.projectile.HitEntityInfo;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Sharkron extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.DUKE_FISHRON;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.OCEAN;
    public static final double BASIC_HEALTH = 1125 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    Player target = null;
    Vector velocity;
    // other variables and AI
    static final double DASH_SPEED = 4;

    DukeFishron owner;
    private void setupVelocity() {
        velocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(), DASH_SPEED);
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            // disappear if no target is available
            if (target == null || !owner.isAlive()) {
                die();
                return;
            }
            // if target is valid, attack
            if (ticksLived > 40) {
                velocity.multiply(0.99);
                velocity.subtract(new Vector(0, 0.05, 0));
            }
            bukkitEntity.setVelocity(velocity);
            // on hit block, remove entity
            MovingObjectPosition hitLocation = HitEntityInfo.rayTraceBlocks(bukkitEntity.getWorld(), bukkitEntity.getLocation().toVector(),
                    bukkitEntity.getLocation().add(bukkitEntity.getVelocity()).toVector());
            if (bukkitEntity.getLocation().getBlock().getType() != org.bukkit.Material.AIR || hitLocation
                     != null) {
                if (hitLocation != null)
                    bukkitEntity.teleport(MathHelper.toBukkitVector(hitLocation.pos).toLocation(bukkitEntity.getWorld()));
                setHealth(0f);
            }
        }
        // face the charge direction
        this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Sharkron(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public Sharkron(DukeFishron owner, Location spawnLoc) {
        super( owner.getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        setCustomName("龙卷鲨");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", owner.phaseAI < 2 ? 450d: 540d);
            attrMap.put("defence", 200d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            // damage multiplier
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = (HashMap<Player, Double>) owner.targetMap.clone();
            target = owner.target;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(4, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            this.noclip = true;
            this.persistent = true;
        }
        // init velocity
        this.velocity = MathHelper.getDirection( ((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(), DASH_SPEED);
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
