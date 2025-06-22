package terraria.entity.boss.hardMode.lunaticCultist;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.HashMap;

public class LunaticCultistClone extends EntityZombieHusk {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.LUNATIC_CULTIST;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    LunaticCultist owner;

    static HashMap<String, Double> attrMapShadowFireball;
    static final double SPEED_FIREBALL = 1.5;
    EntityHelper.ProjectileShootInfo shootInfoShadowFireball;
    static {
        attrMapShadowFireball = new HashMap<>();
        attrMapShadowFireball.put("damage", 516d);
        attrMapShadowFireball.put("knockback", 1.5d);
    }
    int fireballCountdown = -1;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            // disappear if no target is available or owner is dead
            if (target == null || !owner.isAlive()) {
                die();
                return;
            }
            // if target is valid, attack
            else {
                // the cultist clone do not move
                motX = 0;
                motY = 0;
                motZ = 0;
                // plan an attack when owner is initializing a non-summon attack
                if (owner.indexAI == 0 && owner.phaseAttack != 0) {
                    fireballCountdown = 25 + (int) (Math.random() * 10);
                }
                // shoot fireballs
                if (--fireballCountdown == 0) {
                    shootInfoShadowFireball.setLockedTarget(target);
                    shootInfoShadowFireball.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                    shootInfoShadowFireball.velocity = MathHelper.getDirection(shootInfoShadowFireball.shootLoc,
                            target.getEyeLocation(), SPEED_FIREBALL * (1.25 - Math.random() * 0.5) );
                    EntityHelper.spawnProjectile(shootInfoShadowFireball);
                }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // no collision dmg for lunatic cultist clone
    }
    // default constructor to handle chunk unload
    public LunaticCultistClone(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public LunaticCultistClone(Player summonedPlayer, LunaticCultist owner) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("noDamage");
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 594d);
            attrMap.put("damageTakenMulti", 0.85);
            attrMap.put("defence", 94d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MAGIC);
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // other properties
        {
            setBaby(false);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info
        {
            shootInfoShadowFireball = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapShadowFireball,
                    DamageHelper.DamageType.ARROW, "暗影焰");
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
