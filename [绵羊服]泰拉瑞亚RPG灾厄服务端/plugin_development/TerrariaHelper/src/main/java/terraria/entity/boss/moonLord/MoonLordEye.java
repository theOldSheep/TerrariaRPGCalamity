package terraria.entity.boss.moonLord;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class MoonLordEye extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.MOON_LORD;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    enum MoonLordEyeLocation {
        LEFT_HAND (57375 * 2,  80d, 4,"月球领主手", MoonLordBackground.MoonLordBackgroundType.LEFT_HAND),
        RIGHT_HAND(57375 * 2,  80d, 4,"月球领主手", MoonLordBackground.MoonLordBackgroundType.RIGHT_HAND),
        HEAD      (103275 * 2, 100d,6,"月球领主"  , MoonLordBackground.MoonLordBackgroundType.HEAD);
        final double basicHealth, defence;
        final int eyeSize;
        final String eyeName;
        final MoonLordBackground.MoonLordBackgroundType backgroundType;
        private MoonLordEyeLocation(double basicHealth, double defence, int eyeSize, String eyeName, MoonLordBackground.MoonLordBackgroundType backgroundType) {
            this.basicHealth = basicHealth;
            this.defence = defence;
            this.eyeSize = eyeSize;
            this.eyeName = eyeName;
            this.backgroundType = backgroundType;
        }
    }
    int indexAI = -1;
    MoonLordEyeLocation eyeLocation;
    MoonLordBackground background;
    MoonLord owner;


    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            // if target is valid, attack
            if (indexAI >= 0) {
                indexAI++;
            }
            // update background location

        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // no collision dmg
    }
    // default constructor to handle chunk unload
    public MoonLordEye(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public MoonLordEye(Player summonedPlayer, MoonLord owner, MoonLordEyeLocation eyeLocation) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        spawnLoc.setY( spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        this.eyeLocation = eyeLocation;
        setCustomName(eyeLocation.eyeName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, 1);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 1d);
            attrMap.put("damageTakenMulti", 0.95);
            attrMap.put("defence", eyeLocation.defence);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = owner.targetMap;
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(eyeLocation.eyeSize, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = eyeLocation.basicHealth * healthMulti;
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
        // init background
        background = new MoonLordBackground(target, owner, eyeLocation.backgroundType);
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
