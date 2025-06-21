package terraria.entity.boss.hardMode.theTwins;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Spazmatism extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.THE_TWINS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 59670 * 2, BASIC_HEALTH_BR = 341859 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static String bossName = "魔焰眼";
    Retinazer twin;
    Vector dashVelocity = new Vector();
    boolean dashingPhase = false;
    int phaseAI = 1, indexAI = -40;
    static HashMap<String, Double> attrMapCursedFlame, attrMapFlameThrower, attrMapHomingFlame;
    EntityHelper.ProjectileShootInfo shootInfoCursedFlame, shootInfoFlameThrower, shootInfoHomingFlame;
    static {
        attrMapCursedFlame = new HashMap<>();
        attrMapCursedFlame.put("damage", 456d);
        attrMapCursedFlame.put("knockback", 2d);
        attrMapFlameThrower = new HashMap<>();
        attrMapFlameThrower.put("damage", 528d);
        attrMapFlameThrower.put("knockback", 2d);
        attrMapHomingFlame = new HashMap<>();
        attrMapHomingFlame.put("damage", 468d);
        attrMapHomingFlame.put("knockback", 2d);
    }
    private void changePhase() {
        phaseAI ++;
        indexAI = -40;
        bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
        if (phaseAI == 2) {
            // damage: 408 -> 612
            AttributeHelper.tweakAttribute(attrMap, "damage", "204", true);
            // defence: 20 -> 56
            AttributeHelper.tweakAttribute(attrMap, "defence", "36", true);
            addScoreboardTag("isMechanic");
            setCustomName(bossName + "§1");
        }
    }
    private void shootCursedFlame(int state) {
        EntityHelper.ProjectileShootInfo shootInfo;
        double flameSpeed = 1;
        switch (state) {
            case 1:
                shootInfo = shootInfoCursedFlame;
                flameSpeed = 1.25;
                break;
            case 2:
                shootInfo = shootInfoFlameThrower;
                flameSpeed = 1.5;
                break;
            case 3:
            default:
                shootInfo = shootInfoHomingFlame;
                flameSpeed = 1.2;
                break;
        }
        shootInfo.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfo.velocity = MathHelper.getDirection(
                shootInfo.shootLoc, target.getEyeLocation(), flameSpeed);
        shootInfo.setLockedTarget(target);
        EntityHelper.spawnProjectile(shootInfo);
    }
    private void attackAI() {
        // if the twin is in an earlier phase, set damage reduction to a high amount
        if (ticksLived % 5 == 0) {
            if ( twin.phaseAI < phaseAI && twin.isAlive() ) {
                addScoreboardTag("noDamage");
            }
            else {
                removeScoreboardTag("noDamage");
            }
        }
        // phase change
            double healthRatio = getHealth() / getMaxHealth();
        switch (phaseAI) {
            case 1:
                if (healthRatio < 0.7) changePhase();
                break;
            case 2:
                if (healthRatio < 0.25 || !twin.isAlive()) changePhase();
                break;
        }
        // attack
        switch (phaseAI) {
            // phase 1: alternate between firing flames and dashing
            case 1: {
                // hover and shoot flame
                if (indexAI < 60) {
                    // hover
                    Vector offset = bukkitEntity.getLocation().subtract(target.getLocation()).toVector();
                    offset.setY(0);
                    if (offset.lengthSquared() < 1e-5) {
                        offset = new Vector(1, 0, 0);
                    }
                    offset.normalize().multiply(24);
                    Location targetLoc = target.getLocation().add(offset);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double velLen = velocity.length();
                    double maxSpeed = 2;
                    if (velLen > maxSpeed) {
                        velocity.multiply(maxSpeed / velLen);
                    }
                    bukkitEntity.setVelocity(velocity);
                    // shoot flame
                    if (indexAI >= 0 && indexAI % 5 == 0) {
                        shootCursedFlame(1);
                    }
                }
                // short dash eight times
                else {
                    if (indexAI >= 180)
                        indexAI = -1;
                    else {
                        // dash init
                        if ((indexAI - 60) % 15 == 0) {
                            dashVelocity = MathHelper.getDirection(
                                    ((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(), 1.25);
                        }
                        // maintain dash velocity
                        bukkitEntity.setVelocity(dashVelocity);
                    }
                }
                break;
            }
            // phase 2: alternate between firing flame and rapid dash
            case 2: {
                // follow and shoot flame
                if (indexAI < 60) {
                    // fly towards the player
                    bukkitEntity.setVelocity(MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 0.75));
                    // shoot flame
                    shootCursedFlame(2);
                }
                // long dashes
                else {
                    if (indexAI >= 210)
                        indexAI = -1;
                    else {
                        if ((indexAI - 60) % 30 == 0) {
                            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                            dashVelocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 2.25);
                        }
                        // maintain dash velocity
                        bukkitEntity.setVelocity(dashVelocity);
                    }
                }
                break;
            }
            // phase 3: flamethrower, homing flame, 3 long dash
            case 3: {
                // follow and shoot flame
                if (indexAI < 40) {
                    // fly towards the player
                    bukkitEntity.setVelocity(MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(),
                            0.75, true));
                    // shoot flame
                    shootCursedFlame(2);
                }
                // homing flame
                else if (indexAI < 80) {
                    Location targetLoc = target.getLocation().subtract(0, 16, 0);
                    Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    double velLen = velocity.length();
                    double maxSpeed = 1.5;
                    if (velLen > maxSpeed) {
                        velocity.multiply(maxSpeed / velLen);
                    }
                    bukkitEntity.setVelocity(velocity);
                    // homing flame
                    switch (indexAI) {
                        case 50:
                        case 55:
                        case 60:
                        case 65:
                        case 70:
                            shootCursedFlame(3);
                    }
                }
                // long dashes
                else {
                    if (indexAI >= 170)
                        indexAI = -1;
                    else {
                        if ((indexAI - 80) % 30 == 0) {
                            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                            dashVelocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 2.5);
                        }
                        // maintain dash velocity
                        bukkitEntity.setVelocity(dashVelocity);
                    }
                }
                break;
            }
        }
        indexAI ++;
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        boolean isFacingPlayer = true;
        {
            // update target
            if (twin.isAlive()) {
                this.target = twin.target;
                
            }
            else {
                target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                        IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
                if (WorldHelper.isDayTime(bukkitEntity.getWorld()))
                    target = null;
            }
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
                if (!twin.isAlive())
                    targetMap.get(target.getUniqueId()).addAggressionTick();

                attackAI();
            }
        }
        // face the player
        if (dashingPhase)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Spazmatism(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Spazmatism(Player summonedPlayer, Retinazer twin) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = twin.getBukkitEntity().getLocation().add(0, 12, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.twin = twin;
        setCustomName(bossName);
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
            attrMap.put("damage", 408d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 20d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = twin.bossbar;
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = twin.targetMap;
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(8, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = twin.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoCursedFlame = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapCursedFlame, "魔眼咒火球");
            shootInfoFlameThrower = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFlameThrower, "魔眼诅咒焰");
            shootInfoHomingFlame = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapHomingFlame, "魔眼追踪咒火球");
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();

        if (!twin.isAlive() && bossbar.visible) {
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
        if (!twin.isAlive())
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
