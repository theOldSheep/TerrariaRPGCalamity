package terraria.entity.boss.hardMode.ravager;

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
import java.util.UUID;

public class RavagerClaw extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.RAVAGER;
    public static final double BASIC_HEALTH = 38364 * 2, BASIC_HEALTH_POST_PROVIDENCE = 153456 * 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final String name = "毁灭魔像手爪";
    static final double PUNCH_SPEED = 3.25, RETRACT_SPEED = 2.75;

    Ravager owner;
    Vector offsetDir = new Vector(0, 4.8, 0),
            punchVelocity = new Vector();
    int componentIndex;
    int indexAI = 0;
    double punchHealth = 0;


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
                    orthogonalOffsetVec.multiply(componentIndex == 2 ? -4.5 : 4.5);
                    idleLocation = owner.getBukkitEntity().getLocation().add(offsetDir).add(orthogonalOffsetVec);
                }
                if (indexAI < 15) {
                    bukkitEntity.setVelocity(owner.getBukkitEntity().getVelocity());
                    EntityMovementHelper.movementTP(bukkitEntity, idleLocation);
                }
                else {
                    if (indexAI == 15) {
                        punchVelocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(),
                                target.getEyeLocation(), PUNCH_SPEED);
                        bukkitEntity.setVelocity(punchVelocity);
                        punchHealth = getHealth();
                    }
                    else if (indexAI <= 30) {
                        // end punch after hurt
                        if (getHealth() < punchHealth)
                            indexAI = 30;
                        bukkitEntity.setVelocity(punchVelocity);
                    }
                    else {
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
    public RavagerClaw(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public RavagerClaw(Player summonedPlayer, Ravager owner, int index, boolean postProvidence) {
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
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", postProvidence ? 720d : 480d);
            attrMap.put("damageTakenMulti", 0.85);
            attrMap.put("defence", postProvidence ? 160d : 80d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = owner.bossbar;
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = owner.targetMap;
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(5, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = (postProvidence ? BASIC_HEALTH_POST_PROVIDENCE : BASIC_HEALTH) * healthMulti;
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
