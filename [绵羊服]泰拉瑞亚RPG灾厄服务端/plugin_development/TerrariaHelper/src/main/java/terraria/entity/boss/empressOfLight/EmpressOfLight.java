package terraria.entity.boss.empressOfLight;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;
import terraria.util.MathHelper;

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
    String[] particleColor;
    GenericHelper.ParticleLineOptions particleLineOptionsSunDance, particleLineOptionsEverlastingRainbow, particleLineOptionsLance;
    GenericHelper.StrikeLineOptions strikeLineOptionsSunDance, strikeLineOptionsEverlastingRainbow, strikeLineOptionsLance;
    Location attackLoc = null;
    int indexAI = 20, indexAttackPhase = 0;
    double healthRatio = 1;
    boolean secondPhase = false, summonedDuringDay;

    EntityHelper.ProjectileShootInfo shootInfoPrismaticBolt;
    static final double SPEED_PRISMATIC_BOLT = 1.5, SPEED_CHARGE_WINDUP = 0.75, SPEED_CHARGE = 2.5,
            SUN_DANCE_MAX_LENGTH = 32, SUN_DANCE_MAX_WIDTH = 1.5,
            ETERNAL_RAINBOW_WIDTH = 3;
    static final int EVERLASTING_RAINBOW_DURATION = 40;
    static HashMap<String, Double> attrMapPrismaticBolt, attrMapSunDance, attrMapEverlastingRainbow, attrMapEtherealLance;
    static AttackPhase[] attackOrderPhaseOne, attackOrderPhaseTwo;
    static String[] particleColorDay, particleColorNight;
    static {
        // attributes
        {
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
        }
        // attack orders
        {
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
        // particle colors
        {
            particleColorDay = new String[]{
                    "255|255|125",
            };
            particleColorNight = new String[]{
                    "255|0|0",
                    "255|255|0",
                    "0|255|0",
                    "0|255|255",
                    "0|0|255",
                    "255|0|255",
            };
        }
    }

    private int getAttackDuration() {
        int attackDuration = secondPhase ? 48 : 60;
        if (attackPhase == AttackPhase.SUN_DANCE) attackDuration *= 2;
        return attackDuration;
    }
    private void handleAttackOnPhaseChange() {
        switch (attackPhase) {
            case CHARGE:
                AIPhaseCharge();
                break;
            case PRISMATIC_BOLT:
                AIPhasePrismaticBolt();
                break;
            case PRISMATIC_BOLT_V2:
                AIPhasePrismaticBoltV2();
                break;
            case SUN_DANCE:
                AIPhaseSunDance();
                break;
            case ETHEREAL_LANCE:
                AIPhaseEtherealLance();
                break;
            case ETHEREAL_LANCE_V2:
                AIPhaseEtherealLanceV2();
                break;
            case EVERLASTING_RAINBOW:
                AIPhaseEverlastingRainbow();
                break;
        }
    }
    private void switchAttackPhase() {
        AttackPhase lastPhase = attackPhase;
        AttackPhase[] phaseCycle = secondPhase ? attackOrderPhaseTwo : attackOrderPhaseOne;
        attackPhase = phaseCycle[indexAttackPhase % phaseCycle.length];
        // damage increase when start dash, back to normal when finishing
        if (attackPhase == AttackPhase.CHARGE)
            EntityHelper.tweakAttribute(attrMap, "damageMeleeMulti", "0.5", true);
        if (lastPhase == AttackPhase.CHARGE)
            EntityHelper.tweakAttribute(attrMap, "damageMeleeMulti", "0.5", false);
        // play sound
        String soundName;
        switch (attackPhase) {
            case CHARGE:
                soundName = "DA";
                break;
            case EVERLASTING_RAINBOW:
                soundName = "ER";
                break;
            case PRISMATIC_BOLT:
                soundName = "PB";
                break;
            case PRISMATIC_BOLT_V2:
                soundName = "PBV2";
                break;
            case ETHEREAL_LANCE:
                soundName = "EL";
                break;
            case ETHEREAL_LANCE_V2:
                soundName = "ELV2";
                break;
            case SUN_DANCE:
                soundName = "SD";
                break;
            default:
                soundName = "";
        }
        bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.eol." + soundName, 10, 1);
        // assigns a new attack location
        {
            attackLoc = target.getLocation();
            double angle = Math.random() * 360;
            Vector offset = MathHelper.vectorFromYawPitch_quick(angle, 0);
            if (attackPhase == AttackPhase.CHARGE) {
                offset.multiply(16);
            }
            else {
                offset.multiply(12);
                offset.setY(8 + Math.random() * 4);
            }
            attackLoc.add(offset);
        }
        // attack
        handleAttackOnPhaseChange();
        // index reset
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
    private void AIPhasePrismaticBolt() {
        bukkitEntity.teleport(attackLoc);
        bukkitEntity.setVelocity(new Vector());
        for (int shootIndex = 0; shootIndex < 15; shootIndex ++) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (this.isAlive()) {
                    shootInfoPrismaticBolt.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                    Location targetLoc = target.getEyeLocation().add(
                            MathHelper.randomVector());
                    shootInfoPrismaticBolt.velocity = MathHelper.getDirection(shootInfoPrismaticBolt.shootLoc, targetLoc, SPEED_PRISMATIC_BOLT);
                    EntityHelper.spawnProjectile(shootInfoPrismaticBolt);
                }
            }, shootIndex * 2);
        }
    }
    private void AIPhasePrismaticBoltV2() {
        bukkitEntity.teleport(attackLoc);
        bukkitEntity.setVelocity(new Vector());
        for (int shootIndex = 0; shootIndex < 25; shootIndex ++) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (this.isAlive()) {
                    shootInfoPrismaticBolt.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                    shootInfoPrismaticBolt.velocity = MathHelper.randomVector();
                    shootInfoPrismaticBolt.velocity.multiply(SPEED_PRISMATIC_BOLT);
                    EntityHelper.spawnProjectile(shootInfoPrismaticBolt);
                }
            }, shootIndex * 2);
        }
    }
    private void AIPhaseCharge() {
        bukkitEntity.teleport(attackLoc);
        Vector velocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(),
                target.getEyeLocation(), 1);
        Vector velWindup = velocity.clone().multiply(SPEED_CHARGE_WINDUP);
        bukkitEntity.setVelocity(velWindup);
        // dash after 1 second
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            if (this.isAlive()) {
                Vector velCharge = velocity.clone();
                velCharge.multiply(SPEED_CHARGE);
                bukkitEntity.setVelocity(velCharge);
            }
        }, 20);
        // after a certain time interval, reset the velocity to zero vector
        int waitTime = secondPhase ? 45 : 55;
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            if (this.isAlive()) {
                bukkitEntity.setVelocity(new Vector());
            }
        }, waitTime);
    }
    private void AIPhaseSunDance() {
        bukkitEntity.teleport(attackLoc);
        bukkitEntity.setVelocity(new Vector());
        int rays = summonedDuringDay ? 8 : 6;
        double angle = 0, angleOffset = 360 * 0.7 / rays;
        int interval = secondPhase ? 20 : 30;
        // three waves of sun dance
        for (int i = 0; i <= 2; i ++) {
            int finalI = i;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (this.isAlive()) {
                    handleSunDanceSingle(angle + angleOffset * finalI, rays, 1);
                }
            }, interval * i);
        }
    }
    private void handleSunDanceSingle(double angle, int rays, int index) {
        // 360 / 60 for display, 360 / 90 while it deals damage
        boolean hasDamage = index > 10;
        double angleChangeAmount = (hasDamage ? 9d : 6d) / rays;
        // teleport to vertically align with the target
        Location targetLoc = bukkitEntity.getLocation();
        targetLoc.setY(target.getLocation().getY());
        bukkitEntity.teleport(targetLoc);
        // handle particle/damage
        double sizeMultiplier = Math.sqrt(1 - Math.abs(20d - index) / 10);
        if (sizeMultiplier > 1e-5) {
            double rayAngleOffset = 360d / rays, angleCurrentRay = angle;
            for (int rayIdx = 0; rayIdx < rays; rayIdx++) {
                angleCurrentRay += rayAngleOffset;
                double length = SUN_DANCE_MAX_LENGTH * sizeMultiplier, width = SUN_DANCE_MAX_WIDTH * sizeMultiplier;
                if (hasDamage)
                    GenericHelper.handleStrikeLine(bukkitEntity, bukkitEntity.getLocation(), angleCurrentRay, 0,
                            length, width, "", "",
                            new ArrayList<>(), attrMapSunDance, strikeLineOptionsSunDance);
                else {
                    particleLineOptionsSunDance
                            .setWidth(width)
                            .setLength(length);
                    GenericHelper.handleParticleLine(MathHelper.vectorFromYawPitch_quick(angleCurrentRay, 0),
                            bukkitEntity.getLocation(), particleLineOptionsSunDance);
                }
            }
        }
        // next tick
        if (index < 30)
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (this.isAlive()) {
                    handleSunDanceSingle(angle + angleChangeAmount, rays, index + 1);
                }
            }, 1);
    }
    private void AIPhaseEverlastingRainbow() {
        bukkitEntity.teleport(attackLoc);
        bukkitEntity.setVelocity(new Vector());

        for (int i = 0; i < 13; i ++) {
            handleEverlastingRainbowSingle(360d / i, 0,1, bukkitEntity.getLocation());
        }
    }
    private void handleEverlastingRainbowSingle(double angle, double range, int index, Location lastLoc) {
        // calculate next location
        double nextAngle = angle + 5;
        double nextRange = range + (index < 60 ?
                ((60 - index) * 0.0035) : ((140 - index) * -0.0035));
        Vector offsetDir = MathHelper.vectorFromYawPitch_quick(nextAngle, 0);
        offsetDir.multiply(nextRange);
        Location nextLoc = bukkitEntity.getLocation().add(offsetDir);
        // handle lingering damage and particle
        Vector strikeDirection = nextLoc.clone().subtract(lastLoc).toVector();
        double strikeYaw = MathHelper.getVectorYaw(strikeDirection);
        double strikePitch = MathHelper.getVectorPitch(strikeDirection);
        GenericHelper.ParticleLineOptions particleLineOptionsTemp = new GenericHelper.ParticleLineOptions()
                .setParticleColor(GenericHelper.getStringFromColor( GenericHelper.getInterpolateColor(
                        (double) index / 121, particleLineOptionsEverlastingRainbow.getParticleColorObjects() ) ))
                .setTicksLinger(EVERLASTING_RAINBOW_DURATION);
        strikeLineOptionsEverlastingRainbow.setParticleInfo(particleLineOptionsTemp);
        GenericHelper.handleStrikeLine(bukkitEntity, lastLoc, strikeYaw, strikePitch,
                strikeDirection.length(), ETERNAL_RAINBOW_WIDTH, "", "",
                new ArrayList<>(), attrMapEverlastingRainbow, strikeLineOptionsEverlastingRainbow);
        // next tick
        if (index < 120)
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                if (this.isAlive()) {
                    handleEverlastingRainbowSingle(nextAngle, nextRange, index + 1, nextLoc);
                }
            }, 2);
    }
    private void AIPhaseEtherealLance() {
        bukkitEntity.teleport(attackLoc);
        bukkitEntity.setVelocity(new Vector());
    }
    private void AIPhaseEtherealLanceV2() {
        bukkitEntity.teleport(attackLoc);
        bukkitEntity.setVelocity(new Vector());
    }
    private void handleEtherealLanceSingle() {

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
                // attack method alternation
                if (indexAI > getAttackDuration())
                    switchAttackPhase();
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
        particleColor = summonedDuringDay ? particleColorDay : particleColorNight;
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
            setSize(8, false);
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
            shootInfoPrismaticBolt = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapPrismaticBolt,
                    EntityHelper.DamageType.MAGIC, "水晶矢");
        }
        // particle and strike options
        {
            particleLineOptionsSunDance = new GenericHelper.ParticleLineOptions()
                    .setParticleColor(particleColor)
                    .setTicksLinger(1);
            strikeLineOptionsSunDance = new GenericHelper.StrikeLineOptions()
                    .setParticleInfo(particleLineOptionsSunDance)
                    .setThruWall(true);
            particleLineOptionsEverlastingRainbow = new GenericHelper.ParticleLineOptions()
                    .setParticleColor(particleColor)
                    .setTicksLinger(EVERLASTING_RAINBOW_DURATION);
            strikeLineOptionsEverlastingRainbow = new GenericHelper.StrikeLineOptions()
                    .setParticleInfo(particleLineOptionsEverlastingRainbow)
                    .setLingerDelay(2)
                    .setLingerTime(EVERLASTING_RAINBOW_DURATION)
                    .setThruWall(true);
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
            // terra prism drops if killed during day
            if (summonedDuringDay)
                ItemHelper.dropItem(bukkitEntity.getLocation(),
                        ItemHelper.getItemFromDescription("泰拉棱镜"));
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
