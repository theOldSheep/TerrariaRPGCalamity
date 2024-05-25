package terraria.entity.boss.preHardMode.kingSlime;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.gameplay.EventAndTime;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class KingSlime extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.KING_SLIME;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 7140;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final int MAX_SIZE = 32, MIN_SIZE = 12, SHRINK_TIME = 25, DISAPPEAR_TIME = 30;
    static final double TELEPORT_DIST_SQR = 9;
    enum SizeState {
        NORMAL, DISAPPEARED, SHRINKING, GROWING;
    }
    int indexAI = 0, jumpAmount = 0, sizeChangeTimeIndex = 0, normalSize = MAX_SIZE;
    Location lastOnGroundLocation = null;
    SizeState sizeChangeState = SizeState.DISAPPEARED;
    private boolean isOnGround() {
        return onGround ||
                bukkitEntity.getLocation().subtract(0, 0.25, 0).getBlock().getType() != org.bukkit.Material.AIR;
    }
    // return value: should other AI mechanics halt?
    private boolean handleTeleport() {
        setNoGravity(sizeChangeState == SizeState.DISAPPEARED);
        switch (sizeChangeState) {
            case NORMAL: {
                // if boss is on ground
                if (isOnGround() && lastOnGroundLocation != null) {
                    double distSqrFromLast = bukkitEntity.getLocation().distanceSquared(lastOnGroundLocation);
                    lastOnGroundLocation = null;
                    if (distSqrFromLast < TELEPORT_DIST_SQR || jumpAmount % 8 == 0) {
                        sizeChangeState = SizeState.SHRINKING;
                        addScoreboardTag("noDamage");
                        sizeChangeTimeIndex = 0;
                        return true;
                    }
                }
                break;
            }
            case SHRINKING: {
                EntityHelper.slimeResize((Slime) bukkitEntity,
                        (int) Math.ceil(normalSize * (double) (SHRINK_TIME - sizeChangeTimeIndex) / SHRINK_TIME) );
                sizeChangeTimeIndex ++;
                if (sizeChangeTimeIndex >= SHRINK_TIME) {
                    sizeChangeState = SizeState.DISAPPEARED;
                    Location teleportLoc = bukkitEntity.getLocation();
                    teleportLoc.setY(-1);
                    bukkitEntity.teleport(teleportLoc);
                    sizeChangeTimeIndex = 0;
                }
                return true;
            }
            case DISAPPEARED: {
                sizeChangeTimeIndex ++;
                if (sizeChangeTimeIndex >= DISAPPEAR_TIME) {
                    sizeChangeState = SizeState.GROWING;
                    sizeChangeTimeIndex = 1;
                    Location targetLoc = target.getLocation();
                    bukkitEntity.teleport(targetLoc);
                }
                return true;
            }
            case GROWING: {
                EntityHelper.slimeResize((Slime) bukkitEntity,
                        (int) Math.ceil(normalSize * (double) sizeChangeTimeIndex / SHRINK_TIME) );
                sizeChangeTimeIndex ++;
                if (sizeChangeTimeIndex >= SHRINK_TIME) {
                    sizeChangeState = SizeState.NORMAL;
                    removeScoreboardTag("noDamage");
                    sizeChangeTimeIndex = 0;
                    indexAI = 0;
                    EntityHelper.slimeResize((Slime) bukkitEntity, normalSize);
                }
                return true;
            }
        }
        return false;
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
                if (ticksLived % 2 == 0) {
                    double healthRatio = getHealth() / getMaxHealth();
                    int lastNormalSize = normalSize;
                    normalSize = (int) Math.round(MIN_SIZE + (MAX_SIZE - MIN_SIZE) * healthRatio);
                    // teleport mechanism
                    if (!handleTeleport()) {
                        if (normalSize != lastNormalSize)
                            EntityHelper.slimeResize((Slime) bukkitEntity, normalSize);

                        // jump
                        if (indexAI >= 10) {
                            Vector velocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                            velocity.setY(0);
                            if (jumpAmount % 4 == 3) {
                                velocity.normalize().multiply(3);
                                velocity.setY(2.25);
                            } else {
                                velocity.normalize().multiply(1.75);
                                velocity.setY(1.6);
                            }
                            bukkitEntity.setVelocity(velocity);
                            lastOnGroundLocation = bukkitEntity.getLocation();
                            indexAI = 0;
                            jumpAmount++;
                        }

                        // spawn crown jewel
                        if (healthRatio < 0.5 && bossParts.size() == 1)
                            new CrownJewel(bossParts);

                        // add 1 to index
                        if (isOnGround())
                            indexAI++;
                    }
                }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        if (sizeChangeState == SizeState.NORMAL)
            terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public KingSlime(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public KingSlime(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        spawnLoc.setY(-1);
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
        goalSelector.a(0, new PathfinderGoalFloat(this));
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 192d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 20d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
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
                    getBukkitEntity(), "", summonedPlayer, true, bossbar);
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
            this.noclip = false;
            this.persistent = true;
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the event is slime rain, reset its progress even if the boss flees.
        if (EventAndTime.currentEvent == EventAndTime.Events.SLIME_RAIN)
            EventAndTime.eventInfo.put(EventAndTime.EventInfoMapKeys.INVADE_PROGRESS, 0d);
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
