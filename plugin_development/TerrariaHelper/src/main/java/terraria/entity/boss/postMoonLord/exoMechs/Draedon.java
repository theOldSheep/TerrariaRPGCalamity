package terraria.entity.boss.postMoonLord.exoMechs;

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
import org.bukkit.util.Vector;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.*;

public class Draedon extends EntitySlime {
    public enum SubBossType {
        ARTEMIS,
        THANATOS,
        ARES;
    }
    enum Difficulty {
        LOW, MEDIUM, HIGH
    }
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.EXO_MECHS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 10000;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    protected static final double MECHS_ALIGNMENT_SPEED = 3.0, MECHS_ALIGN_DIST = 32.0;
    static String MSG_COLOR = "§#9BFFFF";
    static final String[]
            MESSAGES_SPAWN = {
            "你知道吗？这一刻已经等了太久了。",
            "我对一切未知感到着迷，但最让我着迷的莫过于你的本质。",
            "我将会向你展示，我那些超越神明的造物。",
            "而你，则将在战斗中向我展示你的本质。"},
            MESSAGES_FIRST_TRANSITION = {
            "时间与知识的积累带来的不断改进，正是我作品精华所在。",
            "接近完美的方法，除此再无。"},
            MESSAGES_SECOND_TRANSITION = {
            "很好，很好，你的表现水平完全就在误差范围之内。",
            "这着实令人满意。接下来我们将进入下一个测试环节。"},
            MESSAGES_THIRD_TRANSITION = {
            "自我第一次到得知你的存在，我就一直在研究你的战斗，并以此让我的机械变得更强。",
            "就算是现在，我依然在检测你的行动。一切都在我的计算之内。"},
            MESSAGES_FOURTH_TRANSITION = {
            "有趣，十分有趣。",
            "就算是面对更为困难的挑战，你仍可以稳步推进。"},
            MESSAGES_FIFTH_TRANSITION = {
            "我依然无法理解你的本质，这样下去可不行。",
            "......我一向追求完美，可惜造化弄人，这一定是我犯下的第一个错误。"},
            MESSAGES_SIXTH_TRANSITION = {
            "荒谬至极。",
            "我不会再让那些无用的计算干扰我对这场战斗的观察了。",
            "我将向你展示，我终极造物的全部威力。"},
            MESSAGES_FINAL = {
            "一个未知因素——你，是一个特异点。",
            "你对这片大地和它的历史而言，只是外来之人，就和我一样。",
            "......很抱歉，但在看了这样一场“展示”之后，我必须得花点时间整理我的思绪。",
            "迄今为止喷洒的血液已经让这片大陆变得陈腐无比，毫无生气。",
            "你也挥洒了自己的鲜血，但这可能足以带来一个新的时代......是什么，我不知道。但那一定是我所渴望看到的时代。",
            "现在，你想要接触那位暴君。可惜我无法帮到你。",
            "这并非出自怨恨，毕竟从一开始，我的目标就只有观察刚才的这一场战斗。",
            "但你过去也成功过，所以你最后会找到办法的。",
            "我必须尊重并承认你的胜利，但现在，我得把注意力放回到我的机械上了。"};
    private int messageIndex = 0;
    private int messageDelayCounter = 0;
    private boolean hasTriggered = false;

    private EntityLiving[] subBosses;
    private int currentPhase, activeBossCount;
    boolean[] subBossIsActive;
    private Location sharedHoverLocation;

    public void updateSharedHoverLocation() {
        Location targetLocation = target.getLocation();
        // Calculate the direction vector from the target to the boss
        Location originalHoverLoc = isSubBossActive(SubBossType.ARES) ?
                subBosses[SubBossType.ARES.ordinal()].getBukkitEntity().getLocation() : sharedHoverLocation;
        double yaw = MathHelper.getVectorYaw(originalHoverLoc.subtract(targetLocation).toVector());
        Vector direction = MathHelper.vectorFromYawPitch_approx(yaw, 0).multiply(MECHS_ALIGN_DIST);

        // Calculate the hover location for the boss
        sharedHoverLocation = targetLocation.clone().add(direction);
    }
    public Location getHoverCenterLoc() {
        return sharedHoverLocation.clone();
    }
    public boolean isSubBossActive(SubBossType type) {
        return subBossIsActive[type.ordinal()];
    }
    public int getActiveBossCount() {
        return activeBossCount;
    }
    private void updateActiveBossCount() {
        activeBossCount = 0;
        for (int i = 0; i < subBossIsActive.length; i++) {
            if (subBossIsActive[i] && subBosses[i].isAlive()) {
                activeBossCount++;
            }
        }
    }
    protected void playWarningSound(boolean enragedOrDash) {
        Location loc = target.getLocation();
        loc.getWorld().playSound(loc, enragedOrDash ? "entity.exo_mechs.exo_mech_enraged" : "entity.exo_mechs.exo_mech_dash",
                org.bukkit.SoundCategory.HOSTILE, 5f, 1f);
    }
    protected Difficulty calculateDifficulty(EntityLiving livingEntity) {
        double healthRatio = livingEntity.getHealth() / livingEntity.getMaxHealth();
        if (getActiveBossCount() == 3) {
            return Draedon.Difficulty.LOW;
        } else if ( (getActiveBossCount() == 1 && healthRatio < 0.7) || healthRatio < 0.4) {
            return Draedon.Difficulty.HIGH;
        } else {
            return Draedon.Difficulty.MEDIUM;
        }
    }

