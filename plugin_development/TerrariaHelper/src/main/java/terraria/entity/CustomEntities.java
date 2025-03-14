package terraria.entity;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.entity.EntityType;
import terraria.TerrariaHelper;
import terraria.entity.boss.hardMode.aquaticScourge.AquaticScourge;
import terraria.entity.boss.hardMode.astrumAureus.AstrumAureus;
import terraria.entity.boss.hardMode.astrumAureus.AureusSpawn;
import terraria.entity.boss.hardMode.astrumDeus.AstrumDeus;
import terraria.entity.boss.hardMode.brimstoneElemental.BrimstoneElemental;
import terraria.entity.boss.hardMode.calamitasClone.CalamitasClone;
import terraria.entity.boss.hardMode.calamitasClone.Cataclysm;
import terraria.entity.boss.hardMode.calamitasClone.Catastrophe;
import terraria.entity.boss.postMoonLord.supremeCalamitas.Sepulcher;
import terraria.entity.boss.postMoonLord.supremeCalamitas.SupremeCalamitas;
import terraria.entity.boss.postMoonLord.ceaselessVoid.CeaselessVoid;
import terraria.entity.boss.postMoonLord.ceaselessVoid.DarkEnergy;
import terraria.entity.boss.postMoonLord.devourerOfGods.DevourerOfGods;
import terraria.entity.boss.postMoonLord.devourerOfGods.GodSlayerPortal;
import terraria.entity.boss.postMoonLord.exoMechs.*;
import terraria.entity.boss.postMoonLord.polterghast.Polterghast;
import terraria.entity.boss.postMoonLord.polterghast.PolterghastClone;
import terraria.entity.boss.postMoonLord.signus.Signus;
import terraria.entity.boss.postMoonLord.supremeCalamitas.SupremeCalamitasBrother;
import terraria.entity.boss.postMoonLord.theOldDuke.TheOldDuke;
import terraria.entity.boss.postMoonLord.yharon.Infernado;
import terraria.entity.boss.preHardMode.crabulon.Crabulon;
import terraria.entity.boss.hardMode.cryogen.Cryogen;
import terraria.entity.boss.hardMode.cryogen.CryogenShield;
import terraria.entity.boss.postMoonLord.dragonFolly.DragonFolly;
import terraria.entity.boss.hardMode.dukeFishron.DukeFishron;
import terraria.entity.boss.hardMode.dukeFishron.Sharknado;
import terraria.entity.boss.hardMode.dukeFishron.Sharkron;
import terraria.entity.boss.hardMode.dukeFishron.WaterBlob;
import terraria.entity.boss.hardMode.empressOfLight.EmpressOfLight;
import terraria.entity.boss.event.celestialPillar.CelestialPillar;
import terraria.entity.boss.event.everscream.Everscream;
import terraria.entity.boss.event.headlessHorseman.HeadlessHorseman;
import terraria.entity.boss.event.iceQueen.IceQueen;
import terraria.entity.boss.event.mourningWood.MourningWood;
import terraria.entity.boss.event.pumpking.PumpkingHand;
import terraria.entity.boss.event.pumpking.PumpkingHead;
import terraria.entity.boss.event.pumpking.PumpkingScythe;
import terraria.entity.boss.event.santaNK1.SantaNK1;
import terraria.entity.boss.preHardMode.eyeOfCthulhu.EyeOfCthulhu;
import terraria.entity.boss.preHardMode.eaterOfWorld.EaterOfWorld;
import terraria.entity.boss.preHardMode.desertScourge.DesertNuisance;
import terraria.entity.boss.preHardMode.desertScourge.DesertScourge;
import terraria.entity.boss.hardMode.golem.Golem;
import terraria.entity.boss.hardMode.golem.GolemFist;
import terraria.entity.boss.hardMode.golem.GolemFoot;
import terraria.entity.boss.hardMode.golem.GolemHead;
import terraria.entity.boss.hardMode.leviathanAndAnahita.Anahita;
import terraria.entity.boss.hardMode.leviathanAndAnahita.Leviathan;
import terraria.entity.boss.hardMode.lunaticCultist.*;
import terraria.entity.boss.hardMode.moonLord.*;
import terraria.entity.boss.hardMode.plantera.Plantera;
import terraria.entity.boss.hardMode.plantera.PlanteraTentacle;
import terraria.entity.boss.postMoonLord.profanedGuardians.GuardianAttacker;
import terraria.entity.boss.postMoonLord.profanedGuardians.GuardianCommander;
import terraria.entity.boss.postMoonLord.profanedGuardians.GuardianDefender;
import terraria.entity.boss.postMoonLord.profanedGuardians.GuardianRock;
import terraria.entity.boss.postMoonLord.providence.Providence;
import terraria.entity.boss.hardMode.ravager.*;
import terraria.entity.boss.hardMode.skeletronPrime.SkeletronPrimeHand;
import terraria.entity.boss.hardMode.skeletronPrime.SkeletronPrimeHead;
import terraria.entity.boss.postMoonLord.stormWeaver.StormWeaver;
import terraria.entity.boss.hardMode.theDestroyer.Destroyer;
import terraria.entity.boss.preHardMode.theHiveMind.DarkHeart;
import terraria.entity.boss.preHardMode.theHiveMind.HiveBlob;
import terraria.entity.boss.preHardMode.theHiveMind.TheHiveMind;
import terraria.entity.boss.preHardMode.skeletron.SkeletronHand;
import terraria.entity.boss.preHardMode.skeletron.SkeletronHead;
import terraria.entity.boss.hardMode.queenSlime.QueenSlime;
import terraria.entity.boss.hardMode.thePlaguebringerGoliath.PlagueHomingMissile;
import terraria.entity.boss.hardMode.thePlaguebringerGoliath.PlagueMine;
import terraria.entity.boss.hardMode.thePlaguebringerGoliath.ThePlaguebringerGoliath;
import terraria.entity.boss.preHardMode.theSlimeGod.CrimulanSlime;
import terraria.entity.boss.preHardMode.theSlimeGod.EbonianSlime;
import terraria.entity.boss.preHardMode.theSlimeGod.TheSlimeGod;
import terraria.entity.boss.preHardMode.kingSlime.CrownJewel;
import terraria.entity.boss.preHardMode.kingSlime.KingSlime;
import terraria.entity.boss.hardMode.theTwins.Retinazer;
import terraria.entity.boss.hardMode.theTwins.Spazmatism;
import terraria.entity.boss.preHardMode.wallOfFlesh.WallOfFleshEye;
import terraria.entity.boss.preHardMode.wallOfFlesh.WallOfFleshMouth;
import terraria.entity.boss.postMoonLord.yharon.Yharon;
import terraria.entity.boss.preHardMode.wallOfFlesh.WallOfFleshWall;
import terraria.entity.minion.MinionCaveSpider;
import terraria.entity.minion.MinionHusk;
import terraria.entity.minion.MinionSlime;
import terraria.entity.monster.*;
import terraria.entity.npc.*;
import terraria.entity.others.Critter;
import terraria.entity.others.TerrariaDroppedItem;
import terraria.entity.others.Mount;
import terraria.entity.others.PlayerTornado;
import terraria.util.NMSUtils;

