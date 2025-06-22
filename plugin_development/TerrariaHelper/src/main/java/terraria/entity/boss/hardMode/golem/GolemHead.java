package terraria.entity.boss.hardMode.golem;

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

public class GolemHead extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.GOLEM;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.TEMPLE;
    public static final double BASIC_HEALTH = 45900 * 2, BASIC_HEALTH_BR = 180000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final String name = "石巨人头";
    static final double FLIGHT_SPEED = 2.5, SPEED_FIREBALL = 3, SPEED_BEAM = 2.25;
    static final AimHelper.AimHelperOptions aimHelperFireball, aimHelperBolt;
    static {
        aimHelperFireball = new AimHelper.AimHelperOptions()
                .setProjectileSpeed(SPEED_FIREBALL);
        aimHelperBolt = new AimHelper.AimHelperOptions()
                .setAimMode(true)
                .setTicksTotal(15)
                .setRandomOffsetRadius(1);
    }

    Golem owner;
    Vector offsetDir = new Vector(0, 5.5, 0);
    int indexAI = 0;
    EntityHelper.ProjectileShootInfo shootInfoFireball, shootInfoBeam, shootInfoInfernoBolt;

    private void shootProjectile(int type) {
        EntityHelper.ProjectileShootInfo shootInfo;
        Location shootTargetLocation;
        double projectileSpeed;
        switch (type) {
            case 1:
                shootInfo = shootInfoFireball;
                projectileSpeed = SPEED_FIREBALL;
                shootTargetLocation = AimHelper.helperAimEntity(bukkitEntity, target, aimHelperFireball);
                break;
            case 2:
                shootInfo = shootInfoBeam;
                projectileSpeed = SPEED_BEAM;
                shootTargetLocation = target.getEyeLocation();
                break;
            case 3:
                shootInfo = shootInfoInfernoBolt;
                projectileSpeed = 0;
                shootTargetLocation = AimHelper.helperAimEntity(bukkitEntity, target, aimHelperBolt);
                break;
            default:
                return;
        }
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        if (type == 3) {
            shootInfo.velocity = shootTargetLocation.subtract(shootInfo.shootLoc).toVector();
            shootInfo.velocity.multiply(1d / 15);
        }
        else {
            shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, shootTargetLocation, projectileSpeed);
        }
        EntityHelper.spawnProjectile(shootInfo);
    }
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
                if (owner.phaseAI < 3) {
                    bukkitEntity.setVelocity(owner.getBukkitEntity().getVelocity());
                    EntityMovementHelper.movementTP(bukkitEntity,
                            owner.getBukkitEntity().getLocation().add(offsetDir));
                }
                else {
                    bukkitEntity.setVelocity(MathHelper.getDirection(bukkitEntity.getLocation(),
                            target.getEyeLocation().add(0, 16, 0), FLIGHT_SPEED, true));
                }
                // spawn projectiles

                // fireball
                if (indexAI % 20 == 0)
                    shootProjectile(1);
                // beam
                if (indexAI % 8 == 0)
                    shootProjectile(2);
                // inferno bolt
                if (owner.phaseAI == 3 && indexAI % 50 == 0)
                    shootProjectile(3);
                indexAI ++;
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        if (owner.phaseAI < 3)
            terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public GolemHead(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public GolemHead(Player summonedPlayer, Golem owner) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = summonedPlayer.getLocation().add(0, 25, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.owner = owner;
        setCustomName(name);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("noDamage");
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, 5);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 384d);
            attrMap.put("defence", 40d);
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
            setSize(7, false);
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
        // shoot info
        {
            shootInfoFireball = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), Golem.attrMapFireball,
                    DamageHelper.DamageType.ARROW, "石巨人火球");
            shootInfoBeam = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), Golem.attrMapBeam,
                    DamageHelper.DamageType.MAGIC, "石巨人激光");
            shootInfoInfernoBolt = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), Golem.attrMapInfernoBolt,
                    DamageHelper.DamageType.ARROW, "爆裂火球");
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
