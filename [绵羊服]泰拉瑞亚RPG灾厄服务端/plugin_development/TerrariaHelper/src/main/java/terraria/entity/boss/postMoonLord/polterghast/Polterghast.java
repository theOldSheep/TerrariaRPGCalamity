package terraria.entity.boss.postMoonLord.polterghast;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
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
import java.util.UUID;

public class Polterghast extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.POLTERGHAST;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.DUNGEON;
    public static final double BASIC_HEALTH = 1008000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final double SPEED_PROJECTILE_FOLLOW = 0.6, HOVER_SPEED = 2.0, HOVER_DIST = 24.0, DASH_SPEED = 3.0;
    static final double SPEED_PROJECTILE_CLUSTER = 1.25, SPEED_PROJECTILE = 2.0;
    static HashMap<String, Double> attrMapProjectile;
    static EntityHelper.AimHelperOptions aimHelperDash, aimHelperDashAcceleration;
    EntityHelper.ProjectileShootInfo shootInfoCluster, shootInfoProjectile;
    static {
        aimHelperDash = new EntityHelper.AimHelperOptions()
                .setAccelerationMode(false)
                .setProjectileSpeed(DASH_SPEED);
        aimHelperDashAcceleration = new EntityHelper.AimHelperOptions()
                .setAccelerationMode(true)
                .setProjectileSpeed(DASH_SPEED);

        attrMapProjectile = new HashMap<>();
        attrMapProjectile.put("damage", 864d);
        attrMapProjectile.put("knockback", 1.5d);
    }
    int indexAI = 0, AIPhase = 0;
    Vector velocity = new Vector();
    ArrayList<Entity> spawnedClusterProjectiles = new ArrayList<>();
    PolterghastClone clone = null;


    private void phaseOne() {
        // projectile, dash & windup 30 ticks
        if (indexAI < 90) {
            if (indexAI % 30 == 29) {
                shootProjectiles( ((LivingEntity) bukkitEntity).getEyeLocation());
            }
            velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), SPEED_PROJECTILE_FOLLOW);
        } else if (indexAI < 120) {
            velocity = hoverAbovePlayer(bukkitEntity, HOVER_DIST);
        } else if (indexAI < 150) {
            if (indexAI == 120) {
                velocity = dashTowardsPlayer(bukkitEntity);
            }
            if (indexAI > 135)
                velocity.multiply(0.95);
        } else {
            if (getHealth() / getMaxHealth() <= 0.9) {
                AIPhase = 1;
                bossbar.color = BossBattle.BarColor.YELLOW;
                bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
                setCustomName(BOSS_TYPE.msgName + "§1");
                EntityHelper.tweakAttribute(attrMap, "damage", "152", true);
                EntityHelper.tweakAttribute(attrMap, "defence", "36", false);
            }
            indexAI = -1;
        }
    }

    private void phaseTwo() {
        // projectile 20 ticks, dash & windup 25 ticks
        if (indexAI < 40) {
            if (indexAI >= 0 && indexAI % 20 == 19) {
                shootProjectiles( ((LivingEntity) bukkitEntity).getEyeLocation());
            }
            velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), SPEED_PROJECTILE_FOLLOW);
        } else if (indexAI < 65) {
            velocity = hoverAbovePlayer(bukkitEntity, HOVER_DIST);
        } else if (indexAI < 115) {
            if (indexAI == 65 || indexAI == 90) {
                velocity = dashTowardsPlayer(bukkitEntity);
            }
        } else {
            if (getHealth() / getMaxHealth() <= 0.6) {
                AIPhase = 2;
                bossbar.color = BossBattle.BarColor.PINK;
                bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
                setCustomName(BOSS_TYPE.msgName + "§2");
                EntityHelper.tweakAttribute(attrMap, "damage", "154", true);
                EntityHelper.tweakAttribute(attrMap, "defence", "54", false);
                // rest for 5 seconds (very long as the player needs to align the boss correctly)
                indexAI = -100;
            }
            else {
                indexAI = -1;
            }
        }
    }

    private void phaseThree() {
        if (clone == null) {
            clone = new PolterghastClone(this);
        }

        // clone alive: cluster->30->projectiles & windup 30 -> dash 30
        if (clone.isAlive()) {
            if (indexAI < 60) {
                if (indexAI == 29) {
                    shootClusterProjectiles( ((LivingEntity) bukkitEntity).getEyeLocation());
                    shootClusterProjectiles( ((LivingEntity) clone.getBukkitEntity()).getEyeLocation());
                }
                else if (indexAI == 59) {
                    shootProjectiles( ((LivingEntity) bukkitEntity).getEyeLocation());
                    shootProjectiles( ((LivingEntity) clone.getBukkitEntity()).getEyeLocation());
                    for (Entity eCluster : spawnedClusterProjectiles) {
                        shootProjectiles(eCluster.getLocation());
                    }
                    spawnedClusterProjectiles.clear();
                }
                velocity = MathHelper.getDirection(bukkitEntity.getLocation(),
                        target.getLocation().subtract(0, HOVER_DIST, 0), SPEED_PROJECTILE_FOLLOW);
                clone.velocity = MathHelper.getDirection(clone.getBukkitEntity().getLocation(),
                        target.getLocation().add(0, HOVER_DIST, 0), SPEED_PROJECTILE_FOLLOW);
            } else if (indexAI < 90) {
                velocity = hoverAbovePlayer(bukkitEntity, HOVER_DIST);
                clone.velocity = hoverAbovePlayer(clone.getBukkitEntity(), -HOVER_DIST);
            } else if (indexAI < 120) {
                if (indexAI == 90) {
                    velocity = dashTowardsPlayer(bukkitEntity);
                    clone.velocity = dashTowardsPlayer(clone.getBukkitEntity());
                }
            } else {
                indexAI = -1;
            }
        }
        else {
            AIPhase = 3;
            bossbar.color = BossBattle.BarColor.RED;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
            // rest for 2.5 seconds for the player to get ready
            indexAI = -50;
        }
    }

    private void phaseFour() {
        // clone defeated: projectiles, dash 20, windup 10
        if (indexAI < 20) {
            if (indexAI == 19) {
                shootProjectiles( ((LivingEntity) bukkitEntity).getEyeLocation());
            }
            velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), SPEED_PROJECTILE_FOLLOW);
        } else if (indexAI < 30) {
            velocity = hoverAbovePlayer(bukkitEntity, HOVER_DIST);
        } else if (indexAI < 50) {
            if (indexAI == 30) {
                velocity = dashTowardsPlayer(bukkitEntity);
            }
        } else {
            indexAI = -1;
        }
    }


    // helper methods

    private void shootClusterProjectiles(Location shootLoc) {
        shootInfoCluster.shootLoc = shootLoc;
        shootInfoCluster.velocity = MathHelper.getDirection(shootInfoCluster.shootLoc, target.getEyeLocation(), SPEED_PROJECTILE_CLUSTER);
        spawnedClusterProjectiles.add( EntityHelper.spawnProjectile(shootInfoCluster) );
    }
    private void shootProjectiles(Location shootLoc) {
        shootInfoProjectile.shootLoc = shootLoc;
        for (Vector projVel : MathHelper.getEvenlySpacedProjectileDirections(
                10, 31, target, shootInfoProjectile.shootLoc, SPEED_PROJECTILE)) {
            shootInfoProjectile.velocity = projVel;
            EntityHelper.spawnProjectile(shootInfoProjectile);
        }
    }

    private Vector hoverAbovePlayer(Entity entity, double offset) {
        Location playerLocation = target.getLocation();
        Location bossLocation = entity.getLocation();
        Vector offsetDirection = bossLocation.subtract(playerLocation).toVector();
        offsetDirection.setY(0);
        MathHelper.setVectorLength(offsetDirection, Math.abs(offset) );
        offsetDirection.setY(offset > 0 ? (offset + 2) : offset);
        return MathHelper.getDirection(entity.getLocation(), playerLocation.add(offsetDirection), HOVER_SPEED);
    }

    private Vector dashTowardsPlayer(Entity entity) {
        Location dashLoc;
        switch (AIPhase) {
            case 2:
                dashLoc = EntityHelper.helperAimEntity(bukkitEntity, target, aimHelperDash);
                break;
            case 3:
                dashLoc = EntityHelper.helperAimEntity(bukkitEntity, target, aimHelperDashAcceleration);
                break;
            default:
                dashLoc = target.getEyeLocation();
        }

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 10f, 1f);
        return MathHelper.getDirection( ((LivingEntity) entity).getEyeLocation(), dashLoc, DASH_SPEED);
    }




    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
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
                // increase player aggro duration
                targetMap.get(target.getUniqueId()).addAggressionTick();

                // AI phase is updated within each AI phase to prevent interrupting AI feature

                switch (AIPhase) {
                    case 0:
                        phaseOne();
                        break;
                    case 1:
                        phaseTwo();
                        break;
                    case 2:
                        phaseThree();
                        break;
                    case 3:
                        phaseFour();
                        break;
                }

                indexAI ++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        // facing
        this.yaw = (float) MathHelper.getVectorYaw( velocity );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Polterghast(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public Polterghast(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 768d);
            attrMap.put("damageTakenMulti", 0.8);
            attrMap.put("defence", 180d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(16, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoCluster = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapProjectile,
                    EntityHelper.DamageType.MAGIC, "幽花子母弹");
            shootInfoProjectile = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapProjectile,
                    EntityHelper.DamageType.MAGIC, "幽花弹幕");
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);

            // send loot
            terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
        }
    }
    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // update boss bar and dynamic DR
        terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, BOSS_TYPE);
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