package terraria.entity.boss.plantera;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.empressOfLight.EmpressOfLight;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;

public class Plantera extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PLANTERA;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.JUNGLE;
    public static final double BASIC_HEALTH = 160650 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static double SEED_SPEED = 1.25, THORN_BALL_SPEED = 1, SPORE_GAS_SPEED = 1.5;
    static HashMap<String, Double> attrMapSeed, attrMapPoisonSeed, attrMapThornBall, attrMapSporeGas;
    static {
        attrMapSeed = new HashMap<>();
        attrMapSeed.put("damage", 372d);
        attrMapSeed.put("knockback", 1.5d);
        attrMapPoisonSeed = new HashMap<>();
        attrMapPoisonSeed.put("damage", 420d);
        attrMapPoisonSeed.put("knockback", 1.5d);
        attrMapThornBall = new HashMap<>();
        attrMapThornBall.put("damage", 468d);
        attrMapThornBall.put("knockback", 2.5d);
        attrMapSporeGas = new HashMap<>();
        attrMapSporeGas.put("damage", 468d);
        attrMapSporeGas.put("knockback", 1d);
    }

    EntityHelper.ProjectileShootInfo shootInfoSeed, shootInfoPoisonSeed, shootInfoThornBall, shootInfoSporeGas;
    Vector dashVelocity = new Vector();
    boolean secondPhase = false;
    int dashIndex = 0, indexAI = 0;

    private void phaseOneProjectile(double healthRatio) {
        Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        // seeds and poisoned seeds
        {
            int shootInterval;
            double poisonSeedChance;
            if (healthRatio < 0.5) {
                shootInterval = 2;
                poisonSeedChance = 0.5;
            } else if (healthRatio < 0.6) {
                shootInterval = 3;
                poisonSeedChance = 0.25;
            } else if (healthRatio < 0.7) {
                shootInterval = 5;
                poisonSeedChance = 0.1;
            } else {
                shootInterval = 7;
                poisonSeedChance = 0;
            }
            if (indexAI % shootInterval == 0) {
                EntityHelper.ProjectileShootInfo shootInfo = (Math.random() < poisonSeedChance) ? shootInfoPoisonSeed : shootInfoSeed;
                shootInfo.shootLoc = shootLoc;
                shootInfo.velocity = MathHelper.getDirection(shootLoc, target.getEyeLocation(), SEED_SPEED);
                EntityHelper.spawnProjectile(shootInfo);
            }
        }
        // spike ball
        {
            int shootInterval;
            if (healthRatio < 0.5) {
                shootInterval = 20;
            } else if (healthRatio < 0.6) {
                shootInterval = 30;
            } else if (healthRatio < 0.7) {
                shootInterval = 35;
            } else {
                shootInterval = 40;
            }
            if (indexAI % shootInterval == 0) {
                shootInfoThornBall.shootLoc = shootLoc;
                shootInfoThornBall.velocity = MathHelper.getDirection(shootLoc, target.getEyeLocation(), THORN_BALL_SPEED);
                EntityHelper.spawnProjectile(shootInfoThornBall);
            }
        }
    }
    private void phaseTwoProjectile(double healthRatio) {
        Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        // seeds
        if (indexAI % 20 == 0) {
            int perArcAmount;
            if (healthRatio < 0.1)
                perArcAmount = 6;
            else if (healthRatio < 0.15)
                perArcAmount = 5;
            else if (healthRatio < 0.2)
                perArcAmount = 4;
            else
                perArcAmount = 3;

            shootInfoSeed.shootLoc = shootLoc;
            for (Vector velocity : MathHelper.getCircularProjectileDirections(perArcAmount, 1, 45,
                    target, shootLoc, SEED_SPEED)) {
                shootInfoSeed.velocity = velocity;
                EntityHelper.spawnProjectile(shootInfoSeed);
            }
        }
        // spore cloud
        if (indexAI % 20 == 10) {
            int perArcAmount;
            if (healthRatio < 0.15)
                perArcAmount = 5;
            else
                perArcAmount = 4;

            shootInfoSporeGas.shootLoc = shootLoc;
            for (Vector velocity : MathHelper.getCircularProjectileDirections(perArcAmount, 1, 45,
                    target, shootLoc, SPORE_GAS_SPEED)) {
                shootInfoSporeGas.velocity = velocity;
                EntityHelper.spawnProjectile(shootInfoSporeGas);
            }
        }
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
                double healthRatio = getHealth() / getMaxHealth();
                // phase transition
                if (healthRatio < 0.5 && !secondPhase) {
                    secondPhase = true;
                    setCustomName(BOSS_TYPE.msgName + "§1");
                    // damage 414 -> 578
                    EntityHelper.tweakAttribute(attrMap, "damage", "164", true);
                    // defence 64 -> 20
                    EntityHelper.tweakAttribute(attrMap, "defence", "44", false);
                    for (int i = 0; i < 50; i ++) {
                        new PlanteraTentacle(target, this);
                    }
                }
                // follow player and dashes every 5 seconds
                {
                    dashIndex ++;
                    if (dashIndex >= 0) {
                        double dist = bukkitEntity.getLocation().distance(target.getLocation());
                        double speed = secondPhase ? Math.max(dist / 12.5, 0.8) : Math.max(dist / 17.5, 0.6);
                        boolean keepShorterDirection = true;
                        switch (dashIndex) {
                            case 80:
                                bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                                break;
                            case 100:
                                speed *= 2;
                                keepShorterDirection = false;
                                dashIndex = -10;
                                break;
                        }
                        dashVelocity = MathHelper.getDirection(
                                ((LivingEntity) bukkitEntity).getEyeLocation(),
                                target.getEyeLocation(),
                                speed, keepShorterDirection);
                    }
                    // maintain the speed of dash
                    bukkitEntity.setVelocity(dashVelocity);
                }
                // projectiles
                if (healthRatio > 0.25) {
                    phaseOneProjectile(healthRatio);
                }
                // second phase projectile
                else {
                    phaseTwoProjectile(healthRatio);
                }
            }
            indexAI ++;
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Plantera(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.HeightLayer.getHeightLayer(player.getLocation()) == WorldHelper.HeightLayer.CAVERN &&
                WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public Plantera(Player summonedPlayer) {
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
            attrMap.put("damage", 414d);
            attrMap.put("defence", 64d);
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
                    getBukkitEntity(), BossHelper.BossType.WALL_OF_FLESH.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(10, false);
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
        // projectile info
        {
            shootInfoSeed = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapSeed,
                    EntityHelper.DamageType.ARROW, "种子");
            shootInfoPoisonSeed = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPoisonSeed,
                    EntityHelper.DamageType.ARROW, "毒种子");
            shootInfoThornBall = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapThornBall,
                    EntityHelper.DamageType.ARROW, "刺球");
            shootInfoSporeGas = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapSporeGas,
                    EntityHelper.DamageType.MAGIC, "孢子云");
            shootInfoSporeGas.properties.put("liveTime", 300);
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
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
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
