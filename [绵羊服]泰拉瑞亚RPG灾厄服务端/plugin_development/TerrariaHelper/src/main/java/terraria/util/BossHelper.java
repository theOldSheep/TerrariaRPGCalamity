package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import terraria.entity.boss.cbl.Crabulon;
import terraria.entity.boss.eoc.EyeOfCthulhu;
import terraria.entity.boss.eow.EaterOfWorld;
import terraria.entity.boss.hmzc.DesertScourge;
import terraria.entity.boss.hvm.TheHiveMind;
import terraria.entity.boss.klw.SkeletronHead;
import terraria.entity.boss.slms.TheSlimeGod;
import terraria.entity.boss.slmw.KingSlime;
import terraria.entity.boss.wof.WallOfFleshMouth;

import java.util.ArrayList;
import java.util.HashMap;

public class BossHelper {
    public static HashMap<String, ArrayList<LivingEntity>> bossMap = new HashMap<>();
    public enum BossType {
        KING_SLIME("史莱姆王"), DESERT_SCOURGE("荒漠灾虫"), EYE_OF_CTHULHU("克苏鲁之眼"), CRABULON("菌生蟹"),
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
        public String msgName;
        BossType(String name) {
            this.msgName = name;
        }
        public void playSummonSound(Location loc) {
            switch (this.name()) {
                default:
                    loc.getWorld().playSound(loc, "entity.enderdragon.growl", 10, 1);
            }
        }
    }
    public static boolean spawnBoss(Player target, BossType bossType) {
        return spawnBoss(target, bossType, null);
    }
    public static boolean spawnBoss(Player target, BossType bossType, Object extraInfo) {
        // no duplication!
        if (bossMap.containsKey(bossType.msgName)) return false;
        boolean spawnedSuccessfully = false;
        Location soundLocation = null;
        if (target != null)
            soundLocation = target.getLocation();
        switch (bossType) {
            case KING_SLIME: {
                if (KingSlime.canSpawn(target)) {
                    new KingSlime(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case DESERT_SCOURGE: {
                if (DesertScourge.canSpawn(target)) {
                    new DesertScourge(target, new ArrayList<>(), 0);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case EYE_OF_CTHULHU: {
                if (EyeOfCthulhu.canSpawn(target)) {
                    new EyeOfCthulhu(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case CRABULON: {
                if (Crabulon.canSpawn(target)) {
                    new Crabulon(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case EATER_OF_WORLDS: {
                if (EaterOfWorld.canSpawn(target)) {
                    new EaterOfWorld(target, new ArrayList<>(), 0);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case THE_HIVE_MIND: {
                if (TheHiveMind.canSpawn(target)) {
                    new TheHiveMind(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case SKELETRON: {
                if (SkeletronHead.canSpawn(target)) {
                    new SkeletronHead(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case THE_SLIME_GOD: {
                if (TheSlimeGod.canSpawn(target)) {
                    new TheSlimeGod(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case WALL_OF_FLESH: {
                soundLocation = (Location) extraInfo;
                if (WallOfFleshMouth.canSpawn(target)) {
                    new WallOfFleshMouth(soundLocation);
                    spawnedSuccessfully = true;
                }
                break;
            }
        }
        if (spawnedSuccessfully) {
            bossType.playSummonSound(soundLocation);
            Bukkit.broadcastMessage("§d§l" + bossType.msgName + " 苏醒了！");
        }
        return spawnedSuccessfully;
    }

    public static ArrayList<LivingEntity> getBossList(String boss) {
        return bossMap.get(boss);
    }
}
