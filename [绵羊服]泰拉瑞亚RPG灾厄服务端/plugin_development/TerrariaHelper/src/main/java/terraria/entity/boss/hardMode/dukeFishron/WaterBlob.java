package terraria.entity.boss.hardMode.dukeFishron;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class WaterBlob extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.DUKE_FISHRON;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.OCEAN;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    Player target = null;
    Vector velocity;
    // other variables and AI
    boolean phase2;
    double homingRatio = 0.5, speed = 2;

    DukeFishron owner;
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
            // update velocity
            bukkitEntity.setVelocity(velocity);
            // phase 2: home into target
            if (phase2) {
                homingRatio += 0.025;
                speed += 0.025;
                Vector acceleration = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), homingRatio * speed);
                velocity.add(acceleration);
                double velLen = velocity.length();
                if (velLen > 1e-5)
                    velocity.multiply(speed / velLen);
                // timeout
                if (ticksLived > 100) {
                    die();
                }
            }
            // phase 1: move, burst into sharknado on impact or timeout
            else {
                // impact detection
                MovingObjectPosition hitLocation = HitEntityInfo.rayTraceBlocks(bukkitEntity.getWorld(), bukkitEntity.getLocation().toVector(),
                        bukkitEntity.getLocation().add(bukkitEntity.getVelocity()).toVector());
                if (bukkitEntity.getLocation().getBlock().getType() != org.bukkit.Material.AIR || hitLocation
                        != null) {
                    if (hitLocation != null)
                        bukkitEntity.teleport(MathHelper.toBukkitVector(hitLocation.pos).toLocation(bukkitEntity.getWorld()));
                    die();
                }
                // timeout
                else if (ticksLived > 50) {
                    die();
                }
                // speed decay
                velocity.multiply(0.95);
            }
        }
        // face the charge direction
        this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        // collision dmg
        if (phase2)
            terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public WaterBlob(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public WaterBlob(DukeFishron owner, Vector velocity) {
        super( owner.getWorld() );
        // spawn location
        Location spawnLoc = ((LivingEntity) owner.getBukkitEntity()).getEyeLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.phase2 = owner.phaseAI >= 2;
        this.velocity = velocity;
        setCustomName("水螺旋");
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
            attrMap.put("damage", 1d);
            attrMap.put("defence", 200d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            // damage multiplier
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init health and slime size
        {
            setSize(3, false);
        }
        // boss parts and other properties
        {
            this.noclip = true;
            this.persistent = true;
        }
    }

    // rewrite AI
    @Override
    public void die() {
        super.die();
        // spawn sharknado
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () ->
                new Sharknado(owner, bukkitEntity.getLocation(), new ArrayList<>(), 0, phase2),
                30);
    }
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
