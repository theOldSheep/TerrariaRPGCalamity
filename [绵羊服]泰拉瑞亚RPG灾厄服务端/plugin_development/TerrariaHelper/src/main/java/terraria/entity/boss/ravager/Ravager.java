package terraria.entity.boss.ravager;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.golem.GolemFist;
import terraria.entity.boss.golem.GolemFoot;
import terraria.entity.boss.golem.GolemHead;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Ravager extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.RAVAGER;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 128400 * 2, BASIC_HEALTH_POST_PROVIDENCE = 513600 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static double HORIZONTAL_SPEED = 1.5, VERTICAL_SPEED = 3, FLY_SPEED_MIN = 2, SMASH_SPEED = 3, PILLAR_OFFSET = 8;
    static final EntityHelper.AimHelperOptions spawnAimHelper;
    static {
        spawnAimHelper = new EntityHelper.AimHelperOptions()
                .setAimMode(true)
                .setTicksOffset(10);
    }

    // phase 1: damage body parts only   2: body can be damaged
    int indexAI = 0, phaseAI = 1, jumpIndex = 0;
    boolean falling = false, headFreed = false, postProvidence;
    Vector orthogonalDir = new Vector(), cachedVelocity = new Vector();
    RavagerHead head;
    RavagerClaw[] claws;
    RavagerLeg[] legs;

    private Vector getHorizontalDirection() {
        Location targetLoc = target.getLocation();
        Location currLoc = bukkitEntity.getLocation();
        targetLoc.setY(currLoc.getY());
        return MathHelper.getDirection(currLoc, targetLoc, 1);
    }

    private boolean attemptChangePhase() {
        // do not enter tier 2 if any other part is alive
        if (!headFreed) {
            if (head.isAlive())
                return false;
            else {
                headFreed = true;
                head = new RavagerHead(target, this, postProvidence, true);
            }
        }
        for (RavagerClaw claw : claws)
            if (claw.isAlive())
                return false;
        for (RavagerLeg leg : legs)
            if (leg.isAlive())
                return false;
        // enter tier 2
        removeScoreboardTag("noDamage");
        indexAI = -20;
        phaseAI = 2;
        return true;
    }

    private void spawnRockPillars() {
        Location currTargetLoc = target.getLocation();
        Location predictedTargetLoc = EntityHelper.helperAimEntity(getBukkitEntity(), target, spawnAimHelper);
        Vector offset = predictedTargetLoc.subtract(currTargetLoc).toVector();
        double spawnYaw = MathHelper.getVectorYaw(offset);
        Vector offset1 = MathHelper.vectorFromYawPitch_quick(spawnYaw, 0);
        Vector offset2 = MathHelper.vectorFromYawPitch_quick(spawnYaw + 90, 0);
        offset1.multiply(PILLAR_OFFSET);
        offset2.multiply(PILLAR_OFFSET);
        new RavagerRockPillar(this, target.getLocation().add(offset1));
        new RavagerRockPillar(this, target.getLocation().subtract(offset1));
        new RavagerRockPillar(this, target.getLocation().add(offset2));
        new RavagerRockPillar(this, target.getLocation().subtract(offset2));
    }
    private void phase1AI() {
        // leap
        noclip = true;
        if (indexAI < 0)
            return;
        double speed = HORIZONTAL_SPEED;
        if (indexAI == 0) {
            cachedVelocity = getHorizontalDirection();
            cachedVelocity.multiply(speed);
            cachedVelocity.setY(VERTICAL_SPEED);
            falling = false;
        } else {
            double yComp = cachedVelocity.getY();
            if (falling) {
                yComp -= 0.2;
                // landing
                if (locY + motY < target.getLocation().getY()) {
                    // as soon as the golem is below player, it can collide with blocks.
                    noclip = false;
                    if (locY < 0 || onGround) {
                        cachedVelocity = new Vector();
                        yComp = 0;
                        indexAI = -5;
                        jumpIndex++;
                        bukkitEntity.setVelocity(new Vector());
                    }
                }
            }
            // every second jump chases enemy to the same height
            else if (locY > target.getLocation().getY() || jumpIndex % 2 != 0)
                falling = true;
            cachedVelocity.setY(yComp);
        }
        bukkitEntity.setVelocity(cachedVelocity);
    }
    private void phase2AI() {
        noclip = true;
        if (indexAI < 0)
            return;
        // fly above the player
        if (indexAI <= 40) {
            Location targetLoc = target.getEyeLocation().add(0, 16, 0);
            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
            double velLen = velocity.length();
            double speed = Math.max(velLen * indexAI / 39, FLY_SPEED_MIN);
            //
            if (speed > velLen) {
                speed = velLen;
                indexAI = 40;
            }
            velocity.multiply(speed / velLen);
            bukkitEntity.setVelocity(velocity);
        }
        // smash down
        else {
            motX = 0;
            motY = -SMASH_SPEED;
            motZ = 0;
            // landing
            if (locY + motY < target.getLocation().getY()) {
                // as soon as it will travel below player, it can collide with blocks.
                noclip = false;
                if (locY < 0 || onGround) {
                    indexAI = -15;
                    bukkitEntity.setVelocity(new Vector());
                }
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
                // phase transition and jump
                if (phaseAI == 1) {
                    if (!attemptChangePhase())
                        phase1AI();
                }
                // phase 2 smash
                else {
                    phase2AI();
                }
                // rock pillars
                if (indexAI % 35 == 0) {
                    spawnRockPillars();
                }
                // setup orthogonal direction
                {
                    Location targetLoc = target.getLocation();
                    targetLoc.setY(bukkitEntity.getLocation().getY());
                    orthogonalDir = MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, 1);
                    orthogonalDir = new Vector(orthogonalDir.getZ() * -1, 0, orthogonalDir.getX());
                }
                indexAI ++;
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }


    // default constructor to handle chunk unload
    public Ravager(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.HeightLayer.getHeightLayer(player.getLocation()) == WorldHelper.HeightLayer.SURFACE;
    }
    // a constructor for actual spawning
    public Ravager(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        String providenceName = BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS.msgName;
        postProvidence = PlayerHelper.hasDefeated(summonedPlayer, providenceName);
        // spawn location
        Location spawnLoc = summonedPlayer.getLocation().add(0, 25, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
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
            attrMap.put("damage", postProvidence ? 864d : 576d);
            attrMap.put("damageTakenMulti", 0.65);
            attrMap.put("defence", postProvidence ? 220d : 110d);
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
            BossHelper.BossType bossPrerequisite = postProvidence ? BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS : BossHelper.BossType.PLANTERA;
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), bossPrerequisite.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(12, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = (postProvidence ? BASIC_HEALTH_POST_PROVIDENCE : BASIC_HEALTH) * healthMulti;
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
        // boss parts
        {
            head = new RavagerHead(target, this, postProvidence, false);
            claws = new RavagerClaw[] {
                    new RavagerClaw(target, this, 1, postProvidence),
                    new RavagerClaw(target, this, 2, postProvidence),
            };
            legs = new RavagerLeg[] {
                    new RavagerLeg(target, this, 1, postProvidence),
                    new RavagerLeg(target, this, 2, postProvidence),
            };
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
