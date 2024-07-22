package terraria.entity.boss.preHardMode.wallOfFlesh;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

public class WallOfFleshWall extends EntitySlime {
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.WALL_OF_FLESH;
    public static int SLIME_SIZE = 48;
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
                setHealth(0);
            }
            // if target is valid, adjust position
            else {
                Vector offsetDir = owner.horizontalMoveDirection.clone().multiply(-SLIME_SIZE_BLOCKS * 0.5);
                Location hoverLoc = owner.getBukkitEntity().getLocation().add(offsetDir);
                hoverLoc.setY(yCoord);
                EntityHelper.movementTP(bukkitEntity, hoverLoc);
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
        bukkitEntity.
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        addScoreboardTag("noDamage");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
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