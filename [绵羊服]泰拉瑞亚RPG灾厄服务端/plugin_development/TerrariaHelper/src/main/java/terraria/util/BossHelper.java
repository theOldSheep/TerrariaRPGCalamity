package terraria.util;

import net.minecraft.server.v1_12_R1.BossBattleServer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;

public class BossHelper {
    public static HashMap<String, ArrayList<Entity>> bossMap = new HashMap<>();

    public static void threadBossBar() {

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
        Bukkit.broadcastMessage("BOSS的挑战者为：" + targets.keySet());
        return targets;
    }
    public static boolean checkBossTarget(Entity target, Entity boss, boolean ignoreDistance) {
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            if (!PlayerHelper.isProperlyPlaying(targetPlayer)) return false;
            if (targetPlayer.getGameMode() != GameMode.SURVIVAL) return false;
            if (targetPlayer.getWorld() != boss.getWorld()) return false;
            double distHor = GenericHelper.getHorizontalDistance(targetPlayer.getLocation(), boss.getLocation());
            if (distHor > 100) return ignoreDistance;
            return true;
        }
        return false;
    }
    public static ArrayList<Entity> getBossList(String boss) {
        return bossMap.get(boss);
    }
}