/*
class from https://www.spigotmc.org/threads/nms-tutorials-2-custom-nms-entities-1-11.205192/
 */
public enum CustomEntities {

    BOSS_ANHT               ("Anahita",                23,  EntityType.HUSK,            EntityZombieHusk.class,      Anahita.class),
    BOSS_ANHT_LVT           ("Leviathan",              55,  EntityType.SLIME,           EntitySlime.class,           Leviathan.class),
    BOSS_AQTS               ("AquaticScourge",         55,  EntityType.SLIME,           EntitySlime.class,           AquaticScourge.class),
    BOSS_ASTRUM_DEUS        ("AstrumDeus",             55,  EntityType.SLIME,           EntitySlime.class,           AstrumDeus.class),
    BOSS_AUREUS             ("AstrumAureus",           55,  EntityType.SLIME,           EntitySlime.class,           AstrumAureus.class),
    BOSS_AUREUS_SPAWN       ("AureusSpawn",            55,  EntityType.SLIME,           EntitySlime.class,           AureusSpawn.class),
    BOSS_BELM               ("BrimstoneElemental",     55,  EntityType.SLIME,           EntitySlime.class,           BrimstoneElemental.class),
    BOSS_CBL                ("Crabulon",               55,  EntityType.SLIME,           EntitySlime.class,           Crabulon.class),
    BOSS_CLMT               ("Calamitas",              55,  EntityType.SLIME,           EntitySlime.class,           CalamitasClone.class),
    BOSS_CLMT_CTCLSM        ("Cataclysm",              55,  EntityType.SLIME,           EntitySlime.class,           Cataclysm.class),
    BOSS_CLMT_CTSTPH        ("Catastrophe",            55,  EntityType.SLIME,           EntitySlime.class,           Catastrophe.class),
    BOSS_CYG                ("Cryogen",                55,  EntityType.SLIME,           EntitySlime.class,           Cryogen.class),
    BOSS_CYG_SHIELD         ("CryogenShield",          55,  EntityType.SLIME,           EntitySlime.class,           CryogenShield.class),
    BOSS_CSLS_VOID          ("CeaselessVoid",          55,  EntityType.SLIME,           EntitySlime.class,           CeaselessVoid.class),
    BOSS_CSLS_VOID_DE       ("CeaselessVoidDarkEnergy",55,  EntityType.SLIME,           EntitySlime.class,           DarkEnergy.class),
    BOSS_CULTIST            ("LunaticCultist",         54,  EntityType.ZOMBIE,          EntityZombie.class,          LunaticCultist.class),
    BOSS_CULTIST_AD         ("LunaticAncientDoom",     55,  EntityType.SLIME,           EntitySlime.class,           LunaticAncientDoom.class),
    BOSS_CULTIST_AL         ("LunaticAncientLight",    55,  EntityType.SLIME,           EntitySlime.class,           LunaticAncientLight.class),
    BOSS_CULTIST_CL         ("LunaticCultistClone",    23,  EntityType.HUSK,            EntityZombieHusk.class,      LunaticCultistClone.class),
    BOSS_CULTIST_MS         ("LunaticIceMist",         55,  EntityType.SLIME,           EntitySlime.class,           LunaticIceMist.class),
    BOSS_CULTIST_LO         ("LunaticLightningOrb",    55,  EntityType.SLIME,           EntitySlime.class,           LunaticLightningOrb.class),
    BOSS_CULTIST_PD         ("PhantomDragon",          55,  EntityType.SLIME,           EntitySlime.class,           PhantomDragon.class),
    BOSS_DEVR_OF_GODS       ("DevourerOfGods",         55,  EntityType.SLIME,           EntitySlime.class,           DevourerOfGods.class),
    BOSS_DEVR_OF_GODS_PORTAL("DevourerOfGodsPortal",   55,  EntityType.SLIME,           EntitySlime.class,           GodSlayerPortal.class),
    BOSS_DKFSR              ("DukeFishron",            55,  EntityType.SLIME,           EntitySlime.class,           DukeFishron.class),
    BOSS_DKFSR_SKND         ("Sharknado",              55,  EntityType.SLIME,           EntitySlime.class,           Sharknado.class),
    BOSS_DKFSR_SHARKRON     ("Sharkron",               55,  EntityType.SLIME,           EntitySlime.class,           Sharkron.class),
    BOSS_DKFSR_WTBLB        ("WaterBlob",              55,  EntityType.SLIME,           EntitySlime.class,           WaterBlob.class),
    BOSS_DRAGON_FOLLY       ("DragonFolly",            55,  EntityType.SLIME,           EntitySlime.class,           DragonFolly.class),
    BOSS_DSSC               ("DesertScourge",          55,  EntityType.SLIME,           EntitySlime.class,           DesertScourge.class),
    BOSS_DSSC_NUIS          ("DesertNuisance",         55,  EntityType.SLIME,           EntitySlime.class,           DesertNuisance.class),
    BOSS_DSTY               ("Destroyer",              55,  EntityType.SLIME,           EntitySlime.class,           Destroyer.class),
    BOSS_EOC                ("EyeOfCthulhu",           55,  EntityType.SLIME,           EntitySlime.class,           EyeOfCthulhu.class),
    BOSS_EOL                ("EmpressOfLight",         55,  EntityType.SLIME,           EntitySlime.class,           EmpressOfLight.class),
    BOSS_EOW                ("EaterOfWorlds",          55,  EntityType.SLIME,           EntitySlime.class,           EaterOfWorld.class),
    BOSS_EVERSCREAM         ("Everscream",             55,  EntityType.SLIME,           EntitySlime.class,           Everscream.class),
    BOSS_EXO_APOLLO         ("Apollo",                 55,  EntityType.SLIME,           EntitySlime.class,           Apollo.class),
    BOSS_EXO_ARES           ("Ares",                   55,  EntityType.SLIME,           EntitySlime.class,           Ares.class),
    BOSS_EXO_ARES_ARM       ("AresArm",                55,  EntityType.SLIME,           EntitySlime.class,           AresArm.class),
    BOSS_EXO_ARTEMIS        ("Artemis",                55,  EntityType.SLIME,           EntitySlime.class,           Artemis.class),
    BOSS_EXO_DRAEDON        ("Draedon",                55,  EntityType.SLIME,           EntitySlime.class,           Draedon.class),
    BOSS_EXO_THANATOS       ("Thanatos",               55,  EntityType.SLIME,           EntitySlime.class,           Thanatos.class),
    BOSS_GOLEM              ("Golem",                  55,  EntityType.SLIME,           EntitySlime.class,           Golem.class),
    BOSS_GOLEM_HEAD         ("GolemHead",              55,  EntityType.SLIME,           EntitySlime.class,           GolemHead.class),
    BOSS_GOLEM_FIST         ("GolemFist",              55,  EntityType.SLIME,           EntitySlime.class,           GolemFist.class),
    BOSS_GOLEM_FOOT         ("GolemFoot",              55,  EntityType.SLIME,           EntitySlime.class,           GolemFoot.class),
    BOSS_HEADLESS_HORSEMAN  ("HeadlessHorseman",       55,  EntityType.SLIME,           EntitySlime.class,           HeadlessHorseman.class),
    BOSS_HVM                ("TheHiveMind",            55,  EntityType.SLIME,           EntitySlime.class,           TheHiveMind.class),
    BOSS_HVM_BLOB           ("HiveBlob",               55,  EntityType.SLIME,           EntitySlime.class,           HiveBlob.class),
    BOSS_HVM_HEART          ("DarkHeart",              55,  EntityType.SLIME,           EntitySlime.class,           DarkHeart.class),
    BOSS_ICE_QUEEN          ("IceQueen",               55,  EntityType.SLIME,           EntitySlime.class,           IceQueen.class),
    BOSS_KSLM               ("KingSlime",              55,  EntityType.SLIME,           EntitySlime.class,           KingSlime.class),
    BOSS_KSLM_JEWEL         ("CrownJewel",             55,  EntityType.SLIME,           EntitySlime.class,           CrownJewel.class),
    BOSS_MNLD               ("MoonLord",               55,  EntityType.SLIME,           EntitySlime.class,           MoonLord.class),
    BOSS_MNLD_BG            ("MoonLordBackground",     55,  EntityType.SLIME,           EntitySlime.class,           MoonLordBackground.class),
    BOSS_MNLD_EYE           ("MoonLordEye",            55,  EntityType.SLIME,           EntitySlime.class,           MoonLordEye.class),
    BOSS_MNLD_BOLT          ("PhantasmalBolt",         55,  EntityType.SLIME,           EntitySlime.class,           MoonLordPhantasmalBolt.class),
    BOSS_MNLD_SPHERE        ("PhantasmalSphere",       55,  EntityType.SLIME,           EntitySlime.class,           MoonLordPhantasmalSphere.class),
    BOSS_MNLD_TRUE_EYE      ("TrueEyeOfCthulhu",       55,  EntityType.SLIME,           EntitySlime.class,           MoonLordTrueEyeOfCthulhu.class),
    BOSS_MOURNING_WOOD      ("MourningWood",           55,  EntityType.SLIME,           EntitySlime.class,           MourningWood.class),
    BOSS_PBGL               ("ThePlaguebringerGoliath",55,  EntityType.SLIME,           EntitySlime.class,           ThePlaguebringerGoliath.class),
    BOSS_PBGL_MSL           ("PlagueHomingMissile",    55,  EntityType.SLIME,           EntitySlime.class,           PlagueHomingMissile.class),
    BOSS_PBGL_MN            ("PlagueMine",             55,  EntityType.SLIME,           EntitySlime.class,           PlagueMine.class),
    BOSS_PLTR               ("Plantera",               55,  EntityType.SLIME,           EntitySlime.class,           Plantera.class),
    BOSS_PLTR_TENTACLE      ("PlanteraTentacle",       55,  EntityType.SLIME,           EntitySlime.class,           PlanteraTentacle.class),
    BOSS_PFG_ATTACKER       ("GuardianAttacker",       55,  EntityType.SLIME,           EntitySlime.class,           GuardianAttacker.class),
    BOSS_PFG_COMMANDER      ("GuardianCommander",      55,  EntityType.SLIME,           EntitySlime.class,           GuardianCommander.class),
    BOSS_PFG_DEFENDER       ("GuardianDefender",       55,  EntityType.SLIME,           EntitySlime.class,           GuardianDefender.class),
    BOSS_PFG_ROCK           ("GuardianRock",           55,  EntityType.SLIME,           EntitySlime.class,           GuardianRock.class),
    BOSS_POLTERGHAST        ("Polterghast",            55,  EntityType.SLIME,           EntitySlime.class,           Polterghast.class),
    BOSS_POLTERGHAST_CLONE  ("PolterghastClone",       55,  EntityType.SLIME,           EntitySlime.class,           PolterghastClone.class),
    BOSS_PROVIDENCE         ("Providence",             55,  EntityType.SLIME,           EntitySlime.class,           Providence.class),
    BOSS_PUMPKING_HEAD      ("PumpkingHead",           55,  EntityType.SLIME,           EntitySlime.class,           PumpkingHead.class),
    BOSS_PUMPKING_HAND      ("PumpkingHand",           55,  EntityType.SLIME,           EntitySlime.class,           PumpkingHand.class),
    BOSS_PUMPKING_SCYTHE    ("PumpkingScythe",         55,  EntityType.SLIME,           EntitySlime.class,           PumpkingScythe.class),
    BOSS_QNSLM              ("QueenSlime",             55,  EntityType.SLIME,           EntitySlime.class,           QueenSlime.class),
    BOSS_RVG                ("Ravager",                55,  EntityType.SLIME,           EntitySlime.class,           Ravager.class),
    BOSS_RVG_CLAW           ("RavagerClaw",            55,  EntityType.SLIME,           EntitySlime.class,           RavagerClaw.class),
    BOSS_RVG_HEAD           ("RavagerHead",            55,  EntityType.SLIME,           EntitySlime.class,           RavagerHead.class),
    BOSS_RVG_LEG            ("RavagerLeg",             55,  EntityType.SLIME,           EntitySlime.class,           RavagerLeg.class),
    BOSS_RVG_NUKE           ("RavagerNuke",            55,  EntityType.SLIME,           EntitySlime.class,           RavagerNuke.class),
    BOSS_RVG_PILLAR         ("RavagerRockPillar",      55,  EntityType.SLIME,           EntitySlime.class,           RavagerRockPillar.class),
    BOSS_SANTA_NK1          ("SantaNK1",               55,  EntityType.SLIME,           EntitySlime.class,           SantaNK1.class),
    BOSS_SIGNUS             ("Signus",                 55,  EntityType.SLIME,           EntitySlime.class,           Signus.class),
    BOSS_SKLT               ("Skeletron",              55,  EntityType.SLIME,           EntitySlime.class,           SkeletronHead.class),
    BOSS_SKLT_HAND          ("SkeletronHand",          55,  EntityType.SLIME,           EntitySlime.class,           SkeletronHand.class),
    BOSS_SKLTPM             ("SkeletronPrime",         55,  EntityType.SLIME,           EntitySlime.class,           SkeletronPrimeHead.class),
    BOSS_SKLTPM_HAND        ("SkeletronPrimeHand",     55,  EntityType.SLIME,           EntitySlime.class,           SkeletronPrimeHand.class),
    BOSS_SLMG               ("TheSlimeGod",            55,  EntityType.SLIME,           EntitySlime.class,           TheSlimeGod.class),
    BOSS_SLMG_EB            ("EbonianSlime",           55,  EntityType.SLIME,           EntitySlime.class,           EbonianSlime.class),
    BOSS_SLMG_CR            ("CrimulanSlime",          55,  EntityType.SLIME,           EntitySlime.class,           CrimulanSlime.class),
    BOSS_STORM_WEAVER       ("StormWeaver",            55,  EntityType.SLIME,           EntitySlime.class,           StormWeaver.class),
    BOSS_SUPREME_BROTHERS   ("SupremeCalamitasBrother",55,  EntityType.SLIME,           EntitySlime.class,           SupremeCalamitasBrother.class),
    BOSS_SUPREME_CALAMITAS  ("SupremeCalamitas",       55,  EntityType.SLIME,           EntitySlime.class,           SupremeCalamitas.class),
    BOSS_SUPREME_SEPULCHER  ("Sepulcher",              55,  EntityType.SLIME,           EntitySlime.class,           Sepulcher.class),
    BOSS_THE_OLD_DUKE       ("TheOldDuke",             55,  EntityType.SLIME,           EntitySlime.class,           TheOldDuke.class),
    BOSS_TWIN_RTN           ("Retinazer",              55,  EntityType.SLIME,           EntitySlime.class,           Retinazer.class),
    BOSS_TWIN_SPZ           ("Spazmatism",             55,  EntityType.SLIME,           EntitySlime.class,           Spazmatism.class),
    BOSS_WOF_EYE            ("WallOfFleshEye",         55,  EntityType.SLIME,           EntitySlime.class,           WallOfFleshEye.class),
    BOSS_WOF_MOUTH          ("WallOfFleshMouth",       55,  EntityType.SLIME,           EntitySlime.class,           WallOfFleshMouth.class),
    BOSS_WOF_WALL           ("WallOfFleshWall",        55,  EntityType.SLIME,           EntitySlime.class,           WallOfFleshWall.class),
    BOSS_YHARON             ("Yharon",                 55,  EntityType.SLIME,           EntitySlime.class,           Yharon.class),
    BOSS_YHARON_INFERNADO   ("Infernado",              55,  EntityType.SLIME,           EntitySlime.class,           Infernado.class),
    CRITTER                 ("Critter",                60,  EntityType.SILVERFISH,      EntitySilverfish.class,      Critter.class),
    EVENT_PILLAR            ("CelestialPillar",        53,  EntityType.GIANT,           EntityGiantZombie.class,     CelestialPillar.class),
    MINION_CAVE_SPIDER      ("MinionCaveSpider",       59,  EntityType.CAVE_SPIDER,     EntityCaveSpider.class,      MinionCaveSpider.class),
    MINION_HUSK             ("MinionHusk",             23,  EntityType.HUSK,            EntityZombieHusk.class,      MinionHusk .class),
    MINION_SLIME            ("MinionSlime",            55,  EntityType.SLIME,           EntitySlime.class,           MinionSlime.class),
    MONSTER_CAVE_SPIDER     ("MonsterCaveSpider",      59,  EntityType.CAVE_SPIDER,     EntityCaveSpider.class,      MonsterCaveSpider.class),
    MONSTER_HUSK            ("MonsterHusk",            23,  EntityType.HUSK,            EntityZombieHusk.class,      MonsterHusk.class),
    MONSTER_SILVERFISH      ("MonsterSilverfish",      60,  EntityType.SILVERFISH,      EntitySilverfish.class,      MonsterSilverfish.class),
    MONSTER_SKELETON        ("MonsterSkeleton",        51,  EntityType.SKELETON,        EntitySkeleton.class,        MonsterSkeleton.class),
    MONSTER_SLIME           ("MonsterSlime",           55,  EntityType.SLIME,           EntitySlime.class,           MonsterSlime.class),
    MONSTER_SPIDER          ("MonsterSpider",          52,  EntityType.SPIDER,          EntitySpider.class,          MonsterSpider.class),
    MONSTER_WITHER_SKELETON ("MonsterWitherSkeleton",  5,   EntityType.WITHER_SKELETON, EntitySkeletonWither.class,  MonsterWitherSkeleton.class),
    MONSTER_ZOMBIE          ("MonsterZombie",          54,  EntityType.ZOMBIE,          EntityZombie.class,          MonsterZombie.class),
    MOUNT                   ("Mount",                  55,  EntityType.SLIME,           EntitySlime.class,           Mount.class),
    NPC                     ("NPC",                    120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPC.class),
    NPC_ANGLER              ("NPCAngler",              120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCAngler.class),
    NPC_ARMS_DEALER         ("NPCArmsDealer",          120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCArmsDealer.class),
    NPC_BLOCK_SELLER        ("NPCBlockSeller",         120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCBlockSeller.class),
    NPC_CLOTHIER            ("NPCClothier",            120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCClothier.class),
    NPC_DEMOLITIONIST       ("NPCDemolitionist",       120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCDemolitionist.class),
    NPC_GOBLIN_TINKERER     ("NPCGoblinTinkerer",      120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCGoblinTinkerer.class),
    NPC_GUIDE               ("NPCGuide",               120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCGuide.class),
    NPC_NURSE               ("NPCNurse",               120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCNurse.class),
    NPC_BANDIT              ("NPCBandit",              120, EntityType.VILLAGER,        EntityVillager.class,        TerrariaNPCBandit.class),
    PLAYER_TORNADO          ("PlayerTornado",          55,  EntityType.SLIME,           EntitySlime.class,           PlayerTornado.class);

