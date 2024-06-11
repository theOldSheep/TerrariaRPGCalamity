package terraria.entity.boss.hardMode.astrumAureus;

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

public class AureusSpawn extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.ASTRUM_AUREUS;
    public static final double BASIC_HEALTH = 4500 * 2;
    HashMap<String, Double> attrMap;
    AstrumAureus owner;
    // other variables and AI
    static final String name = "小白星";
    static final double CHASE_SPEED_MAX = 3, CHASE_ACC = 0.15;
    private void explode() {
        EntityHelper.handleEntityExplode(bukkitEntity, 4.5, new ArrayList<>(), ((LivingEntity) bukkitEntity).getEyeLocation());
        die();
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        Player target = owner.target;
        {
            // update target
            if (!owner.isAlive())
                target = null;
            // disappear if no target is available
            if (target == null) {
                die();
                return;
            }
            // if target is valid, attack
            else {
                Location eyeLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                // wander around the target
                if (getHealth() * 2 > getMaxHealth()) {
                    Vector velocity = target.getEyeLocation().subtract(eyeLoc).toVector();
                    velocity.multiply(0.05);
                    bukkitEntity.setVelocity(velocity);
                }
                // dash into the target and explode on getting close or hitting block
                else {
                    Vector velocity = bukkitEntity.getVelocity();
                    Vector acc = MathHelper.getDirection(eyeLoc, target.getEyeLocation(), CHASE_ACC);
                    velocity.add(acc);
                    double velLen = velocity.length();
                    if (velLen > CHASE_SPEED_MAX)
                        velocity.multiply(CHASE_SPEED_MAX / velLen);
                    bukkitEntity.setVelocity(velocity);
                    // explode
                    if (bukkitEntity.getLocation().getBlock().getType().isSolid())
                        explode();
                    else if (eyeLoc.distanceSquared(target.getEyeLocation()) < 10)
                        explode();
                }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public AureusSpawn(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public AureusSpawn(Player summonedPlayer, AstrumAureus owner) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        setCustomName(name);
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
            attrMap.put("damage", 480d);
            attrMap.put("defence", 20d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, owner.bossbar);
        // init target map
        {
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, owner.targetMap.clone());
        }
        // init health and slime size
        {
            setSize(4, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(owner.targetMap.size());
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
