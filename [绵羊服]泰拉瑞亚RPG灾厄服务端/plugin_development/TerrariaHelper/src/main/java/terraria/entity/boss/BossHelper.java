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
import terraria.entity.projectile.HitEntityInfo;
import terraria.util.*;

import java.util.*;

public class BossHelper {
    public static double[] getHealthInfo(ArrayList<LivingEntity> bossParts) {
        double[] result = new double[] {0d, 0d};
        for (LivingEntity e : bossParts) {
            result[0] += e.getHealth();
            result[1] += e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        return result;
    }
    public static void updateBossBarAndDamageReduction(BossBattleServer bossbar, ArrayList<LivingEntity> bossParts, terraria.util.BossHelper.BossType type) {
        updateBossBarAndDamageReduction(bossbar, bossParts, bossParts.get(0).getTicksLived(), type);
    }
    public static void updateBossBarAndDamageReduction(BossBattleServer bossbar, ArrayList<LivingEntity> bossParts, int ticksLived, terraria.util.BossHelper.BossType type) {
        double[] healthInfo = getHealthInfo(bossParts);
        double healthRatio = healthInfo[0] / healthInfo[1];
        bossbar.setProgress((float) healthRatio);
        // dynamic Damage Reduction
        String bossName = type.msgName;
        double targetTime = TerrariaHelper.settingConfig.getInt("BossDefeatTime." + bossName, 900);
        double dynamicDR;
        if (ticksLived < targetTime) {
            if (healthRatio < 0.99)
                dynamicDR = Math.min((healthRatio / (1 - healthRatio)) * ticksLived / (targetTime - ticksLived), 1d);
            else
                dynamicDR = 1d;
        }
        // once exceeding targeted defeat time, dynamic DR multiplier linearly increases to 1 over maximum of 10 seconds.
        else {
            MetadataValue val = EntityHelper.getMetadata(bossParts.get(0), "dynamicDR");
            if (val != null) dynamicDR = val.asDouble();
            else dynamicDR = 1d;
            dynamicDR = Math.min(dynamicDR + 0.005, 1);
        }
        for (LivingEntity bossPart : bossParts)
            EntityHelper.setMetadata(bossPart, "dynamicDR", dynamicDR);
//        Bukkit.broadcastMessage("Dynamic DR: " + dynamicDR + ", time: " + ticksLived + "/" + targetTime + ", health: " + healthInfo[0] + "/" + healthInfo[1]);
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
    public static HashMap<Player, Double> setupBossTarget(Entity boss, String bossDefeatRequirement,
                                                          Player ply, boolean hasDistanceRestriction, BossBattleServer bossbar) {
        HashMap<Player, Double> targets = new HashMap<>();
        String team = EntityHelper.getMetadata(ply, "team").asString();
        for (Player currPly : boss.getWorld().getPlayers()) {
            if (!currPly.getName().equals(ply.getName())) {
                // unauthorized
                if (!PlayerHelper.isProperlyPlaying(currPly)) continue;
                // hasn't defeated prerequisite
                if (!PlayerHelper.hasDefeated(currPly, bossDefeatRequirement)) continue;
                // not in the same team
                if (!EntityHelper.getMetadata(currPly, "team").asString().equals(team)) continue;
                // too far away
                if (hasDistanceRestriction &&
                        GenericHelper.getHorizontalDistance(currPly.getLocation(), ply.getLocation()) > 192) continue;
            }
            bossbar.addPlayer(((CraftPlayer) currPly).getHandle());
            targets.put(currPly, 0d);
        }
        bossbar.setVisible(true);
        EntityHelper.setMetadata(boss, "bossbar", bossbar);
        EntityHelper.setMetadata(boss, "targets", targets);
        // print out targets of the boss
        StringBuilder msg = new StringBuilder();
        boolean firstAppend = true;
        msg.append("BOSS的挑战者为：");
        for (Player target : targets.keySet()) {
            if (!firstAppend)
                msg.append(", ");
            else
                firstAppend = false;
            msg.append(target.getName());
        }
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
            if (distHor > 100) return ignoreDistance;
            return true;
        }
        return false;
    }
    public static Player updateBossTarget(Player currentTarget, Entity boss, boolean ignoreDistance,
                                          WorldHelper.BiomeType biomeRequired, Collection<Player> availableTargets) {
        Player finalTarget = currentTarget;
        if (!checkBossTarget(currentTarget, boss, ignoreDistance, biomeRequired)) {
            // save all applicable targets
            ArrayList<Player> candidates = new ArrayList<>();
            for (Player ply : availableTargets) {
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
            EntityHelper.handleDamage(monsterBkt, hitEntityInfo.getHitEntity().getBukkitEntity(),
                    damage, "DirectDamage");
        }
    }
    public static void handleBossDeath(terraria.util.BossHelper.BossType bossType,
                                       ArrayList<LivingEntity> bossParts, HashMap<Player, Double> targetMap) {
        double[] healthInfo = terraria.entity.boss.BossHelper.getHealthInfo(bossParts);
        double dmgDealtReq = healthInfo[1] / targetMap.size() / 10;
        // boss death message
        Bukkit.broadcastMessage("§d§l" + bossType.msgName + " 被击败了.");
        switch (bossType) {
            case THE_HIVE_MIND:
                Bukkit.broadcastMessage("§#00FFFF苍青色的光辉照耀着这片土地。");
                break;
        }
        // calculate and broadcast damage dealt
        {
            double totalPlyDmg = 0;
            TreeSet<Map.Entry<Double, String>> damageList = new TreeSet<>(Comparator.comparingDouble(Map.Entry::getKey));
            for (Map.Entry<Player, Double> entry : targetMap.entrySet()) {
                damageList.add(new AbstractMap.SimpleImmutableEntry<>(entry.getValue(),
                        "[" + entry.getKey().getDisplayName() + "]") );
                totalPlyDmg += entry.getValue();
            }
            double debuffDmg = healthInfo[1] - totalPlyDmg;
            if (debuffDmg < 1e-5) debuffDmg = 0;
            damageList.add(new AbstractMap.SimpleImmutableEntry<>(debuffDmg, "§7减益等非直接伤害来源") );
            damageList.add(new AbstractMap.SimpleImmutableEntry<>(dmgDealtReq, "§7获得战利品所需最低伤害") );
            // send damage dealt
            Bukkit.broadcastMessage("————————伤害信息————————");
            for (Iterator<Map.Entry<Double, String>> it = damageList.descendingIterator(); it.hasNext(); ) {
                Map.Entry<Double, String> damageInfo = it.next();
                // player, damage, percent damage dealt
                Bukkit.broadcastMessage(String.format("%1$s 伤害：%2$.0f (占比%3$.1f%%)",
                        damageInfo.getValue(), damageInfo.getKey(), damageInfo.getKey() * 100 / healthInfo[1]));
            }
            Bukkit.broadcastMessage("————————伤害信息————————");
        }
        // send out loot
        {
            ItemStack loopBag = ItemHelper.getItemFromDescription(bossType.msgName + "的 专家模式福袋");
            for (Player ply : targetMap.keySet()) {
                if (targetMap.get(ply) >= dmgDealtReq) {
                    ply.sendMessage("§a恭喜你击败了BOSS[§r" + bossType.msgName + "§a]!");
                    PlayerHelper.setDefeated(ply, bossType.msgName, true);
                    PlayerHelper.giveItem(ply, loopBag, true);
                } else {
                    ply.sendMessage("§aBOSS " + bossType.msgName + " 已经被击败。很遗憾，您的输出不足以获得一份战利品。");
                }
            }
        }
    }
}