    private String name;
    private int id;
    private EntityType entityType;
    private Class<? extends Entity> nmsClass;
    private Class<? extends Entity> customClass;
    private MinecraftKey key;
    private MinecraftKey oldKey;

    private CustomEntities(String name, int id, EntityType entityType, Class<? extends Entity> nmsClass, Class<? extends Entity> customClass) {
        this.name = name;
        this.id = id;
        this.entityType = entityType;
        this.nmsClass = nmsClass;
        this.customClass = customClass;
        this.key = new MinecraftKey(name);
        this.oldKey = EntityTypes.b.b(nmsClass);
    }

    public static void registerEntities() {
        for (CustomEntities ce : CustomEntities.values())
            ce.register();
        // override some vanilla entities
        NMSUtils.registerEntity("Terraria_Dropped_Item", NMSUtils.Type.DROPPED_ITEM, TerrariaDroppedItem.class, true);
        TerrariaHelper.LOGGER.info("Custom entities have been registered.");
    }
    public static void unregisterEntities() { for (CustomEntities ce : CustomEntities.values()) ce.unregister(); }
    private void register() {
        EntityTypes.d.add(key);
        EntityTypes.b.a(id, key, customClass);
    }

    private void unregister() {
        EntityTypes.d.remove(key);
        EntityTypes.b.a(id, oldKey, nmsClass);
    }

    public String getName() {
        return name;
    }

    public int getID() {
        return id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Class<?> getCustomClass() {
        return customClass;
    }
}