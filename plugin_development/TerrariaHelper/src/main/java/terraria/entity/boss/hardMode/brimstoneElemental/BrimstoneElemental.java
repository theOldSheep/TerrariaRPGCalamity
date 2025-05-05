package terraria.entity.boss.hardMode.brimstoneElemental;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class BrimstoneElemental extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.BRIMSTONE_ELEMENTAL;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.BRIMSTONE_CRAG;
    public static final double BASIC_HEALTH = 118080 * 2, BASIC_HEALTH_BR = 1872000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    public static final double BULLET_HELL_IMMEDIATE_START_DIST_SQR = 32 * 32;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final HashMap<String, Double> attrMapBrimstoneRay, attrMapBrimstoneDart, attrMapHellFireball;
    static final AimHelper.AimHelperOptions rayAimHelper;
    static final GenericHelper.ParticleLineOptions hintParticleOption, rayParticleOption;
    static final GenericHelper.StrikeLineOptions rayOption;
    static {
        rayAimHelper = new AimHelper.AimHelperOptions()
                .setAimMode(true)
                .setTicksTotal(20);

        hintParticleOption = new GenericHelper.ParticleLineOptions()
                .setLength(48)
                .setWidth(0.1, false)
                .setStepsize(2)
                .setVanillaParticle(false)
                .setParticleColor("b/brmh")
                .setTicksLinger(1);
        rayParticleOption = new GenericHelper.ParticleLineOptions()
                .setWidth(0.25)
                .setVanillaParticle(false)
                .setParticleColor("b/brm")
                .setTicksLinger(20);

        rayOption = new GenericHelper.StrikeLineOptions()
                .setLingerTime(20)
                .setLingerDelay(4)
                .setParticleInfo(rayParticleOption);

        attrMapBrimstoneRay = new HashMap<>();
        attrMapBrimstoneRay.put("damage", 684d);
        attrMapBrimstoneRay.put("knockback", 2d);
        attrMapBrimstoneDart = new HashMap<>();
        attrMapBrimstoneDart.put("damage", 384d);
        attrMapBrimstoneDart.put("knockback", 2d);
        attrMapHellFireball = new HashMap<>();
        attrMapHellFireball.put("damage", 468d);
        attrMapHellFireball.put("knockback", 2d);
    }
    public EntityHelper.ProjectileShootInfo psiDart, psiFireball, psiHellBlast;
    Location aimLoc = null;
    int indexAI = -40, phaseAI = 1;
    // 1: dart, 2: fireball, 3: hell blast
    private void shootProjectile(int type) {
        switch (type) {
            case 1: {
                double shootSpeed = phaseAI == 2 ? 1.4 : 1.25;
                psiDart.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                for (Vector direction : MathHelper.getCircularProjectileDirections(
                        9, 4, 90, target, psiDart.shootLoc, shootSpeed)) {
                    psiDart.velocity = direction;
                    EntityHelper.spawnProjectile(psiDart);
                }
                break;
            }
            case 2: {
                psiFireball.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                psiFireball.velocity = MathHelper.getDirection(psiFireball.shootLoc, target.getEyeLocation(), 1.6);
                EntityHelper.spawnProjectile(psiFireball);
                break;
            }
            case 3: {
                Location eyeLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                Vector direction = target.getEyeLocation().subtract(eyeLoc).toVector();
                double dirLen = direction.length();
                if (dirLen < 1e-5) {
                    direction = new Vector(0, 1, 0);
                    dirLen = 1;
                }
                // <= 25 block: 2, increases rapidly as distance increases
                double projectileSpeed = Math.max(dirLen * dirLen * 2 / (25 * 25), 2);
                direction.multiply(projectileSpeed / dirLen);
                psiHellBlast.velocity = direction;
                for (Vector offsetDir : MathHelper.getCircularProjectileDirections(
                        5, 3, 90, direction, 2.5)) {
                    psiHellBlast.shootLoc = eyeLoc.clone().add(offsetDir);
                    EntityHelper.spawnProjectile(psiHellBlast);
                }
                break;
            }
        }
    }
    private void changePhase() {
        int newPhase;
        double healthRatio = getHealth() / getMaxHealth();
        boolean belowHalfHealth = healthRatio < 0.5;
        switch (phaseAI) {
            case 1:
                if (belowHalfHealth) newPhase = Math.random() < 0.5 ? 2 : 3;
                else newPhase = 2;
                break;
            case 2:
                if (belowHalfHealth) newPhase = Math.random() < 0.5 ? 1 : 3;
                else newPhase = 1;
                break;
            case 3:
            default:
                newPhase = Math.random() < 0.5 ? 1 : 2;
        }
        indexAI = -1;
        phaseAI = newPhase;
        setCustomName(BOSS_TYPE.msgName + "§" + newPhase);
        switch (newPhase) {
            case 1:
                AttributeHelper.tweakAttribute(attrMap, "defence",
                        "" + (30 - attrMap.get("defence")), true);
                bossbar.color = BossBattle.BarColor.GREEN;
                break;
            case 2:
                AttributeHelper.tweakAttribute(attrMap, "defence",
                        "" + (120 - attrMap.get("defence")), true);
                bossbar.color = BossBattle.BarColor.RED;
                indexAI = -60;
                break;
            case 3:
                AttributeHelper.tweakAttribute(attrMap, "defence",
                        "" + (60 - attrMap.get("defence")), true);
                bossbar.color = BossBattle.BarColor.YELLOW;
                break;
        }
        bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
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
                // AI
                switch (phaseAI) {
                    // loosely hover above player, shoot fireballs over time
                    case 1: {
                        // movement
                        Location targetLoc = target.getLocation().add(0, 24, 0);
                        Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                        double velLen = velocity.length();
                        double maxSpeed = 2;
                        if (velLen > maxSpeed) {
                            velocity.multiply(maxSpeed / velLen);
                        }
                        bukkitEntity.setVelocity(velocity);
                        // projectiles
                        int fireballInterval = 10, fireballAmount = indexAI / fireballInterval;
                        if (indexAI >= 0 && indexAI % fireballInterval == 0) {
                            shootProjectile(2);
                            if (fireballAmount % 2 == 0)
                                shootProjectile(1);
                        }
                        // next phase after 10 fireballs
                        if (indexAI + 1 >= fireballInterval * 11) {
                            changePhase();
                        }
                        break;
                    }
                    // gain defence, shoot barrages of brimstone hell blast
                    case 2: {
                        // decelerate to a stop
                        double acc = 0.95;
                        motX *= acc;
                        motY *= acc;
                        motZ *= acc;
                        // when far away enough, immediately start shooting projectiles
                        if (indexAI < 0 && bukkitEntity.getLocation().distanceSquared(target.getLocation()) > BULLET_HELL_IMMEDIATE_START_DIST_SQR) {
                            indexAI = 0;
                        }
                        // shoot barrages of brimstone hell blast
                        if (indexAI >= 0) {
                            int shootInterval = 15;
                            if (indexAI % shootInterval == 0) {
                                shootProjectile(1);
                                shootProjectile(3);
                            }
                            // next phase after 5 shots
                            if (indexAI + 1 >= shootInterval * 6) {
                                changePhase();
                            }
                        }
                        break;
                    }
                    // only below 50% health: aim, then shoot a laser at the player
                    case 3: {
                        // movement
                        Location targetLoc = target.getLocation().add(0, 16, 0);
                        Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                        velocity.multiply(0.2);
                        bukkitEntity.setVelocity(velocity);
                        // ray
                        int cycleTime = 40, cycleAmount = indexAI / cycleTime, cycleIndex = indexAI % cycleTime;
                        if (cycleAmount < 3) {
                            if (cycleIndex <= 30) {
                                // update aimed location
                                if (cycleIndex <= 20)
                                    aimLoc = AimHelper.helperAimEntity(bukkitEntity, target, rayAimHelper);
                                // get direction
                                Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                                Vector direction = aimLoc.clone().subtract(shootLoc).toVector();
                                // warning sound
                                if (cycleIndex % 10 == 7) {
                                    bossbar.color = BossBattle.BarColor.RED;
                                    bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
                                }
                                if (cycleIndex % 10 == 0) {
                                    bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 10f, 1f);
                                    bossbar.color = BossBattle.BarColor.YELLOW;
                                    bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
                                }
                                // fire ray
                                if (cycleIndex == 30) {
                                    GenericHelper.handleStrikeLine(bukkitEntity, shootLoc,
                                            MathHelper.getVectorYaw(direction), MathHelper.getVectorPitch(direction), 48, 0.5,
                                            "", "", new ArrayList<>(), attrMapBrimstoneRay, rayOption);
                                }
                                // hint targeted location
                                else {
                                    // display fired ray
                                    hintParticleOption.setLength(48);
                                    GenericHelper.handleParticleLine(direction, shootLoc, hintParticleOption);
                                }
                            }
                        }
                        // next phase after 3 rays
                        else {
                            changePhase();
                        }
                        break;
                    }
                }
                indexAI ++;
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public BrimstoneElemental(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public BrimstoneElemental(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 30;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName + "§1");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 360d);
            attrMap.put("damageTakenMulti", 0.85d);
            attrMap.put("defence", 30d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
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
            setSize(8, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
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
            psiDart = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapBrimstoneDart,
                    DamageHelper.DamageType.MAGIC, "硫火飞弹");
            psiFireball = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapHellFireball,
                    DamageHelper.DamageType.MAGIC, "炼狱硫火球");
            psiHellBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapHellFireball,
                    DamageHelper.DamageType.MAGIC, "硫火亡魂");
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
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // update boss bar and dynamic DR
        terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, BOSS_TYPE);
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
