package terraria.entity.boss.preHardMode.theSlimeGod;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class TheSlimeGod extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_SLIME_GOD;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 4000 * 2, BASIC_HEALTH_BR = 207840 * 2;
    public static final double BASIC_HEALTH_SLIME = 15552 * 2, BASIC_HEALTH_SLIME_BR = 808080 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    int indexAI = 0;
    boolean ebonianDefeated = false, crimulanDefeated = false, enraged = false;
    static final double PROJECTILE_SPEED = 2,
            BIG_SLIME_HOR_VEL = 0.5, BIG_SLIME_VER_VEL = 1.4,
            MID_SLIME_HOR_VEL = 0.3, MID_SLIME_VER_VEL = 1.2,
            SMALL_SLIME_HOR_VEL = 0.25, SMALL_SLIME_VER_VEL = 1.15;
    static final int BIG_JUMP_DELAY = 6, MID_JUMP_DELAY = 3, SMALL_JUMP_DELAY = 2;
    EntityHelper.ProjectileShootInfo shootInfo;
    static final HashMap<String, Double> attrMapProjectile;
    static final AimHelper.AimHelperOptions aimOption;
    static {
        attrMapProjectile = new HashMap<>();
        attrMapProjectile.put("damage", 252d);
        attrMapProjectile.put("knockback", 4d);

        aimOption = new AimHelper.AimHelperOptions()
                .setAimMode(false)
                .setProjectileSpeed(PROJECTILE_SPEED)
                .setIntensity(1d);
    }
    private Vector getTargetDirectionVector(double length) {
        Location targetLoc;
        if (enraged)
            targetLoc = AimHelper.helperAimEntity(bukkitEntity, target, aimOption);
        else
            targetLoc = target.getEyeLocation();
        return getTargetDirectionVector(targetLoc, length);
    }
    private Vector getTargetDirectionVector(Location targetLoc, double length) {
        Vector velocity = targetLoc.subtract( ((LivingEntity) bukkitEntity).getEyeLocation() ).toVector();
        double velLen = velocity.length();
        if (velLen < 1e-5) {
            velocity = MathHelper.randomVector();
            velLen = 1;
        }
        velocity.multiply( length / velLen );
        return velocity;
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
                getAttributeInstance(GenericAttributes.maxHealth).setValue(1d);
                die();
                return;
            }
            // increase player aggro duration
            targetMap.get(target.getUniqueId()).addAggressionTick();
            // if target is valid, attack
            if (ticksLived % 3 == 0) {
                // test if the slimes are defeated
                {
                    ebonianDefeated = true;
                    crimulanDefeated = true;
                    for (int i = 1; i < bossParts.size(); i++) {
                        Entity testEntity = bossParts.get(i);
                        if (testEntity.isDead())
                            continue;
                        net.minecraft.server.v1_12_R1.Entity nmsTestEntity = ((CraftEntity) testEntity).getHandle();
                        if (nmsTestEntity instanceof CrimulanSlime)
                            crimulanDefeated = false;
                        else if (nmsTestEntity instanceof EbonianSlime)
                            ebonianDefeated = false;
                    }
                    enraged = ebonianDefeated && crimulanDefeated;
                    if (enraged)
                        removeScoreboardTag("noDamage");
                    else
                        addScoreboardTag("noDamage");
                }
                // dash
                if (indexAI == 0) {
                    bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10,
                            enraged ? 1.5f : 1f);
                    bukkitEntity.setVelocity( getTargetDirectionVector(PROJECTILE_SPEED) );
                }
                // shoot projectiles
                else if (indexAI > 10) {
                    // slow down
                    Vector velocity = bukkitEntity.getVelocity();
                    Vector acceleration = getTargetDirectionVector(target.getLocation(), 0.25);
                    velocity.add(acceleration);
                    velocity.multiply(0.9);
                    bukkitEntity.setVelocity(velocity);
                    // shoot projectile
                    if (enraged) {
                        shootInfo.projectileName = Math.random() < 0.5 ? "血化深渊之球" : "黑檀深渊之球";
                        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                        shootInfo.velocity = getTargetDirectionVector(PROJECTILE_SPEED);
                        EntityHelper.spawnProjectile(shootInfo);
                    }
                    // back to dash
                    if (indexAI >= 20) {
                        indexAI = -1;
                    }
                }
                // add 1 to index
                indexAI ++;
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public TheSlimeGod(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public TheSlimeGod(Player summonedPlayer) {
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
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 270d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 0d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
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
                    getBukkitEntity(), "", summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
        {
            setSize(4, false);
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
            shootInfo = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapProjectile, "血化深渊之球");
            // spawn crimulan and ebonian slime
            new CrimulanSlime(this, BossHelper.accountForBR(BASIC_HEALTH_SLIME_BR, BASIC_HEALTH_SLIME) * healthMulti);
            new EbonianSlime(this, BossHelper.accountForBR(BASIC_HEALTH_SLIME_BR, BASIC_HEALTH_SLIME) * healthMulti);
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
