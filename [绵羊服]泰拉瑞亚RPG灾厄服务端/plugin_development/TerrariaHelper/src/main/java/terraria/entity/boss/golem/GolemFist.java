package terraria.entity.boss.golem;

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

import java.util.ArrayList;
import java.util.HashMap;

public class GolemFist extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.GOLEM;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.TEMPLE;
    public static final double BASIC_HEALTH = 13386 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final String name = "石巨人拳头";
    static final double PUNCH_SPEED = 2.5, RETRACT_SPEED = 2.25;

    Golem owner;
    Vector offsetDir = new Vector(0, -0.6, 0);
    int componentIndex;
    int indexAI = 0;


    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            if (owner.isAlive())
                target = owner.target;
            else
                target = null;
            // disappear if no target is available
            if (target == null) {
                die();
                return;
            }
            // if target is valid, attack
            else {
                // velocity and location
                Location idleLocation;
                {
                    Vector orthogonalOffsetVec = owner.orthogonalDir.clone();
                    if (componentIndex == 2)
                        orthogonalOffsetVec.multiply(-1);
                    idleLocation = owner.getBukkitEntity().getLocation().add(offsetDir).add(orthogonalOffsetVec);
                }
                if (indexAI < 15) {
                    bukkitEntity.setVelocity(owner.getBukkitEntity().getVelocity());
                    bukkitEntity.teleport(idleLocation);
                }
                else {
                    if (indexAI == 15) {
                        bukkitEntity.setVelocity(
                                MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(),
                                        target.getEyeLocation(), PUNCH_SPEED));
                    }
                    else if (indexAI > 30) {
                        Vector velocity =
                                MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(),
                                        idleLocation, RETRACT_SPEED, true);
                        bukkitEntity.setVelocity(velocity);
                        if (velocity.lengthSquared() < RETRACT_SPEED * RETRACT_SPEED)
                            indexAI = -1;
                    }
                }
                indexAI ++;
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public GolemFist(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public GolemFist(Player summonedPlayer, Golem owner, int index) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = summonedPlayer.getLocation().add(0, 25, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.componentIndex = index;
        setCustomName(name + "§" + index);
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
            attrMap.put("damage", 360d);
            attrMap.put("defence", 56d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init boss bar
        bossbar = owner.bossbar;
        EntityHelper.setMetadata(bukkitEntity, "bossbar", bossbar);
        // init target map
        {
            targetMap = owner.targetMap;
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
        }
        // init health and slime size
        {
            setSize(5, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = owner.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
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
            for (int i = -2; i <= 2; i ++)
                for (int j = -2; j <= 2; j ++) {
                    org.bukkit.Chunk currChunk = bukkitEntity.getLocation().add(i << 4, 0, j << 4).getChunk();
                    currChunk.load();
                }
        }
        // AI
        AI();
    }
}
