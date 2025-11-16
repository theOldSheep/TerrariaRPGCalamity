package terraria.entity.boss;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.BossBattleServer;
import net.minecraft.server.v1_12_R1.EntityLiving;
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
import terraria.entity.boss.postMoonLord.exoMechs.Apollo;
import terraria.entity.boss.postMoonLord.exoMechs.Ares;
import terraria.entity.boss.postMoonLord.exoMechs.Artemis;
import terraria.entity.boss.postMoonLord.exoMechs.Thanatos;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.EventAndTime;
import terraria.util.*;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

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
        // default - only account for the health of damage-taking parts, and the multiplier is 1 times
        Predicate<LivingEntity> criteria = (e) -> {
            MetadataValue mdv = MetadataHelper.getMetadata(e, MetadataHelper.MetadataName.DAMAGE_TAKER);
            return (mdv == null || mdv.value() == e);
        };
        Function<LivingEntity, Double> multiplier = (e) -> 1d;
        // tweak total health with special criteria/multiplier.
        // bosses with multiple health pool parts, damage taker metadata of one part will be removed on death.
        // thus the naive, damage-taker-based approach will be flawed.
        // note that *simple* worm-like bosses are handled by criteria.
        switch (bossType) {
            case ASTRUM_DEUS:
                // 50% health dealt as a whole, and 50% health dealt to the two parts -> 150% total
                criteria = (e) -> true;
                multiplier = (e) -> 1.5d / AstrumDeus.TOTAL_LENGTH;
                break;
            case EXO_MECHS:
                criteria = (e) -> true;
                multiplier = (e) -> {
                    net.minecraft.server.v1_12_R1.Entity eNms = ((CraftEntity) e).getHandle();
                    // Artemis/Apollo: calculated twice
                    if (eNms instanceof Artemis || eNms instanceof Apollo) {
                        return 1d / 2;
                    }
                    // Thanatos: calculated by its length
                    if (eNms instanceof Thanatos) {
                        return 1d / Thanatos.TOTAL_LENGTH;
                    }
                    // Ares: calculated 5 times
                    return 1d / 5;
                };
                break;
        }
        for (LivingEntity e : bossParts) {
            if (criteria.test(e)) {
                double multi = multiplier.apply(e);
                result[0] += e.getHealth() * multi;
                result[1] += e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * multi;
            }
        }
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
            MetadataValue metadataVal = MetadataHelper.getMetadata(bossParts.get(0), MetadataHelper.MetadataName.DYNAMIC_DAMAGE_REDUCTION);
            if (metadataVal != null) currDynamicDM = metadataVal.asDouble();
            else currDynamicDM = 0.9d; // has 10% DR at the beginning ("prior" assumption about player's DPS build).
            // dynamic DR only applies when the current time elapsed is within the expected time to defeat the boss
            if (ticksLived < targetTime && ticksLived > 1) {
                // update DM if health is not full/empty
                if (healthRatio > 0.000001 && healthRatio < 0.999999) {
                    // corrected DPS = expected DPS + (raw DPS - expected DPS) * x
                    // corrected DPS = expected DPS * (1-x) + raw DPS * x
                    // dynamic DM = corrected / raw = expected / raw * (1-x) + x
                    // raw DPS * dynamic DM = corrected DPS = (max health - curr health) / time elapsed
                    // raw DPS = (max health - curr health) / (time elapsed * dynamic DM)
                    // dynamic DM = (max health / expected time) / ((max health - curr health) / (time elapsed * DM)) * (1-x) + x
                    // dynamic DM = (max health * time elapsed * DM) / (expected time * (max health - curr health)) * (1-x) + x
                    // dynamic DM = DM/((max health - curr health) / max health) * (time elapsed / expected time) * (1-x) + x
                    // DM/(1 - health ratio) * (time elapsed / expected time) * (1-x) + x

                    double x = 0.5;
                    double timeElapsedRatio = ticksLived / targetTime;
                    double factor = timeElapsedRatio / (1 - healthRatio) * (1-x);
                    dynamicDamageMultiplier = currDynamicDM * factor + x;
                    // gradually change the damage multiplier
                    dynamicDamageMultiplier = dynamicDamageMultiplier * 0.1 + currDynamicDM * 0.9;

                    // dynamic damage multiplier can not increase player damage
                    dynamicDamageMultiplier = Math.min(dynamicDamageMultiplier, 1d);
                }
                // if full/empty health DM does not change
                else
                    dynamicDamageMultiplier = currDynamicDM;
            }
            // once exceeding targeted defeat time, dynamic DR multiplier linearly increases to 1
            else {
                dynamicDamageMultiplier = Math.min(currDynamicDM + 0.002, 1);
            }
        }
        for (LivingEntity bossPart : bossParts) {
            MetadataHelper.setMetadata(bossPart, MetadataHelper.MetadataName.DYNAMIC_DAMAGE_REDUCTION, dynamicDamageMultiplier);
        }
    }
    public static double getBossHealthMulti(int numPly) {
        // sqrt(x) + x - 1; first few players in addition to the first would contribute to a slightly higher multiplier
//        return Math.sqrt(numPly) + numPly - 1;
        return numPly;
    }
    public static HashMap<UUID, BossTargetInfo> setupBossTarget(Entity boss, String bossDefeatRequirement,
                                                          Player ply, boolean hasDistanceRestriction, boolean sendMsg, BossBattleServer bossbar) {
        HashMap<UUID, BossTargetInfo> targets = new HashMap<>();
        String team = MetadataHelper.getMetadata(ply, MetadataHelper.MetadataName.PLAYER_TEAM).asString();
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
                if (!MetadataHelper.getMetadata(currPly, MetadataHelper.MetadataName.PLAYER_TEAM).asString().equals(team)) continue;
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
        MetadataHelper.setMetadata(boss, MetadataHelper.MetadataName.BOSS_BAR, bossbar);
        MetadataHelper.setMetadata(boss, MetadataHelper.MetadataName.BOSS_TARGET_MAP, targets);
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
            if (distHor > 200) return ignoreDistance;
            return true;
        }
        return false;
    }
    // this is in place to help aim helpers
    public static void updateSpeedForAimHelper(Entity boss) {
        MetadataValue currVel = MetadataHelper.getMetadata(boss, MetadataHelper.MetadataName.ENTITY_CURRENT_VELOCITY);
        if (currVel != null)
            MetadataHelper.setMetadata(boss, MetadataHelper.MetadataName.ENTITY_LAST_VELOCITY, currVel.value());
        // calculate velocity
        net.minecraft.server.v1_12_R1.Entity bossNMS = ((CraftEntity) boss).getHandle();
        Vector velocity = new Vector(bossNMS.locX - bossNMS.lastX, bossNMS.locY - bossNMS.lastY, bossNMS.locZ - bossNMS.lastZ);

        MetadataHelper.setMetadata(boss, MetadataHelper.MetadataName.ENTITY_CURRENT_VELOCITY, velocity);
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
                (net.minecraft.server.v1_12_R1.Entity entity) -> DamageHelper.checkCanDamage(monsterBkt, entity.getBukkitEntity(), false));
        double damage = AttributeHelper.getAttrMap(monsterBkt).getOrDefault("damage", 1d);
        for (HitEntityInfo hitEntityInfo : toDamage) {
            Entity victimEntity = hitEntityInfo.getHitEntity().getBukkitEntity();
            // do not damage an entity with exception
            if (damageExceptions != null && damageExceptions.contains(victimEntity))
                continue;
            DamageHelper.handleDamage(monsterBkt, victimEntity,
                    damage, DamageHelper.DamageReason.CONTACT_DAMAGE);
            // record damage CD
            if (damageExceptions != null) {
                DamageHelper.damageCD(damageExceptions, victimEntity, damageCDTicks);
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
            // calculate and broadcast boss aggression time & dmg dealt
            int ticksAggressionReq = calculateAndPrintAggro(targetMap);
            double dmgDealtReq = calculateAndPrintDamage(targetMap, healthInfo[1]); // healthInfo[1] is the actualBossHealth
            // print the final line to wrap up the summary
            Bukkit.broadcastMessage(SUMMARY_HEADER_FINAL);

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

            // loot
            if (bossType.hasTreasureBag) {
                // init new NPC recipe message
                String npcSellsHint = null;
                ArrayList<NPCHelper.NPCType> npcTypes = new ArrayList<>();
                for (NPCHelper.NPCType npcType : NPCHelper.NPCType.values()) {
                    if (TerrariaHelper.NPCConfig.contains("shops." + npcType.displayName + "." + bossType.msgName))
                        npcTypes.add(npcType);
                }
                if (! npcTypes.isEmpty()) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("§e以下NPC开放新的交易：");
                    for (NPCHelper.NPCType npcType : npcTypes) {
                        builder.append(npcType.displayName);
                        builder.append(" ");
                    }
                    npcSellsHint = builder.toString();
                }
                // loot bag item
                ItemStack loopBag = ItemHelper.getItemFromDescription(bossType.msgName + "的 专家模式福袋");
                for (UUID plyID : targetMap.keySet()) {
                    Player ply = Bukkit.getPlayer(plyID);
                    if (ply == null)
                        continue;
                    boolean hasEnoughContribution = targetMap.get(plyID).damageDealt >= dmgDealtReq ||
                            targetMap.get(plyID).ticksAggression >= ticksAggressionReq;
                    if (hasEnoughContribution) {
                        ply.sendMessage("§a恭喜你击败了BOSS[§r" + bossType.msgName + "§a]!");
                        // on first defeat: record boss progress & hint new NPC trade
                        if (! PlayerHelper.hasDefeated(ply, bossType.msgName)) {
                            if (npcSellsHint != null)
                                ply.sendMessage(npcSellsHint);
                            PlayerHelper.setDefeated(ply, bossType.msgName, true);
                        }
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
