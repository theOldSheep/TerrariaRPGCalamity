package terraria.entity.boss.hardMode.leviathanAndAnahita;

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
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Anahita extends EntityZombieHusk {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.LEVIATHAN_AND_ANAHITA;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.OCEAN;
    public static final double BASIC_HEALTH = 99840 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static String bossName = "阿娜希塔";
    enum AIPhase {
        WATER_SPEAR, FROST_MIST, TREBLE_CLEF, BUBBLE, DASH, HOVER;
    }
    Leviathan leviathan;
    Vector dashVelocity = new Vector();
    AIPhase phaseAI = AIPhase.HOVER;
    int indexAI = -40;
    double healthRatio = 1d;
    static HashMap<String, Double> attrMapWaterSpear, attrMapFrostMist, attrMapTrebleClef, attrMapBubble;
    static final double SPEED_WATER_SPEAR = 1.5, SPEED_FROST_MIST = 0.9, SPEED_TREBLE_CLEF = 1.25, SPEED_BUBBLE = 1,
            SPEED_USUAL = 2.5, SPEED_DASH = 3;
    static final int DASH_DURATION = 40;
    EntityHelper.ProjectileShootInfo shootInfoWaterSpear, shootInfoFrostMist, shootInfoTrebleClef, shootInfoBubble;
    static {
        attrMapWaterSpear = new HashMap<>();
        attrMapWaterSpear.put("damage", 468d);
        attrMapWaterSpear.put("knockback", 2d);
        attrMapFrostMist = new HashMap<>();
        attrMapFrostMist.put("damage", 504d);
        attrMapFrostMist.put("knockback", 2d);
        attrMapTrebleClef = new HashMap<>();
        attrMapTrebleClef.put("damage", 540d);
        attrMapTrebleClef.put("knockback", 2d);
        attrMapBubble = new HashMap<>();
        attrMapBubble.put("damage", 540d);
        attrMapBubble.put("knockback", 2d);
        attrMapBubble.put("health", 1d);
        attrMapBubble.put("healthMax", 1d);
    }
    private void changePhase() {
        AIPhase lastPhase = phaseAI;
        // change phase
        if (leviathan.healthRatio > 0.7) {
            phaseAI = AIPhase.HOVER;
        }
        else {
            ArrayList<AIPhase> availablePhase = new ArrayList<>();
            availablePhase.add(AIPhase.WATER_SPEAR);
            availablePhase.add(AIPhase.FROST_MIST);
            availablePhase.add(AIPhase.TREBLE_CLEF);
            availablePhase.add(AIPhase.BUBBLE);
            availablePhase.add(AIPhase.DASH);
            availablePhase.remove(phaseAI);
            phaseAI = availablePhase.get((int) (Math.random() * availablePhase.size()));
        }
        // aftermath
        indexAI = -1;
        // invulnerable when leviathan has more than 70% health
        if (phaseAI == AIPhase.HOVER)
            addScoreboardTag("noDamage");
        else
            removeScoreboardTag("noDamage");
        // deal extra contact damage when dashing
        if (lastPhase == AIPhase.DASH)
            EntityHelper.tweakAttribute(attrMap, "damageMulti", "0.5", false);
        else if (phaseAI == AIPhase.DASH)
            EntityHelper.tweakAttribute(attrMap, "damageMulti", "0.5", true);
    }
    private void shootProjectiles() {
        EntityHelper.ProjectileShootInfo shootInfo;
        int shootAmount = 1;
        double projectileSpeed;
        switch (phaseAI) {
            case BUBBLE:
                shootInfo = shootInfoBubble;
                projectileSpeed = SPEED_BUBBLE;
                break;
            case FROST_MIST:
                shootInfo = shootInfoFrostMist;
                shootAmount = healthRatio < 0.7 ? 4 : 3;
                projectileSpeed = SPEED_FROST_MIST;
                break;
            case TREBLE_CLEF:
                shootInfo = shootInfoTrebleClef;
                shootAmount = healthRatio < 0.7 ? 9 : 6;
                projectileSpeed = SPEED_TREBLE_CLEF;
                break;
            case WATER_SPEAR:
                shootInfo = shootInfoWaterSpear;
                shootAmount = healthRatio < 0.7 ? 12 : 8;
                projectileSpeed = SPEED_WATER_SPEAR;
                break;
            default:
                return;
        }
        // shoot projectiles
        shootInfo.setLockedTarget(target);
        // bubble: shoot at eye location
        if (phaseAI == AIPhase.BUBBLE) {
            shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, target.getEyeLocation(), projectileSpeed);
            EntityHelper.spawnProjectile(shootInfo);
        }
        // other projectiles surrounds target
        else {
            for (Vector offset : MathHelper.getCircularProjectileDirections(
                    shootAmount, 2, 180, new Vector(0, 1, 0), 1)) {
                Vector offsetActual = offset.clone().multiply(16);
                shootInfo.shootLoc = target.getEyeLocation().add(offsetActual);
                shootInfo.velocity = offset.multiply(-projectileSpeed);
                EntityHelper.spawnProjectile(shootInfo);
            }
        }
    }
    private void attackAI() {
        healthRatio = getHealth() / getMaxHealth();
        // movement
        {
            Location targetLoc = null;
            switch (phaseAI) {
                case DASH: {
                    if (indexAI % DASH_DURATION < 10) {
                        targetLoc = bukkitEntity.getLocation().add(0, 1, 0);
                    }
                    break;
                }
                case BUBBLE: {
                    targetLoc = target.getEyeLocation().add(0, 16, 0);
                    break;
                }
                default: {
                    Location tempLoc = target.getLocation();
                    Location currLoc = ((LivingEntity) bukkitEntity).getLocation();
                    tempLoc.setY(currLoc.getY());
                    Vector offsetVec = MathHelper.getDirection(tempLoc, currLoc, 12);
                    offsetVec.setY(12);
                    targetLoc = target.getEyeLocation().add(offsetVec);
                }
            }
            if (targetLoc != null) {
                bukkitEntity.setVelocity(
                        MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, SPEED_USUAL, true));
            }
        }
        // attack and phase transition
        switch (phaseAI) {
            case BUBBLE: {
                if (indexAI % 5 == 0)
                    shootProjectiles();
                if (indexAI >= 50)
                    changePhase();
                break;
            }
            case DASH: {
                if (indexAI % DASH_DURATION == 10) {
                    dashVelocity = MathHelper.getDirection(
                            ((LivingEntity) bukkitEntity).getEyeLocation(),
                            target.getEyeLocation(),
                            SPEED_DASH);
                }
                bukkitEntity.setVelocity(dashVelocity);
                if (indexAI >= DASH_DURATION * 3)
                    changePhase();
                break;
            }
            case FROST_MIST:
            case TREBLE_CLEF:
            case WATER_SPEAR: {
                if (indexAI == 0)
                    shootProjectiles();
                if (indexAI >= 60)
                    changePhase();
                break;
            }
            case HOVER: {
                if (indexAI > 1)
                    changePhase();
                break;
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

                attackAI();
            }
        }
        // face the player
        if (phaseAI == AIPhase.DASH)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Anahita(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public Anahita(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, -6, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(bossName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        bukkitEntity.addScoreboardTag("noDamage");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 384d);
            attrMap.put("damageTakenMulti", 0.8);
            attrMap.put("defence", 50d);
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
                    getBukkitEntity(), BossHelper.BossType.PLANTERA.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health
        {
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            setBaby(false);
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // spawn leviathan
        this.leviathan = new Leviathan(summonedPlayer, this);
        // shoot info's
        {
            shootInfoWaterSpear = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapWaterSpear,
                    EntityHelper.DamageType.MAGIC, "水之枪");
            shootInfoFrostMist = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFrostMist,
                    EntityHelper.DamageType.MAGIC, "霜雾");
            shootInfoTrebleClef = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapTrebleClef,
                    EntityHelper.DamageType.MAGIC, "魅惑之音");
            shootInfoBubble = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapBubble,
                    EntityHelper.DamageType.MAGIC, "爆炸泡泡");
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar if both boss are defeated
        if (!leviathan.isAlive()) {
            bossbar.setVisible(false);
            BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        }
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);

            // send loot
            if (!leviathan.isAlive()) {
                terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
            }
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
