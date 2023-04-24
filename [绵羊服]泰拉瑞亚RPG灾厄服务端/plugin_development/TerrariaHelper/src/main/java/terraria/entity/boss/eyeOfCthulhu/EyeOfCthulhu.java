package terraria.entity.boss.eyeOfCthulhu;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class EyeOfCthulhu extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EYE_OF_CTHULHU;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 12132;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    private enum AIPhase {
        CHARGE, SUMMON, HALT;
    }
    int rageRotationIndex = -1;
    int indexAI = 1, countAI = 0;
    AIPhase typeAI = AIPhase.CHARGE;
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
                for (org.bukkit.entity.LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                return;
            }
            // if target is valid, attack
            else {
                // AI
                if (ticksLived % 3 == 0) {
                    double healthRatio = this.getHealth() / this.getMaxHealth();
                    //
                    // not enraged
                    //
                    if (rageRotationIndex == -1) {
                        // start enrage when reaching low health
                        if (healthRatio < 0.75) {
                            rageRotationIndex = 15;
                            bossbar.color = BossBattle.BarColor.YELLOW;
                            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
                            setCustomName(BOSS_TYPE.msgName + "§1");
                            attrMap.put("defence", 50d);
                        }
                        else {
                            switch (typeAI) {
                                case CHARGE: {
                                    if (indexAI <= 15) {
                                        // init dash direction
                                        if (indexAI == 0) {
                                            Vector direction = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                                            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                                            double dirLen = direction.length();
                                            if (dirLen < 0.5) {
                                                direction = new Vector(0, -1, 0);
                                                dirLen = 1;
                                            }
                                            direction.multiply(0.75 / dirLen);
                                            bukkitEntity.setVelocity(direction);
                                        }
                                        // charge speed decay
                                        motX *= 0.95;
                                        motY *= 0.95;
                                        motZ *= 0.95;
                                    }
                                    // after charge
                                    else {
                                        countAI ++;
                                        indexAI = -1;
                                        if (countAI >= 3) {
                                            countAI = 0;
                                            typeAI = AIPhase.SUMMON;
                                        }
                                    }
                                    break;
                                }
                                case SUMMON: {
                                    Vector velocity = target.getLocation().add(0, 10, 0).subtract(bukkitEntity.getLocation()).toVector();
                                    velocity.multiply(1d / 10);
                                    bukkitEntity.setVelocity(velocity);
                                    if (indexAI >= 5) {
                                        indexAI = -1;
                                        countAI ++;
                                        if (countAI >= 1)
                                            MonsterHelper.spawnMob("克苏鲁的仆从",
                                                    ((LivingEntity) bukkitEntity).getEyeLocation(), target);
                                        if (countAI >= 4 && Math.random() < 0.5) {
                                            countAI = 0;
                                            typeAI = AIPhase.CHARGE;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    //
                    // ready to start enraged state, spinning
                    //
                    else if (rageRotationIndex > 0) {
                        MonsterHelper.spawnMob("克苏鲁的仆从",
                                ((LivingEntity) bukkitEntity).getEyeLocation(), target);
                        rageRotationIndex --;
                        if (rageRotationIndex == 0) {
                            attrMap.put("damage", 132d);
                            attrMap.put("defence", 0d);
                            bossbar.color = BossBattle.BarColor.RED;
                            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
                            indexAI = -1;
                            countAI = 0;
                            typeAI = AIPhase.CHARGE;
                        }
                    }
                    //
                    // final, enraged state
                    //
                    else {
                        // tier 1, health > 55%
                        if (healthRatio > 0.55) {
                            switch (typeAI) {
                                case CHARGE: {
                                    if (indexAI <= 12) {
                                        // init dash direction
                                        if (indexAI == 0) {
                                            Vector direction = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                                            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1.5f);
                                            double dirLen = direction.length();
                                            if (dirLen < 0.5) {
                                                direction = new Vector(0, -1, 0);
                                                dirLen = 1;
                                            }
                                            direction.multiply(1.5 / dirLen);
                                            bukkitEntity.setVelocity(direction);
                                        }
                                    }
                                    // after charge
                                    else {
                                        countAI ++;
                                        indexAI = -1;
                                        if (countAI >= 3) {
                                            countAI = 0;
                                            typeAI = AIPhase.HALT;
                                        }
                                    }
                                    break;
                                }
                                case HALT: {
                                    // dash randomly twice if health is quite low
                                    if (healthRatio < 0.65) {
                                        if (indexAI <= 10) {
                                            // init dash direction
                                            if (indexAI == 0) {
                                                Vector direction = target.getLocation()
                                                        .add(Math.random() * 10 - 5, Math.random() * 10 - 5, Math.random() * 10 - 5)
                                                        .subtract(bukkitEntity.getLocation()).toVector();
                                                bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1.5f);
                                                direction.multiply(0.06);
                                                bukkitEntity.setVelocity(direction);
                                            }
                                        }
                                        // back to charge directly
                                        else {
                                            countAI ++;
                                            indexAI = -1;
                                            if (countAI >= 2) {
                                                countAI = 0;
                                                typeAI = AIPhase.CHARGE;
                                            }
                                        }
                                    }
                                    // hover if health is relatively high
                                    else {
                                        Vector velocity = target.getLocation().add(0, 12, 0)
                                                .subtract(bukkitEntity.getLocation()).toVector();
                                        velocity.multiply(0.025);
                                        bukkitEntity.setVelocity(velocity);
                                        if (indexAI > 12) {
                                            indexAI = -1;
                                            countAI = 0;
                                            typeAI = AIPhase.CHARGE;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        // tier 2, health < 55%
                        else {
                            switch (typeAI) {
                                // 5x charge
                                case CHARGE: {
                                    if (indexAI <= 10) {
                                        // init dash direction
                                        if (indexAI == 0) {
                                            Vector direction = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                                            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1.5f);
                                            double dirLen = direction.length();
                                            if (dirLen < 0.5) {
                                                direction = new Vector(0, -1, 0);
                                                dirLen = 1;
                                            }
                                            direction.multiply(2 / dirLen);
                                            bukkitEntity.setVelocity(direction);
                                        }
                                        motX *= 0.95;
                                        motY *= 0.95;
                                        motZ *= 0.95;
                                    }
                                    // halt
                                    else {
                                        countAI ++;
                                        indexAI = -1;
                                        if (countAI >= 5) {
                                            countAI = 0;
                                            typeAI = AIPhase.HALT;
                                        }
                                    }
                                    break;
                                }
                                case HALT: {
                                    // dashes horizontally if health is quite low
                                    if (healthRatio < 0.4) {
                                        // prepare dash
                                        if (indexAI < 10) {
                                            Vector offset = target.getLocation()
                                                    .subtract(bukkitEntity.getLocation()).toVector();
                                            offset.setY(0);
                                            double offsetLen = offset.length();
                                            if (offsetLen < 0.1) {
                                                offsetLen = 1;
                                                offset = new Vector(0, 0, 1);
                                            }
                                            offset.multiply(20 / offsetLen);
                                            Vector velocity = target.getLocation().subtract(offset)
                                                    .subtract(bukkitEntity.getLocation()).toVector();
                                            velocity.multiply(0.15);
                                            bukkitEntity.setVelocity(velocity);
                                        }
                                        // init dash direction
                                        else if (indexAI == 10) {
                                            Vector direction = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                                            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1f);
                                            double dirLen = direction.length();
                                            if (dirLen < 0.5) {
                                                direction = new Vector(0, -1, 0);
                                                dirLen = 1;
                                            }
                                            direction.multiply(4 / dirLen);
                                            bukkitEntity.setVelocity(direction);
                                            attrMap.put("damageMulti", 1.25d);
                                        }
                                        // back to directly charging player
                                        else if (indexAI >= 15) {
                                            indexAI = -1;
                                            countAI = 0;
                                            typeAI = AIPhase.CHARGE;
                                            attrMap.put("damageMulti", 1d);
                                        }
                                    }
                                    // hover if health is relatively high
                                    else {
                                        Vector velocity = target.getLocation().add(0, 12, 0)
                                                .subtract(bukkitEntity.getLocation()).toVector();
                                        velocity.multiply(0.025);
                                        bukkitEntity.setVelocity(velocity);
                                        if (indexAI > 8) {
                                            indexAI = -1;
                                            countAI = 0;
                                            typeAI = AIPhase.CHARGE;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    // add 1 to index
                    indexAI ++;
                }
            }
        }
        // face the player
        if (typeAI == AIPhase.HALT)
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public EyeOfCthulhu(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.isDayTime(player.getWorld()) ) return false;
        return true;
    }
    // a constructor for actual spawning
    public EyeOfCthulhu(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
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
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 102d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 24d);
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
                    getBukkitEntity(), "", summonedPlayer, true, bossbar);
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
