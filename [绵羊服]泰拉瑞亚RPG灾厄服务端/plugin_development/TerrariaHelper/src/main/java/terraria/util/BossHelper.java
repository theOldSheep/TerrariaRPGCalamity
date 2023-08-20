package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import terraria.entity.boss.aquaticScourge.AquaticScourge;
import terraria.entity.boss.astrumAureus.AstrumAureus;
import terraria.entity.boss.astrumDeus.AstrumDeus;
import terraria.entity.boss.brimstoneElemental.BrimstoneElemental;
import terraria.entity.boss.calamitasClone.CalamitasClone;
import terraria.entity.boss.crabulon.Crabulon;
import terraria.entity.boss.cryogen.Cryogen;
import terraria.entity.boss.dukeFishron.DukeFishron;
import terraria.entity.boss.empressOfLight.EmpressOfLight;
import terraria.entity.boss.eyeOfCthulhu.EyeOfCthulhu;
import terraria.entity.boss.eaterOfWorld.EaterOfWorld;
import terraria.entity.boss.desertScourge.DesertScourge;
import terraria.entity.boss.golem.Golem;
import terraria.entity.boss.leviathanAndAnahita.Anahita;
import terraria.entity.boss.lunaticCultist.LunaticCultist;
import terraria.entity.boss.moonLord.MoonLord;
import terraria.entity.boss.plantera.Plantera;
import terraria.entity.boss.ravager.Ravager;
import terraria.entity.boss.ravager.RavagerNuke;
import terraria.entity.boss.skeletronPrime.SkeletronPrimeHead;
import terraria.entity.boss.theDestroyer.Destroyer;
import terraria.entity.boss.theHiveMind.TheHiveMind;
import terraria.entity.boss.skeletron.SkeletronHead;
import terraria.entity.boss.queenSlime.QueenSlime;
import terraria.entity.boss.thePlaguebringerGoliath.ThePlaguebringerGoliath;
import terraria.entity.boss.theSlimeGod.TheSlimeGod;
import terraria.entity.boss.kingSlime.KingSlime;
import terraria.entity.boss.theTwins.Retinazer;
import terraria.entity.boss.wallOfFlesh.WallOfFleshMouth;

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
        PLANTERA("世纪之花"), MOURNING_WOOD("哀木", false), PUMPKING("南瓜王", false),
        HEADLESS_HORSEMAN("无头骑士", false), EVERSCREAM("常绿尖叫怪", false),
        SANTA_NK1("圣诞坦克", false), ICE_QUEEN("冰雪女王", false),
        LEVIATHAN_AND_ANAHITA("阿娜希塔和利维坦"), ASTRUM_AUREUS("白金星舰"),
        GOLEM("石巨人"), THE_PLAGUEBRINGER_GOLIATH("瘟疫使者歌莉娅"), EMPRESS_OF_LIGHT("光之女皇"),
        DUKE_FISHRON("猪鲨公爵"), RAVAGER("毁灭魔像"), LUNATIC_CULTIST("拜月教邪教徒", false),
        ASTRUM_DEUS("星神游龙"), MOON_LORD("月球领主"), PROFANED_GUARDIANS("亵渎守卫"),
        THE_DRAGONFOLLY("痴愚金龙"), PROVIDENCE_THE_PROFANED_GODDESS("亵渎天神，普罗维登斯"),
        STORM_WEAVER("风暴编织者"), CEASELESS_VOID("无尽虚空"),
        SIGNUS_ENVOY_OF_THE_DEVOURER("神之使徒西格纳斯"), POLTERGHAST("噬魂幽花"), THE_OLD_DUKE("硫海遗爵"),
        THE_DEVOURER_OF_GODS("神明吞噬者"), YHARON_DRAGON_OF_REBIRTH("丛林龙，犽戎"),
        EXO_MECHS("星流巨械"), SUPREME_WITCH_CALAMITAS("至尊灾厄");
        public final String msgName;
        public final boolean hasTreasureBag;
        BossType(String name) {
            this(name, true);
        }
        BossType(String name, boolean hasTreasureBag) {
            this.msgName = name;
            this.hasTreasureBag = hasTreasureBag;
        }
        public void playSummonSound(Location loc) {
            switch (this) {
                case EMPRESS_OF_LIGHT:
                    loc.getWorld().playSound(loc, "entity.eol.summoned", 10, 1);
                    break;
                case RAVAGER:
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 10, 1);
                    break;
                default:
                    loc.getWorld().playSound(loc, "entity.enderdragon.growl", 10, 1);
            }
        }

        @Override
        public String toString() {
            return msgName;
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
            // pre-wall of flesh
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
            // pre-plantera
            case QUEEN_SLIME: {
                if (QueenSlime.canSpawn(target)) {
                    new QueenSlime(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case CRYOGEN: {
                if (Cryogen.canSpawn(target)) {
                    new Cryogen(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case THE_TWINS: {
                if (Retinazer.canSpawn(target)) {
                    new Retinazer(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case AQUATIC_SCOURGE: {
                if (AquaticScourge.canSpawn(target)) {
                    new AquaticScourge(target, new ArrayList<>(), 0);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case THE_DESTROYER: {
                if (Destroyer.canSpawn(target)) {
                    new Destroyer(target, new ArrayList<>(), 0);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case BRIMSTONE_ELEMENTAL: {
                if (BrimstoneElemental.canSpawn(target)) {
                    new BrimstoneElemental(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case SKELETRON_PRIME: {
                if (SkeletronPrimeHead.canSpawn(target)) {
                    new SkeletronPrimeHead(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case CALAMITAS_CLONE: {
                if (CalamitasClone.canSpawn(target)) {
                    new CalamitasClone(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case PLANTERA: {
                if (Plantera.canSpawn(target)) {
                    new Plantera(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            // pre-moon lord
            case LEVIATHAN_AND_ANAHITA: {
                if (Anahita.canSpawn(target)) {
                    new Anahita(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case ASTRUM_AUREUS: {
                if (AstrumAureus.canSpawn(target)) {
                    new AstrumAureus(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case GOLEM: {
                if (Golem.canSpawn(target)) {
                    new Golem(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case THE_PLAGUEBRINGER_GOLIATH: {
                if (ThePlaguebringerGoliath.canSpawn(target)) {
                    new ThePlaguebringerGoliath(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case EMPRESS_OF_LIGHT: {
                if (EmpressOfLight.canSpawn(target)) {
                    new EmpressOfLight(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case DUKE_FISHRON: {
                if (DukeFishron.canSpawn(target)) {
                    new DukeFishron(target, (Location) extraInfo);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case RAVAGER: {
                if (Ravager.canSpawn(target)) {
                    new Ravager(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case LUNATIC_CULTIST: {
                if (LunaticCultist.canSpawn(target)) {
                    new LunaticCultist(target, (Location) extraInfo);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case ASTRUM_DEUS: {
                if (AstrumDeus.canSpawn(target)) {
                    new AstrumDeus(target, new ArrayList<>(), (Location) extraInfo,0);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case MOON_LORD: {
                if (MoonLord.canSpawn(target)) {
                    new MoonLord(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            // pre-providence
        }
        if (spawnedSuccessfully) {
            bossType.playSummonSound(soundLocation);
            switch (bossType) {
                case MOON_LORD:
                case ASTRUM_DEUS:
                    break;
                default:
                    Bukkit.broadcastMessage("§d§l" + bossType.msgName + " 苏醒了！");
            }
        }
        return spawnedSuccessfully;
    }

    public static ArrayList<LivingEntity> getBossList(String boss) {
        return bossMap.get(boss);
    }
}
