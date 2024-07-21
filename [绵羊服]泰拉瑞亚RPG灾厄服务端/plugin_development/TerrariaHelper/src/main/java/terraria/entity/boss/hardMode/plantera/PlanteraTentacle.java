package terraria.entity.boss.hardMode.plantera;

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

import java.util.HashMap;

public class PlanteraTentacle extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PLANTERA;
    public static final double BASIC_HEALTH = 2600 * 2, BASIC_HEALTH_BR = 5100 * 2;
    HashMap<String, Double> attrMap;
    // other variables and AI
    static final double SPEED = 2;

    Plantera owner;
    Vector offsetDirection;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            Player target = owner.target;
            // disappear if no target is available
            if (target == null || !(owner.isAlive()) ) {
                die();
                return;
            }
            // if target is valid, attack
            else {
                terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
                bukkitEntity.setVelocity(MathHelper.getDirection(
                        bukkitEntity.getLocation(),
                        owner.getBukkitEntity().getLocation().add(offsetDirection),
                        SPEED));
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( offsetDirection );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public PlanteraTentacle(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public PlanteraTentacle(Player summonedPlayer, Plantera owner) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        setCustomName("世纪之花触手");
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 552d);
            attrMap.put("damageTakenMulti", 0.9);
            attrMap.put("defence", 40d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // boss bar
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, owner.bossbar);
        // init target map
        {
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, owner.targetMap);
        }
        // init health and slime size
        {
            setSize(4, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(owner.targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            owner.bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // offset direction
        offsetDirection = MathHelper.randomVector();
        offsetDirection.multiply(8);
    }

    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // load nearby chunks
        {
            for (int i = -1; i <= 1; i ++)
                for (int j = -1; j <= 1; j ++) {
                    org.bukkit.Chunk currChunk = bukkitEntity.getLocation().add(i << 4, 0, j << 4).getChunk();
                    currChunk.load();
                }
        }
        // AI
        AI();
    }
}
