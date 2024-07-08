package terraria.entity.boss.event.iceQueen;

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
import terraria.gameplay.EventAndTime;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class IceQueen extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.ICE_QUEEN;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 56355 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    public static final int EVENT_BOSS_INDEX = 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    int indexAI = 0;
    static HashMap<String, Double> attrMapFrostShard, attrMapFrostWave;
    EntityHelper.ProjectileShootInfo shootInfoFrostShard, shootInfoFrostWave;
    static {
        attrMapFrostShard = new HashMap<>();
        attrMapFrostShard.put("damage", 444d);
        attrMapFrostShard.put("knockback", 1d);
        attrMapFrostWave = new HashMap<>();
        attrMapFrostWave.put("damage", 504d);
        attrMapFrostWave.put("knockback", 1d);
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d) {
            bukkitEntity.setVelocity(new Vector());
            return;
        }
        // AI
        {
            // update target
            target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
            if (EventAndTime.currentEvent != EventAndTime.Events.FROST_MOON)
                target = null;
            // disappear if no target is available
            if (target == null) {
                for (LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                return;
            }
            // AI

            // increase player aggro duration
            targetMap.get(target.getUniqueId()).addAggressionTick();
            // frost waves
            if (indexAI < 100) {
                Vector direction = MathHelper.getDirection(
                        bukkitEntity.getLocation(), target.getLocation().add(0, 15, 0), 1 );
                bukkitEntity.setVelocity(direction);
                if (indexAI % 15 == 0) {
                    shootInfoFrostWave.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                    shootInfoFrostWave.velocity = MathHelper.getDirection(
                            shootInfoFrostWave.shootLoc, target.getEyeLocation(), 1.75);
                    EntityHelper.spawnProjectile(shootInfoFrostWave);
                }
            }
            // ice shard from above
            else if (indexAI < 180) {
                Vector direction = target.getLocation().add(0, 15, 0).subtract(bukkitEntity.getLocation()).toVector();
                direction.multiply(1d / 3);
                bukkitEntity.setVelocity(direction);
                if (indexAI % 4 == 0) {
                    shootInfoFrostShard.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                    shootInfoFrostShard.velocity = new Vector(0, -0.75, 0);
                    EntityHelper.spawnProjectile(shootInfoFrostShard);
                }
            }
            // ice shard
            else {
                bukkitEntity.setVelocity(new Vector(0, 0, 0));
                if (indexAI % 2 == 0) {
                    shootInfoFrostShard.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                    Location targetLoc = target.getEyeLocation().add(
                            Math.random() * 8 - 4,
                            Math.random() * 8 - 4,
                            Math.random() * 8 - 4);
                    shootInfoFrostShard.velocity = MathHelper.getDirection(shootInfoFrostShard.shootLoc,
                            targetLoc, 1.4);
                    EntityHelper.spawnProjectile(shootInfoFrostShard);
                }
            }

            indexAI = (indexAI + 1) % 260;
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public IceQueen(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public IceQueen(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // send spawn message
        Bukkit.broadcastMessage("§d§l" + BOSS_TYPE + " 苏醒了！");
        // spawn location
        Vector offsetDir = MathHelper.vectorFromYawPitch_approx(Math.random() * 360, 0);
        offsetDir.multiply(40);
        Location spawnLoc = summonedPlayer.getLocation().add(offsetDir);
        spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc));
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.SPAWN_IN_EVENT, EventAndTime.Events.FROST_MOON);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.KILL_CONTRIBUTE_EVENT_PROGRESS, 375d);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 540d);
            attrMap.put("defence", 76d);
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
        boolean postDoG = PlayerHelper.hasDefeated(summonedPlayer, BossHelper.BossType.THE_DEVOURER_OF_GODS);
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(),
                    (postDoG ? BossHelper.BossType.THE_DEVOURER_OF_GODS : BossHelper.BossType.PLANTERA).msgName,
                    summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(7, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            if (postDoG)
                health *= 2.5d;
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
        // add boss counter
        EventAndTime.eventBossAmount[EVENT_BOSS_INDEX] ++;
        // shoot info
        shootInfoFrostShard = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, new Vector(), attrMapFrostShard, EntityHelper.DamageType.ARROW, "寒霜碎块");
        shootInfoFrostWave = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, new Vector(), attrMapFrostWave, EntityHelper.DamageType.MAGIC, "寒霜波");
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // remove boss counter
        EventAndTime.eventBossAmount[EVENT_BOSS_INDEX] --;
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);
            // drop one of special items
            double chance = EventAndTime.getWaveEventBossDropRate();
            if (Math.random() < chance) {
                if (Math.random() < 1d/3) {
                    ItemHelper.dropItem(bukkitEntity.getLocation(), "北极");
                }
                else if (Math.random() < 1d/2) {
                    ItemHelper.dropItem(bukkitEntity.getLocation(), "雪人炮");
                }
                else {
                    ItemHelper.dropItem(bukkitEntity.getLocation(), "暴雪法杖");
                }
            }
            // send death message
            Bukkit.broadcastMessage("§d§l" + BOSS_TYPE + " 被击败了.");
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
