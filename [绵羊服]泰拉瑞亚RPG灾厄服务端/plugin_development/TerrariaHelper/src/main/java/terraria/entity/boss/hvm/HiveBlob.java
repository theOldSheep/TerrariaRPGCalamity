package terraria.entity.boss.hvm;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;

public class HiveBlob extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_HIVE_MIND;
    public static final double BASIC_HEALTH = 100 * 2;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    Player target = null;
    // other variables and AI
    TheHiveMind owner;
    Vector offset;
    int indexAI = 0;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            // disappear if no target is available
            if (owner.dead || owner.getHealth() < 1e-5 || target == null) {
                die();
                return;
            }
            // if target is valid, attack
            if (ticksLived % 3 == 0) {
                if (indexAI % 20 == 0) {
                    offset = MathHelper.randomVector();
                    offset.multiply(6 + Math.random() * 3);
                }
                // move towards boss
                {
                    Location targetLoc = ((LivingEntity) owner.getBukkitEntity()).getEyeLocation().add(offset);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    velocity.multiply(0.1);
                    bukkitEntity.setVelocity(velocity);
                }
                // shoot projectile
                if (indexAI % 33 == 0) {
                    Vector projVel = target.getEyeLocation().subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector();
                    double projVelLen = projVel.length();
                    if (projVelLen < 1e-5) {
                        projVelLen = 1;
                        projVel = new Vector(0, 1, 0);
                    }
                    projVel.multiply(1.5 / projVelLen);
                    EntityHelper.spawnProjectile(bukkitEntity, projVel, attrMap, "恶毒瘤块");
                }
                // add 1 to index
                indexAI ++;
            }
        }
    }
    // default constructor to handle chunk unload
    public HiveBlob(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.isDayTime(player.getWorld()) ) return false;
        return true;
    }
    // a constructor for actual spawning
    public HiveBlob(TheHiveMind owner) {
        super( owner.getWorld() );
        this.owner = owner;
        // spawn location
        double angle = Math.random() * 720d, dist = 8;
        Location spawnLoc = owner.getBukkitEntity().getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("腐化球");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 180d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 16d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 0.2d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            EntityHelper.setDamageType(bukkitEntity, "Arrow");
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init target map
        {
            targetMap = owner.targetMap;
            target = owner.target;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
        }
        // init health and slime size
        {
            setSize(2, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
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
