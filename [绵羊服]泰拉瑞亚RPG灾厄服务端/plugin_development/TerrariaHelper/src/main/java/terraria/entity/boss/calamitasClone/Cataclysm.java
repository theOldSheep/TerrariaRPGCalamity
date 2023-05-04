package terraria.entity.boss.calamitasClone;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Cataclysm extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CALAMITAS_CLONE;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 39600 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static String bossName = "灾祸";
    CalamitasClone owner;
    boolean dashingPhase = false;
    int indexAI = -40;
    static HashMap<String, Double> attrMapFlameThrower;
    EntityHelper.ProjectileShootInfo shootInfoFlameThrower;
    static {
        attrMapFlameThrower = new HashMap<>();
        attrMapFlameThrower.put("damage", 468d);
        attrMapFlameThrower.put("knockback", 2d);
    }
    private void shootCursedFlame() {
        EntityHelper.ProjectileShootInfo shootInfo;
        double flameSpeed;
        shootInfo = shootInfoFlameThrower;
        flameSpeed = 1.75;
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfo.velocity = MathHelper.getDirection(
                shootInfo.shootLoc, target.getEyeLocation(), flameSpeed);
        EntityHelper.spawnProjectile(shootInfo);
    }
    private void attackAI() {
        // attack
        // follow and shoot flame
        if (indexAI < 60) {
            dashingPhase = false;
            // fly towards the player
            bukkitEntity.setVelocity(MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 1));
            // shoot flame
            shootCursedFlame();
        }
        // long dashes
        else {
            dashingPhase = true;
            if (indexAI >= 210)
                indexAI = -1;
            else if ((indexAI - 60) % 30 == 0) {
                bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                bukkitEntity.setVelocity(MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 3));
            }
        }
        indexAI ++;
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            target = owner.target;
            // disappear if no target is available
            if (target == null) {
                for (LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                return;
            }
            // if target is valid, attack
            else {
                attackAI();
            }
        }
        // face the player
        if (dashingPhase)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Cataclysm(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Cataclysm(Player summonedPlayer, CalamitasClone owner) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = owner.getBukkitEntity().getLocation().add(0, 12, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        setCustomName(bossName);
        setCustomNameVisible(true);
        addScoreboardTag("isMechanic");
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 396d);
            attrMap.put("damageTakenMulti", 0.85);
            attrMap.put("defence", 20d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, "Melee");
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init boss bar
        bossbar = owner.bossbar;
        EntityHelper.setMetadata(bukkitEntity, "bossbar", targetMap);
        // init target map
        {
            targetMap = owner.targetMap;
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
        }
        // init health and slime size
        {
            setSize(8, false);
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
        // shoot info's
        {
            shootInfoFlameThrower = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFlameThrower, "硫火喷射");
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
