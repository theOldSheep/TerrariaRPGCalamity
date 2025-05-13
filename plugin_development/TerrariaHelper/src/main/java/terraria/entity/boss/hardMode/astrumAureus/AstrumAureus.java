package terraria.entity.boss.hardMode.astrumAureus;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class AstrumAureus extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.ASTRUM_AUREUS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.ASTRAL_INFECTION;
    public static final double BASIC_HEALTH = 282240 * 2, BASIC_HEALTH_BR = 1776000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    enum AIPhase {
        RECHARGE, CRAWL, JUMP;
    }
    boolean falling = false;
    AIPhase phaseAI = AIPhase.RECHARGE;
    int indexAI = -40, attacksDuringPhase = 0;
    double healthRatio = 1;
    static HashMap<String, Double> attrMapLaser, attrMapCrystal;
    static final int SPREAD_JUMP_MIN = 11, SPREAD_JUMP_MAX = 11, SPREAD_CRAWL_MIN = 8, SPREAD_CRAWL_MAX = 8;
    static final double SPEED_LASER = 2.25, SPEED_CRYSTAL = 1.5,
            HORIZONTAL_SPEED = 1.75, HORIZONTAL_ACC = 0.125,
            SPEED_CRAWL_MULTI_MIN = 0.25, SPEED_CRAWL_MULTI_MAX = 0.5,
            SPEED_JUMP_MULTI_MIN = 1, SPEED_JUMP_MULTI_MAX = 1;
    EntityHelper.ProjectileShootInfo shootInfoLaser, shootInfoCrystal;
    static {
        attrMapLaser = new HashMap<>();
        attrMapLaser.put("damage", 540d);
        attrMapLaser.put("knockback", 2d);
        attrMapCrystal = new HashMap<>();
        attrMapCrystal.put("damage", 600d);
        attrMapCrystal.put("knockback", 2d);
    }
    private void randomTeleport() {
        for (int i = 0; i < 2; i ++)
            new AureusSpawn(target, this);
        // teleport
        Vector offset = MathHelper.vectorFromYawPitch_approx(Math.random() * 360, 0);
        offset.multiply(40);
        Location teleportLoc = target.getLocation().add(offset);
        teleportLoc.setY(teleportLoc.getWorld().getHighestBlockYAt(teleportLoc));
        bukkitEntity.teleport(teleportLoc);
    }
    private void changePhase() {
        // change phase
        switch (phaseAI) {
            case JUMP:
                if (attacksDuringPhase > 5)
                    phaseAI = AIPhase.RECHARGE;
                else
                    return;
                break;
            case RECHARGE:
                phaseAI = AIPhase.CRAWL;
                // Damage Taken: 1 -> 0.75
                AttributeHelper.tweakAttribute(attrMap, "damageTakenMulti", "-0.3333", true);
                // defence: 40 -> 80
                AttributeHelper.tweakAttribute(attrMap, "defence", "40", true);
                break;
            case CRAWL:
                phaseAI = AIPhase.JUMP;
                break;
        }
        // aftermath
        indexAI = -1;
        attacksDuringPhase = 0;
        // take additional damage during recharge
        if (phaseAI == AIPhase.RECHARGE) {
            bossbar.color = BossBattle.BarColor.GREEN;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
            // Damage Taken: 0.75 -> 1
            AttributeHelper.tweakAttribute(attrMap, "damageTakenMulti", "-0.3333", false);
            // defence: 80 -> 40
            AttributeHelper.tweakAttribute(attrMap, "defence", "40", false);
        }
        else {
            bossbar.color = BossBattle.BarColor.RED;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
        }
    }

    private int getShootSpread() {
        int spreadMin = phaseAI == AIPhase.CRAWL ? SPREAD_CRAWL_MIN : SPREAD_JUMP_MIN;
        int spreadMax = phaseAI == AIPhase.CRAWL ? SPREAD_CRAWL_MAX : SPREAD_JUMP_MAX;
        return spreadMin + (int) ((1 - healthRatio) * (spreadMax - spreadMin));
    }
    private double getSpeedMulti() {
        double speedMultiMin = phaseAI == AIPhase.CRAWL ? SPEED_CRAWL_MULTI_MIN : SPEED_JUMP_MULTI_MIN;
        double speedMultiMax = phaseAI == AIPhase.CRAWL ? SPEED_CRAWL_MULTI_MAX : SPEED_JUMP_MULTI_MAX;
        return speedMultiMin + (1 - healthRatio) * (speedMultiMax - speedMultiMin);
    }
    private Vector getHorizontalDirection() {
        Location targetLoc = target.getLocation();
        Location currLoc = bukkitEntity.getLocation();
        targetLoc.setY(currLoc.getY());
        return MathHelper.getDirection(currLoc, targetLoc, 1);
    }

    private void shootLasers() {
        shootInfoLaser.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        for (Vector shootVelocity : MathHelper.getCircularProjectileDirections(
                getShootSpread(), 3, 60,
                target, shootInfoLaser.shootLoc, SPEED_LASER)) {
            shootInfoLaser.velocity = shootVelocity;
            EntityHelper.spawnProjectile(shootInfoLaser);
        }
    }
    private void AIPhaseRecharge() {
        if (indexAI < 50 && indexAI % 4 == 0) {
            Vector shootDir = MathHelper.vectorFromYawPitch_approx(Math.random() * 360, -75 - Math.random() * 15);
            shootDir.multiply(SPEED_CRYSTAL);
            shootInfoCrystal.setLockedTarget(target);
            shootInfoCrystal.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            shootInfoCrystal.velocity = shootDir;
            EntityHelper.spawnProjectile(shootInfoCrystal);
        }
        else if (indexAI == 50)
            randomTeleport();
        else if (indexAI > 80)
            changePhase();
    }
    private void AIPhaseCrawl() {
        // crawl movement
        this.noclip = true;
        {
            Vector horizontalAcc = getHorizontalDirection();
            double speedMulti = getSpeedMulti();
            horizontalAcc.multiply(HORIZONTAL_ACC * speedMulti);
            Vector velocity = bukkitEntity.getVelocity();
            velocity.setY(0);
            velocity.add(horizontalAcc);
            double velLen = velocity.length();
            double horSpeed = HORIZONTAL_SPEED * speedMulti;
            if (velLen > horSpeed)
                velocity.multiply(horSpeed / velLen);

            double verticalVelocity = 0.5;
            Block testBlk = bukkitEntity.getLocation().subtract(0, verticalVelocity, 0).getBlock();
            if (testBlk.getType().isSolid()) {
                double targetY = testBlk.getY() + 1.0001;
                if (testBlk.getRelative(BlockFace.UP).getType().isSolid()) {
                    targetY ++;
                }
                velocity.setY( Math.min(verticalVelocity, targetY - locY) );
            }
            else {
                velocity.setY(-verticalVelocity);
            }
            bukkitEntity.setVelocity(velocity);
        }
        // shoot projectiles
        if (indexAI % 30 == 0) {
            shootLasers();
        }
        if (indexAI > 150)
            changePhase();
    }
    private void AIPhaseJump() {
        if (indexAI >= 0) {
            double speedMulti = getSpeedMulti();
            double speed = HORIZONTAL_SPEED * speedMulti;
            Vector velocity;
            if (indexAI == 0) {
                velocity = getHorizontalDirection();
                velocity.multiply(speed);
                velocity.setY(2);
                falling = false;
            }
            else {
                // horizontal velocity
                velocity = bukkitEntity.getVelocity();
                double yComp = velocity.getY();
                velocity.setY(0);
                Vector horAcc = getHorizontalDirection();
                horAcc.multiply(HORIZONTAL_ACC * speedMulti);
                velocity.add(horAcc);
                double velLen = velocity.length();
                if (velLen > speed) {
                    velocity.multiply(speed / velLen);
                }
                noclip = true;
                if (falling) {
                    yComp -= 0.075;
                    // landing
                    if (locY < target.getLocation().getY()) {
                        noclip = false;
                        if (locY < 0 || onGround) {
                            shootLasers();
                            velocity = new Vector();
                            yComp = 0;
                            indexAI = -30;
                            attacksDuringPhase ++;
                            bukkitEntity.setVelocity(new Vector());
                            changePhase();
                        }
                    }
                }
                else if (locY > target.getLocation().getY())
                    falling = true;
                velocity.setY(yComp);
            }
            bukkitEntity.setVelocity(velocity);
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
                    IGNORE_DISTANCE, terraria.entity.boss.BossHelper.TimeRequirement.NONE, BIOME_REQUIRED, targetMap.keySet());
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
            healthRatio = getHealth() / getMaxHealth();
            switch (phaseAI) {
                case RECHARGE:
                    AIPhaseRecharge();
                    break;
                case CRAWL:
                    AIPhaseCrawl();
                    break;
                case JUMP:
                    AIPhaseJump();
                    break;
            }
            indexAI ++;
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        if (phaseAI != AIPhase.RECHARGE)
            terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public AstrumAureus(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public AstrumAureus(Player summonedPlayer) {
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
            attrMap.put("damage", 660d);
            attrMap.put("damageTakenMulti", 1d);
            attrMap.put("defence", 40d);
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
                    getBukkitEntity(), BossHelper.BossType.PLANTERA.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(16, false);
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
        }
        // shoot info's
        {
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapLaser,
                    DamageHelper.DamageType.MAGIC, "星幻激光");
            shootInfoCrystal = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapCrystal,
                    DamageHelper.DamageType.BULLET, "星幻凝晶");
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
