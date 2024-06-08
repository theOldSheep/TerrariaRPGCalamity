package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import terraria.entity.boss.hardMode.aquaticScourge.AquaticScourge;
import terraria.entity.boss.hardMode.astrumAureus.AstrumAureus;
import terraria.entity.boss.hardMode.astrumDeus.AstrumDeus;
import terraria.entity.boss.hardMode.brimstoneElemental.BrimstoneElemental;
import terraria.entity.boss.hardMode.calamitasClone.CalamitasClone;
import terraria.entity.boss.postMoonLord.ceaselessVoid.CeaselessVoid;
import terraria.entity.boss.postMoonLord.devourerOfGods.DevourerOfGods;
import terraria.entity.boss.postMoonLord.exoMechs.Draedon;
import terraria.entity.boss.postMoonLord.polterghast.Polterghast;
import terraria.entity.boss.postMoonLord.signus.Signus;
import terraria.entity.boss.postMoonLord.theOldDuke.TheOldDuke;
import terraria.entity.boss.preHardMode.crabulon.Crabulon;
import terraria.entity.boss.hardMode.cryogen.Cryogen;
import terraria.entity.boss.postMoonLord.dragonFolly.DragonFolly;
import terraria.entity.boss.hardMode.dukeFishron.DukeFishron;
import terraria.entity.boss.hardMode.empressOfLight.EmpressOfLight;
import terraria.entity.boss.preHardMode.eyeOfCthulhu.EyeOfCthulhu;
import terraria.entity.boss.preHardMode.eaterOfWorld.EaterOfWorld;
import terraria.entity.boss.preHardMode.desertScourge.DesertScourge;
import terraria.entity.boss.hardMode.golem.Golem;
import terraria.entity.boss.hardMode.leviathanAndAnahita.Anahita;
import terraria.entity.boss.hardMode.lunaticCultist.LunaticCultist;
import terraria.entity.boss.hardMode.moonLord.MoonLord;
import terraria.entity.boss.hardMode.plantera.Plantera;
import terraria.entity.boss.postMoonLord.profanedGuardians.GuardianCommander;
import terraria.entity.boss.postMoonLord.providence.Providence;
import terraria.entity.boss.hardMode.ravager.Ravager;
import terraria.entity.boss.hardMode.skeletronPrime.SkeletronPrimeHead;
import terraria.entity.boss.postMoonLord.stormWeaver.StormWeaver;
import terraria.entity.boss.hardMode.theDestroyer.Destroyer;
import terraria.entity.boss.preHardMode.theHiveMind.TheHiveMind;
import terraria.entity.boss.preHardMode.skeletron.SkeletronHead;
import terraria.entity.boss.hardMode.queenSlime.QueenSlime;
import terraria.entity.boss.hardMode.thePlaguebringerGoliath.ThePlaguebringerGoliath;
import terraria.entity.boss.preHardMode.theSlimeGod.TheSlimeGod;
import terraria.entity.boss.preHardMode.kingSlime.KingSlime;
import terraria.entity.boss.hardMode.theTwins.Retinazer;
import terraria.entity.boss.preHardMode.wallOfFlesh.WallOfFleshMouth;
import terraria.entity.boss.postMoonLord.yharon.Yharon;

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
                    loc.getWorld().playSound(loc, "entity.eol.summoned", SoundCategory.HOSTILE, 10, 1);
                    break;
                case RAVAGER:
                    loc.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, SoundCategory.HOSTILE, 10, 1);
                    break;
                case STORM_WEAVER:
                    loc.getWorld().playSound(loc, "entity.storm_weaver.summoned", SoundCategory.HOSTILE, 10, 1);
                    break;
                case CEASELESS_VOID:
                    loc.getWorld().playSound(loc, "entity.ceaseless_void.summoned", SoundCategory.HOSTILE, 10, 1);
                    break;
                case SIGNUS_ENVOY_OF_THE_DEVOURER:
                    loc.getWorld().playSound(loc, "entity.signus.summoned", SoundCategory.HOSTILE, 10, 1);
                    break;
                default:
                    loc.getWorld().playSound(loc, "entity.enderdragon.growl", SoundCategory.HOSTILE, 10, 1);
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
            // pre-servants of gods
            case THE_DRAGONFOLLY: {
                if (DragonFolly.canSpawn(target)) {
                    new DragonFolly(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case PROFANED_GUARDIANS: {
                if (GuardianCommander.canSpawn(target)) {
                    new GuardianCommander(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case PROVIDENCE_THE_PROFANED_GODDESS: {
                if (Providence.canSpawn(target)) {
                    new Providence(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case STORM_WEAVER: {
                if (StormWeaver.canSpawn(target)) {
                    new StormWeaver(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case CEASELESS_VOID: {
                if (CeaselessVoid.canSpawn(target)) {
                    new CeaselessVoid(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case SIGNUS_ENVOY_OF_THE_DEVOURER: {
                if (Signus.canSpawn(target)) {
                    new Signus(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            // post-servants of gods
            case POLTERGHAST: {
                if (Polterghast.canSpawn(target)) {
                    new Polterghast(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case THE_OLD_DUKE: {
                if (TheOldDuke.canSpawn(target)) {
                    new TheOldDuke(target, (Location) extraInfo);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case THE_DEVOURER_OF_GODS: {
                if (DevourerOfGods.canSpawn(target)) {
                    new DevourerOfGods(target, new ArrayList<>(), 0);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case YHARON_DRAGON_OF_REBIRTH: {
                if (Yharon.canSpawn(target)) {
                    new Yharon(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
            case EXO_MECHS: {
                if (Draedon.canSpawn(target)) {
                    new Draedon(target);
                    spawnedSuccessfully = true;
                }
                break;
            }
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
