package terraria.entity.boss.postMoonLord.exoMechs;

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
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Draedon extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 100;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI


    private EntityLiving[] subBosses;
    private int currentPhase;
    private boolean[] subBossIsActive;


    private void managePhases() {
        int originalPhase = currentPhase;
        List<Integer> aliveBossIndexes = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            if (subBosses[i].getHealth() > 0) {
                aliveBossIndexes.add(i);
            }
        }

        switch (currentPhase) {
            case -1:
                // Initialize the fight, select a random boss
                int randomIndex = aliveBossIndexes.get((int) (Math.random() * aliveBossIndexes.size()));
                subBossIsActive[randomIndex] = true;
                currentPhase = 1;
                break;
            case 1:
                // First boss fights the player until it's health is below 70%
                EntityLiving firstSubBoss = null;
                for (int index : aliveBossIndexes) {
                    if (subBossIsActive[index]) {
                        firstSubBoss = subBosses[index];
                        break;
                    }
                }
                if (firstSubBoss != null && firstSubBoss.getHealth() / firstSubBoss.getMaxHealth() < 0.7) {
                    updateSubBosses(aliveBossIndexes, 0.7, true, 2);
                    currentPhase = 2;
                }
                break;
            case 2:
                // First boss rests, other two bosses activate
                for (int index : aliveBossIndexes) {
                    if (subBossIsActive[index] && subBosses[index].getHealth() / subBosses[index].getMaxHealth() < 0.7) {
                        // All three sub-bosses fight the player with less aggressive attacks
                        updateSubBosses(aliveBossIndexes, 0.0, true, 3);
                        currentPhase = 3;
                        break;
                    }
                }
                break;
            case 3:
                // All three bosses fight the player until one of them has health below 40%
                for (int index : aliveBossIndexes) {
                    if (subBossIsActive[index] && subBosses[index].getHealth() / subBosses[index].getMaxHealth() < 0.4) {
                        // Solo the player
                        updateSubBosses(aliveBossIndexes, 0.4, false, 1);
                        currentPhase = 4;
                        break;
                    }
                }
                break;
            case 4:
                // Solo boss fights the player until it's defeated
                if (aliveBossIndexes.size() == 2) {
                    updateSubBosses(aliveBossIndexes, 0.0, true, 2);
                    currentPhase = 5;
                }
                break;
            case 5:
                // Two remaining bosses fight the player until one of them has health below 40%
                for (int index : aliveBossIndexes) {
                    if (subBossIsActive[index] && subBosses[index].getHealth() / subBosses[index].getMaxHealth() < 0.4) {
                        // Solo the player
                        updateSubBosses(aliveBossIndexes, 0.4, false, 1);
                        currentPhase = 6;
                        break;
                    }
                }
                break;
            case 6:
                // Solo boss fights the player until it's defeated
                if (aliveBossIndexes.size() == 1) {
                    updateSubBosses(aliveBossIndexes, 0.0, true, 1);
                    currentPhase = 7;
                }
                break;
            case 7:
                // Last boss fights the player until it's defeated
                if (aliveBossIndexes.size() == 0) {
                    // All sub-bosses are defeated, make Draedon vulnerable
                    this.removeScoreboardTag("noDamage");
                    // No longer need any check in switch statement
                    currentPhase = 8;
                }
                break;
        }

        if (currentPhase != originalPhase) {
            printPhaseInfo();
            for (int index : aliveBossIndexes) {
                if (subBossIsActive[index]) {
                    subBosses[index].removeScoreboardTag("noDamage");
                } else {
                    subBosses[index].addScoreboardTag("noDamage");
                }
            }
        }
    }

    private void updateSubBosses(List<Integer> aliveBossIndexes, double healthRatioThreshold, boolean aboveThreshold, int limit) {
        int count = 0;
        for (int index : aliveBossIndexes) {
            if ((subBosses[index].getHealth() / subBosses[index].getMaxHealth() > healthRatioThreshold) == aboveThreshold) {
                if (count < limit) {
                    subBossIsActive[index] = true;
                    count++;
                } else {
                    subBossIsActive[index] = false;
                }
            } else {
                subBossIsActive[index] = false;
            }
        }
    }

    private void printPhaseInfo() {
        System.out.println("Current Phase: " + currentPhase);
        for (int i = 0; i < 3; i++) {
            System.out.println("Sub-boss " + i + " health ratio: " + subBosses[i].getHealth() / subBosses[i].getMaxHealth());
            System.out.println("Sub-boss " + i + " active: " + subBossIsActive[i]);
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

                // TODO
                // Make Draedon hover in front of the player
                Location targetLocation = target.getLocation().add(target.getLocation().getDirection().multiply(10));
                this.setLocation(targetLocation.getX(), targetLocation.getY(), targetLocation.getZ(), 0, 0);

                managePhases();
            }
        }
        // facing
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
    }
    // default constructor to handle chunk unload
    public Draedon(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public Draedon(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = summonedPlayer.getLocation().add(summonedPlayer.getLocation().getDirection().multiply(32) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        addScoreboardTag("noDamage");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 1d);
            attrMap.put("damageTakenMulti", 99d);
            attrMap.put("defence", 0d);
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
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(5, false);
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
        // Initialize sub-bosses
        {
            subBosses = new EntityLiving[3];
            subBosses[0] = new Thanatos(this, summonedPlayer.getLocation().add(0, -5, 0));
            subBosses[1] = new Ares(this, summonedPlayer.getLocation().add(0, -5, 0));
            subBosses[2] = new Artemis(this, summonedPlayer.getLocation().add(0, -5, 0));

            // Initialize sub-boss active status
            subBossIsActive = new boolean[3];
            for (int i = 0; i < 3; i++) {
                subBossIsActive[i] = false;
            }

            currentPhase = -1;
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