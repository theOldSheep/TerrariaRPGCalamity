package terraria.entity.boss.hardMode.moonLord;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class MoonLordBackground extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.MOON_LORD;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    MoonLord owner;
    // other variables and AI
    enum MoonLordBackgroundType {
        BODY      ("月球领主身体", 35),
        LEFT_HAND ("月球领主左手", 25),
        RIGHT_HAND("月球领主右手", 25),
        HEAD      ("月球领主头"  , 30);
        final String entityName;
        final int entitySize;
        private MoonLordBackgroundType(String entityName, int entitySize) {
            this.entityName = entityName;
            this.entitySize = entitySize;
        }
    }
    MoonLordBackgroundType backgroundType;


    // default constructor to handle chunk unload
    public MoonLordBackground(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public MoonLordBackground(Player summonedPlayer, MoonLord owner, MoonLordBackgroundType backgroundType) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        this.owner = owner;
        Location spawnLoc = owner.getBukkitEntity().getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.backgroundType = backgroundType;
        setCustomName(backgroundType.entityName);
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
            attrMap.put("damage", 1d);
            attrMap.put("defence", 0d);
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
            setSize(backgroundType.entitySize, false);
            double health = 1;
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
        // set velocity to zero
        bukkitEntity.setVelocity(new Vector());
        // update facing direction
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // remove when the boss has been defeated
        if (! owner.isAlive()) {
            die();
        }
    }
}
