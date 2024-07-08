package terraria.entity.boss.event.headlessHorseman;

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
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.util.Vector;
import terraria.gameplay.EventAndTime;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class HeadlessHorseman extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.HEADLESS_HORSEMAN;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 8287 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    public static final int EVENT_BOSS_INDEX = 2;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
            if (EventAndTime.currentEvent != EventAndTime.Events.PUMPKIN_MOON)
                target = null;
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
            // force target
            setGoalTarget( ((CraftPlayer) target).getHandle(), EntityTargetEvent.TargetReason.CLOSEST_PLAYER, false );
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public HeadlessHorseman(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public HeadlessHorseman(Player summonedPlayer) {
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
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.SPAWN_IN_EVENT, EventAndTime.Events.PUMPKIN_MOON);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.KILL_CONTRIBUTE_EVENT_PROGRESS, 125d);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 584d);
            attrMap.put("defence", 80d);
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
            setSize(6, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            if (postDoG)
                health *= 3.5d;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.persistent = true;
            getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(1d);
        }
        // add boss counter
        EventAndTime.eventBossAmount[EVENT_BOSS_INDEX] ++;
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
