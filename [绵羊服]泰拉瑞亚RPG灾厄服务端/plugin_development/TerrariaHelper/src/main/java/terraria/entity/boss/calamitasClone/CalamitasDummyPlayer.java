package terraria.entity.boss.calamitasClone;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
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

public class CalamitasDummyPlayer extends EntityZombieHusk {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CALAMITAS_CLONE;
    static final EntityHelper.AimHelperOptions velocityHandler;
    static {
        velocityHandler = new EntityHelper.AimHelperOptions()
                .setAimMode(true)
                .setTicksOffset(1);
    }
    Player lastDisguisedPlayer = null;
    CalamitasClone owner;
    private void AI() {
        if (!owner.isAlive()) {
            die();
            return;
        }
        Player target = owner.target;
        // disappear if no target is available
        if (target == null) {
            die();
            return;
        }
        boolean duringBulletHell = owner.bulletHellProjectiles.size() > 0;
        if (duringBulletHell) {
            // disguise the monster
            if (lastDisguisedPlayer != target) {
                PlayerDisguise disguise = new PlayerDisguise(target);
                DisguiseAPI.disguiseEntity(bukkitEntity, disguise);
                setCustomName(target.getName());
                lastDisguisedPlayer = target;
            }
            // teleport the monster
            bukkitEntity.teleport(target.getLocation().add(owner.bullet_hell_orth_dir));
            // set velocity
            Vector velocity = EntityHelper.helperAimEntity(bukkitEntity, target, velocityHandler)
                    .subtract( ((LivingEntity) bukkitEntity).getEyeLocation() ).toVector();
            bukkitEntity.setVelocity(velocity);
        }
        else {
            lastDisguisedPlayer = null;
            this.locX = owner.locX;
            this.locY = -10;
            this.locZ = owner.locZ;
            this.motX = 0;
            this.motY = 0;
            this.motZ = 0;
        }
        // face the player
        EntityPlayer plyNMS = ((CraftPlayer) target).getHandle();
        this.yaw = plyNMS.yaw;
        this.pitch = plyNMS.pitch;
    }
    // default constructor to handle chunk unload
    public CalamitasDummyPlayer(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public CalamitasDummyPlayer(Player summonedPlayer, CalamitasClone owner) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        spawnLoc.setY(-10);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        setCustomName(" ");
        setCustomNameVisible(true);
        addScoreboardTag("noDamage");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init boss bar
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, owner.bossbar);
        // init health
        {
            double health = 1;
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
    }

    // rewrite AI
    @Override
    public void B_() {
        super.B_();
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
