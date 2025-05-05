package terraria.entity.boss.hardMode.lunaticCultist;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.gameplay.EventAndTime;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class LunaticCultist extends EntityZombie {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.LUNATIC_CULTIST;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 183600 * 2, BASIC_HEALTH_BR = 420750 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    int phaseAttack = 1;
    int indexAI = -40, ticksBeforeAttack = 200;
    double lastHealth, mainYaw = 0;
    ArrayList<LunaticCultistClone> clones = new ArrayList<>(6);
    Location centerLoc;
    PhantomDragon dragon = null;

    static HashMap<String, Double> attrMapFireball;
    static final int SUMMON_TIMEOUT = 50;
    static final double SPEED_FIREBALL = 1.5;
    static GenericHelper.ParticleLineOptions summonParticle;
    EntityHelper.ProjectileShootInfo shootInfoFireball;
    static {
        attrMapFireball = new HashMap<>();
        attrMapFireball.put("damage", 540d);
        attrMapFireball.put("knockback", 1.5d);

        summonParticle = new GenericHelper.ParticleLineOptions()
                .setVanillaParticle(true)
                .setParticleColor("255|255|255")
                .setWidth(1);
    }

    private void nextAttack() {
        indexAI = -20;
        phaseAttack++;
        phaseAttack %= 6;
        // for summoning phase, init y-coordinate as -10 and can not be actively targeted by minions etc.
        if (phaseAttack == 0) {
            // no duplication for phantom dragon
            if (dragon != null && dragon.isAlive()) {
                nextAttack();
                return;
            }
            removeScoreboardTag("isMonster");
            // teleport
            locY = -10;
            for (LunaticCultistClone toTeleport : clones) {
                toTeleport.locY = -10;
            }
        }
        // otherwise, it can be targeted by minions and teleported above the targeted player in advance
        else {
            addScoreboardTag("isMonster");
            // teleport
            mainYaw = Math.random() * 360;
            bukkitEntity.teleport(getHoverLocation());
            int index = 1;
            for (LunaticCultistClone toTeleport : clones) {
                toTeleport.getBukkitEntity().teleport(getHoverLocation(index++));
            }
        }
    }
    private Location getHoverLocation() {
        return getHoverLocation(0);
    }
    private Location getHoverLocation(int index) {
        double dirYaw = mainYaw;
        double dirPitch = -90;
        if (index > 0) {
            if (index % 2 == 0)
                dirYaw += 180;
            dirPitch += ( (index + 1) / 2) * 3.5;
        }
        Vector offsetDir = MathHelper.vectorFromYawPitch_approx(dirYaw, dirPitch);
        offsetDir.multiply(20);
        return target.getLocation().add(offsetDir);
    }
    // summon clones
    private void displaySummonParticle(Entity entity) {
        if (entity == bukkitEntity)
            summonParticle.setParticleColor("20|110|220");
        else
            summonParticle.setParticleColor("255|255|255");
        Vector direction = entity.getLocation().subtract(centerLoc).toVector();
        summonParticle.setLength(direction.length());
        GenericHelper.handleParticleLine(direction, centerLoc, summonParticle);
    }
    private void attackPhase0() {
        if (indexAI < 0) {
            return;
        }
        // setup info
        if (indexAI == 0) {
            lastHealth = getHealth();
            // spawn clones
            int spawnAmount = Math.min(2, 6 - clones.size());
            for (int i = 0; i < spawnAmount; i ++) {
                clones.add( new LunaticCultistClone(target, this) );
            }
            // setup location
            centerLoc = getHoverLocation();
            double angle = Math.random();
            int totalEntities = clones.size();
            for (int i = 0; i <= totalEntities; i ++) {
                Location targetLoc = centerLoc.clone();
                Vector offset = MathHelper.vectorFromYawPitch_approx(angle, 0);
                offset.multiply(6);
                targetLoc.add(offset);
                angle += 360d / (totalEntities + 1);

                Entity toTeleport = i >= clones.size() ? bukkitEntity : clones.get(i).getBukkitEntity();
                toTeleport.teleport(targetLoc);
            }
        }
        // particle hint
        {
            displaySummonParticle(bukkitEntity);
            for (LunaticCultistClone currClone : clones)
                displaySummonParticle(currClone.getBukkitEntity());
        }
        // cancel on receiving damage
        if (lastHealth > 0 && getHealth() + 1e-5 < lastHealth) {
            indexAI = SUMMON_TIMEOUT - 10;
            lastHealth = -1;
        }
        if (indexAI >= SUMMON_TIMEOUT) {
            // summon phantom dragon if not damaged
            if (lastHealth > 0) {
                dragon = new PhantomDragon(target, new ArrayList<>(), this, 0, true, centerLoc);
            }
            nextAttack();
        }
    }
    // fireball
    private void attackPhase1() {
        if (indexAI < 0)
            return;
        if (indexAI % 10 == 0) {
            shootInfoFireball.setLockedTarget(target);
            shootInfoFireball.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            shootInfoFireball.velocity = MathHelper.getDirection(shootInfoFireball.shootLoc,
                    target.getEyeLocation(), SPEED_FIREBALL);
            EntityHelper.spawnProjectile(shootInfoFireball);
        }
        if (indexAI >= 49)
            nextAttack();
    }
    // ice mist and shard
    private void attackPhase2() {
        if (indexAI < 0)
            return;
        if (indexAI % 30 == 0)
            new LunaticIceMist(this);
        if (indexAI >= 50)
            nextAttack();
    }
    // lightning
    private void attackPhase3() {
        if (indexAI < 0)
            return;
        if (indexAI == 10)
            new LunaticLightningOrb(target, this);
        if (indexAI >= 50)
            nextAttack();
    }
    // ancient light
    private void attackPhase4() {
        if (indexAI < 0)
            return;
        if (indexAI % 8 == 0) {
            Location spawnLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
            for (Vector velocity : MathHelper.getCircularProjectileDirections(
                    5, 1, 45, target, spawnLoc, 3)) {
                new LunaticAncientLight(target, this, velocity, spawnLoc);
            }
        }
        if (indexAI >= 39)
            nextAttack();
    }
    // ancient doom
    private void attackPhase5() {
        if (indexAI < 0)
            return;
        if (indexAI == 0) {
            for (int i = 0; i < 10; i ++)
                new LunaticAncientDoom(this);
        }
        if (indexAI >= 70)
            nextAttack();
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
            // increase player aggro duration
            targetMap.get(target.getUniqueId()).addAggressionTick();
            // if target is valid, attack
            if (ticksBeforeAttack > 0) {
                bukkitEntity.teleport(target.getLocation().add(0, 16, 0));
                ticksBeforeAttack --;
                if (ticksBeforeAttack <= 0)
                    removeScoreboardTag("noDamage");
            }
            else {
                // the cultist do not move
                motX = 0;
                motY = 0;
                motZ = 0;
                // handle different attacks
                switch (phaseAttack) {
                    case 0:
                        attackPhase0();
                        break;
                    case 1:
                        attackPhase1();
                        break;
                    case 2:
                        attackPhase2();
                        break;
                    case 3:
                        attackPhase3();
                        break;
                    case 4:
                        attackPhase4();
                        break;
                    case 5:
                        attackPhase5();
                        break;
                }
                indexAI ++;
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // no collision dmg for lunatic cultist
    }
    // default constructor to handle chunk unload
    public LunaticCultist(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public LunaticCultist(Player summonedPlayer, Location spawnLoc) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        bukkitEntity.addScoreboardTag("noDamage");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        if (EventAndTime.isBossRushActive())
            ticksBeforeAttack = 1;
        else
            summonedPlayer.sendMessage("§a你有10秒的时间做好挑战拜月教邪教徒的准备！");
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 594d);
            attrMap.put("damageTakenMulti", 0.5d);
            attrMap.put("defence", 94d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MAGIC);
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
        // init health
        {
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BossHelper.accountForBR(BASIC_HEALTH_BR, BASIC_HEALTH) * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            setBaby(false);
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoFireball = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapFireball,
                    DamageHelper.DamageType.ARROW, "火球");
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
            // drop items (manipulator etc.)
            if (! EventAndTime.isBossRushActive())
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
