package terraria.entity.boss.eaterOfWorld;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;

public class EaterOfWorld extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EATER_OF_WORLDS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.CORRUPTION;
    public static final double BASIC_HEALTH = 372 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    public static final int TOTAL_LENGTH = 79;
    public static final double
            HEAD_DMG = 264d, HEAD_DEF = 16d,
            BODY_DMG = 80d, BODY_DEF = 12d,
            TAIL_DMG = 64d, TAIL_DEF = 20d;
    public static final EntityHelper.WormSegmentMovementOptions FOLLOW_PROPERTY =
            new EntityHelper.WormSegmentMovementOptions()
                    .setFollowDistance(1.53)
                    .setFollowingMultiplier(1)
                    .setStraighteningMultiplier(0.1)
                    .setVelocityOrTeleport(false);
    static final HashMap<String, Double> attrMapSpit;
    static {
        attrMapSpit = new HashMap<>();
        attrMapSpit.put("damage", 192d);
        attrMapSpit.put("health", 1d);
        attrMapSpit.put("healthMax", 1d);
        attrMapSpit.put("knockback", 4d);
    }
    public EntityHelper.ProjectileShootInfo projectileProperty;
    int index;
    int indexAI = (int) (Math.random() * 500);
    Vector dVec = null, bufferVec = new Vector(0, 0, 0);
    boolean charging = true;
    private void headRushEnemy(int totalTickSeg) {
        if (ticksLived % 2 == 0) return;
        double distSqr = bukkitEntity.getLocation().distanceSquared(target.getLocation());
        if (dVec == null)
            indexAI = 0;
        else if (distSqr > 3600)
            indexAI = 0;
        indexAI %= 60;
        if (indexAI == 0)
            charging = true;
        // set velocity
        boolean shouldIncreaseIdx = true;
        if (indexAI < 25) {
            if (charging) {
                // stop charging after getting close enough
                if (distSqr < 64 && dVec != null)
                    charging = false;
                else {
                    Location targetLoc = target.getEyeLocation();
                    dVec = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double dVecLen = dVec.length();
                    if (dVecLen < 0.01) {
                        dVec = new Vector(0, -1, 0);
                        dVecLen = 1;
                    }
                    dVec.multiply(0.5 / dVecLen);
                    shouldIncreaseIdx = false;
                }
            }
            else {
                dVec.multiply(0.975);
                dVec.setY(dVec.getY() - 0.025);
            }
        }
        else {
            if (locY > target.getLocation().getY())
                shouldIncreaseIdx = false;
            else {
                Location targetLoc = target.getLocation().subtract(0, 20, 0);
                dVec = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                double dVecLen = dVec.length();
                if (dVecLen < 0.01) {
                    dVec = new Vector(0, -1, 0);
                    dVecLen = 1;
                }
                dVec.multiply(0.35 / dVecLen);
            }
            dVec.multiply(0.95);
            dVec.setY(dVec.getY() - 0.05);
        }
        // tweak buffer vector, cloned and scaled to produce actual velocity
        bufferVec.add(dVec);
        double bufferVecLen = bufferVec.length();
        if (bufferVecLen > 1.2) {
            bufferVec.multiply(1.2 / bufferVecLen);
            bufferVecLen = 1.2;
        }
        Vector actualVelocity = bufferVec.clone();
        double actualLen = bufferVecLen;
        if (actualLen < 0.01) {
            actualVelocity = new Vector(1, 0, 0);
            actualLen = 1;
        }
        actualVelocity.multiply( (0.75 * Math.min(
                3 / Math.min((double) totalTickSeg / 10, 3d), 2) ) / actualLen);
        bukkitEntity.setVelocity(actualVelocity);
        // increase indexAI
        if (shouldIncreaseIdx)
            indexAI ++;
    }
    private void bodySpit() {
        if (ticksLived % 2 == 0) return;
        // increase indexAI, then mod it based on height layer
        indexAI ++;
        switch (WorldHelper.HeightLayer.getHeightLayer(bukkitEntity.getLocation())) {
            case SPACE:
            case SURFACE:
                indexAI %= 200;
                break;
            default:
                indexAI %= 75;
        }
        // spit
        if (indexAI == 0) {
            Location shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            Vector velocity = target.getEyeLocation().subtract(shootLoc).toVector();
            velocity.normalize();
            projectileProperty.shootLoc = shootLoc;
            projectileProperty.velocity = velocity;
            Entity projectile = EntityHelper.spawnProjectile(projectileProperty);
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
                for (LivingEntity segment : bossParts) {
                    segment.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    segment.setHealth(0d);
                    segment.remove();
                }
                return;
            }
            // if target is valid, attack
            else {
                // attack
                boolean isHead = index == 0 ||
                        bossParts.get(index - 1).getHealth() < 1e-5 ||
                        bossParts.get(index - 1).isDead();
                if (isHead) {
                    // if this is the only segment nearby
                    int totalTickSegment = 1;
                    for (int idx = index + 1; idx < TOTAL_LENGTH; idx ++) {
                        if (bossParts.get(idx).getHealth() < 1e-5 || bossParts.get(idx).isDead())
                            break;
                        totalTickSegment ++;
                    }
                    if (totalTickSegment == 1) {
                        setHealth(0f);
                        return;
                    }
                    // attack
                    headRushEnemy(totalTickSegment);
                    // face the player
                    this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
                    // follow
                    EntityHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, index);
                }
                else {
                    bodySpit();
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public EaterOfWorld(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public EaterOfWorld(Player summonedPlayer, ArrayList<LivingEntity> bossParts, int index) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // copy variable
        this.bossParts = bossParts;
        this.index = index;
        // spawn location
        Location spawnLoc;
        if (index == 0) {
            double angle = Math.random() * 720d, dist = 40;
            spawnLoc = summonedPlayer.getLocation().add(
                    MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        } else {
            spawnLoc = bossParts.get(index - 1).getLocation().add(0, -1, 0);
        }
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageRangedMulti", 0.75d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            // head
            if (index == 0) {
                attrMap.put("damage", HEAD_DMG);
                attrMap.put("defence", HEAD_DEF);
            }
            // tail
            else if (index + 1 == TOTAL_LENGTH) {
                attrMap.put("damage", TAIL_DMG);
                attrMap.put("defence", TAIL_DEF);
            }
            // body
            else {
                attrMap.put("damage", BODY_DMG);
                attrMap.put("defence", BODY_DEF);
            }
            EntityHelper.setDamageType(bukkitEntity, "Melee");
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init boss bar
        if (index == 0) {
            bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                    BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
            EntityHelper.setMetadata(bukkitEntity, "bossbar", targetMap);
        } else {
            bossbar = (BossBattleServer) EntityHelper.getMetadata(bossParts.get(0), "bossbar").value();
        }
        // init target map
        {
            if (index == 0) {
                targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                        getBukkitEntity(), "", summonedPlayer, true, bossbar);
            } else {
                targetMap = (HashMap<Player, Double>) EntityHelper.getMetadata(bossParts.get(0), "targets").value();
            }
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
            target = summonedPlayer;
        }
        // init health and slime size
        {
            setSize(3, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts.add((LivingEntity) bukkitEntity);
            if (index == 0)
                BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
            // projectile info
            projectileProperty = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapSpit, "Arrow", "魔唾液");
            projectileProperty.properties.put("penetration", 9);
            // next segment
            if (index + 1 < TOTAL_LENGTH)
                new EaterOfWorld(summonedPlayer, bossParts, index + 1);
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // modify the segment before and after it
        if (index > 0) {
            LivingEntity before = bossParts.get(index - 1);
            if (before.getHealth() > 1e-5 && !before.isDead()) {
                HashMap<String, Double> atm = EntityHelper.getAttrMap(before);
                atm.put("damage", TAIL_DMG);
                atm.put("defence", TAIL_DEF);
            }
        }
        if (index + 1 < TOTAL_LENGTH) {
            LivingEntity after = bossParts.get(index + 1);
            if (after.getHealth() > 1e-5 && !after.isDead()) {
                HashMap<String, Double> atm = EntityHelper.getAttrMap(after);
                atm.put("damage", HEAD_DMG);
                atm.put("defence", HEAD_DEF);
            }
        }
        // drop loot
        if (getMaxHealth() > 10) {
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);
        }
        // check if all segments are defeated
        boolean defeated = BossHelper.bossMap.containsKey(BOSS_TYPE.msgName);
        for (LivingEntity entity : bossParts)
            if (entity.getHealth() > 1e-5 && !entity.isDead()) {
                defeated = false;
                break;
            }
        if (defeated) {
            // disable boss bar
            bossbar.setVisible(false);
            BossHelper.bossMap.remove(BOSS_TYPE.msgName);
            // if the boss has been defeated properly
            if (getMaxHealth() > 10) {
                // send loot
                terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
            }
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
        boolean shouldCurrSegmentUpdate = true;
        for (int i = 0; i < index; i ++)
            if (!bossParts.get(i).isDead() && bossParts.get(i).getHealth() > 1e-5) {
                shouldCurrSegmentUpdate = false;
                break;
            }
        if (shouldCurrSegmentUpdate)
            terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, ticksLived, BOSS_TYPE);
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
