package terraria.entity.boss.moonLord;

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
import terraria.entity.boss.thePlaguebringerGoliath.PlagueHomingMissile;
import terraria.entity.boss.thePlaguebringerGoliath.PlagueMine;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class MoonLord extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.MOON_LORD;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 211140 * 2;
    public static final boolean IGNORE_DISTANCE = true;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final double MOVE_SPEED = 0.75, MAX_DIST_BEFORE_TELEPORT = 100;
    int indexAI = (int) (Math.random() * 360);
    MoonLordBackground background;
    double reachLeft = 15, reachRight = 15;
    boolean secondPhase = false;
    MoonLordEye head, leftHand, rightHand;

    private void setupSingleLocation(EntitySlime eye, MoonLordBackground background,
                                     Location backgroundLoc, double verticalOffset) {
        Vector facingDirection = MathHelper.getDirection(backgroundLoc,
                target.getLocation(), eye.getSize() * 0.255);
        double facingYaw = MathHelper.getVectorYaw(facingDirection);
        // teleport
        Location eyeLoc = backgroundLoc.clone()
                .add(facingDirection).add(0, verticalOffset, 0);
        eye.getBukkitEntity().teleport(eyeLoc);
        background.getBukkitEntity().teleport(backgroundLoc);
        // facing direction
        eye.yaw = (float) facingYaw;
        background.yaw = (float) facingYaw;
    }
    private void setupLocation() {
        double centerLocAngle = indexAI / 10d;
        // setup new location
        Location targetedCenterLoc, actualCenterLoc;
        {
            Vector offsetDir = MathHelper.vectorFromYawPitch_quick(centerLocAngle, 0);
            offsetDir.multiply(50);
            targetedCenterLoc = target.getLocation().add(offsetDir);
            double distSqr = targetedCenterLoc.distanceSquared(background.getBukkitEntity().getLocation());
            // move towards actual targeted location if close enough
            if (distSqr < MAX_DIST_BEFORE_TELEPORT * MAX_DIST_BEFORE_TELEPORT) {
                actualCenterLoc = background.getBukkitEntity().getLocation();
                actualCenterLoc.add(MathHelper.getDirection(
                        actualCenterLoc, targetedCenterLoc, MOVE_SPEED, true));
            }
            // teleport to actual targeted location if far away
            else
                actualCenterLoc = targetedCenterLoc;
        }
        Vector facingDirection = MathHelper.getDirection(actualCenterLoc,
                target.getLocation(), 1);
        double facingYaw = MathHelper.getVectorYaw(facingDirection);
        // teleport body
        setupSingleLocation(this, background, actualCenterLoc, 8);
        // teleport head
        Location headBackgroundLoc = actualCenterLoc.clone().add(0, 12, 0);
        setupSingleLocation(head, head.background, headBackgroundLoc, 9.25);
        // teleport hands
        Vector leftHandOffset = MathHelper.vectorFromYawPitch_quick(facingYaw - 90, 0);
        Vector rightHandOffset = leftHandOffset.clone();
        leftHandOffset.multiply(reachLeft);
        rightHandOffset.multiply(reachRight);
        Location leftHandBackgroundLoc = actualCenterLoc.clone().add(0, 8, 0).add(leftHandOffset);
        Location rightHandBackgroundLoc = actualCenterLoc.clone().add(0, 8, 0).add(rightHandOffset);
        setupSingleLocation(leftHand, leftHand.background, leftHandBackgroundLoc, 3);
        setupSingleLocation(rightHand, rightHand.background, rightHandBackgroundLoc, 3);
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
            // if target is valid, teleport parts to location
            setupLocation();
            // if the eyes are destroyed, the heart can now be damaged.
            if (!secondPhase && head.getHealth() < 10 && leftHand.getHealth() < 10 && rightHand.getHealth() < 10) {
                removeScoreboardTag("noDamage");
                setCustomName("月球领主心脏§1");

                secondPhase = true;
            }
            // increase index to account for slow rotation
            indexAI ++;
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // no collision dmg
    }
    // default constructor to handle chunk unload
    public MoonLord(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public MoonLord(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        spawnLoc.setY( spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("月球领主心脏");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("noDamage");
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 1d);
            attrMap.put("damageTakenMulti", 0.95);
            attrMap.put("defence", 140d);
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
                    getBukkitEntity(), BossHelper.BossType.GOLEM.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(6, false);
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
        // background
        background = new MoonLordBackground(target, this, MoonLordBackground.MoonLordBackgroundType.BODY);
        // spawn eyes
        leftHand = new MoonLordEye(target, this, MoonLordEye.MoonLordEyeLocation.LEFT_HAND);
        rightHand = new MoonLordEye(target, this, MoonLordEye.MoonLordEyeLocation.RIGHT_HAND);
        head = new MoonLordEye(target, this, MoonLordEye.MoonLordEyeLocation.HEAD);
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
