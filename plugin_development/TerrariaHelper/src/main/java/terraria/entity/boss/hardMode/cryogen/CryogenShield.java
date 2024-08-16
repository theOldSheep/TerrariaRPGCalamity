package terraria.entity.boss.hardMode.cryogen;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.event.entity.CreatureSpawnEvent;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

import java.util.HashMap;

public class CryogenShield extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CRYOGEN;
    public static final double BASIC_HEALTH = 2100 * 2, BASIC_HEALTH_BR = 16800 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    // other variables and AI
    Cryogen owner;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d || owner.getHealth() < 1e-5 || !(owner.isAlive())) {
            die();
            return;
        }
        // AI
        {
            bukkitEntity.setVelocity(owner.getBukkitEntity().getVelocity());
            EntityHelper.movementTP(bukkitEntity, owner.getBukkitEntity().getLocation().subtract(0, 1, 0));

            
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( owner.target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public CryogenShield(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public CryogenShield(Cryogen owner) {
        super( owner.getWorld() );
        // spawn location
        this.owner = owner;
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("冰川护盾");
        setCustomNameVisible(true);
        setNoGravity(true);
        persistent = true;
        noclip = true;
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 432d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("damageTakenMulti", 0.6d);
            attrMap.put("defence", 0d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, owner.targetMap.clone());
        }
        // init health and slime size
        {
            setSize(8 + 4, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(owner.targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
    }

    @Override
    public void die() {
        super.die();
        // make the owner realize that the shield is broken
        owner.shield = null;
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