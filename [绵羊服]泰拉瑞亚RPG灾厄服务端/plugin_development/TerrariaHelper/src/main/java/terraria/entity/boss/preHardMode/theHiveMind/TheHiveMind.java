package terraria.entity.boss.preHardMode.theHiveMind;

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
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class TheHiveMind extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_HIVE_MIND;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.CORRUPTION;
    public static final double BASIC_HEALTH = 24480 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final int SIZE = 10;
    static HashMap<String, Double> attrMapShaderRain;
    static {
        attrMapShaderRain = new HashMap<>();
        attrMapShaderRain.put("damage", 204d);
    }
    private enum AIPhase {
        CHARGE, CIRCLE, DASH, BURROW;
    }
    boolean secondPhase = false;
    int indexAI = 1, spawnAmountLeft = 19;
    AIPhase typeAI = AIPhase.CHARGE;
    Vector circleVec1 = new Vector(), circleVec2 = new Vector(), dashVelocity = new Vector();
    private void updateTypeAI(AIPhase newPhase) {
        if (newPhase == AIPhase.BURROW)
            addScoreboardTag("noDamage");
        else
            removeScoreboardTag("noDamage");
        typeAI = newPhase;
        indexAI = -1;
    }
    private void spawnMonsters(boolean canSpawnRain) {
        // amount = sqrt(2x)
        // x = amount * amount / 2
        for (int i = 0; i * i / 2 < targetMap.size(); i ++) {
            double rdm = Math.random();
            Location spawnLoc = bukkitEntity.getLocation().add(Math.random() * 10 - 5, Math.random() * 10 - 5, Math.random() * 10 - 5);
            // shader rain
            if (rdm < 0.25) {
                if (!canSpawnRain) {
                    return;
                }
                EntityHelper.spawnProjectile(bukkitEntity, spawnLoc, new Vector(), attrMapShaderRain,
                        EntityHelper.DamageType.MAGIC, "腐蚀之云");
            }
            // eater of soul
            else if (rdm < 0.4) {
                MonsterHelper.spawnMob("噬魂怪", spawnLoc, target);
            }
            // devourer
            else if (rdm < 0.55) {
                MonsterHelper.spawnMob("吞噬者", spawnLoc, target);
            }
            // dark heart
            else if (rdm < 0.7) {
                new DarkHeart(this);
            }
            // Dank Creeper
            else {
                MonsterHelper.spawnMob("沼泽之眼", spawnLoc, target);
            }
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
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
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
            // if target is valid, attack
            double healthRatio = getHealth() / getMaxHealth();
            if (!secondPhase && healthRatio < 0.8) {
                secondPhase = true;
            }
            // attack AI
            switch (typeAI) {
                case CHARGE: {
                    if (ticksLived % 3 == 0) {
                        double chargeSpd = (1 - healthRatio) * 0.55;
                        Vector velocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                        double velLen = velocity.length();
                        if (velLen > 1e-5) {
                            velocity.multiply(chargeSpd / velLen);
                            bukkitEntity.setVelocity(velocity);
                        }
                        // enter next phase
                        int duration = secondPhase ? 15 : 25;
                        AIPhase nextPhase = secondPhase ? AIPhase.CIRCLE : AIPhase.BURROW;
                        if (indexAI >= duration) {
                            updateTypeAI(nextPhase);
                        }
                        // add 1 to index
                        indexAI++;
                    }
                    break;
                }
                case CIRCLE: {
                    if (indexAI == 0) {
                        bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1.5f);
                        // circle vector 1
                        circleVec1 = bukkitEntity.getLocation().subtract(target.getLocation()).toVector();
                        double cv1Len = circleVec1.length();
                        if (cv1Len < 1e-5) {
                            circleVec1 = MathHelper.randomVector();
                            cv1Len = 1;
                        }
                        circleVec1.multiply(1d / cv1Len);
                        // circle vector 2
                        circleVec2 = null;
                        while (circleVec2 == null) {
                            circleVec2 = MathHelper.randomVector();
                            circleVec2.subtract( MathHelper.vectorProjection(circleVec1, circleVec2) );
                            double cv2Len = circleVec2.length();
                            if (cv2Len < 1e-5)
                                circleVec2 = null;
                            else {
                                circleVec2.multiply(1d / cv2Len);
                            }
                        }
                    }
                    double dist = 15;
                    Vector offsetVector = circleVec1.clone().multiply(MathHelper.xcos_degree(indexAI) * dist)
                            .add(circleVec2.clone().multiply(MathHelper.xsin_degree(indexAI) * dist));
                    Location targetLoc = target.getLocation().add(offsetVector);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    bukkitEntity.setVelocity(velocity);
                    // add 1 to index
                    indexAI += 20;
                    if (indexAI > 720) {
                        updateTypeAI(AIPhase.DASH);
                    }
                    break;
                }
                case DASH: {
                    if (ticksLived % 3 == 0) {
                        if (indexAI == 0) {
                            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                            dashVelocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = dashVelocity.length();
                            double chargeSpd = 2.5 - healthRatio * 0.75;
                            if (velLen < 1e-5) {
                                dashVelocity = new Vector(1, 0, 0);
                                velLen = 1;
                            }
                            dashVelocity.multiply(chargeSpd / velLen);
                        }
                        bukkitEntity.setVelocity(dashVelocity);
                        // spawn monsters
                        if (Math.random() < 0.15)
                            spawnMonsters(true);
                        // next AI phase
                        if (indexAI >= 10) {
                            updateTypeAI(AIPhase.BURROW);
                        }
                        // add 1 to index
                        indexAI++;
                    }
                    break;
                }
                // teleport near target
                case BURROW: {
                    bukkitEntity.setVelocity(new Vector());
                    if (ticksLived % 2 == 0) {
                        if (indexAI < SIZE) {
                            EntityHelper.slimeResize((Slime) bukkitEntity, SIZE - indexAI);
                        } else if (indexAI == SIZE) {
                            double angle = Math.random() * 360, radius = 15 + Math.random() * 10;
                            Location teleportLoc = bukkitEntity.getWorld().getHighestBlockAt(
                                            target.getLocation().add(MathHelper.xsin_degree(angle) * radius, 0,
                                                    MathHelper.xcos_degree(angle) * radius))
                                    .getLocation().add(0, 1, 0);
                            bukkitEntity.teleport(teleportLoc);
                        } else {
                            int newSize = indexAI - SIZE;
                            EntityHelper.slimeResize((Slime) bukkitEntity, newSize);
                            if (newSize >= SIZE) {
                                updateTypeAI(AIPhase.CHARGE);
                            }
                        }
                        // add 1 to index
                        indexAI++;
                    }
                }
            }
            // spawn additional monsters
            int currSpawnAmountLeft = (int) (healthRatio * 20);
            while (currSpawnAmountLeft < spawnAmountLeft) {
                spawnAmountLeft --;
                if (!secondPhase) {
                    // hive blobs
                    for (int i = 0; i < 5; i++) {
                        new HiveBlob(this);
                    }
                    // other random monsters
                    for (int i = 0; i < 8; i++)
                        spawnMonsters(false);
                }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        if (typeAI != AIPhase.BURROW)
            terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public TheHiveMind(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.BiomeType.getBiome(player) != BIOME_REQUIRED ) return false;
        return true;
    }
    // a constructor for actual spawning
    public TheHiveMind(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 20;
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
            attrMap.put("defence", 16d);
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
            setSize(SIZE, false);
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
            // hive blobs
            for (int i = 0; i < 15; i ++) {
                new HiveBlob(this);
            }
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
