package terraria.entity.boss.cryogen;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;

public class Cryogen extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CRYOGEN;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.TUNDRA;
    public static final double BASIC_HEALTH = 86400 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
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
    public EntityHelper.ProjectileShootInfo psiBlast, psiBomb;
    CryogenShield shield = null;
    Location teleportLoc = null;
    boolean invincible = false;
    int indexAI = 0, phaseAI = 1, shieldIndex = 0, iceBombIndex = 0;
    private void shootIceBlasts(int amount) {
        psiBlast.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        for (Vector velocity : MathHelper.getCircularProjectileDirections(amount)) {
            velocity.multiply(1.5);
            psiBlast.velocity = velocity;
            EntityHelper.spawnProjectile(psiBlast);
        }
    }
    private void shootIceBombs() {
        psiBomb.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        for (Vector velocity : MathHelper.getCircularProjectileDirections(3)) {
            velocity.multiply(1);
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
                attrMap.put("damageTakenMulti", 0.79d);
                attrMap.put("defence", 20d);
                break;
            case 3:
                attrMap.put("damageTakenMulti", 0.88d);
                attrMap.put("defence", 12d);
                break;
            case 4:
                attrMap.put("damageTakenMulti", 1d);
                attrMap.put("defence", 0d);
                break;
            case 5:
                psiBlast.properties.put("autoTrace", true);
                psiBlast.properties.put("autoTraceMethod", 2);
                psiBlast.properties.put("autoTraceRadius", 32d);
                psiBlast.properties.put("autoTraceSharpTurning", false);
                psiBlast.properties.put("autoTraceAbility", 1d);
                break;
        }
        indexAI = -1;
        phaseAI = newPhase;
        setCustomName(BOSS_TYPE.msgName + "§" + newPhase);
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
                // AI
                double healthRatio = getHealth() / getMaxHealth();
                // phase-specific AI
                switch (phaseAI) {
                    // phase 1 (phase 2 technically, as death mode omits original phase 1)
                    case 1: {
                        // rotate around player and fire ice blasts
                        if (indexAI < 30) {
                            Location targetLoc = target.getLocation().add(
                                    MathHelper.xsin_degree(ticksLived * 5) * 16,
                                    12,
                                    MathHelper.xcos_degree(ticksLived * 5) * 16);
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 1.75;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                            switch (indexAI) {
                                case 0:
                                case 15:
                                    shootIceBlasts(12);
                            }
                        }
                        // then, stay where it is, fire 3 rounds of more ice blasts and dash
                        else {
                            // stay, fire rounds of ice blasts
                            if (indexAI < 50) {
                                bukkitEntity.setVelocity(new Vector());
                                switch (indexAI) {
                                    case 30:
                                    case 45:
                                    case 50:
                                        shootIceBlasts(16);
                                }
                            }
                            // dash
                            else if (indexAI == 50) {
                                Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 2.25);
                                bukkitEntity.setVelocity(velocity);
                            }
                            // finish one AI cycle
                            else if (indexAI >= 80)
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
                        if (indexAI < 30) {
                            Location targetLoc = target.getLocation();
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 1;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                            switch (indexAI) {
                                case 0:
                                case 15:
                                    shootIceBlasts(12);
                            }
                        }
                        // then, stay where it is, fire 3 rounds of more ice blasts and dash
                        else {
                            // stay, fire rounds of ice blasts
                            if (indexAI <= 110) {
                                int subIndex = (indexAI - 30) % 40;
                                if (subIndex < 20)
                                    bukkitEntity.setVelocity(new Vector());
                                switch (subIndex) {
                                    case 10:
                                    case 20:
                                        shootIceBlasts(16);
                                        break;
                                }
                                // dash
                                if (subIndex == 20) {
                                    Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 2.75);
                                    bukkitEntity.setVelocity(velocity);
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
                            double maxSpeed = 0.75;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                            if (indexAI % 15 == 0)
                                shootIceBlasts(12);
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
                            if (dirLen > 16) {
                                Vector velocity = bukkitEntity.getVelocity();
                                direction.multiply(0.075 / dirLen);
                                velocity.add( direction );
                                indexAI = -1;
                            }
                            // close enough: dash towards player
                            else {
                                direction.multiply(2.5 / dirLen);
                                bukkitEntity.setVelocity(direction);
                            }
                        }
                        // repeat AI cycle
                        else if (indexAI > 20)
                            indexAI = -1;
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
                            Location targetLoc = target.getLocation().add(0, 12, 0);
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 1.75;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                        }
                        // dashes into player
                        else {
                            switch (indexAI) {
                                case 30:
                                case 45:
                                case 60:
                                    Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 2.25);
                                    bukkitEntity.setVelocity(velocity);
                                    shootIceBlasts(2);
                                    break;
                                // a new AI cycle
                                case 75:
                                    indexAI = -1;
                                    break;
                            }
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
                            Location targetLoc = target.getLocation().add(0, 10, 0);
                            Vector velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                            double velLen = velocity.length();
                            double maxSpeed = 2.25;
                            if (velLen > maxSpeed) {
                                velocity.multiply(maxSpeed / velLen);
                            }
                            bukkitEntity.setVelocity(velocity);
                        }
                        // dashes into player
                        else {
                            switch (indexAI) {
                                case 20:
                                case 30:
                                case 40:
                                    Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), target.getLocation(), 1.75);
                                    bukkitEntity.setVelocity(velocity);
                                    shootIceBlasts(4);
                                    break;
                                // a new AI cycle
                                case 55:
                                    indexAI = -1;
                                    break;
                            }
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
        setCustomName(BOSS_TYPE.msgName + "§1");
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
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
            EntityHelper.setDamageType(bukkitEntity, "Melee");
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, "bossbar", targetMap);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), "血肉之墙", summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
        }
        // init health and slime size
        {
            setSize(8, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
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
            psiBlast = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapIceBlast, "Magic", "冰霜爆");
            psiBomb = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapIceBomb, "Magic", "冰霜炸弹");
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

            Bukkit.broadcastMessage("§d§l" + BOSS_TYPE.msgName + " 被击败了.");
            // send out loot
            double[] healthInfo = terraria.entity.boss.BossHelper.getHealthInfo(bossParts);
            double dmgDealtReq = healthInfo[1] / targetMap.size() / 10;
            ItemStack loopBag = ItemHelper.getItemFromDescription( BOSS_TYPE.msgName + "的 专家模式福袋");
            for (Player ply : targetMap.keySet()) {
                if (targetMap.get(ply) >= dmgDealtReq) {
                    ply.sendMessage("§a恭喜你击败了BOSS[§r" + BOSS_TYPE.msgName + "§a]!");
                    PlayerHelper.setDefeated(ply, BOSS_TYPE.msgName, true);
                    PlayerHelper.giveItem(ply, loopBag, true);
                }
                else {
                    ply.sendMessage("§aBOSS " + BOSS_TYPE.msgName + " 已经被击败。很遗憾，您的输出不足以获得一份战利品。");
                }
            }
        }
    }
    // rewrite AI
    @Override
    public void B_() {
        Bukkit.broadcastMessage("phase " + phaseAI);
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
