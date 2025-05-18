package terraria.entity.boss.postMoonLord.theOldDuke;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Sound;
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

public class TheOldDuke extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_OLD_DUKE;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.SULPHUROUS_OCEAN;
    public static final double BASIC_HEALTH = 1440000 * 2, BASIC_HEALTH_BR = 960000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final String VORTEX_ROAR_SOUND = "entity.old_duke.old_duke_roar";
    public static final double PHASE_TRANSITION_1 = 0.8;
    public static final double PHASE_TRANSITION_2 = 0.5;
    public static final double DASH_SPEED = 2.75;
    public static final int SPIKE_BALL_COUNT = 5;
    public static final int SHARK_PROJECTILE_COUNT = 10;
    public static final double SPIKE_BALL_SPEED = 1.2;
    public static final double SHARK_PROJECTILE_SPEED = 1.15;
    static HashMap<String, Double> attrMapSpikeBall, attrMapShark, attrMapVortex;
    static AimHelper.AimHelperOptions aimHelperDash, aimHelperDashAcceleration;
    EntityHelper.ProjectileShootInfo shootInfoSpikeBall, shootInfoShark, shootInfoVortex;
    static {
        aimHelperDash = new AimHelper.AimHelperOptions()
                .setAccelerationMode(false)
                .setProjectileSpeed(DASH_SPEED);
        aimHelperDashAcceleration = new AimHelper.AimHelperOptions()
                .setAccelerationMode(true)
                .setProjectileSpeed(DASH_SPEED);

        attrMapSpikeBall = new HashMap<>();
        attrMapSpikeBall.put("damage", 864d);
        attrMapSpikeBall.put("knockback", 2.25d);
        attrMapShark = new HashMap<>();
        attrMapShark.put("damage", 864d);
        attrMapShark.put("knockback", 4.5d);
        attrMapShark.put("health", 24000d);
        attrMapShark.put("healthMax", 24000d);
        attrMapVortex = new HashMap<>();
        attrMapVortex.put("damage", 1320d);
        attrMapVortex.put("knockback", 1.5d);
    }
    int indexAI = -100, AIPhase = 1;
    boolean teleported = false;
    Vector velocity = new Vector(0, 0.1, 0);


    private void transitionToPhase(int newPhase) {
        AIPhase = newPhase;
        indexAI = -50;
        velocity = new Vector(0, 0, 0);
        if (newPhase == 2) {
            bossbar.color = BossBattle.BarColor.YELLOW;
        } else if (newPhase == 3) {
            bossbar.color = BossBattle.BarColor.RED;
        }
        bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
    }
    private void moveTowardsPlayer() {
        velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 0.5);
    }
    private void fireSpikeBalls() {
        shootInfoSpikeBall.shootLoc = bukkitEntity.getLocation();
        shootInfoSpikeBall.setLockedTarget(target);
        for (int i = 0; i < SPIKE_BALL_COUNT; i++) {
            shootInfoSpikeBall.velocity = MathHelper.randomVector().multiply(SPIKE_BALL_SPEED);
            EntityHelper.spawnProjectile(shootInfoSpikeBall);
        }
    }
    private void fireSharkProjectiles() {
        shootInfoShark.shootLoc = bukkitEntity.getLocation();
        shootInfoShark.setLockedTarget(target);
        for (int i = 0; i < SHARK_PROJECTILE_COUNT; i++) {
            shootInfoShark.velocity = MathHelper.randomVector().multiply(SHARK_PROJECTILE_SPEED);
            EntityHelper.spawnProjectile(shootInfoShark);
        }
    }
    private void summonSulphurousVertex() {
        shootInfoVortex.shootLoc = bukkitEntity.getLocation();
        EntityHelper.spawnProjectile(shootInfoVortex);
    }
    private void summonVertexAheadAndTeleport() {
        // Calculate the position ahead of the player
        Vector playerVelocity = target.getVelocity();
        MathHelper.setVectorLength(playerVelocity, 48);
        Location aheadLocation = target.getLocation().add(playerVelocity);

        shootInfoVortex.shootLoc = aheadLocation;
        EntityHelper.spawnProjectile(shootInfoVortex);
        // Teleport to the vertex location
        bukkitEntity.teleport(aheadLocation);
        teleported = true;
    }
    private void dash() {
        bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 10, 1f);

        AimHelper.AimHelperOptions aimHelperOption;
        double random = Math.random();
        if (random < 0.25) {
            aimHelperOption = aimHelperDashAcceleration;
        } else if (random < 0.75) {
            aimHelperOption = aimHelperDash;
        } else {
            aimHelperOption = null;
        }

        Location targetLocation;
        if (aimHelperOption != null) {
            targetLocation = AimHelper.helperAimEntity(bukkitEntity, target, aimHelperOption);
        } else {
            targetLocation = target.getEyeLocation();
        }

        velocity = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), targetLocation, DASH_SPEED);
    }

    private void phase1AI() {
        if (indexAI < 90) {
            // Dash towards the player thrice
            if (indexAI % 30 == 0) {
                dash();
            }
            if (indexAI % 30 > 20) {
                // Slow down and fly higher for the last 10 ticks of each dash
                velocity.multiply(0.99).setY(velocity.getY() + 0.1);
            }
        } else if (indexAI < 130) {
            // Move towards the player and fire spike balls
            moveTowardsPlayer();
            if (indexAI == 110) {
                fireSpikeBalls();
            }
        } else if (indexAI < 220) {
            // Dash towards the player thrice
            if (indexAI % 30 == 10) {
                dash();
            }
            if (indexAI % 30 < 10) {
                // Slow down and fly higher for the last 10 ticks of each dash
                velocity.multiply(0.99).setY(velocity.getY() + 0.1);
            }
        } else if (indexAI < 260) {
            // Move towards the player and fire shark projectiles
            moveTowardsPlayer();
            if (indexAI == 245) {
                fireSharkProjectiles();
            }
        } else {
            // Reset the AI cycle
            indexAI = -1;
        }
    }

    private void phase2AI() {
        if (indexAI < 50) {
            // Dash towards the player twice
            if (indexAI % 25 == 0) {
                dash();
            }
            if (indexAI % 25 > 20) {
                // Slow down and fly higher for the last 5 ticks of each dash
                velocity.multiply(0.99).setY(velocity.getY() + 0.1);
            }
        } else if (indexAI < 90) {
            // Summon a vertex
            moveTowardsPlayer();
            if (indexAI == 50) {
                bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), VORTEX_ROAR_SOUND, 10, 1.5f);
            }
            else if (indexAI == 75) {
                summonSulphurousVertex();
            }
        } else if (indexAI < 140) {
            // Dash towards the player twice
            if (indexAI % 25 == 15) {
                dash();
            }
            if (indexAI % 25 < 15 && indexAI % 25 >= 10) {
                // Slow down and fly higher for the last 5 ticks of each dash
                velocity.multiply(0.99).setY(velocity.getY() + 0.1);
            }
        } else if (indexAI < 170) {
            // Move towards the player and fire projectiles
            moveTowardsPlayer();
            if (indexAI == 155) {
                if (Math.random() < 0.5) {
                    fireSharkProjectiles();
                } else {
                    fireSpikeBalls();
                }
            }
        } else {
            // Reset the AI cycle
            indexAI = -1;
        }
    }

    private void phase3AI() {
        if (indexAI < 75) {
            // Dash towards the player thrice
            if (indexAI % 25 == 0) {
                dash();
            }
            if (indexAI % 25 > 20) {
                // Slow down and fly higher for the last 5 ticks of each dash
                velocity.multiply(0.99).setY(velocity.getY() + 0.1);
            }
        } else if (indexAI < 105) {
            if (indexAI == 75) {
                bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), VORTEX_ROAR_SOUND, 10, 1.5f);
            }
            // Summon a vertex ahead of the player and teleport
            else if (indexAI == 85) {
                summonVertexAheadAndTeleport();
            }
            // Move towards the player
            moveTowardsPlayer();
        } else if (indexAI < 180) {
            // Dash towards the player thrice
            if (indexAI % 25 == 5) {
                dash();
            }
            if (indexAI % 25 < 5) {
                // Slow down and fly higher for the last 5 ticks of each dash
                velocity.multiply(0.99).setY(velocity.getY() + 0.1);
            }
        } else if (indexAI < 205) {
            // Move towards the player and fire projectiles
            moveTowardsPlayer();
            if (indexAI == 190) {
                if (Math.random() < 0.5) {
                    fireSharkProjectiles();
                } else {
                    fireSpikeBalls();
                }
            }
        } else {
            // Reset the AI cycle
            indexAI = -1;
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

                // Check for phase transitions
                double healthRatio = getHealth() / getMaxHealth();
                if (healthRatio < PHASE_TRANSITION_2) {
                    if (AIPhase != 3)
                        transitionToPhase(3);
                } else if (healthRatio < PHASE_TRANSITION_1) {
                    if (AIPhase != 2)
                        transitionToPhase(2);
                }

                if (indexAI >= 0) {
                    // Call the current phase's AI
                    switch (AIPhase) {
                        case 1:
                            phase1AI();
                            break;
                        case 2:
                            phase2AI();
                            break;
                        case 3:
                            phase3AI();
                            break;
                    }
                }
                indexAI++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        // facing
        this.yaw = (float) MathHelper.getVectorYaw( velocity );
        // collision dmg
        if (teleported)
            teleported = false;
        else
            terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public TheOldDuke(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public TheOldDuke(Player summonedPlayer, Location spawnLoc) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 864d);
            attrMap.put("damageTakenMulti", 0.75);
            attrMap.put("defence", 90d);
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
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer, true, bossbar);
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
            shootInfoSpikeBall = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapSpikeBall,
                    DamageHelper.DamageType.ARROW, "遗爵鲨牙刺球");
            shootInfoShark = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapShark,
                    DamageHelper.DamageType.MELEE, "硫海龙鲨");
            shootInfoVortex = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapVortex,
                    DamageHelper.DamageType.MAGIC, "遗爵硫海漩涡");
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