package terraria.entity.boss;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.BossBattleServer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.aquaticScourge.AquaticScourge;
import terraria.entity.boss.astrumDeus.AstrumDeus;
import terraria.entity.boss.desertScourge.DesertNuisance;
import terraria.entity.boss.desertScourge.DesertScourge;
import terraria.entity.boss.theDestroyer.Destroyer;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.EventAndTime;
import terraria.util.*;

import javax.annotation.Nullable;
import java.util.*;

public class BossHelper {
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
            result[0] += e.getHealth();
            result[1] += e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        // tweak total health so that health for bosses that share health pool are calculated correctly.
        double multiplier;
        switch (bossType) {
            case DESERT_SCOURGE:
                 multiplier = 1d / DesertScourge.TOTAL_LENGTH;
                break;
            case WALL_OF_FLESH:
                multiplier = 1d / 3;
                break;
            case AQUATIC_SCOURGE:
                multiplier = 1d / AquaticScourge.TOTAL_LENGTH;
                break;
            case THE_DESTROYER:
                multiplier = 1d / Destroyer.TOTAL_LENGTH;
                break;
            case ASTRUM_DEUS:
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
                                                          Player ply, boolean hasDistanceRestriction, BossBattleServer bossbar) {
        HashMap<UUID, BossTargetInfo> targets = new HashMap<>();
        String team = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM).asString();
        // print out targets of the boss
        StringBuilder msg = new StringBuilder();
        boolean firstAppend = true;
        msg.append("BOSS的挑战者为：");
        // setup targets of the boss
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
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                () -> Bukkit.broadcastMessage(msg.toString()), 1);
        return targets;
    }
    public static boolean checkBossTarget(Entity target, Entity boss, boolean ignoreDistance, WorldHelper.BiomeType biomeRequired) {
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            if (!PlayerHelper.isProperlyPlaying(targetPlayer)) return false;
            if (targetPlayer.getWorld() != boss.getWorld()) return false;
            if (biomeRequired != null && WorldHelper.BiomeType.getBiome(targetPlayer, false) != biomeRequired) return false;
            double distHor = GenericHelper.getHorizontalDistance(targetPlayer.getLocation(), boss.getLocation());
            if (distHor > 165) return ignoreDistance;
            return true;
        }
        return false;
    }
    // generally, this function also handles misc aspects like cached velocity
    public static Player updateBossTarget(Player currentTarget, Entity boss, boolean ignoreDistance,
                                          WorldHelper.BiomeType biomeRequired, Collection<UUID> availableTargets) {
        // update saved velocity
        {
            MetadataValue currVel = EntityHelper.getMetadata(boss, EntityHelper.MetadataName.ENTITY_CURRENT_VELOCITY);
            if (currVel != null)
                EntityHelper.setMetadata(boss, EntityHelper.MetadataName.ENTITY_LAST_VELOCITY, currVel.value());
            EntityHelper.setMetadata(boss, EntityHelper.MetadataName.ENTITY_CURRENT_VELOCITY, boss.getVelocity());
        }
        // update target
        Player finalTarget = currentTarget;
        if (!checkBossTarget(currentTarget, boss, ignoreDistance, biomeRequired)) {
            // save all applicable targets
            ArrayList<Player> candidates = new ArrayList<>();
            for (UUID plyID : availableTargets) {
                Player ply = Bukkit.getPlayer(plyID);
                if (ply == null)
                    continue;
                if ( checkBossTarget(ply, boss, ignoreDistance, biomeRequired) )
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
        double[] healthInfo = terraria.entity.boss.BossHelper.getHealthInfo(bossParts, bossType);
        // boss death message
        Bukkit.broadcastMessage("§d§l" + bossType.msgName + " 被击败了.");
        switch (bossType) {
            case DESERT_SCOURGE:
                Bukkit.broadcastMessage("§#7FFFD4§l地下沙漠的深处轰隆作响……");
                break;
            case THE_HIVE_MIND:
                Bukkit.broadcastMessage("§#00FFFF§l苍青色的光辉照耀着这片土地。");
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
        // calculate and broadcast boss aggression time
        int ticksAggressionReq;
        {
            // calculate total boss fight duration and required aggression duration
            int bossFightDuration = 0;
            for (BossTargetInfo fightInfo : targetMap.values()) {
                bossFightDuration += fightInfo.ticksAggression;
            }
            ticksAggressionReq = bossFightDuration / targetMap.size() / 2;
            // record aggro duration for each player
            TreeSet<Map.Entry<Integer, String>> aggroList = new TreeSet<>(Comparator.comparingDouble(Map.Entry::getKey));
            for (Map.Entry<UUID, BossTargetInfo> entry : targetMap.entrySet()) {
                Player ply = Bukkit.getPlayer(entry.getKey());
                if (ply == null)
                    continue;
                int plyDmg = entry.getValue().ticksAggression;
                aggroList.add(new AbstractMap.SimpleImmutableEntry<>(plyDmg,
                        "[" + ply.getDisplayName() + "]") );
            }
            aggroList.add(new AbstractMap.SimpleImmutableEntry<>(bossFightDuration, "§7BOSS战总时长") );
            aggroList.add(new AbstractMap.SimpleImmutableEntry<>(ticksAggressionReq, "§7获得战利品所需仇恨时长") );
            // send damage dealt
            Bukkit.broadcastMessage("————————BOSS仇恨时长————————");
            for (Iterator<Map.Entry<Integer, String>> it = aggroList.descendingIterator(); it.hasNext(); ) {
                Map.Entry<Integer, String> aggroInfo = it.next();
                // player, damage, percent damage dealt
                Bukkit.broadcastMessage(String.format("%1$s 时长：%2$.1f秒 (占比%3$.1f%%)",
                        aggroInfo.getValue(), aggroInfo.getKey() / 20d,
                        (double) aggroInfo.getKey() * 100 / bossFightDuration));
            }
        }
        // calculate and broadcast damage dealt
        double dmgDealtReq;
        {
            double totalPlyDmg = 0;
            TreeSet<Map.Entry<Double, String>> damageList = new TreeSet<>(Comparator.comparingDouble(Map.Entry::getKey));
            for (Map.Entry<UUID, BossTargetInfo> entry : targetMap.entrySet()) {
                Player ply = Bukkit.getPlayer(entry.getKey());
                if (ply == null)
                    continue;
                double plyDmg = entry.getValue().damageDealt;
                damageList.add(new AbstractMap.SimpleImmutableEntry<>(plyDmg,
                        "[" + ply.getDisplayName() + "]") );
                totalPlyDmg += plyDmg;
            }
            // prevent having player damage over 100%
            double actualBossHealth = Math.max(healthInfo[1], totalPlyDmg);
            dmgDealtReq = actualBossHealth / targetMap.size() / 5;
            double debuffDmg = actualBossHealth - totalPlyDmg;
            if (debuffDmg < 1e-5) debuffDmg = 0;
            damageList.add(new AbstractMap.SimpleImmutableEntry<>(debuffDmg, "§7减益等未记录伤害来源") );
            damageList.add(new AbstractMap.SimpleImmutableEntry<>(dmgDealtReq, "§7获得战利品所需最低伤害") );
            // send damage dealt
            Bukkit.broadcastMessage("—————————伤害信息—————————");
            for (Iterator<Map.Entry<Double, String>> it = damageList.descendingIterator(); it.hasNext(); ) {
                Map.Entry<Double, String> damageInfo = it.next();
                // player, damage, percent damage dealt
                Bukkit.broadcastMessage(String.format("%1$s 伤害：%2$.0f (占比%3$.1f%%)",
                        damageInfo.getValue(), damageInfo.getKey(), damageInfo.getKey() * 100 / actualBossHealth));
            }
            Bukkit.broadcastMessage("————————————————————————");
        }
        // send out loot
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
            }
        }

    }
}
