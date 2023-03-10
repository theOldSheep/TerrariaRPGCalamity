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
    public enum BossType {
        SLIME_KING("史莱姆王"), DESERT_SCOURGE("荒漠灾虫"), EYE_OF_CTHULHU("克苏鲁之眼"), CRABULON("菌生蟹"),
        EATER_OF_WORLDS("世界吞噬者"), THE_HIVE_MIND("腐巢意志"), SKELETRON("骷髅王"),
        THE_SLIME_GOD("史莱姆之神"), WALL_OF_FLESH("血肉之墙"), QUEEN_SLIME("史莱姆皇后"), CRYOGEN("极地之灵"),
        THE_TWINS("双子魔眼"), AQUATIC_SCOURGE("渊海灾虫"), THE_DESTROYER("毁灭者"),
        BRIMSTONE_ELEMENTAL("硫磺火元素"), SKELETRON_PRIME("机械骷髅王"), CALAMITAS_CLONE("灾厄之眼"),
        PLANTERA("世纪之花"), LEVIATHAN_AND_ANAHITA("阿娜希塔和利维坦"), ASTRUM_AUREUS("白金星舰"),
        GOLEM("石巨人"), THE_PLAGUEBRINGER_GOLIATH("瘟疫使者歌莉娅"), EMPRESS_OF_LIGHT("光之女皇"),
        DUKE_FISHRON("猪鲨公爵"), RAVAGER("毁灭魔像"), LUNATIC_CULTIST("拜月教邪教徒"), ASTRUM_DEUS("星神游龙"),
        MOON_LORD("月球领主"), PROFANED_GUARDIANS("亵渎守卫"), THE_DRAGONFOLLY("痴愚金龙"),
        PROVIDENCE_THE_PROFANED_GODDESS("亵渎天神，普罗维登斯"), STORM_WEAVER("风暴编织者"), CEASELESS_VOID("无尽虚空"),
        SIGNUS_ENVOY_OF_THE_DEVOURER("神之使徒西格纳斯"), POLTERGHAST("噬魂幽花"), THE_OLD_DUKE("硫海遗爵"),
        THE_DEVOURER_OF_GODS("神明吞噬者"), YHARON_DRAGON_OF_REBIRTH("丛林龙，犽戎"),
        EXO_MECHS("星流巨械"), SUPREME_WITCH_CALAMITAS("至尊灾厄");
        public String name;
        BossType(String name) {
            this.name = name;
        }
    }
    public static void spawnBoss(Player target, BossType bossType) {
        switch (bossType) {
            case EYE_OF_CTHULHU:
        }
    }

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
