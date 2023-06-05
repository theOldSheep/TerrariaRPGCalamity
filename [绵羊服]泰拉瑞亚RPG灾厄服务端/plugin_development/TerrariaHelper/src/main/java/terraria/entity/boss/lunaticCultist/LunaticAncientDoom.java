package terraria.entity.boss.lunaticCultist;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
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

public class LunaticAncientDoom extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.LUNATIC_CULTIST;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    LunaticCultist owner;
    boolean doomOrEnd;
    Vector dir1, dir2;

    private void split() {
        Location spawnLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        // get directions
        Vector dirDirect = MathHelper.getDirection(spawnLoc, target.getEyeLocation(), 1);
        Vector dirOrth1 = new Vector();
        while (dirOrth1.lengthSquared() < 1e-5) {
            dirOrth1 = MathHelper.randomVector();
            dirOrth1.subtract(MathHelper.vectorProjection(dirDirect, dirOrth1));
        }
        dirOrth1.normalize();
        Vector dirOrth2 = dirDirect.getCrossProduct(dirOrth1);
        Vector[] directions = new Vector[] {
                dirDirect,
                dirDirect.clone().multiply(-1),
                dirOrth1,
                dirOrth1.clone().multiply(-1),
                dirOrth2,
                dirOrth2.clone().multiply(-1),
        };
        // spawn projectiles
        for (Vector velocity : directions) {
            new LunaticAncientDoom(owner, false, spawnLoc)
                    .getBukkitEntity().setVelocity(velocity);
        }
        die();
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
                if (doomOrEnd) {
                    double angle = ticksLived * 7.2;
                    double sinVal = MathHelper.xsin_degree(angle);
                    double cosVal = MathHelper.xcos_degree(angle);
                    Vector offset1 = dir1.clone();
                    offset1.multiply(sinVal);
                    Vector offset2 = dir2.clone();
                    offset2.multiply(cosVal);

                    bukkitEntity.teleport(target.getEyeLocation().add(offset1).add(offset2));
                    bukkitEntity.setVelocity(new Vector());

                    // timeout
                    if (ticksLived > 60)
                        split();
                }
                // prophecy's end
                else {
                    Vector velocity = bukkitEntity.getVelocity();
                    velocity.normalize().multiply(2);
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
    public LunaticAncientDoom(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public LunaticAncientDoom(LunaticCultist owner) {
        this(owner, true, ((LivingEntity) owner.getBukkitEntity()).getLocation());
    }
    public LunaticAncientDoom(LunaticCultist owner, boolean doomOrEnd, Location spawnLoc) {
        super( owner.getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        owner.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.doomOrEnd = doomOrEnd;
        setCustomName(doomOrEnd ? "远古噩运" : "预言之末");
        setCustomNameVisible(true);
        if (!doomOrEnd)
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
            attrMap.put("damage", doomOrEnd ? 1d : 540d);
            attrMap.put("knockback", 2d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MAGIC);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // size and other properties
        {
            setSize(doomOrEnd ? 5 : 3, false);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
            if (doomOrEnd) {
                dir1 = MathHelper.randomVector().multiply(12);
                dir2 = new Vector();
                while (dir2.lengthSquared() < 1e-5) {
                    dir2 = MathHelper.randomVector();
                    dir2.subtract(MathHelper.vectorProjection(dir1, dir2));
                }
                dir2.normalize().multiply(8);
            }
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
