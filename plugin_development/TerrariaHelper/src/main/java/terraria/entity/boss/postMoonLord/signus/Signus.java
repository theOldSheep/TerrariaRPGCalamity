package terraria.entity.boss.postMoonLord.signus;

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
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.*;

public class Signus extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SIGNUS_ENVOY_OF_THE_DEVOURER;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.UNDERWORLD;
    public static final double BASIC_HEALTH = 864000 * 2, BASIC_HEALTH_BR = 1728000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    public HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    public Player target = null;
    // other variables and AI
    static final double SPEED_PHASE_1_FOLLOW = 0.8, DIST_TELEPORT = 32, SPEED_LANTERN = 1.0;
    static final double SPEED_PHASE_2_HOVER = 1.2, VERTICAL_DIST_HOVER = 17.5, SPEED_SCYTHE = 1.25;
    static final double SPEED_PHASE_3_DASH = 1.75, SPEED_DECAY_PHASE_3 = 0.99;
    static HashMap<String, Double> attrMapLantern, attrMapScythe;
    EntityHelper.ProjectileShootInfo shootInfoLantern, shootInfoScythe;
    static {
        attrMapLantern = new HashMap<>();
        attrMapLantern.put("damage", 780d);
        attrMapLantern.put("knockback", 1.5d);
        attrMapScythe = new HashMap<>();
        attrMapScythe.put("damage", 800d);
        attrMapScythe.put("knockback", 1.5d);
    }

    private int indexAI = 0, phaseAI = 1;
    boolean teleported = false, dashing = false, isSummonedByDoG = false;
    Vector velocity = new Vector();


    private void updatePhase() {
        // Calculate health percentage
        float healthPercentage = getHealth() / getMaxHealth();

        // If the boss's health is below 30%, never enter phase 1
        int newPhase = phaseAI;

        // Ensure the new phase is different from the current phase
        while (newPhase == phaseAI) {
            if (healthPercentage < 0.5) {
                newPhase = random.nextInt(2) + 2;
            } else {
                newPhase = random.nextInt(3) + 1;
            }
        }

        // Bossbar color hinting attack phase
        switch (newPhase) {
            case 1:
                bossbar.color = BossBattle.BarColor.GREEN;
                indexAI = -11;
                break;
            case 2:
                bossbar.color = BossBattle.BarColor.PURPLE;
                indexAI = -21;
                break;
            case 3:
                bossbar.color = BossBattle.BarColor.RED;
                indexAI = -11;
                break;
        }
        bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);

        // Update the current phase
        phaseAI = newPhase;
    }
    // teleport and shoot lanterns
    private void phase1AI() {
        velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), SPEED_PHASE_1_FOLLOW);

        if (indexAI >= 80)
            updatePhase();
        if (indexAI <= 40 && indexAI % 20 == 0) {
            // Projectile
            shootInfoLantern.setLockedTarget(target);
            shootInfoLantern.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            shootInfoLantern.velocity = MathHelper.getDirection(shootInfoLantern.shootLoc, target.getEyeLocation(), SPEED_LANTERN);
            EntityHelper.spawnProjectile(shootInfoLantern);
            // Teleport
            Location newLoc = target.getLocation().add(MathHelper.randomVector().multiply(DIST_TELEPORT));
            bukkitEntity.teleport(newLoc);
            teleported = true;
        }
    }
    // fire clusters of scythes
    private void phase2AI() {
        Location hoverLoc = target.getLocation().add(0, VERTICAL_DIST_HOVER, 0);
        velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverLoc, SPEED_PHASE_2_HOVER);

        if (indexAI % 20 == 0) { // 20 ticks = 1 second
            shootInfoScythe.setLockedTarget(target);
            shootInfoScythe.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            for (Vector shootDir : MathHelper.getEvenlySpacedProjectileDirections(
                    10, 31, target, shootInfoScythe.shootLoc, SPEED_SCYTHE)) {
                shootInfoScythe.velocity = shootDir;
                EntityHelper.spawnProjectile(shootInfoScythe);
            }
        }
        if (indexAI >= 50) {
            updatePhase();
        }
    }
    // dash and leave scythes along the path
    private void phase3AI() {
        if (indexAI % 25 < 10) {
            double accRatio = 0.5;
            velocity.multiply(1 - accRatio).add(
                    MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), SPEED_PHASE_3_DASH).multiply(accRatio) );

            shootInfoScythe.setLockedTarget(target);
            shootInfoScythe.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            shootInfoScythe.velocity = MathHelper.setVectorLength(velocity.clone(), SPEED_SCYTHE);
            EntityHelper.spawnProjectile(shootInfoScythe);
        }
        else {
            velocity.multiply(SPEED_DECAY_PHASE_3);
            dashing = true;
            if (indexAI >= 39)
                updatePhase();
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
                    IGNORE_DISTANCE, isSummonedByDoG ? null : BIOME_REQUIRED, targetMap.keySet());
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

                dashing = false;
                if (phaseAI == 1) {
                    phase1AI();
                } else if (phaseAI == 2) {
                    phase2AI();
                } else if (phaseAI == 3) {
                    phase3AI();
                }

                indexAI ++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        // face the player
        if (dashing)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        if (teleported)
            teleported = false;
        else
            terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Signus(World world) {
        super(world);
        super.die();
    }

    private static boolean isDoGAlive() {
        return BossHelper.bossMap.containsKey(BossHelper.BossType.THE_DEVOURER_OF_GODS.msgName);
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED && !isDoGAlive();
    }
    // a constructor for actual spawning
    public Signus(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        isSummonedByDoG = isDoGAlive();
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 925d);
            attrMap.put("damageTakenMulti", 0.85d);
            attrMap.put("defence", 120d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer,
                    true, !isSummonedByDoG, bossbar);
            target = summonedPlayer;
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(8, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            if (isSummonedByDoG)
                healthMulti *= 0.5;
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
        // shoot info's
        {
            shootInfoLantern = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapLantern,
                    DamageHelper.DamageType.ARROW, "无限灯笼");
            shootInfoScythe = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapScythe,
                    DamageHelper.DamageType.MAGIC, "西格纳斯鬼镰");
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
        if (!isSummonedByDoG && getMaxHealth() > 10) {
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