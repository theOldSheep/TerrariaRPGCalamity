package terraria.entity.boss.astrumDeus;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
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

public class AstrumDeus extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.ASTRUM_DEUS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.ASTRAL_INFECTION;
    public static final double BASIC_HEALTH = 1728000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    AstrumDeus head;
    public static final int TOTAL_LENGTH = 81, SECOND_HEAD_INDEX = TOTAL_LENGTH / 2 + 1, SPAWN_ANIMATION_DURATION = 150;
    public static final double
            HEAD_DMG = 720d, HEAD_DEF = 70d, HEAD_DR = 0.2,
            BODY_DMG = 480d, BODY_DEF = 40d, BODY_DR = 0.1,
            TAIL_DMG = 384d, TAIL_DEF = 100d, TAIL_DR = 0.3,
            SPEED_LASER = 2.25, SPEED_MINE = 1.5;
    public static final EntityHelper.WormSegmentMovementOptions FOLLOW_PROPERTY =
            new EntityHelper.WormSegmentMovementOptions()
                    .setFollowDistance(5)
                    .setFollowingMultiplier(1)
                    .setStraighteningMultiplier(0.1)
                    .setVelocityOrTeleport(false);
    static final EntityHelper.AimHelperOptions laserAimHelper;
    static HashMap<String, Double> attrMapLaser, attrMapMine, attrMapHead, attrMapBody, attrMapTail;
    static {
        laserAimHelper = new EntityHelper.AimHelperOptions()
                .setProjectileSpeed(SPEED_LASER);

        attrMapLaser = new HashMap<>();
        attrMapLaser.put("damage", 528d);
        attrMapLaser.put("knockback", 2d);
        attrMapMine = new HashMap<>();
        attrMapMine.put("damage", 624d);
        attrMapMine.put("knockback", 2d);

        attrMapHead = new HashMap<>();
        attrMapHead.put("crit", 0.04);
        attrMapHead.put("knockback", 4d);
        attrMapHead.put("knockbackResistance", 1d);
        attrMapHead.put("damage", HEAD_DMG);
        attrMapHead.put("defence", HEAD_DEF);
        attrMapHead.put("damageTakenMulti", 1 - HEAD_DR);

        attrMapBody = new HashMap<>();
        attrMapBody.put("crit", 0.04);
        attrMapBody.put("knockback", 4d);
        attrMapBody.put("knockbackResistance", 1d);
        attrMapBody.put("damage", BODY_DMG);
        attrMapBody.put("defence", BODY_DEF);
        attrMapBody.put("damageTakenMulti", 1 - BODY_DR);

        attrMapTail = new HashMap<>();
        attrMapTail.put("crit", 0.04);
        attrMapTail.put("knockback", 4d);
        attrMapTail.put("knockbackResistance", 1d);
        attrMapTail.put("damage", TAIL_DMG);
        attrMapTail.put("defence", TAIL_DEF);
        attrMapTail.put("damageTakenMulti", 1 - TAIL_DR);
    }
    public EntityHelper.ProjectileShootInfo projectilePropertyLaserBlue, projectilePropertyLaserOrange, projectilePropertyMine;
    int index;
    int indexAI = 0;
    double healthRatio = 1;
    Vector dVec = null, bufferVec = new Vector(0, 0, 0);
    boolean charging = true, secondPhase = false;
    // spawn particle related
    int spawnAnimationIndex = SPAWN_ANIMATION_DURATION;
    Location summonedLocation, summonParticleLoc1, summonParticleLoc2;
    static GenericHelper.ParticleLineOptions summonParticle1, summonParticle2;
    static {
        summonParticle1 = new GenericHelper.ParticleLineOptions()
                .setParticleColor("0|255|255")
                .setWidth(0.15)
                .setTicksLinger(10);
        summonParticle2 = new GenericHelper.ParticleLineOptions()
                .setParticleColor("255|255|0")
                .setWidth(0.15)
                .setTicksLinger(10);
    }
    private void handleSpawnAnimation() {
        if (index != 0) {
            spawnAnimationIndex --;
            return;
        }
        summonedLocation.add(0, 0.125, 0);
        // get the max possible radius
        double maxRadiusMulti = 1.0001 - Math.abs( (spawnAnimationIndex * 2d / SPAWN_ANIMATION_DURATION) - 1);
        maxRadiusMulti = Math.sqrt(maxRadiusMulti);
        double maxRadius = 6 * maxRadiusMulti;
        // get the current radius and angle
        double currRadiusMulti = 1.0001 - Math.abs( (spawnAnimationIndex % 15 * 2d / 15) - 1);
        currRadiusMulti = Math.sqrt(currRadiusMulti);
        double currRadius = maxRadius * currRadiusMulti;
        double currAngle = 12 * spawnAnimationIndex;
        // get current display location
        double offsetX = MathHelper.xsin_degree(currAngle) * currRadius;
        double offsetZ = MathHelper.xcos_degree(currAngle) * currRadius;
        Location currLoc1 = summonedLocation.clone().add(offsetX, 0, offsetZ);
        Location currLoc2 = summonedLocation.clone().subtract(offsetX, 0, offsetZ);
        // spawn particles
        Vector particleDir1 = summonParticleLoc1.subtract(currLoc1).toVector();
        summonParticle1.setLength(particleDir1.length());
        GenericHelper.handleParticleLine(particleDir1, currLoc1, summonParticle1);
        Vector particleDir2 = summonParticleLoc2.subtract(currLoc2).toVector();
        summonParticle2.setLength(particleDir2.length());
        GenericHelper.handleParticleLine(particleDir2, currLoc2, summonParticle2);
        // save current location
        summonParticleLoc1 = currLoc1;
        summonParticleLoc2 = currLoc2;
        // remove 1 from spawnAnimationIndex
        if (--spawnAnimationIndex <= 0) {
            bukkitEntity.teleport(summonedLocation);
        }
    }
    // AI related
    // only the first head should call this function
    private void phaseTransition() {
        // new tail
        LivingEntity newTail = bossParts.get(SECOND_HEAD_INDEX - 2);
        EntityHelper.setMetadata(newTail, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMapTail.clone());
        newTail.setCustomName(BOSS_TYPE.msgName + "§2");
        // remove a segment to separate the two
        LivingEntity toRemove = bossParts.get(SECOND_HEAD_INDEX - 1);
        toRemove.setHealth(0d);
        toRemove.remove();
        // new head
        LivingEntity newHead = bossParts.get(SECOND_HEAD_INDEX);
        AstrumDeus newHeadNMS = (AstrumDeus) ((CraftLivingEntity) newHead).getHandle();
        EntityHelper.setMetadata(newHead, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMapHead.clone());
        newHead.setCustomName(BOSS_TYPE.msgName);
        // new head should become a new health pool
        double updatedHealth = getHealth();
        // tweak second half of body to share health with second head
        for (int idx = SECOND_HEAD_INDEX; idx < bossParts.size(); idx ++) {
            LivingEntity currentSegment = bossParts.get(idx);
            AstrumDeus currSegmentNMS = (AstrumDeus) ((CraftLivingEntity) currentSegment).getHandle();
            // update health of second half
            currentSegment.setHealth(updatedHealth);
            EntityHelper.setMetadata(currentSegment, EntityHelper.MetadataName.DAMAGE_TAKER,
                    idx == SECOND_HEAD_INDEX ? null : newHead);
            currSegmentNMS.head = idx == SECOND_HEAD_INDEX ? null : newHeadNMS;
        }
        // mark second phase as true to prevent excessive call
        secondPhase = true;
    }
    private void shootProjectiles(int attackMethod) {
        EntityHelper.ProjectileShootInfo shootInfo;
        switch (attackMethod) {
            case 1: {
                if (healthRatio > 0.5)
                    shootInfo = index % 2 == 0 ? projectilePropertyLaserBlue : projectilePropertyLaserOrange;
                else
                    shootInfo = index * 2 < TOTAL_LENGTH ? projectilePropertyLaserBlue : projectilePropertyLaserOrange;
                break;
            }
            case 2:
            default: {
                shootInfo = projectilePropertyMine;
                break;
            }
        }
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        switch (attackMethod) {
            // laser
            case 1: {
                Location targetLoc;
                if (healthRatio > 0.5)
                    targetLoc = target.getEyeLocation();
                else
                    targetLoc = EntityHelper.helperAimEntity(shootInfo.shootLoc, target, laserAimHelper);
                shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, targetLoc, SPEED_LASER);
                EntityHelper.spawnProjectile(shootInfo);
                break;
            }
            // mine
            case 2: {
                shootInfo.velocity = MathHelper.randomVector().multiply(SPEED_MINE);
                EntityHelper.spawnProjectile(shootInfo);
                break;
            }
        }
    }
    private void headRushEnemy() {
        double healthRatio = getHealth() / getMaxHealth();
        double distSqr = bukkitEntity.getLocation().distanceSquared(target.getLocation());
        if (dVec == null)
            indexAI = 0;
        indexAI %= 60;
        if (indexAI == 0) {
            charging = true;
            indexAI = 1;
        }
        // set velocity
        boolean shouldIncreaseIdx = true;
        if (indexAI < 25) {
            if (charging) {
                // stop charging after getting close enough
                double chargeDist = 12;
                if (distSqr < chargeDist * chargeDist && dVec != null) {
                    charging = false;
                    // fire a spread of sand clouds
                    shootProjectiles(2);
                }
                else {
                    Location targetLoc = target.getEyeLocation();
                    dVec = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double dVecLen = dVec.length();
                    if (dVecLen < 0.01) {
                        dVec = new Vector(0, -1, 0);
                        dVecLen = 1;
                    }
                    dVec.multiply(1 / dVecLen);
                    shouldIncreaseIdx = false;
                }
            }
            else {
                dVec.multiply(0.975);
                if (index == 0)
                    dVec.setY(dVec.getY() - 0.05);
                else
                    dVec.setY(dVec.getY() + 0.05);
            }
        }
        else {
            // one head borrows, one head flies
            boolean yCoordRequirementMet = locY > target.getLocation().getY();
            if (index != 0) yCoordRequirementMet = !yCoordRequirementMet;
            if (yCoordRequirementMet)
                shouldIncreaseIdx = false;
            else {
                Location targetLoc = target.getLocation().subtract(0, index == 0 ? 20 : -20, 0);
                dVec = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                double dVecLen = dVec.length();
                if (dVecLen < 0.01) {
                    dVec = new Vector(0, -1, 0);
                    dVecLen = 1;
                }
                dVec.multiply(0.35 / dVecLen);
            }
            dVec.multiply(0.95);
            if (index == 0)
                dVec.setY(dVec.getY() - 0.05);
            else
                dVec.setY(dVec.getY() + 0.05);
        }
        // tweak buffer vector, cloned and scaled to produce actual velocity
        bufferVec.add(dVec);
        double bufferVecLen = bufferVec.length();
        double maxBufferVecLen = 2.25;
        if (bufferVecLen > maxBufferVecLen) {
            bufferVec.multiply(maxBufferVecLen / bufferVecLen);
            bufferVecLen = maxBufferVecLen;
        }
        Vector actualVelocity = bufferVec.clone();
        double actualLen = bufferVecLen;
        if (actualLen < 0.01) {
            actualVelocity = new Vector(1, 0, 0);
            actualLen = 1;
        }
        double speed = 3 - healthRatio;
        actualVelocity.multiply( speed / actualLen);
        bukkitEntity.setVelocity(actualVelocity);
        // increase indexAI
        if (shouldIncreaseIdx)
            indexAI ++;
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            healthRatio = getHealth() / getMaxHealth();
            boolean isHead = head == null;
            // update target
            if (isHead) {
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
            }
            else
                target = head.target;
            // if target is valid, attack
            // phase transition
            if (index == 0 && !secondPhase && healthRatio < 0.5)
                phaseTransition();

            if (spawnAnimationIndex > 0) {
                handleSpawnAnimation();
            }
            // head
            else if (isHead) {
                Bukkit.broadcastMessage(index + " health " + getHealth());
                // update boss bar and dynamic DR
                terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, ticksLived, BOSS_TYPE);
                // apply extreme gravity to target
                EntityHelper.applyEffect(target, "极限重力", 310);
                // attack
                headRushEnemy();
                // face the player
                this.yaw = (float) MathHelper.getVectorYaw(target.getLocation().subtract(bukkitEntity.getLocation()).toVector());
                // follow
                EntityHelper.handleSegmentsFollow(bossParts, FOLLOW_PROPERTY, index);
            }
            // body and tail
            else {
                if (++indexAI % 200 == index * 2) {
                    shootProjectiles(1);
                }
                if (index % 2 == 0 && indexAI % 100 == index) {
                    shootProjectiles(2);
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public AstrumDeus(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public AstrumDeus(Player summonedPlayer, ArrayList<LivingEntity> bossParts, Location spawnParticleLoc, int index) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // copy variable
        this.bossParts = bossParts;
        this.index = index;
        // spawn location
        summonedLocation = spawnParticleLoc;
        summonParticleLoc1 = spawnParticleLoc.clone();
        summonParticleLoc2 = spawnParticleLoc.clone();
        Location spawnLoc = spawnParticleLoc.clone();
        spawnLoc.setY(-10);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        if (index == 0) {
            setCustomName(BOSS_TYPE.msgName);
            this.head = null;
        }
        else {
            this.head = (AstrumDeus) ((CraftEntity) bossParts.get(0)).getHandle();
            if (index + 1 < TOTAL_LENGTH)
                setCustomName(BOSS_TYPE.msgName + "§1");
            else
                setCustomName(BOSS_TYPE.msgName + "§2");
        }
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            // head
            if (index == 0) {
                attrMap = (HashMap<String, Double>) attrMapHead.clone();
            }
            // tail
            else if (index + 1 == TOTAL_LENGTH) {
                attrMap = (HashMap<String, Double>) attrMapTail.clone();
            }
            // body
            else {
                attrMap = (HashMap<String, Double>) attrMapBody.clone();
            }
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        if (index == 0) {
            bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                    BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        } else {
            bossbar = (BossBattleServer) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_BAR).value();
        }
        // init target map
        {
            if (index == 0) {
                targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                        getBukkitEntity(), BossHelper.BossType.WALL_OF_FLESH.msgName, summonedPlayer, true, bossbar);
            } else {
                targetMap = (HashMap<Player, Double>) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
            }
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
            target = summonedPlayer;
        }
        // init health and slime size
        {
            setSize(10, false);
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
            projectilePropertyLaserBlue = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapLaser,
                    EntityHelper.DamageType.ARROW, "星幻激光");
            projectilePropertyLaserOrange = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapLaser,
                    EntityHelper.DamageType.ARROW, "星幻激光");
            projectilePropertyLaserOrange.properties.put("trailColor", "255|255|0");
            projectilePropertyMine = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapMine,
                    EntityHelper.DamageType.ARROW, "幻星雷");
            // segment settings
            if (head != null)
                EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.DAMAGE_TAKER, head.getBukkitEntity());
            // next segment
            if (index + 1 < TOTAL_LENGTH)
                new AstrumDeus(summonedPlayer, bossParts, spawnParticleLoc, index + 1);
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        if (index == 0 || index == SECOND_HEAD_INDEX) {
            // prevent duplicated death handling
            if (! (bossParts.get(0).isDead() && bossParts.get(SECOND_HEAD_INDEX).isDead()) )
                return;
            if (!bossbar.visible)
                return;
            // drop loot
            if (getMaxHealth() > 10) {
                terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);
            }
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
        // update health
        if (head != null)
            setHealth(head.getHealth());
        // load nearby chunks
        if (index % 10 == 0) {
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
