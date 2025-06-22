package terraria.entity.boss.preHardMode.wallOfFlesh;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.util.*;

public class WallOfFleshWall extends EntitySlime {
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.WALL_OF_FLESH;
    public static int SLIME_SIZE = 24;
    public static double SLIME_SIZE_BLOCKS = SLIME_SIZE * 0.5;
    // basic variables
    WallOfFleshMouth owner;
    double yCoord;
    float yawDir;

    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // removed after target is dead
            if (owner.target == null || ! owner.isAlive()) {
                setNoGravity(false);
                setHealth(0f);
            }
            // if target is valid, adjust position
            else {
                Vector offsetDir = owner.horizontalMoveDirection.clone().multiply(-SLIME_SIZE_BLOCKS * 0.5);
                Location hoverLoc = owner.getBukkitEntity().getLocation().add(offsetDir);
                hoverLoc.setY(yCoord);
                EntityMovementHelper.movementTP(bukkitEntity, hoverLoc);
                bukkitEntity.setVelocity(owner.getBukkitEntity().getVelocity());
            }
        }
        // facing
        this.yaw = yawDir;
    }
    // default constructor to handle chunk unload
    public WallOfFleshWall(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public WallOfFleshWall(WallOfFleshMouth owner, double yCoord) {
        super( owner.getWorld() );
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        spawnLoc.setY( yCoord );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        (owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName + "墙体");
        setCustomNameVisible(false);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        addScoreboardTag("noDamage");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init health and slime size
        {
            setSize(SLIME_SIZE, false);
            double health = 0.1;
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
        this.owner = owner;
        this.yCoord = yCoord;
        this.yawDir = (float) MathHelper.getVectorYaw(owner.horizontalMoveDirection);
        ((LivingEntity) bukkitEntity).addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0), true);
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