    private void managePhases() {
        int originalPhase = currentPhase;
        List<Integer> aliveBossIndexes = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            if (subBosses[i].getHealth() > 0) {
                aliveBossIndexes.add(i);
            } else {
                subBossIsActive[i] = false;
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
                    terraria.entity.boss.BossHelper.sendBossMessages(20, 0, bukkitEntity, MSG_COLOR, MESSAGES_FIRST_TRANSITION);
                }
                break;
            case 2:
                // First boss rests, other two bosses activate
                for (int index : aliveBossIndexes) {
                    if (subBossIsActive[index] && subBosses[index].getHealth() / subBosses[index].getMaxHealth() < 0.7) {
                        // All three sub-bosses fight the player with less aggressive attacks
                        updateSubBosses(aliveBossIndexes, 0.0, true, 3);
                        currentPhase = 3;
                        terraria.entity.boss.BossHelper.sendBossMessages(20, 0, bukkitEntity, MSG_COLOR, MESSAGES_SECOND_TRANSITION);
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
                        terraria.entity.boss.BossHelper.sendBossMessages(20, 0, bukkitEntity, MSG_COLOR, MESSAGES_THIRD_TRANSITION);
                        break;
                    }
                }
                break;
            case 4:
                // Solo boss fights the player until it's defeated
                if (aliveBossIndexes.size() == 2) {
                    updateSubBosses(aliveBossIndexes, 0.0, true, 2);
                    currentPhase = 5;
                    terraria.entity.boss.BossHelper.sendBossMessages(20, 0, bukkitEntity, MSG_COLOR, MESSAGES_FOURTH_TRANSITION);
                }
                break;
            case 5:
                // Two remaining bosses fight the player until one of them has health below 40%
                for (int index : aliveBossIndexes) {
                    if (subBossIsActive[index] && subBosses[index].getHealth() / subBosses[index].getMaxHealth() < 0.4) {
                        // Solo the player
                        updateSubBosses(aliveBossIndexes, 0.4, false, 1);
                        currentPhase = 6;
                        terraria.entity.boss.BossHelper.sendBossMessages(20, 0, bukkitEntity, MSG_COLOR, MESSAGES_FIFTH_TRANSITION);
                        break;
                    }
                }
                break;
            case 6:
                // Solo boss fights the player until it's defeated
                if (aliveBossIndexes.size() == 1) {
                    updateSubBosses(aliveBossIndexes, 0.0, true, 1);
                    currentPhase = 7;
                    terraria.entity.boss.BossHelper.sendBossMessages(20, 0, bukkitEntity, MSG_COLOR, MESSAGES_SIXTH_TRANSITION);
                }
                break;
            case 7:
                // Last boss fights the player until it's defeated
                if (aliveBossIndexes.size() == 0) {
                    // All sub-bosses are defeated
                    this.removeScoreboardTag("noDamage");
                    currentPhase = 8;
                    hasTriggered = true;
                }
                break;
        }

        if (currentPhase != originalPhase) {
            bossbar.color = BossBattle.BarColor.GREEN;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);

            for (int index : aliveBossIndexes) {
                if (subBossIsActive[index]) {
                    subBosses[index].removeScoreboardTag("noDamage");
                } else {
                    subBosses[index].addScoreboardTag("noDamage");
                }
            }
            updateActiveBossCount(); // Update active boss count when phase changes
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

    public void broadcastMsg() {
        if (hasTriggered) {
            if (messageDelayCounter % 20 == 0) { // broadcast a message every 20 ticks
                if (messageIndex < MESSAGES_FINAL.length) {
                    for (UUID uuid : targetMap.keySet()) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.sendMessage(MSG_COLOR + MESSAGES_FINAL[messageIndex]);
                        }
                    }
                    messageIndex++;
                } else {
                    die();
                }
            }
            messageDelayCounter++;
        }
    }

    private void AI() {
        // no AI after death
        if (getHealth() <= 0d) {
            setNoGravity(false);
            bukkitEntity.setVelocity(new Vector(0, -1, 0));
            return;
        }
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

                // Hover in front of the player
                double alignmentDistance = currentPhase == 8 ? 10: 40;

                Location targetLocation = target.getLocation();
                Vector direction = MathHelper.vectorFromYawPitch_approx(targetLocation.getYaw(), 0); // pitch is set to 0 to keep the direction horizontal
                direction.multiply(alignmentDistance);

                Location destination = targetLocation.add(direction);

                Vector velocity = MathHelper.getDirection(bukkitEntity.getLocation(), destination, 3, true);
                bukkitEntity.setVelocity(velocity);

                managePhases();
                updateSharedHoverLocation();
                broadcastMsg();
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
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.vectorFromYawPitch_approx(summonedPlayer.getLocation().getYaw(), 0).multiply(64) );
        sharedHoverLocation = spawnLoc.clone();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("██-██ “嘉登”");
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
            attrMap.put("damageTakenMulti", 1d);
            attrMap.put("defence", 0d);
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
            subBosses = new EntityLiving[SubBossType.values().length];
            subBosses[SubBossType.ARTEMIS.ordinal()] = new Artemis(this, spawnLoc.clone().add(0, -100, 0));
            subBosses[SubBossType.THANATOS.ordinal()] = new Thanatos(this, spawnLoc.clone().add(0, -100, 0));
            subBosses[SubBossType.ARES.ordinal()] = new Ares(this, spawnLoc.clone().add(0, -100, 0));

            // Initialize sub-boss active status
            subBossIsActive = new boolean[3];
            for (int i = 0; i < 3; i++) {
                subBossIsActive[i] = false;
            }

            currentPhase = -1;
        }
        terraria.entity.boss.BossHelper.sendBossMessages(20, 0, bukkitEntity, MSG_COLOR, MESSAGES_SPAWN);
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