package terraria.entity.boss.preHardMode.theHiveMind;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;

import java.util.HashMap;
import java.util.UUID;

public class DarkHeart extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_HIVE_MIND;
    public static final double BASIC_HEALTH = 150 * 2, BASIC_HEALTH_BR = 3432 * 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    Player target = null;
    // other variables and AI
    TheHiveMind owner;
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
                // move towards enemy
                {
                    Location targetLoc = target.getEyeLocation().add(0, 8, 0);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    velocity.multiply(0.1);
                    bukkitEntity.setVelocity(velocity);
                }
                // shoot projectile
                if (indexAI % 2 == 0) {
                    Vector projVel = new Vector(0, -1, 0);
                    EntityHelper.spawnProjectile(bukkitEntity, projVel, attrMap, "暗影之雨");
                }
                // add 1 to index
                indexAI ++;
            }
        }
    }
    // default constructor to handle chunk unload
    public DarkHeart(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.isDayTime(player.getWorld()) ) return false;
        return true;
    }
    // a constructor for actual spawning
    public DarkHeart(TheHiveMind owner) {
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
        setCustomName("暗之心");
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
            attrMap.put("damage", 204d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 4d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 0.68d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.ARROW);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) owner.targetMap.clone();
            target = owner.target;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(3, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
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
