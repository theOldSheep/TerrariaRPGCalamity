package terraria.entity.boss.preHardMode.kingSlime;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftSlime;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class CrownJewel extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.KING_SLIME;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 6066 * 2, BASIC_HEALTH_BR = 909900 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    KingSlime owner;
    int indexAI = 0;
    private void shootProjectiles() {
        if (target == null) return;
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, new Vector(), attrMap, "--");
        shootInfo.projectileName = "殷红弹幕";
        shootInfo.properties.put("gravity", 0d);
        shootInfo.properties.put("blockHitAction", "thru");
        for (int i = 0; i < 3; i ++) {
            Location targetedLoc = target.getEyeLocation().add(
                    Math.random() * 2 - 1, Math.random() * 2 - 1, Math.random() * 2 - 1);
            Vector velocity = targetedLoc.subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector();
            double velLen = velocity.length();
            if (velLen < 1e-5) {
                velLen = 1d;
                velocity = new Vector(0, 1, 0);
            }
            velocity.multiply(1.5 / velLen);
            shootInfo.velocity = velocity;
            EntityHelper.spawnProjectile(shootInfo);
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            if (! owner.isAlive())
                die();
            // update target
            target = owner.target;
            // if target is valid, attack
            if (ticksLived % 3 == 0) {
                Vector velocity = target.getLocation().add(0, 8, 0).subtract(bukkitEntity.getLocation()).toVector();
                velocity.multiply(0.025);
                bukkitEntity.setVelocity(velocity);
                // shoot projectiles
                if (indexAI % 7 == 0)
                    shootProjectiles();

                indexAI ++;
            }
        }
    }
    // default constructor to handle chunk unload
    public CrownJewel(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.isDayTime(player.getWorld()) ) return false;
        return true;
    }
    // a constructor for actual spawning
    public CrownJewel(ArrayList<LivingEntity> bossParts) {
        super( ((CraftEntity) bossParts.get(0)).getHandle().getWorld() );
        owner = (KingSlime) ((CraftSlime) bossParts.get(0)).getHandle();
        Player summonedPlayer = owner.target;
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("王冠宝石");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        bukkitEntity.addScoreboardTag("noDamage");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 100d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("knockback", 4d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.ARROW);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init health and slime size
        {
            setSize(2, false);
        }
        // boss parts and other properties
        {
            this.bossParts = bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        if (owner.dead || owner.getHealth() < 1e-5) {
            die();
            return;
        }
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
