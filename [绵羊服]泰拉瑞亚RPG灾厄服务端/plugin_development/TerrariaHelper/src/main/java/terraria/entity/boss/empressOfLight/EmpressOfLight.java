package terraria.entity.boss.empressOfLight;

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

public class EmpressOfLight extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EMPRESS_OF_LIGHT;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.HALLOW;
    public static final double BASIC_HEALTH = 224910 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    enum AttackPhase {
        CHARGE, PRISMATIC_BOLT, PRISMATIC_BOLT_V2, SUN_DANCE, EVERLASTING_RAINBOW, ETHEREAL_LANCE, ETHEREAL_LANCE_V2;
    }
    AttackPhase attackPhase = AttackPhase.CHARGE;
    int indexAI = -40, indexAttackPhase = 0;
    double healthRatio = 1;
    boolean secondPhase = false, summonedDuringDay;

    EntityHelper.ProjectileShootInfo shootInfoStinger, shootInfoMissile, shootInfoNukeBarrage;
    static HashMap<String, Double> attrMapPrismaticBolt, attrMapSunDance, attrMapEverlastingRainbow, attrMapEtherealLance;
    static AttackPhase[] attackOrderPhaseOne, attackOrderPhaseTwo;
    static {
        attrMapPrismaticBolt = new HashMap<>();
        attrMapPrismaticBolt.put("damage", 468d);
        attrMapPrismaticBolt.put("knockback", 1.5d);
        attrMapSunDance = new HashMap<>();
        attrMapSunDance.put("damage", 540d);
        attrMapSunDance.put("knockback", 2d);
        attrMapEverlastingRainbow = new HashMap<>();
        attrMapEverlastingRainbow.put("damage", 468d);
        attrMapEverlastingRainbow.put("knockback", 2d);
        attrMapEtherealLance = new HashMap<>();
        attrMapEtherealLance.put("damage", 468d);
        attrMapEtherealLance.put("knockback", 2d);

        attackOrderPhaseOne = new AttackPhase[]{
            AttackPhase.PRISMATIC_BOLT,
            AttackPhase.CHARGE,
            AttackPhase.SUN_DANCE,
            AttackPhase.CHARGE,
            AttackPhase.EVERLASTING_RAINBOW,
            AttackPhase.PRISMATIC_BOLT,
            AttackPhase.CHARGE,
            AttackPhase.ETHEREAL_LANCE,
            AttackPhase.CHARGE,
            AttackPhase.EVERLASTING_RAINBOW,
        };
        attackOrderPhaseTwo = new AttackPhase[]{
            AttackPhase.ETHEREAL_LANCE_V2,
            AttackPhase.PRISMATIC_BOLT,
            AttackPhase.CHARGE,
            AttackPhase.EVERLASTING_RAINBOW,
            AttackPhase.PRISMATIC_BOLT,
            AttackPhase.SUN_DANCE,
            AttackPhase.ETHEREAL_LANCE,
            AttackPhase.CHARGE,
            AttackPhase.PRISMATIC_BOLT_V2,
        };
    }

    private void switchAttackPhase() {
        AttackPhase[] phaseCycle = secondPhase ? attackOrderPhaseTwo : attackOrderPhaseOne;
        attackPhase = phaseCycle[indexAttackPhase % phaseCycle.length];

        EntityHelper.tweakAttribute(attrMap, "damageMeleeMulti", "0.5", attackPhase == AttackPhase.CHARGE);

        indexAI = -1;
    }
    private void toSecondPhase() {
        indexAttackPhase = 0;
        indexAI = -40;
        BOSS_TYPE.playSummonSound(bukkitEntity.getLocation());
        secondPhase = true;
    }
    private Vector getHorizontalDirection() {
        Location targetLoc = target.getLocation();
        Location currLoc = bukkitEntity.getLocation();
        targetLoc.setY(currLoc.getY());
        return MathHelper.getDirection(currLoc, targetLoc, 1);
    }
    // attack AI
    private void AIPhaseCharge() {

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
            // if summoned during day time, disappear at night, vise versa.
            if (summonedDuringDay != WorldHelper.isDayTime(bukkitEntity.getWorld()))
                target = null;
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
                healthRatio = getHealth() / getMaxHealth();
                if (!secondPhase && healthRatio < 0.5)
                    toSecondPhase();
                // TODO
                switch (attackPhase) {
                    case CHARGE:
                        AIPhaseCharge();
                        break;
                }
                indexAI ++;
            }
        }
        // face the player
        if (attackPhase == AttackPhase.CHARGE)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public EmpressOfLight(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public EmpressOfLight(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = summonedPlayer.getLocation().add(0, 12, 0);
        spawnLoc.setY( spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        summonedDuringDay = WorldHelper.isDayTime(bukkitEntity.getWorld());
        setCustomName(BOSS_TYPE.msgName);
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
            attrMap.put("damage", 396d);
            attrMap.put("damageTakenMulti", 0.85);
            attrMap.put("defence", 100d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            // damage multiplier
            double damageMultiplier = summonedDuringDay ? 2d : 1d;
            attrMap.put("damageMulti", damageMultiplier);
            attrMapPrismaticBolt.put("damageMulti", damageMultiplier);
            attrMapSunDance.put("damageMulti", damageMultiplier);
            attrMapEverlastingRainbow.put("damageMulti", damageMultiplier);
            attrMapEtherealLance.put("damageMulti", damageMultiplier);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MAGIC);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.GOLEM.msgName, summonedPlayer, true, bossbar);
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
            shootInfoStinger = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapStingerMissile,
                    EntityHelper.DamageType.ARROW, "瘟疫导弹");
            shootInfoMissile = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapStingerMissile,
                    EntityHelper.DamageType.ROCKET, "瘟疫火箭");
            shootInfoNukeBarrage = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapNukeBarrage,
                    EntityHelper.DamageType.ROCKET, "瘟疫核弹");
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
