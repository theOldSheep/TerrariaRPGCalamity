package terraria.entity.boss.hardMode.lunaticCultist;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
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

public class LunaticIceMist extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.LUNATIC_CULTIST;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    LunaticCultist owner;
    boolean mistOrShard;

    static final double SPEED_MIST = 1.5, SPEED_SHARD = 2, ACC_MIST = 0.125;
    private void spawnIceShards() {
        Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        for (Vector shootSpeed : MathHelper.getCircularProjectileDirections(7, 1, 60,
                target, shootLoc, SPEED_SHARD)) {
            new LunaticIceMist(target, owner, false, shootLoc)
                    .getBukkitEntity().setVelocity(shootSpeed);
        }
    }
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
                // ice mist
                if (mistOrShard) {
                    Vector acc = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), ACC_MIST);
                    Vector velocity = bukkitEntity.getVelocity();
                    velocity.add(acc);
                    if (velocity.lengthSquared() < 1e-5)
                        velocity = acc;
                    velocity.normalize().multiply(SPEED_MIST);
                    bukkitEntity.setVelocity(velocity);

                    // timeout
                    if (ticksLived > 100)
                        die();
                    else if (ticksLived % 15 == 14)
                        spawnIceShards();
                }
                // ice shard
                else {
                    Vector velocity = bukkitEntity.getVelocity();
                    velocity.normalize().multiply(SPEED_SHARD);
                    bukkitEntity.setVelocity(velocity);
                    // timeout
                    if (ticksLived > 60)
                        die();
                }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public LunaticIceMist(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public LunaticIceMist(LunaticCultist owner) {
        this(owner.target, owner, true, ((LivingEntity) owner.getBukkitEntity()).getLocation());
    }
    public LunaticIceMist(Player summonedPlayer, LunaticCultist owner, boolean mistOrShard, Location spawnLoc) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.mistOrShard = mistOrShard;
        setCustomName(mistOrShard ? "冰球" : "冰雪碎块");
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
            attrMap.put("damage", 624d);
            attrMap.put("knockback", 4d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MAGIC);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // size and other properties
        {
            setSize(mistOrShard ? 4 : 2, false);
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
        if (mistOrShard) {
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
