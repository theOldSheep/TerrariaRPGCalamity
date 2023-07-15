package terraria.entity.boss.event.santaNK1;

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

public class SantaNK1 extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SANTA_NK1;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 29835 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    public static final int EVENT_BOSS_INDEX = 1;
    public static final double HORIZONTAL_ACC = 0.05, HORIZONTAL_SPEED = 0.25, ROCKET_SPEED = 2.25;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    int indexAI = 0;
    static EntityHelper.AimHelperOptions aimHelperRocket;
    static HashMap<String, Double> attrMapBullet, attrMapSpikyBall, attrMapRocket;
    EntityHelper.ProjectileShootInfo shootInfoBullet, shootInfoSpikyBall, shootInfoRocket;
    static {
        attrMapBullet = new HashMap<>();
        attrMapBullet.put("damage", 432d);
        attrMapBullet.put("knockback", 1d);
        attrMapSpikyBall = new HashMap<>();
        attrMapSpikyBall.put("damage", 960d);
        attrMapSpikyBall.put("knockback", 2d);
        attrMapRocket = new HashMap<>();
        attrMapRocket.put("damage", 504d);
        attrMapRocket.put("knockback", 2d);

        aimHelperRocket = new EntityHelper.AimHelperOptions()
                .setProjectileSpeed(ROCKET_SPEED)
                .setRandomOffsetRadius(1);
    }
    private Vector getHorizontalDirection() {
        Location targetLoc = target.getLocation();
        Location currLoc = bukkitEntity.getLocation();
        targetLoc.setY(currLoc.getY());
        return MathHelper.getDirection(currLoc, targetLoc, 1);
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

            // bullets
            if (indexAI % 4 == 0) {
                shootInfoBullet.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                Location targetLoc = target.getEyeLocation().add(
                        Math.random() * 4 - 2,
                        Math.random() * 4 - 2,
                        Math.random() * 4 - 2);
                shootInfoBullet.velocity = MathHelper.getDirection(shootInfoBullet.shootLoc,
                        targetLoc, 1.4);
                EntityHelper.spawnProjectile(shootInfoBullet);
            }
            // spike balls
            if (indexAI % 150 == 0) {
                shootInfoSpikyBall.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                for (int i = 0; i < 10; i ++) {
                    shootInfoSpikyBall.velocity = MathHelper.randomVector().multiply(0.35);
                    EntityHelper.spawnProjectile(shootInfoSpikyBall);
                }
            }
            // rockets
            switch (indexAI % 300) {
                case 240:
                case 244:
                case 248:
                case 252:
                case 256:
                case 260:
                case 264:
                case 268:
                case 272:
                case 276:
                case 280:
                case 284:
                case 288:
                case 292:
                case 296:
                {
                    shootInfoRocket.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                    Location targetLoc = EntityHelper.helperAimEntity(bukkitEntity, target, aimHelperRocket);
                    shootInfoRocket.velocity = MathHelper.getDirection(shootInfoRocket.shootLoc,
                            targetLoc, ROCKET_SPEED);
                    EntityHelper.spawnProjectile(shootInfoRocket);
                }
            }
            // slowly move towards target
            {
                Vector horizontalAcc = getHorizontalDirection();
                horizontalAcc.multiply(HORIZONTAL_ACC);
                Vector velocity = bukkitEntity.getVelocity();
                velocity.setY(0);
                velocity.add(horizontalAcc);
                double velLen = velocity.length();
                double horSpeed = HORIZONTAL_SPEED;
                if (velLen > horSpeed)
                    velocity.multiply(horSpeed / velLen);
                double verticalVelocity = 0.4;
                velocity.setY(
                        bukkitEntity.getLocation().subtract(0, verticalVelocity, 0).getBlock().getType().isSolid()
                                ? verticalVelocity : -verticalVelocity);
                bukkitEntity.setVelocity(velocity);
            }

            indexAI = (indexAI + 1) % 300;
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public SantaNK1(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public SantaNK1(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // send spawn message
        Bukkit.broadcastMessage("§d§l" + BOSS_TYPE + " 苏醒了！");
        // spawn location
        Vector offsetDir = MathHelper.vectorFromYawPitch_quick(Math.random() * 360, 0);
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
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.KILL_CONTRIBUTE_EVENT_PROGRESS, 250d);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 540d);
            attrMap.put("defence", 112d);
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
        // init health and slime size
        {
            setSize(15, false);
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
        // add boss counter
        EventAndTime.eventBossAmount[EVENT_BOSS_INDEX] ++;
        // shoot info
        shootInfoSpikyBall = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, new Vector(), attrMapSpikyBall, EntityHelper.DamageType.ARROW, "尖刺球");
        shootInfoBullet = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, new Vector(), attrMapBullet, EntityHelper.DamageType.BULLET, "火枪子弹");
        shootInfoRocket = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, new Vector(), attrMapRocket, EntityHelper.DamageType.ROCKET, "红烟花火箭");
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
                if (Math.random() < 1d/2) {
                    ItemHelper.dropItem(bukkitEntity.getLocation(), "链式机枪");
                }
                else {
                    ItemHelper.dropItem(bukkitEntity.getLocation(), "精灵熔枪");
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
