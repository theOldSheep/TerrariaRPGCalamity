package terraria.entity.boss.leviathanAndAnahita;

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
import terraria.entity.boss.theTwins.Spazmatism;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Leviathan extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.LEVIATHAN_AND_ANAHITA;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.OCEAN;
    public static final double BASIC_HEALTH = 17414 * 2;
//    public static final double BASIC_HEALTH = 174144 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static String bossName = "利维坦";
    enum AIPhase {
        METEOR, SUMMON, DASH;
    }
    Anahita anahita;
    AIPhase phaseAI = AIPhase.METEOR;
    int indexAI = -40;
    double healthRatio = 1d;
    static HashMap<String, Double> attrMapMeteor;
    static final double SPEED_METEOR = 2, SPEED_HOVER = 2, SPEED_DASH = 3;
    static final int DASH_DURATION = 60;
    EntityHelper.ProjectileShootInfo shootInfoMeteor;
    static {
        attrMapMeteor = new HashMap<>();
        attrMapMeteor.put("damage", 600d);
        attrMapMeteor.put("knockback", 5d);
    }
    private void changePhase() {
        // change phase
        ArrayList<AIPhase> availablePhase = new ArrayList<>();
        availablePhase.add(AIPhase.METEOR);
        availablePhase.add(AIPhase.METEOR);
        availablePhase.add(AIPhase.DASH);
        availablePhase.add(AIPhase.DASH);
        availablePhase.add(AIPhase.SUMMON);
        availablePhase.remove(phaseAI);
        phaseAI = availablePhase.get((int) (Math.random() * availablePhase.size()));
        // aftermath
        indexAI = -1;
    }
    private void shootMeteor() {
        shootInfoMeteor.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoMeteor.velocity = MathHelper.getDirection(shootInfoMeteor.shootLoc, target.getEyeLocation(), SPEED_METEOR);
        EntityHelper.spawnProjectile(shootInfoMeteor);
    }
    private void attackAI() {
        // attack
        healthRatio = getHealth() / getMaxHealth();
        // velocity
        {
            boolean shouldAlignHorizontally;
            switch (phaseAI) {
                case DASH: {
                    int subIndex = indexAI % DASH_DURATION;
                    if (subIndex < 30) {
                        shouldAlignHorizontally = true;
                    } else {
                        shouldAlignHorizontally = false;
                        if (subIndex == 30) {
                            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
                            Location targetLoc = target.getLocation();
                            targetLoc.setY(bukkitEntity.getLocation().getY());
                            bukkitEntity.setVelocity(MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, SPEED_DASH));
                        }
                    }
                    break;
                }
                case METEOR:
                case SUMMON:
                default: {
                    shouldAlignHorizontally = true;
                    break;
                }
            }
            if (shouldAlignHorizontally) {
                Location tempLoc = target.getEyeLocation();
                Location eyeLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
                tempLoc.setY(eyeLoc.getY());
                Vector offsetVec = MathHelper.getDirection(tempLoc, eyeLoc, 16);
                Location hoverTargetLocation = target.getEyeLocation().add(offsetVec);
                bukkitEntity.setVelocity(
                        MathHelper.getDirection(eyeLoc, hoverTargetLocation, SPEED_HOVER, true));
            }
        }
        // projectiles and phase transition
        switch (phaseAI) {
            case DASH: {
                int dashedAmount = indexAI / DASH_DURATION;
                int maxDashAmount;
                if (healthRatio < 0.4)
                    maxDashAmount = 3;
                else if (healthRatio < 0.7)
                    maxDashAmount = 2;
                else
                    maxDashAmount = 1;
                if (dashedAmount >= maxDashAmount)
                    changePhase();
                break;
            }
            case METEOR: {
                if (indexAI % 12 == 0) {
                    shootMeteor();
                }
                if (indexAI >= 120)
                    changePhase();
                break;
            }
            case SUMMON: {
                int summonAmount = anahita.healthRatio < 0.2 ? 3 : 2;
                if (indexAI % 6 == 0) {
                    MonsterHelper.spawnMob("深海吞食者", bukkitEntity.getLocation(), target);
                }
                if (indexAI >= 6 * summonAmount)
                    changePhase();
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
        {
            // update target
            if (anahita.isAlive()) {
                target = anahita.target;
            }
            else {
                target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                        IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
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
                attackAI();
            }
        }
        // face the player
        if (phaseAI == AIPhase.DASH)
            this.yaw = (float) MathHelper.getVectorYaw( bukkitEntity.getVelocity() );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Leviathan(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public Leviathan(Player summonedPlayer, Anahita anahita) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = anahita.getBukkitEntity().getLocation().add(0, -15, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        this.anahita = anahita;
        setCustomName(bossName);
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
            attrMap.put("damage", 648d);
            attrMap.put("damageTakenMulti", 0.65);
            attrMap.put("defence", 80d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init boss bar
        bossbar = anahita.bossbar;
        EntityHelper.setMetadata(bukkitEntity, "bossbar", bossbar);
        // init target map
        {
            targetMap = anahita.targetMap;
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
        }
        // init health and slime size
        {
            setSize(20, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = anahita.bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
        // shoot info's
        {
            shootInfoMeteor = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapMeteor,
                    EntityHelper.DamageType.MAGIC, "流星喷射");
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar if both boss are defeated
        if (!anahita.isAlive()) {
            bossbar.setVisible(false);
            BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        }
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);

            // send loot
            if (!anahita.isAlive()) {
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
        // update boss bar and dynamic DR
        if (!anahita.isAlive())
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
