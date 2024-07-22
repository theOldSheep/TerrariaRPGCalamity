package terraria.entity.boss;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.BossBattleServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.event.celestialPillar.CelestialPillar;
import terraria.entity.boss.hardMode.astrumDeus.AstrumDeus;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.EventAndTime;
import terraria.util.*;

import javax.annotation.Nullable;
import java.util.*;

public class BossHelper {
    public enum TimeRequirement {
        DAY,NIGHT,NONE;
        public boolean validate(World wld) {
            if (this == NONE)
                return true;
            boolean isAppropriate = WorldHelper.isDayTime(wld);
            if (this == NIGHT)
                isAppropriate = ! isAppropriate;
            return isAppropriate;
        }
    }
    private static final String
            SUMMARY_HEADER_AGGRO  = "————————BOSS仇恨时长（秒）————————",
            SUMMARY_HEADER_DAMAGE = "———————————伤害汇总———————————",
            SUMMARY_HEADER_FINAL  = "———————————————————————————————";
    public static class BossTargetInfo {
        public double damageDealt = 0d;
        public int ticksAggression = 0;
        public BossTargetInfo addDamageDealt(double damageDealt) {
            this.damageDealt += damageDealt;
            return this;
        }
        public BossTargetInfo setDamageDealt(double damageDealt) {
            this.damageDealt = damageDealt;
            return this;
        }
        public BossTargetInfo setAggressionDuration(int ticksAggression) {
            this.ticksAggression = ticksAggression;
            return this;
        }
        public BossTargetInfo addAggressionTick() {
            this.ticksAggression ++;
            return this;
        }
    }
    public static double[] getHealthInfo(ArrayList<LivingEntity> bossParts, terraria.util.BossHelper.BossType bossType) {
        double[] result = new double[] {0d, 0d};
        for (LivingEntity e : bossParts) {
            MetadataValue mdv = EntityHelper.getMetadata(e, EntityHelper.MetadataName.DAMAGE_TAKER);
            // only account for the health of damage-taking parts
            if (mdv != null && mdv.value() != e)
                continue;
            result[0] += e.getHealth();
            result[1] += e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        // tweak total health so that health for bosses that share health pool are calculated correctly.
        double multiplier;
        switch (bossType) {
            case ASTRUM_DEUS:
                // 50% health dealt as a whole, and 50% health dealt to the two parts -> 150% total
                multiplier = 1.5d / AstrumDeus.TOTAL_LENGTH;
                break;
            default:
                multiplier = 1d;
        }
        result[0] *= multiplier;
        result[1] *= multiplier;
        return result;
    }
    public static void updateBossBarAndDamageReduction(BossBattleServer bossbar, ArrayList<LivingEntity> bossParts, terraria.util.BossHelper.BossType type) {
        updateBossBarAndDamageReduction(bossbar, bossParts, bossParts.get(0).getTicksLived(), type);
    }
    public static void updateBossBarAndDamageReduction(BossBattleServer bossbar, ArrayList<LivingEntity> bossParts, int ticksLived, terraria.util.BossHelper.BossType type) {
        double[] healthInfo = getHealthInfo(bossParts, type);
        double healthRatio = healthInfo[0] / healthInfo[1];
        bossbar.setProgress((float) healthRatio);
        // dynamic Damage Reduction
        double dynamicDamageMultiplier;
        {
            String bossName = type.msgName;
            double targetTime = TerrariaHelper.settingConfig.getInt("BossDefeatTime." + bossName, 900);
            // get current dynamic damage multiplier
            double currDynamicDM;
            MetadataValue metadataVal = EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.DYNAMIC_DAMAGE_REDUCTION);
            if (metadataVal != null) currDynamicDM = metadataVal.asDouble();
            else currDynamicDM = 1d;
            // dynamic DR only applies when the current time elapsed is within the expected time to defeat the boss
            if (ticksLived < targetTime && ticksLived > 1) {
                if (healthRatio > 0.000001 && healthRatio < 0.999999) {
                    double damageRatioPotentialBeforeDDM = (1 - healthRatio) / currDynamicDM;
                    double timeElapsedRatio = ticksLived / targetTime;
                    // actual DPS * dynamic DM = expected DPS = max health / expected time
                    // (actual damage / time elapsed) * dynamic DM = max health / expected time
                    // (actual damage / max health) * dynamic DM / time elapsed = 1 / expected time
                    // dynamic DM = time elapsed / expected time * max health / actual damage
                    // dynamic DM = time elapsed / expected time * max health / (damage dealt / dynamic DM)
                    // dynamic DM = time elapsed / expected time *  dynamic DM * max health / recorded damage dealt
                    // dynamic DM = time elapsed / expected time /  ( (recorded damage dealt / max health) / dynamic DM)
                    // dynamic DM = time elapsed / expected time /  ( (1 - health ratio) / dynamic DM)
                    // dynamic DM = time elapsed ratio / damageRatioPotentialBeforeDDM
                    dynamicDamageMultiplier = timeElapsedRatio / damageRatioPotentialBeforeDDM;
                    // gradually change the damage multiplier
                    dynamicDamageMultiplier = dynamicDamageMultiplier * 0.1 + currDynamicDM * 0.9;
                    // dynamic damage multiplier can not increase player damage or decrease damage excessively
                    dynamicDamageMultiplier = Math.min(dynamicDamageMultiplier, 1d);
                    dynamicDamageMultiplier = Math.max(dynamicDamageMultiplier, 0.15d);
                }
                else
                    dynamicDamageMultiplier = currDynamicDM;
            }
            // once exceeding targeted defeat time, dynamic DR multiplier linearly increases to 1 over maximum of 10 seconds.
            else {
                dynamicDamageMultiplier = Math.min(currDynamicDM + 0.005, 1);
            }
//            Bukkit.broadcastMessage("Dynamic DR multi: " + dynamicDamageMultiplier + ", time: " + ticksLived + "/" + targetTime + ", health: " + healthInfo[0] + "/" + healthInfo[1]);
        }
        for (LivingEntity bossPart : bossParts)
            EntityHelper.setMetadata(bossPart, EntityHelper.MetadataName.DYNAMIC_DAMAGE_REDUCTION, dynamicDamageMultiplier);
    }
    public static double getBossHealthMulti(int numPly) {
        double multi = 1, multiInc = 0.35;
        // multiInc -> 1 for curr > 30
        for (int curr = 1; curr < Math.min(numPly, 32); curr ++) {
            multi += multiInc;
            multiInc += (1 - multiInc) / 3;
        }
        multi += Math.max(0, numPly - 32);
        if (numPly >= 10) multi = (multi * 2 + 8) / 3;
        return multi;
    }
    public static HashMap<UUID, BossTargetInfo> setupBossTarget(Entity boss, String bossDefeatRequirement,
                                                          Player ply, boolean hasDistanceRestriction, boolean sendMsg, BossBattleServer bossbar) {
        HashMap<UUID, BossTargetInfo> targets = new HashMap<>();
        String team = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM).asString();
        // print out targets of the boss
        StringBuilder msg = new StringBuilder();
        boolean firstAppend = true;
        msg.append("BOSS的挑战者为：");
        // setup targets of the boss
        if (EventAndTime.isBossRushActive())
            bossDefeatRequirement = "毕业";
        for (Player currPly : boss.getWorld().getPlayers()) {
            if (!currPly.getName().equals(ply.getName())) {
                // unauthorized
                if (!PlayerHelper.isProperlyPlaying(currPly)) continue;
                // hasn't defeated prerequisite
                if (!PlayerHelper.hasDefeated(currPly, bossDefeatRequirement)) continue;
                // not in the same team
                if (!EntityHelper.getMetadata(currPly, EntityHelper.MetadataName.PLAYER_TEAM).asString().equals(team)) continue;
                // too far away
                if (hasDistanceRestriction &&
                        GenericHelper.getHorizontalDistance(currPly.getLocation(), ply.getLocation()) > 192) continue;
            }
            bossbar.addPlayer(((CraftPlayer) currPly).getHandle());
            targets.put(currPly.getUniqueId(), new BossTargetInfo());
            // append target message
            if (!firstAppend)
                msg.append(", ");
            else
                firstAppend = false;
            msg.append(currPly.getName());
        }
        bossbar.setVisible(true);
        EntityHelper.setMetadata(boss, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        EntityHelper.setMetadata(boss, EntityHelper.MetadataName.BOSS_TARGET_MAP, targets);
        // print targets
        if (sendMsg)
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> Bukkit.broadcastMessage(msg.toString()), 1);
        return targets;
    }
    public static HashMap<UUID, BossTargetInfo> setupBossTarget(Entity boss, String bossDefeatRequirement,
                                                          Player ply, boolean hasDistanceRestriction, BossBattleServer bossbar) {
        return setupBossTarget(boss, bossDefeatRequirement, ply, hasDistanceRestriction, true, bossbar);
    }
    public static boolean checkBossTarget(Entity target, Entity boss, boolean ignoreDistance,
                                          TimeRequirement timeRequirement, WorldHelper.BiomeType biomeRequired) {
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            if (!PlayerHelper.isProperlyPlaying(targetPlayer)) return false;
            if (targetPlayer.getWorld() != boss.getWorld()) return false;
            // do not check for biome/time during boss rush
            if (! EventAndTime.isBossRushActive()) {
                if (! timeRequirement.validate(targetPlayer.getWorld()))
                    return false;
                if (biomeRequired != null && WorldHelper.BiomeType.getBiome(targetPlayer, false) != biomeRequired)
                    return false;
            }
            double distHor = GenericHelper.getHorizontalDistance(targetPlayer.getLocation(), boss.getLocation());
            if (distHor > 165) return ignoreDistance;
            return true;
        }
        return false;
    }
    // this is in place to help aim helpers
    public static void updateSpeedForAimHelper(Entity boss) {
        MetadataValue currVel = EntityHelper.getMetadata(boss, EntityHelper.MetadataName.ENTITY_CURRENT_VELOCITY);
        if (currVel != null)
            EntityHelper.setMetadata(boss, EntityHelper.MetadataName.ENTITY_LAST_VELOCITY, currVel.value());
        // calculate velocity
        net.minecraft.server.v1_12_R1.Entity bossNMS = ((CraftEntity) boss).getHandle();
        Vector velocity = new Vector(bossNMS.locX - bossNMS.lastX, bossNMS.locY - bossNMS.lastY, bossNMS.locZ - bossNMS.lastZ);

        EntityHelper.setMetadata(boss, EntityHelper.MetadataName.ENTITY_CURRENT_VELOCITY, velocity);
    }
    // generally, this function also handles misc aspects like cached velocity
    public static Player updateBossTarget(Player currentTarget, Entity boss, boolean ignoreDistance,
                                          WorldHelper.BiomeType biomeRequired, Collection<UUID> availableTargets) {
        return updateBossTarget(currentTarget, boss, ignoreDistance, TimeRequirement.NONE, biomeRequired, availableTargets);
    }
    public static Player updateBossTarget(Player currentTarget, Entity boss, boolean ignoreDistance, TimeRequirement timeRequired,
                                          WorldHelper.BiomeType biomeRequired, Collection<UUID> availableTargets) {
        // update target
        Player finalTarget = currentTarget;
        if (!checkBossTarget(currentTarget, boss, ignoreDistance, timeRequired, biomeRequired)) {
            // save all applicable targets
            ArrayList<Player> candidates = new ArrayList<>();
            for (UUID plyID : availableTargets) {
                Player ply = Bukkit.getPlayer(plyID);
                if (ply == null)
                    continue;
                if ( checkBossTarget(ply, boss, ignoreDistance, timeRequired, biomeRequired) )
                    candidates.add(ply);
            }
            // update the target as a random new target
            if (candidates.size() == 0)
                finalTarget = null;
            else
                finalTarget = candidates.get( (int) (Math.random() * candidates.size()) );
        }
        return finalTarget;
    }
    public static void collisionDamage(net.minecraft.server.v1_12_R1.Entity boss) {
        collisionDamage(boss, null, 1);
    }
    public static void collisionDamage(net.minecraft.server.v1_12_R1.Entity boss, @Nullable Collection<Entity> damageExceptions, int damageCDTicks) {
        Entity monsterBkt = boss.getBukkitEntity();
        AxisAlignedBB bb = boss.getBoundingBox();
        double xWidth = (bb.d - bb.a) / 2, zWidth = (bb.f - bb.c) / 2, height = (bb.e - bb.b) / 2;
        Vector finalLoc = new Vector(bb.a + xWidth, bb.b + height, bb.c + zWidth);
        Vector initLoc = finalLoc.clone().add(new Vector(
                boss.lastX - boss.locX, boss.lastY - boss.locY, boss.lastZ - boss.locZ));
        Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(monsterBkt.getWorld(),
                initLoc, finalLoc,
                xWidth, height, zWidth,
                (net.minecraft.server.v1_12_R1.Entity entity) -> EntityHelper.checkCanDamage(monsterBkt, entity.getBukkitEntity(), false));
        double damage = EntityHelper.getAttrMap(monsterBkt).getOrDefault("damage", 1d);
        for (HitEntityInfo hitEntityInfo : toDamage) {
            Entity victimEntity = hitEntityInfo.getHitEntity().getBukkitEntity();
            // do not damage an entity with exception
            if (damageExceptions != null && damageExceptions.contains(victimEntity))
                continue;
            EntityHelper.handleDamage(monsterBkt, victimEntity,
                    damage, EntityHelper.DamageReason.DIRECT_DAMAGE);
            // record damage CD
            if (damageExceptions != null) {
                EntityHelper.damageCD(damageExceptions, victimEntity, damageCDTicks);
            }
        }
    }
    public static void handleBossDeath(terraria.util.BossHelper.BossType bossType,
                                       ArrayList<LivingEntity> bossParts, HashMap<UUID, BossTargetInfo> targetMap) {
        boolean isBossRush = EventAndTime.isBossRushActive();

        double[] healthInfo = terraria.entity.boss.BossHelper.getHealthInfo(bossParts, bossType);
        // boss death message
        Bukkit.broadcastMessage("§d§l" + bossType.msgName + " 被击败了.");
        if (isBossRush) {
            EventAndTime.bossRushSpawn(true);
        }
        else {
            // additional defeat messages
            switch (bossType) {
                case DESERT_SCOURGE:
                    Bukkit.broadcastMessage("§#7FFFD4地下沙漠的深处轰隆作响……");
                    break;
                case THE_HIVE_MIND:
                    Bukkit.broadcastMessage("§#00FFFF苍青色的光辉照耀着这片土地。");
                    break;
                case WALL_OF_FLESH:
                    Bukkit.broadcastMessage("§#7FFFD4沉沦之海在颤动……");
                    Bukkit.broadcastMessage("§#FFD700一颗明星从天堂中坠陨！");
                    break;
                case CRYOGEN:
                    Bukkit.broadcastMessage("§#87CEFA寒晶能量从冰之洞穴中迸发而出。");
                    break;
                case PLANTERA:
                    Bukkit.broadcastMessage("§#ADFF2F富含能量的植物物质已在地下形成。");
                    break;
                case CALAMITAS_CLONE:
                    Bukkit.broadcastMessage("§#4169E1海洋的深处传来震动。");
                    break;
                case ASTRUM_AUREUS:
                    Bukkit.broadcastMessage("§#FFD700星幻敌人得到了增强！");
                    break;
                case GOLEM:
                    Bukkit.broadcastMessage("§#00FF00一场瘟疫席卷了丛林。");
                    break;
                case ASTRUM_DEUS:
                    Bukkit.broadcastMessage("§#FFD700幻星的封印已破碎！你可以挖掘炫星矿了。");
                    break;
                case MOON_LORD:
                    Bukkit.broadcastMessage("§#FFA500亵渎之火猛烈燃烧！");
                    Bukkit.broadcastMessage("§#EE82EE宇宙的恐惧正注视着这一切……");
                    Bukkit.broadcastMessage("§#D3D3D3冷黯的能量散布至宇宙之间。");
                    Bukkit.broadcastMessage("§#00FFFF尖叫声回荡于地牢之中。");
                    break;
                case PROVIDENCE_THE_PROFANED_GODDESS:
                    Bukkit.broadcastMessage("§#FFA500灾厄造物已被血石洗礼。");
                    Bukkit.broadcastMessage("§#90EE90石化树皮正在丛林的淤泥中爆发。");
                    break;
                case POLTERGHAST:
                    Bukkit.broadcastMessage("§#4169E1深渊之灵受到了威胁。");
                    break;
                case THE_DEVOURER_OF_GODS:
                    Bukkit.broadcastMessage("§#FFA500收割之月散布着诡异的光芒。");
                    Bukkit.broadcastMessage("§#00FFFF寒霜之月散发着明洁的光辉。");
                    Bukkit.broadcastMessage("§#FFA500黑蚀之日蓄势待发。");
                    break;
                case YHARON_DRAGON_OF_REBIRTH:
                    Bukkit.broadcastMessage("§#FFD700远古巨龙的力量在洞穴中显现，交织着穿过岩石。");
                    break;
            }
            // calculate and broadcast boss aggression time & dmg dealt
            int ticksAggressionReq = calculateAndPrintAggro(targetMap);
            double dmgDealtReq = calculateAndPrintDamage(targetMap, healthInfo[1]); // healthInfo[1] is the actualBossHealth
            // print the final line to wrap up the summary
            Bukkit.broadcastMessage(SUMMARY_HEADER_FINAL);

            // loot
            if (bossType.hasTreasureBag) {
                ItemStack loopBag = ItemHelper.getItemFromDescription(bossType.msgName + "的 专家模式福袋");
                for (UUID plyID : targetMap.keySet()) {
                    Player ply = Bukkit.getPlayer(plyID);
                    if (ply == null)
                        continue;
                    boolean hasEnoughContribution = targetMap.get(plyID).damageDealt >= dmgDealtReq ||
                            targetMap.get(plyID).ticksAggression >= ticksAggressionReq;
                    if (hasEnoughContribution) {
                        ply.sendMessage("§a恭喜你击败了BOSS[§r" + bossType.msgName + "§a]!");
                        PlayerHelper.setDefeated(ply, bossType.msgName, true);
                        PlayerHelper.giveItem(ply, loopBag, true);
                    } else {
                        ply.sendMessage("§aBOSS " + bossType.msgName + " 已经被击败。很遗憾，您对BOSS战的贡献不足以获得一份战利品。");
                        ply.sendMessage("若要获得一份战利品，请在BOSS战中贡献更多的伤害或吸引更久的仇恨。");
                    }
                }
            }
            // other mechanics
            switch (bossType) {
                case KING_SLIME: {
                    if (EventAndTime.currentEvent == EventAndTime.Events.SLIME_RAIN)
                        EventAndTime.endEvent();
                    break;
                }
                case LUNATIC_CULTIST: {
                    CelestialPillar.handlePillarSpawn();
                    break;
                }
            }
        }
    }
    // Helper function to calculate and broadcast aggro
    private static int calculateAndPrintAggro(HashMap<UUID, BossTargetInfo> targetMap) {
        int bossFightDuration = targetMap.values().stream().mapToInt((targetInfo) -> targetInfo.ticksAggression).sum();
        int ticksAggressionReq = bossFightDuration / targetMap.size() / 2;

        List<Map.Entry<Double, String>> aggroList = buildUnorderedRankingList(targetMap, true);
        aggroList.add(new AbstractMap.SimpleImmutableEntry<>((double) bossFightDuration, "BOSS战总时长"));
        aggroList.add(new AbstractMap.SimpleImmutableEntry<>((double) ticksAggressionReq, "获得战利品所需仇恨时长"));

        sortAndBroadcastRanking(SUMMARY_HEADER_AGGRO, aggroList, bossFightDuration, true);

        return ticksAggressionReq;
    }
    // Helper function to calculate and broadcast damage
    private static double calculateAndPrintDamage(HashMap<UUID, BossTargetInfo> targetMap, double actualBossHealth) {
        double totalPlyDmg = targetMap.values().stream().mapToDouble((targetInfo) -> targetInfo.damageDealt).sum();
        actualBossHealth = Math.max(actualBossHealth, totalPlyDmg);

        List<Map.Entry<Double, String>> damageList = buildUnorderedRankingList(targetMap, false);
        double debuffDmg = actualBossHealth - totalPlyDmg;
        double dmgDealtReq = actualBossHealth / targetMap.size() / 5;
        if (debuffDmg < 1e-5) debuffDmg = 0;
        damageList.add(new AbstractMap.SimpleImmutableEntry<>(actualBossHealth, "总伤害"));
        damageList.add(new AbstractMap.SimpleImmutableEntry<>(debuffDmg, "减益等未记录伤害来源"));
        damageList.add(new AbstractMap.SimpleImmutableEntry<>(dmgDealtReq, "获得战利品所需最低伤害"));

        sortAndBroadcastRanking(SUMMARY_HEADER_DAMAGE, damageList, actualBossHealth, false);

        return dmgDealtReq;
    }
    // Helper function to build the ranking list; do not sort it yet as additional features may be added afterwards.
    private static List<Map.Entry<Double, String>> buildUnorderedRankingList(
            HashMap<UUID, BossTargetInfo> targetMap, boolean isAggro) {
        List<Map.Entry<Double, String>> rankingList = new ArrayList<>();
        for (Map.Entry<UUID, BossTargetInfo> entry : targetMap.entrySet()) {
            Player ply = Bukkit.getPlayer(entry.getKey());
            if (ply == null)
                continue;
            double plyValue = (isAggro ? entry.getValue().ticksAggression : entry.getValue().damageDealt);
            rankingList.add(new AbstractMap.SimpleImmutableEntry<>(plyValue, "[" + ply.getDisplayName() + "]"));
        }
        return rankingList;
    }
    // Helper function to broadcast the ranking
    private static void sortAndBroadcastRanking(String title, List<Map.Entry<Double, String>> rankingList,
                                                double totalValue, boolean isAggro) {
        Bukkit.broadcastMessage(title);


        String format = "%1$s%2$-8." + (isAggro ? "1" : "0") + "f(%3$5.1f%%) · %4$s";
        rankingList.sort(Comparator.comparingDouble(Map.Entry<Double, String>::getKey).reversed()); // Sort in descending order
        for (Map.Entry<Double, String> rankInfo : rankingList) {
            double value = rankInfo.getKey();
            String name = rankInfo.getValue(), colorCode = name.startsWith("[") ? "§r" : "§7";
            Bukkit.broadcastMessage(String.format(format,
                    colorCode, (isAggro ? value / 20 : value), value * 100 / totalValue, name));
        }
    }
    public static void sendBossMessages(int delay, int index, Entity sentBy, String prefix, String... messages) {
        // validations
        if (index < 0 || index >= messages.length)
            return;
        if (sentBy != null && ! sentBy.isValid())
            return;

        Bukkit.broadcastMessage(prefix + messages[index]);
        Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(),
                () -> sendBossMessages(delay, index + 1, sentBy, prefix, messages), delay);
    }
}
