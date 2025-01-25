package terraria.entity.boss.hardMode.golem;

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
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Golem extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.GOLEM;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.TEMPLE;
    public static final double BASIC_HEALTH = 104623 * 2, BASIC_HEALTH_BR = 272625 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapFireball, attrMapBeam, attrMapInfernoBolt;
    static {
        attrMapFireball = new HashMap<>();
        attrMapFireball.put("damage", 468d);
        attrMapFireball.put("knockback", 2.5d);
        attrMapBeam = new HashMap<>();
        attrMapBeam.put("damage", 504d);
        attrMapBeam.put("knockback", 1d);
        attrMapInfernoBolt = new HashMap<>();
        attrMapInfernoBolt.put("damage", 540d);
        attrMapInfernoBolt.put("knockback", 2d);
    }
    static double HORIZONTAL_SPEED = 1, VERTICAL_SPEED = 3;

    // phase 1: damage fists only  2: head only  3: body can be damaged
    int indexAI = 0, phaseAI = 1, jumpIndex = 0;
    boolean falling = false;
    Vector orthogonalDir = new Vector(), cachedVelocity = new Vector();
    GolemHead head;
    GolemFist[] fists;
    EntityHelper.ProjectileShootInfo shootInfoBeam;

    private Vector getHorizontalDirection() {
        Location targetLoc = target.getLocation();
        Location currLoc = bukkitEntity.getLocation();
        targetLoc.setY(currLoc.getY());
        return MathHelper.getDirection(currLoc, targetLoc, 1);
    }
    private void shootLaser() {
        shootInfoBeam.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoBeam.velocity = MathHelper.getDirection(shootInfoBeam.shootLoc, target.getEyeLocation(), 3);
        EntityHelper.spawnProjectile(shootInfoBeam);
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
                // phase transition
                switch (phaseAI) {
                    case 1:
                        if ( !(fists[0].isAlive() || fists[1].isAlive()) ) {
                            head.removeScoreboardTag("noDamage");
                            phaseAI = 2;
                        }
                        break;
                    case 2:
                        if ( head.getHealth() < 10 ) {
                            phaseAI = 3;
                            bukkitEntity.removeScoreboardTag("noDamage");
                            head.addScoreboardTag("noDamage");
                            EntityHelper.setMetadata(head.getBukkitEntity(), EntityHelper.MetadataName.HEALTH_LOCKED_AT_AMOUNT, null);
                        }
                        break;
                }
                // leap
                noclip = true;
                if (indexAI >= 0) {
                    double speed = HORIZONTAL_SPEED;
                    if (indexAI == 0) {
                        cachedVelocity = getHorizontalDirection();
                        cachedVelocity.multiply(speed);
                        cachedVelocity.setY(VERTICAL_SPEED);
                        falling = false;
                    }
                    else {
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
                                    indexAI = -30;
                                    jumpIndex ++;
                                    bukkitEntity.setVelocity(new Vector());
                                }
                            }
                        }
                        // every third jump chases enemy to the same height
                        else if (locY > target.getLocation().getY() || jumpIndex % 3 != 0)
                            falling = true;
                        cachedVelocity.setY(yComp);
                    }
                    bukkitEntity.setVelocity(cachedVelocity);
                }
                else if (phaseAI == 3 && indexAI % 5 == 0) {
                    shootLaser();
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
    public Golem(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public Golem(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
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
            attrMap.put("damage", 480d);
            attrMap.put("defence", 52d);
            attrMap.put("damageTakenMulti", 0.75d);
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
            setSize(12, false);
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
        // boss parts
        {
            head = new GolemHead(target, this);
            fists = new GolemFist[] {
                    new GolemFist(target, this, 1),
                    new GolemFist(target, this, 2),
            };
            new GolemFoot(target, this, 1);
            new GolemFoot(target, this, 2);
        }
        // shoot info
        {
            shootInfoBeam = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapBeam,
                    DamageHelper.DamageType.MAGIC, "石巨人激光");
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
