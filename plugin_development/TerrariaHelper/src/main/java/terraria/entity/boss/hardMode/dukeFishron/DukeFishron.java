package terraria.entity.boss.hardMode.dukeFishron;

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
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class DukeFishron extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.DUKE_FISHRON;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.OCEAN;
    public static final double BASIC_HEALTH = 214200 * 2, BASIC_HEALTH_BR = 621180 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    enum AttackPhase {
        DASH, BUBBLE, SHARKNADO, DASH_1, DASH_2, DASH_3;
    }
    static final double DASH_SPEED_1 = 1.25, BUBBLE_FOLLOW_SPEED = 0.5, BUBBLE_SPEED = 0.75,
            DASH_SPEED_2 = 1.5, BUBBLE_ROTATE_RADIUS = 32,
            DASH_SPEED_3 = 2;
    static final HashMap<String, Double> attrMapDetonatingBubble;
    static final AimHelper.AimHelperOptions dashAimHelper;
    static {
        dashAimHelper = new AimHelper.AimHelperOptions();

        attrMapDetonatingBubble = new HashMap<>();
        attrMapDetonatingBubble.put("damage", 450d);
        attrMapDetonatingBubble.put("knockback", 2d);
        attrMapDetonatingBubble.put("health", 1d);
        attrMapDetonatingBubble.put("healthMax", 1d);
    }

    public EntityHelper.ProjectileShootInfo psiDetonatingBubble;
    int indexAI = -40, phaseAI = 1;
    AttackPhase attackPhase = AttackPhase.DASH, lastNonDashAttack = AttackPhase.DASH;
    Vector dashVelocity = new Vector(),
            bubbleDir1 = new Vector(), bubbleDir2 = new Vector();
    double healthRatio = 1;


    private void changePhaseAI() {
        phaseAI ++;
        // set other properties
        indexAI = -40;
        bukkitEntity.setVelocity(new Vector());
        switch (phaseAI) {
            case 2:
                setCustomName(BOSS_TYPE.msgName + "§2");
                bossbar.color = BossBattle.BarColor.YELLOW;
                attackPhase = AttackPhase.DASH;
                // damage: 420 -> 604
                AttributeHelper.tweakAttribute(bukkitEntity, "damage", "184", true);
                // defence: 100 -> 80
                AttributeHelper.tweakAttribute(bukkitEntity, "defence", "20", false);
                break;
            case 3:
                setCustomName(BOSS_TYPE.msgName + "§3");
                bossbar.color = BossBattle.BarColor.RED;
                attackPhase = AttackPhase.DASH_1;
                // damage: 604 -> 554
                AttributeHelper.tweakAttribute(bukkitEntity, "damage", "50", false);
                // defence: 80 -> 0
                AttributeHelper.tweakAttribute(bukkitEntity, "defence", "80", false);
                break;
        }
        bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
    }
    private void changeAttackPhase() {
        if (attackPhase != AttackPhase.DASH)
            lastNonDashAttack = attackPhase;
        boolean shouldTeleport = false;
        switch (attackPhase) {
            case DASH:
                attackPhase = lastNonDashAttack == AttackPhase.BUBBLE ?
                        AttackPhase.SHARKNADO : AttackPhase.BUBBLE;
                break;
            case BUBBLE:
            case SHARKNADO:
                attackPhase = AttackPhase.DASH;
                break;
            case DASH_1:
                attackPhase = AttackPhase.DASH_2;
                shouldTeleport = true;
                break;
            case DASH_2:
                attackPhase = AttackPhase.DASH_3;
                shouldTeleport = true;
                break;
            case DASH_3:
                attackPhase = AttackPhase.DASH_1;
                shouldTeleport = true;
                break;
        }
        if (shouldTeleport) {
            Location tempLoc = bukkitEntity.getLocation();
            tempLoc.setY(target.getLocation().getY());
            Vector teleportOffset = MathHelper.getDirection(tempLoc, target.getLocation(), 20);
            teleportOffset.setY(8);
            Location targetLoc = target.getEyeLocation().add(teleportOffset);
            bukkitEntity.teleport(targetLoc);
        }
        indexAI = -15;
    }
    private void initDash(double minSpeed, int ticksReach, double chanceDirectDash) {
        Location targetLoc;
        // 50% chance to dash directly into enemy
        if (Math.random() < chanceDirectDash)
            targetLoc = target.getEyeLocation();
            // 50% chance to dash into predicted location of enemy
        else {
            dashAimHelper.setAimMode(true).setTicksTotal(ticksReach);
            targetLoc = AimHelper.helperAimEntity(bukkitEntity, target, dashAimHelper);
        }
        dashVelocity = targetLoc.subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector();
        // (minSpeed * ticksReach) ^ 2
        if (dashVelocity.lengthSquared() < minSpeed * minSpeed * ticksReach * ticksReach)
            dashVelocity.normalize().multiply(minSpeed);
        else
            dashVelocity.multiply(1d / ticksReach);
        bukkitEntity.setVelocity(dashVelocity);
    }
    private void shootBubble() {
        psiDetonatingBubble.setLockedTarget(target);
        psiDetonatingBubble.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        psiDetonatingBubble.velocity = MathHelper.getDirection(psiDetonatingBubble.shootLoc,
                target.getEyeLocation(), BUBBLE_SPEED);
        EntityHelper.spawnProjectile(psiDetonatingBubble);
        // additional sharkron spawning in phase 2
        if (phaseAI == 2 && indexAI % 4 == 0) {
            new Sharkron(this, ((LivingEntity) bukkitEntity).getEyeLocation());
        }
    }
    private void shootSharknado() {
        // phase 1, shoot 3 water blobs in 3 directions
        if (phaseAI == 1) {
            double angle = Math.random() * 360;
            for (int offset = 0; offset < 360; offset += 120) {
                Vector velocity = MathHelper.vectorFromYawPitch_approx(angle + offset, 0);
                velocity.multiply(2);
                new WaterBlob(this, velocity);
            }
        }
        // phase 2/4, shoot homing water blob that explodes into a giant sharknado
        else {
            new WaterBlob(this, MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 1));
        }
    }
    private void AIPhase1() {
        // change phase
        if (healthRatio < 0.7) {
            changePhaseAI();
            return;
        }
        // attacks
        switch (attackPhase) {
            case DASH: {
                // change attack method after dash * 5
                if (indexAI >= 125) {
                    changeAttackPhase();
                }
                // init dash
                else if (indexAI % 25 == 0) {
                    initDash(DASH_SPEED_1, 12, 0);
                }
                // keep the dash
                else if (indexAI % 25 < 20) {
                    bukkitEntity.setVelocity(dashVelocity);
                }
                // move upward a bit before next move
                else {
                    bukkitEntity.setVelocity(new Vector(0, 0.3, 0));
                }
                break;
            }
            case BUBBLE: {
                // change attack method
                if (indexAI >= 60) {
                    changeAttackPhase();
                }
                else {
                    // bubble
                    if (indexAI % 3 == 0) {
                        shootBubble();
                    }
                    // movement
                    bukkitEntity.setVelocity(MathHelper.getDirection(
                            bukkitEntity.getLocation(), target.getLocation(), BUBBLE_FOLLOW_SPEED));
                }
                break;
            }
            case SHARKNADO: {
                // change attack method
                if (indexAI >= 30) {
                    changeAttackPhase();
                }
                else {
                    // spawn sharknado
                    if (indexAI == 15) {
                        shootSharknado();
                    }
                    // movement
                    Vector velocity = bukkitEntity.getVelocity();
                    velocity.multiply(0.95);
                    bukkitEntity.setVelocity(velocity);
                }
                break;
            }
        }
    }
    private void AIPhase2() {
        // change phase
        if (healthRatio < 0.4) {
            changePhaseAI();
            return;
        }
        // attacks
        switch (attackPhase) {
            case DASH: {
                // change attack method after dash * 3
                if (indexAI >= 75) {
                    changeAttackPhase();
                }
                // dash
                else {
                    int dashIndex = indexAI % 25;
                    // init dash
                    if (dashIndex == 0) {
                        initDash(DASH_SPEED_2, 12, 0.2);
                    }
                    // keep the dash
                    else if (dashIndex < 15) {
                        bukkitEntity.setVelocity(dashVelocity);
                    }
                    // move towards enemy a bit before next move
                    else {
                        bukkitEntity.setVelocity(MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(),
                                target.getEyeLocation(), 0.5));
                    }
                }
                break;
            }
            case BUBBLE: {
                // change attack method
                if (indexAI >= 50) {
                    changeAttackPhase();
                }
                else {
                    // init rotate info
                    if (indexAI == 0) {
                        bubbleDir1 = MathHelper.getDirection(bukkitEntity.getLocation(),
                                target.getLocation(), BUBBLE_ROTATE_RADIUS);
                        bubbleDir2 = new Vector();
                        while (bubbleDir2.lengthSquared() < 1e-5) {
                            bubbleDir2 = MathHelper.randomVector();
                            bubbleDir2.subtract(MathHelper.vectorProjection(bubbleDir1, bubbleDir2));
                        }
                        bubbleDir2.normalize().multiply(BUBBLE_ROTATE_RADIUS);
                    }
                    // bubble and sharks
                    if (indexAI <= 40) {
                        // bubble and sharkron
                        shootBubble();
                        // movement, rotate 18 degree per tick (20 tick/cycle)
                        double angle = indexAI * 18;
                        double sinAng = MathHelper.xsin_degree(angle);
                        double cosAng = MathHelper.xcos_degree(angle);
                        Vector offset1 = bubbleDir1.clone();
                        offset1.multiply(cosAng);
                        Vector offset2 = bubbleDir2.clone();
                        offset2.multiply(sinAng);
                        Location targetLoc = target.getLocation().subtract(offset1).subtract(offset2);
                        bukkitEntity.setVelocity(
                                targetLoc.subtract( bukkitEntity.getLocation() ).toVector() );
                    }
                    // hover a little longer after attack is done before starting next phase
                    else {
                        bukkitEntity.setVelocity(new Vector(0, 0.25, 0));
                    }
                }
                break;
            }
            case SHARKNADO: {
                // change attack method
                if (indexAI >= 25) {
                    changeAttackPhase();
                }
                else {
                    // spawn sharknado
                    if (indexAI == 15) {
                        shootSharknado();
                    }
                    // movement
                    Vector velocity = bukkitEntity.getVelocity();
                    velocity.multiply(0.95);
                    bukkitEntity.setVelocity(velocity);
                }
                break;
            }
        }
    }
    private void AIPhase3() {
        boolean finalPhase = healthRatio < 0.2;
        int dashAmount;
        switch (attackPhase) {
            case DASH_1:
                dashAmount = 1;
                break;
            case DASH_2:
                dashAmount = 2;
                break;
            case DASH_3:
            default:
                dashAmount = 3;
        }
        // change attack method
        int dashDuration = 22;
        if (indexAI >= dashDuration * dashAmount) {
            if (!finalPhase) {
                changeAttackPhase();
            }
            indexAI = -30;
        }
        else {
            int dashIndex = indexAI % dashDuration;
            // begin dash
            if (dashIndex == 5) {
                setCustomName(BOSS_TYPE.msgName + "§2");
                // predictive dash only on the first dash in all dash sequences
                initDash(DASH_SPEED_3, 10, indexAI > (dashDuration * (dashAmount - 1)) ? 0 : 1);
            }
            // keep the dash
            else if (dashIndex < 18) {
                bukkitEntity.setVelocity(dashVelocity);
            }
            // before the beginning and after the end of each dash
            else {
                setCustomName(BOSS_TYPE.msgName + "§3");
                bukkitEntity.setVelocity(new Vector(0, 0.25, 0));
            }
        }
        // shoot sharknado
        if (finalPhase && indexAI % 175 == 0) {
            shootSharknado();
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
                if (indexAI >= 0) {
                    switch (phaseAI) {
                        case 1:
                            AIPhase1();
                            break;
                        case 2:
                            AIPhase2();
                            break;
                        case 3:
                            AIPhase3();
                            break;
                    }
                }
                indexAI ++;
            }
        }
        // face the player
        if (attackPhase == AttackPhase.DASH)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public DukeFishron(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public DukeFishron(Player summonedPlayer, Location spawnLoc) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName + "§1");
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
            attrMap.put("damage", 420d);
            attrMap.put("damageTakenMulti", 0.75d);
            attrMap.put("defence", 100d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            // damage multiplier
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
                    getBukkitEntity(), BossHelper.BossType.GOLEM.msgName, summonedPlayer, true, bossbar);
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
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            psiDetonatingBubble = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapDetonatingBubble,
                    DamageHelper.DamageType.MAGIC, "爆炸泡泡");
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
