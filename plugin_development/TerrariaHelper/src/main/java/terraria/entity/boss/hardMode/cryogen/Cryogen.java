package terraria.entity.boss.hardMode.cryogen;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Particle;
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

public class Cryogen extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CRYOGEN;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.TUNDRA;
    public static final double BASIC_HEALTH = 86400 * 2, BASIC_HEALTH_BR = 720000 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static final HashMap<String, Double> attrMapIceBlast, attrMapIceBomb;
    static {
        attrMapIceBlast = new HashMap<>();
        attrMapIceBlast.put("damage", 336d);
        attrMapIceBlast.put("knockback", 4d);
        attrMapIceBomb = new HashMap<>();
        attrMapIceBomb.put("damage", 420d);
        attrMapIceBomb.put("knockback", 4d);
    }
    public EntityHelper.ProjectileShootInfo psiBlast, psiHomingBlast, psiBomb;
    CryogenShield shield = null;
    Location teleportLoc = null;
    Vector dashVelocity = new Vector();
    boolean invincible = false;
    int indexAI = -40, phaseAI = 1, shieldIndex = 0, iceBombIndex = 0;
    static final int ICE_BLAST_REGULAR = 11, ICE_BLAST_BEFORE_DASH = 17, ICE_BLAST_TIER_5 = 2, ICE_BLAST_TIER_6 = 4;

    private void shootHomingIceBlasts(int amount) {
        psiHomingBlast.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        psiHomingBlast.setLockedTarget(target);
        int amountArcs = phaseAI >= 5 ? 2 : 3;
        double projectileSpeed = phaseAI >= 5 ? 0.75 : 1.5;
        for (Vector velocity : MathHelper.getCircularProjectileDirections(
                amount, amountArcs, 75, target, psiHomingBlast.shootLoc, projectileSpeed)) {
            psiHomingBlast.velocity = velocity;
            EntityHelper.spawnProjectile(psiHomingBlast);
        }
    }
    private void shootIceBlasts(int amount) {
        psiBlast.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        int amountArcs = phaseAI >= 5 ? 2 : 3;
        double projectileSpeed = phaseAI >= 5 ? 0.75 : 1.5;
        for (Vector velocity : MathHelper.getCircularProjectileDirections(
                amount, amountArcs, 75, target, psiBlast.shootLoc, projectileSpeed)) {
            psiBlast.velocity = velocity;
            EntityHelper.spawnProjectile(psiBlast);
        }
    }
    private void shootIceBombs() {
        psiBomb.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        for (Vector velocity : MathHelper.getCircularProjectileDirections(
                3, 2, 90, target, psiBomb.shootLoc, 1)) {
            psiBomb.velocity = velocity;
            EntityHelper.spawnProjectile(psiBomb);
        }
    }
    private void spawnShield() {
        shield = new CryogenShield(this);
        invincible = true;
        addScoreboardTag("noDamage");
    }
    private void changePhase(int newPhase) {
        switch (newPhase) {
            case 2:
                // Damage Taken multiplier: 0.73 -> 0.79
                AttributeHelper.tweakAttribute(attrMap, "damageTakenMulti", "-0.08219178", false);
                assert Math.abs(0.79 - attrMap.get("damageTakenMulti")) > 1e-3;
                // Defence: 26 -> 20
                AttributeHelper.tweakAttribute(attrMap, "defence", "6", false);
                break;
            case 3:
                // Damage Taken multiplier: 0.79 -> 0.88
                AttributeHelper.tweakAttribute(attrMap, "damageTakenMulti", "-0.11392405", false);
                assert Math.abs(0.88 - attrMap.get("damageTakenMulti")) > 1e-3;
                // Defence: 20 -> 12
                AttributeHelper.tweakAttribute(attrMap, "defence", "8", false);
                break;
            case 4:
                // Damage Taken multiplier: 0.88 -> 1
                AttributeHelper.tweakAttribute(attrMap, "damageTakenMulti", "-0.13636363", false);
                assert Math.abs(1 - attrMap.get("damageTakenMulti")) > 1e-3;
                // Defence: 12 -> 0
                AttributeHelper.tweakAttribute(attrMap, "defence", "12", false);
                break;
        }
        indexAI = -30;
        phaseAI = newPhase;
        // give the player some time to react
        bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
        bukkitEntity.setVelocity(new Vector());
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
                // AI
                double healthRatio = getHealth() / getMaxHealth();
                // phase-specific AI
                switch (phaseAI) {
                    // phase 1 (phase 2 technically, as death mode omits original phase 1)
                    case 1: {
                        // rotate around player and fire ice blasts
                        if (indexAI < 40) {
                            Vector offset = bukkitEntity.getLocation().subtract(target.getLocation()).toVector();
                            offset.setY(0);
                            if (offset.lengthSquared() < 1e-5) {
                                offset = new Vector(1, 0, 0);
                            }
                            offset.normalize().multiply(24);
                            offset.setY(12);
                            Location targetLoc = target.getLocation().add(offset);
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 2;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                            switch (indexAI) {
                                case 0:
                                case 20:
                                    shootIceBlasts(ICE_BLAST_REGULAR);
                            }
                        }
                        // then, stay where it is, fire 3 rounds of more ice blasts and dash
                        else {
                            // stay, fire rounds of ice blasts and dash
                            if (indexAI <= 60) {
                                bukkitEntity.setVelocity(new Vector());
                                switch (indexAI) {
                                    case 40:
                                    case 50:
                                    case 60:
                                        shootIceBlasts(ICE_BLAST_BEFORE_DASH);
                                }
                                // dash
                                if (indexAI == 60) {
                                    Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 2.25);
                                    bukkitEntity.setVelocity(velocity);
                                }
                            }
                            // finish one AI cycle
                            else if (indexAI >= 90)
                                indexAI = -1;
                        }
                        // phase transition
                        if (healthRatio < 0.8) {
                            changePhase(phaseAI + 1);
                        }
                        break;
                    }
                    // phase 2
                    case 2: {
                        // flies towards player, fire two rounds of ice blasts
                        if (indexAI < 40) {
                            Location targetLoc = target.getLocation();
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 0.6;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                            switch (indexAI) {
                                case 0:
                                case 20:
                                    shootIceBlasts(ICE_BLAST_REGULAR);
                            }
                        }
                        // then, stay where it is, fire 2 rounds of more ice blasts and dash
                        else {
                            // stay, fire rounds of ice blasts
                            if (indexAI <= 140) {
                                int subIndex = (indexAI - 40) % 50;
                                if (subIndex < 20)
                                    bukkitEntity.setVelocity(new Vector());
                                switch (subIndex) {
                                    case 10:
                                    case 20:
                                        shootIceBlasts(ICE_BLAST_BEFORE_DASH);
                                        break;
                                }
                                // dash
                                if (subIndex >= 20) {
                                    if (subIndex == 20) {
                                        dashVelocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 2.75);
                                    }
                                    if (subIndex >= 40) {
                                        dashVelocity.multiply(0.9);
                                    }
                                    bukkitEntity.setVelocity(dashVelocity);
                                }
                            }
                            // finish one AI cycle
                            else {
                                indexAI = -1;
                            }
                        }
                        // phase transition
                        if (healthRatio < 0.6) {
                            changePhase(phaseAI + 1);
                        }
                        break;
                    }
                    // phase 3
                    case 3: {
                        // chase player, fire ice blasts
                        {
                            Location targetLoc = target.getLocation();
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 0.5;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                            if (indexAI % 15 == 0)
                                shootIceBlasts(ICE_BLAST_REGULAR);
                        }
                        // randomly teleport around the player
                        switch (indexAI) {
                            case 60:
                                Vector offset = MathHelper.randomVector();
                                offset.multiply(15 + Math.random() * 5);
                                teleportLoc = target.getLocation().add(offset);
                            // display particles
                            case 65:
                            case 70:
                            case 75:
                            case 80:
                            case 95:
                                bukkitEntity.getWorld().spawnParticle(Particle.EXPLOSION_HUGE,
                                        ((LivingEntity) bukkitEntity).getEyeLocation(), 1);
                                bukkitEntity.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, teleportLoc, 1);
                                break;
                            case 100:
                                bukkitEntity.teleport(teleportLoc);
                                indexAI = -1;
                                break;
                        }
                        // phase transition
                        if (healthRatio < 0.5) {
                            changePhase(phaseAI + 1);
                        }
                        break;
                    }
                    // phase 4
                    case 4: {
                        // loosely accelerate towards player
                        Vector direction = target.getEyeLocation().subtract( ((LivingEntity) bukkitEntity).getEyeLocation() ).toVector();
                        double dirLen = direction.length();
                        // prevent zero vector bug
                        if (dirLen < 1e-5) {
                            direction = new Vector(0, 1, 0);
                            dirLen = 1;
                        }
                        // too far: accelerate towards player
                        if (indexAI == 0) {
                            if (dirLen > 24) {
                                Vector velocity = bukkitEntity.getVelocity();
                                direction.multiply(0.25 / dirLen);
                                // the boss can make turns quicker, also this limits max speed to 2.
                                velocity.multiply(0.875);
                                velocity.add( direction );
                                bukkitEntity.setVelocity(velocity);
                                indexAI = -1;
                            }
                            // close enough: dash towards player
                            else {
                                direction.multiply(3 / dirLen);
                                dashVelocity = direction;
                                bukkitEntity.setVelocity(dashVelocity);
                            }
                        }
                        // repeat AI cycle
                        else {
                            // reset velocity to cached dash velocity
                            bukkitEntity.setVelocity(dashVelocity);
                            if (indexAI > 30)
                                indexAI = -1;
                        }
                        // phase transition
                        if (healthRatio < 0.35) {
                            changePhase(phaseAI + 1);
                        }
                        break;
                    }
                    // phase 5
                    case 5: {
                        // hover above player
                        if (indexAI < 30) {
                            Location targetLoc = target.getLocation().add(0, 24, 0);
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 1.75;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                        }
                        // halt for 15 ticks to signal the player
                        else if (indexAI < 45) {
                            bukkitEntity.setVelocity(new Vector());
                        }
                        // dashes into player
                        else {
                            switch (indexAI) {
                                case 45:
                                case 70:
                                case 95:
                                    dashVelocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 2.25);
                                    shootHomingIceBlasts(ICE_BLAST_TIER_5);
                                    break;
                                // a new AI cycle
                                case 120:
                                    indexAI = -1;
                                    break;
                            }
                            bukkitEntity.setVelocity(dashVelocity);
                        }
                        // phase transition
                        if (healthRatio < 0.25) {
                            changePhase(phaseAI + 1);
                        }
                        break;
                    }
                    // phase 6
                    case 6: {
                        // hover above player
                        if (indexAI < 20) {
                            Location targetLoc = target.getLocation().add(0, 20, 0);
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 2.25;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                        }
                        // halt for 10 ticks to signal the player
                        else if (indexAI < 30) {
                            bukkitEntity.setVelocity(new Vector());
                        }
                        // dashes into player
                        else {
                            switch (indexAI) {
                                case 30:
                                case 50:
                                case 70:
                                    dashVelocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 1.75);
                                    shootHomingIceBlasts(ICE_BLAST_TIER_6);
                                    break;
                                // a new AI cycle
                                case 100:
                                    indexAI = -1;
                                    break;
                            }
                            // reset velocity to dash velocity
                            if (indexAI < 90)
                                bukkitEntity.setVelocity(dashVelocity);
                        }
                    }
                }
                // shield
                if (shield == null) {
                    // if shield is broken
                    if (invincible) {
                        invincible = false;
                        removeScoreboardTag("noDamage");
                        shieldIndex = 0;
                    }
                    // shield respawns 12 seconds after breaking
                    else if (++shieldIndex > 240)
                        spawnShield();
                }
                // ice bombs
                // spawn every 10 seconds before phase 5 and 6
                if (phaseAI < 5) {
                    if (++iceBombIndex >= 200) {
                        shootIceBombs();
                        iceBombIndex = 0;
                    }
                }
                // in phase 5 and 6, only shoot bombs when hovering
                else {
                    if (indexAI == 10)
                        shootIceBombs();
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
    public Cryogen(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public Cryogen(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 30;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
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
            attrMap.put("damage", 552d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("damageTakenMulti", 0.73d);
            attrMap.put("defence", 26d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
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
                    getBukkitEntity(), BossHelper.BossType.WALL_OF_FLESH.msgName, summonedPlayer, true, bossbar);
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
        // projectile info
        {
            psiBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapIceBlast,
                    DamageHelper.DamageType.MAGIC, "冰霜爆");
            psiHomingBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapIceBlast,
                    DamageHelper.DamageType.MAGIC, "追踪冰霜爆");
            psiBomb = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapIceBomb,
                    DamageHelper.DamageType.MAGIC, "冰霜炸弹");
        }
        // spawn with shield
        {
            spawnShield();
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
