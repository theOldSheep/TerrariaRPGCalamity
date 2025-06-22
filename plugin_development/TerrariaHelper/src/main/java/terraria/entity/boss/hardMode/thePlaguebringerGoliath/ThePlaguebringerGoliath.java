package terraria.entity.boss.hardMode.thePlaguebringerGoliath;

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
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class ThePlaguebringerGoliath extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_PLAGUEBRINGER_GOLIATH;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.JUNGLE;
    public static final double BASIC_HEALTH = 255600 * 2, BASIC_HEALTH_BR = 888000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    enum AIPhase {
        DASH, SUMMON, SHOOT, NUKE;
    }
    AIPhase phaseAI = AIPhase.DASH;
    Vector dashVelocity = new Vector();
    int indexAI = -40, attacksDuringPhase = 0;
    double healthRatio = 1;
    boolean secondPhase = false;

    static HashMap<String, Double> attrMapStingerMissile, attrMapNukeBarrage;
    static final double SPEED_NORMAL = 2.25, SPEED_DASH = 3, SPEED_STINGER = 1.75, SPEED_MISSILE = 2.5, SPEED_NUKE = 3;
    EntityHelper.ProjectileShootInfo shootInfoStinger, shootInfoMissile, shootInfoNukeBarrage;
    static {
        attrMapStingerMissile = new HashMap<>();
        attrMapStingerMissile.put("damage", 540d);
        attrMapStingerMissile.put("knockback", 1.5d);
        attrMapNukeBarrage = new HashMap<>();
        attrMapNukeBarrage.put("damage", 660d);
        attrMapNukeBarrage.put("knockback", 2d);
    }
    private void changePhase() {
        indexAI = -1;
        // change phase
        switch (phaseAI) {
            case DASH:
                if (attacksDuringPhase < 5)
                    return;
                phaseAI = AIPhase.SHOOT;
                break;
            case SHOOT:
                phaseAI = AIPhase.SUMMON;
                break;
            case SUMMON:
                phaseAI = secondPhase ? AIPhase.NUKE : AIPhase.DASH;
                break;
            case NUKE:
                if (attacksDuringPhase < 4)
                    return;
                phaseAI = AIPhase.DASH;
                break;
        }
        // bar color
        switch (phaseAI) {
            case DASH:
                bossbar.color = BossBattle.BarColor.RED;
                break;
            case NUKE:
                bossbar.color = BossBattle.BarColor.PURPLE;
                break;
            case SUMMON:
                bossbar.color = BossBattle.BarColor.YELLOW;
                break;
            case SHOOT:
                bossbar.color = BossBattle.BarColor.GREEN;
                break;
        }
        bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
        // aftermath
        attacksDuringPhase = 0;
    }

    private void toSecondPhase() {
        Bukkit.broadcastMessage("§#00FF00瘟疫核弹已就绪，准备发射！！！**音量控诉**");
        secondPhase = true;
    }
    private Vector getHorizontalDirection() {
        Location targetLoc = target.getLocation();
        Location currLoc = bukkitEntity.getLocation();
        targetLoc.setY(currLoc.getY());
        if (currLoc.distanceSquared(targetLoc) < 1e-5)
            return new Vector(1, 0, 0);
        return MathHelper.getDirection(currLoc, targetLoc, 1);
    }

    private void shootStingerMissile() {
        EntityHelper.ProjectileShootInfo shootInfo;
        double speed;
        if (Math.random() < 0.2) {
            shootInfo = shootInfoMissile;
            speed = SPEED_MISSILE;
        }
        else {
            shootInfo = shootInfoStinger;
            speed = SPEED_STINGER;
        }
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, target.getEyeLocation(), speed);
        EntityHelper.spawnProjectile(shootInfo);
    }

    private void AIPhaseDash() {
        Location eyeLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        int dashCountdown = 20 - indexAI;
        if (dashCountdown >= 0) {
            Location targetLoc = target.getEyeLocation();
            double speed;
            if (dashCountdown == 0) {
                bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                speed = SPEED_DASH;
            }
            else {
                speed = SPEED_NORMAL;
                Vector offset = getHorizontalDirection();
                offset.multiply(24);
                targetLoc.subtract(offset);
            }
            dashVelocity = MathHelper.getDirection(eyeLoc, targetLoc, speed);
            bukkitEntity.setVelocity(dashVelocity);
        }
        else {
            // maintain dash velocity
            bukkitEntity.setVelocity(dashVelocity);
            if (indexAI > 50) {
                attacksDuringPhase ++;
                changePhase();
            }
        }
    }
    private void AIPhaseShoot() {
        // hover above target
        {
            Location targetLoc = target.getEyeLocation().add(0, 20, 0);
            bukkitEntity.setVelocity( MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, SPEED_NORMAL) );
        }
        // shoot projectiles
        if (indexAI % 8 == 0) {
            shootStingerMissile();
        }
        else if (indexAI > 100)
            changePhase();
    }
    private void AIPhaseSummon() {
        // speed decays
        {
            Vector velocity = bukkitEntity.getVelocity();
            velocity.multiply(0.95);
            bukkitEntity.setVelocity(velocity);
        }
        // summon destructible rockets and mines
        if (indexAI % 4 == 0) {
            // summon mine
            if (Math.random() < 0.33 && secondPhase)
                new PlagueMine(this);
            // summon rocket
            else
                new PlagueHomingMissile(this);
        }
        if (indexAI > 40)
            changePhase();
    }
    private void AIPhaseNuke() {
        // hover diagonally above player
        if (indexAI < 20) {
            Location targetLoc = target.getEyeLocation();
            Vector offset = getHorizontalDirection();
            offset.multiply(24);
            targetLoc.subtract(offset);
            targetLoc.add(0, 20, 0);
            dashVelocity = MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, SPEED_NORMAL);
            bukkitEntity.setVelocity(dashVelocity);
        }
        // dash and fire a spread of nuke
        else if (indexAI == 20) {
            // dash
            {
                dashVelocity = getHorizontalDirection();
                dashVelocity.multiply(SPEED_DASH);
                double yDist = target.getLocation().getY() + 24 - locY;
                dashVelocity.setY(yDist / 50);
                bukkitEntity.setVelocity(dashVelocity);
            }
            // shoot barrage of nuke
            shootInfoNukeBarrage.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            for (Vector shootVel : MathHelper.getCircularProjectileDirections(
                    7, 3, 45, target, shootInfoNukeBarrage.shootLoc, SPEED_NUKE)) {
                shootInfoNukeBarrage.velocity = shootVel;
                EntityHelper.spawnProjectile(shootInfoNukeBarrage);
            }
        }
        else {
            // maintain the original dash velocity
            bukkitEntity.setVelocity(dashVelocity);
            if (indexAI > 30) {
                attacksDuringPhase ++;
                changePhase();
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
            // if target is valid, attack
            else {
                // increase player aggro duration
                targetMap.get(target.getUniqueId()).addAggressionTick();

                healthRatio = getHealth() / getMaxHealth();
                if (!secondPhase && healthRatio < 0.5)
                    toSecondPhase();
                switch (phaseAI) {
                    case DASH:
                        AIPhaseDash();
                        break;
                    case SHOOT:
                        AIPhaseShoot();
                        break;
                    case SUMMON:
                        AIPhaseSummon();
                        break;
                    case NUKE:
                        AIPhaseNuke();
                        break;
                }
                indexAI ++;
            }
        }
        // face the player
        if (phaseAI == AIPhase.DASH)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public ThePlaguebringerGoliath(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public ThePlaguebringerGoliath(Player summonedPlayer) {
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
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 594d);
            attrMap.put("damageTakenMulti", 0.9);
            attrMap.put("defence", 100d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.RED, BossBattle.BarStyle.PROGRESS);
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.GOLEM.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
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
            shootInfoStinger = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapStingerMissile,
                    DamageHelper.DamageType.ARROW, "歌莉娅瘟疫导弹");
            shootInfoMissile = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapStingerMissile,
                    DamageHelper.DamageType.ROCKET, "瘟疫火箭");
            shootInfoNukeBarrage = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapNukeBarrage,
                    DamageHelper.DamageType.ROCKET, "瘟疫核弹");
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