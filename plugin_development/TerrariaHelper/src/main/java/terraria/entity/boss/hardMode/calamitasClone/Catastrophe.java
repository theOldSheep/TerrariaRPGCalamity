package terraria.entity.boss.hardMode.calamitasClone;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
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

public class Catastrophe extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CALAMITAS_CLONE;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 33075 * 2, BASIC_HEALTH_BR = 228500 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static String bossName = "灾难";
    static HashMap<String, Double> attrMapDeathLaser, attrMapRapidFire;
    static AimHelper.AimHelperOptions laserAimHelper;
    static final double SPEED_LASER_1 = 1.85, SPEED_LASER_2 = 2;
    CalamitasClone owner;
    int indexAI = -60;
    EntityHelper.ProjectileShootInfo shootInfoGenericLaser;
    static {
        attrMapDeathLaser = new HashMap<>();
        attrMapDeathLaser.put("damage", 384d);
        attrMapDeathLaser.put("knockback", 2d);
        attrMapRapidFire = new HashMap<>();
        attrMapRapidFire.put("damage", 280d);
        attrMapRapidFire.put("knockback", 2d);

        laserAimHelper = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(SPEED_LASER_2)
                .setIntensity(0.5);
    }
    private void shootLaser(int state) {
        double laserSpeed = 0.75;
        Location targetLoc = target.getEyeLocation();
        switch (state) {
            case 1:
                shootInfoGenericLaser.attrMap = attrMapDeathLaser;
                laserSpeed = SPEED_LASER_1;
                break;
            case 2:
                shootInfoGenericLaser.attrMap = attrMapRapidFire;
                laserSpeed = SPEED_LASER_2;
                targetLoc = AimHelper.helperAimEntity(bukkitEntity, target, laserAimHelper);
                break;
        }
        shootInfoGenericLaser.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoGenericLaser.velocity = MathHelper.getDirection(
                shootInfoGenericLaser.shootLoc, targetLoc, laserSpeed);
        EntityHelper.spawnProjectile(shootInfoGenericLaser);
    }
    private void attackAI() {
        if (indexAI >= 0) {
            // alternate between firing death lasers and rapid lasers
            // hover above and shoot death laser
            if (indexAI < 40) {
                // hover above the player
                Location targetLoc = target.getLocation().add(0, 24, 0);
                Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                double velLen = velocity.length();
                double maxSpeed = 2;
                if (velLen > maxSpeed) {
                    velocity.multiply(maxSpeed / velLen);
                }
                bukkitEntity.setVelocity(velocity);
                // shoot laser
                if (indexAI % 10 == 0) {
                    shootLaser(1);
                }
            }
            // horizontally hover and rapid fire
            else {
                // hover horizontally
                Vector offset = bukkitEntity.getLocation().subtract(target.getLocation()).toVector();
                offset.setY(0);
                if (offset.lengthSquared() < 1e-5) {
                    offset = new Vector(1, 0, 0);
                }
                offset.normalize().multiply(24);
                Location targetLoc = target.getLocation().add(offset);
                Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                double velLen = velocity.length();
                double maxSpeed = 2;
                if (velLen > maxSpeed) {
                    velocity.multiply(maxSpeed / velLen);
                }
                bukkitEntity.setVelocity(velocity);
                // fire laser
                if (indexAI >= 90)
                    indexAI = -1;
                else if ((indexAI - 40) % 5 == 0) {
                    shootLaser(2);
                }
            }
        }
        indexAI ++;
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        boolean isFacingPlayer = true;
        {
            // update target
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
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Catastrophe(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Catastrophe(Player summonedPlayer, CalamitasClone owner) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        this.owner = owner;
        Location spawnLoc = owner.getBukkitEntity().getLocation().add(0, 6, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(bossName);
        setCustomNameVisible(true);
        addScoreboardTag("isMechanic");
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 432d);
            attrMap.put("damageTakenMulti", 0.85d);
            attrMap.put("defence", 20d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = owner.bossbar;
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = owner.targetMap;
            target = summonedPlayer;
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(8, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
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
            shootInfoGenericLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapDeathLaser,
                    DamageHelper.DamageType.MAGIC, "硫火飞弹");
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
