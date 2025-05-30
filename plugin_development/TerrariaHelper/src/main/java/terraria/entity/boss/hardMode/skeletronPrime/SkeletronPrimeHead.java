package terraria.entity.boss.hardMode.skeletronPrime;

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
import terraria.gameplay.EventAndTime;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SkeletronPrimeHead extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SKELETRON_PRIME;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 64260 * 3, BASIC_HEALTH_BR = 230000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    enum PhaseAI {
        SKULL, ROCKET, ROTATE;
    }
    PhaseAI phase = PhaseAI.SKULL;
    int indexAI = 0, handsAlive = 4;
    boolean spinning = false;
    static final double SPINNING_DMG = 612, REGULAR_DMG = 306;
    public EntityHelper.ProjectileShootInfo shootInfoSkull, shootInfoRocket, shootInfoLaser;
    static HashMap<String, Double> attrMapSkull, attrMapRocket, attrMapLaser;
    static {
        attrMapSkull = new HashMap<>();
        attrMapSkull.put("damage", 372d);
        attrMapSkull.put("knockback", 2d);
        attrMapRocket = new HashMap<>();
        attrMapRocket.put("damage", 444d);
        attrMapRocket.put("knockback", 2d);
        attrMapLaser = new HashMap<>();
        attrMapLaser.put("damage", 372d);
        attrMapLaser.put("knockback", 2d);
    }
    private void shootSkull() {
        shootInfoSkull.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoSkull.velocity = MathHelper.getDirection(
                shootInfoSkull.shootLoc, target.getEyeLocation(), 1);
        shootInfoSkull.setLockedTarget(target);
        EntityHelper.spawnProjectile(shootInfoSkull);
    }
    private void shootRocket() {
        shootInfoRocket.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoRocket.velocity = MathHelper.getDirection(
                shootInfoRocket.shootLoc, target.getEyeLocation(), 1.5);
        shootInfoRocket.setLockedTarget(target);
        EntityHelper.spawnProjectile(shootInfoRocket);
    }
    private void shootLaser() {
        shootInfoLaser.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        for (Vector velocity : MathHelper.getCircularProjectileDirections(
                7, 3, 75, target, shootInfoLaser.shootLoc, 2)) {
            shootInfoLaser.velocity = velocity;
            EntityHelper.spawnProjectile(shootInfoLaser);
        }
    }
    private void switchPhase(double healthRatio) {
        PhaseAI newPhase;
        switch (phase) {
            case SKULL:
                if (healthRatio < 0.25)
                    newPhase = PhaseAI.ROCKET;
                else
                    newPhase = PhaseAI.ROTATE;
                break;
            case ROCKET:
                newPhase = PhaseAI.ROTATE;
                break;
            case ROTATE:
                newPhase = PhaseAI.SKULL;
                break;
            default:
                return;
        }
        // roar for warning
        switch (newPhase) {
            case SKULL:
            case ROTATE:
                bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1f);
        }
        // name etc.
        if (newPhase == PhaseAI.ROTATE) {
            setCustomName(BOSS_TYPE.msgName + "§1");
            spinning = true;
        }
        else {
            setCustomName(BOSS_TYPE.msgName);
            spinning = false;
        }
        phase = newPhase;
        indexAI = -1;
    }
    private void spawnHands(Player targetPlayer) {
        for (int i = 0; i < 4; i ++) {
            new SkeletronPrimeHand(targetPlayer, bossParts, this, i);
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
                    IGNORE_DISTANCE, terraria.entity.boss.BossHelper.TimeRequirement.NIGHT, BIOME_REQUIRED, targetMap.keySet());
            // disappear if no target is available
            if (target == null) {
                for (LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                return;
            }
            // increase player aggro duration
            targetMap.get(target.getUniqueId()).addAggressionTick();
            // AI
            double healthRatio = getHealth() / getMaxHealth();
            handsAlive = 0;
            // update attribute according to current state
            {
                for (int i = 1; i < bossParts.size(); i++)
                    if (!bossParts.get(i).isDead()) handsAlive++;
                if (WorldHelper.isDayTime(bukkitEntity.getWorld()) && !EventAndTime.isBossRushActive()) {
                    attrMap.put("defence", 9999d);
                    attrMap.put("damage", 9999d);
                }
                // if hands are alive, the head should get increased defence.
                else {
                    attrMap.put("damage", spinning ? SPINNING_DMG : REGULAR_DMG);
                    attrMap.put("defence", (spinning ? 2d : 1d) * (48 + 16 * handsAlive));
                }
            }
            // attack

            // if hands are not alive
            if (handsAlive == 0) {
                switch (phase) {
                    case SKULL: {
                        Location targetLoc = target.getLocation().add(
                                MathHelper.xsin_degree(indexAI * 9) * 12, 16, MathHelper.xcos_degree(indexAI * 9) * 12);
                        Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                        bukkitEntity.setVelocity(velocity);
                        if (indexAI % 4 == 0)
                            shootSkull();
                        if (indexAI > 40)
                            switchPhase(healthRatio);
                        break;
                    }
                    case ROCKET: {
                        Location targetLoc = target.getLocation().add(0, 16, 0);
                        Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                        velocity.multiply(0.1);
                        bukkitEntity.setVelocity(velocity);
                        if (indexAI % 8 == 0)
                            shootRocket();
                        if (indexAI > 50)
                            switchPhase(healthRatio);
                        break;
                    }
                    case ROTATE: {
                        Vector vHead = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                        vHead.multiply(1d / 12);
                        double vHeadLen = vHead.length();
                        if (vHeadLen > 2)
                            vHead.multiply(2d / vHeadLen);
                        bukkitEntity.setVelocity(vHead);
                        // shoot lasers
                        if (healthRatio < 0.5 && indexAI % 10 == 0) {
                            shootLaser();
                        }
                        if (indexAI >= 59) {
                            switchPhase(healthRatio);
                        }
                        break;
                    }
                }
                indexAI++;
            }
            // if hands are still alive
            else {
                Vector vHead = target.getLocation().add(0, 12, 0).subtract(bukkitEntity.getLocation()).toVector();
                vHead.multiply(1d / 20);
                double vHeadLen = vHead.length();
                if (vHeadLen > 0.7)
                    vHead.multiply(0.7 / vHeadLen);
                bukkitEntity.setVelocity(vHead);
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public SkeletronPrimeHead(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.isDayTime(player.getWorld()) ) return false;
        return true;
    }
    // a constructor for actual spawning
    public SkeletronPrimeHead(Player summonedPlayer) {
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
        addScoreboardTag("isMechanic");
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", REGULAR_DMG);
            attrMap.put("damageTakenMulti", 0.5);
            attrMap.put("defence", 48d);
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
            spawnHands(summonedPlayer);
        }
        // shoot info
        {
            shootInfoSkull = new EntityHelper.ProjectileShootInfo(
                    bukkitEntity, new Vector(), attrMapSkull, DamageHelper.DamageType.MAGIC, "机械诅咒头");
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(
                    bukkitEntity, new Vector(), attrMapRocket, DamageHelper.DamageType.MAGIC, "死亡激光");
            shootInfoRocket = new EntityHelper.ProjectileShootInfo(
                    bukkitEntity, new Vector(), attrMapRocket, DamageHelper.DamageType.ROCKET, "机械红烟花火箭");
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
