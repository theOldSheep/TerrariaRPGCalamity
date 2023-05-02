package terraria.entity;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.entity.EntityType;
import terraria.entity.boss.aquaticScourge.AquaticScourge;
import terraria.entity.boss.brimstoneElemental.BrimstoneElemental;
import terraria.entity.boss.crabulon.Crabulon;
import terraria.entity.boss.cryogen.Cryogen;
import terraria.entity.boss.cryogen.CryogenShield;
import terraria.entity.boss.eyeOfCthulhu.EyeOfCthulhu;
import terraria.entity.boss.eaterOfWorld.EaterOfWorld;
import terraria.entity.boss.desertScourge.DesertNuisance;
import terraria.entity.boss.desertScourge.DesertScourge;
import terraria.entity.boss.skeletronPrime.SkeletronPrimeHand;
import terraria.entity.boss.skeletronPrime.SkeletronPrimeHead;
import terraria.entity.boss.theDestroyer.Destroyer;
import terraria.entity.boss.theHiveMind.DarkHeart;
import terraria.entity.boss.theHiveMind.HiveBlob;
import terraria.entity.boss.theHiveMind.TheHiveMind;
import terraria.entity.boss.skeletron.SkeletronHand;
import terraria.entity.boss.skeletron.SkeletronHead;
import terraria.entity.boss.queenSlime.QueenSlime;
import terraria.entity.boss.theSlimeGod.CrimulanSlime;
import terraria.entity.boss.theSlimeGod.EbonianSlime;
import terraria.entity.boss.theSlimeGod.TheSlimeGod;
import terraria.entity.boss.kingSlime.CrownJewel;
import terraria.entity.boss.kingSlime.KingSlime;
import terraria.entity.boss.theTwins.Retinazer;
import terraria.entity.boss.theTwins.Spazmatism;
import terraria.entity.boss.wallOfFlesh.WallOfFleshEye;
import terraria.entity.boss.wallOfFlesh.WallOfFleshMouth;
import terraria.entity.minion.MinionCaveSpider;
import terraria.entity.minion.MinionHusk;
import terraria.entity.minion.MinionSlime;
import terraria.entity.monster.MonsterHusk;
import terraria.entity.monster.MonsterSlime;
import terraria.entity.monster.MonsterZombie;
import terraria.entity.npc.*;

/*
class from https://www.spigotmc.org/threads/nms-tutorials-2-custom-nms-entities-1-11.205192/
 */
public enum CustomEntities {

    BOSS_AQTS          ("AquaticScourge",    55,  EntityType.SLIME,           EntitySlime.class,      AquaticScourge.class),
    BOSS_BELM          ("BrimstoneElemental",55,  EntityType.SLIME,           EntitySlime.class,      BrimstoneElemental.class),
    BOSS_CBL           ("Crabulon",          55,  EntityType.SLIME,           EntitySlime.class,      Crabulon.class),
    BOSS_CYG           ("Cryogen",           55,  EntityType.SLIME,           EntitySlime.class,      Cryogen.class),
    BOSS_CYG_SHIELD    ("CryogenShield",     55,  EntityType.SLIME,           EntitySlime.class,      CryogenShield.class),
    BOSS_DSTY          ("Destroyer",         55,  EntityType.SLIME,           EntitySlime.class,      Destroyer.class),
    BOSS_DSSC          ("DesertScourge",     55,  EntityType.SLIME,           EntitySlime.class,      DesertScourge.class),
    BOSS_DSSC_NUIS     ("DesertNuisance",    55,  EntityType.SLIME,           EntitySlime.class,      DesertNuisance.class),
    BOSS_EOC           ("EyeOfCthulhu",      55,  EntityType.SLIME,           EntitySlime.class,      EyeOfCthulhu.class),
    BOSS_EOW           ("EaterOfWorlds",     55,  EntityType.SLIME,           EntitySlime.class,      EaterOfWorld.class),
    BOSS_HVM           ("TheHiveMind",       55,  EntityType.SLIME,           EntitySlime.class,      TheHiveMind.class),
    BOSS_HVM_BLOB      ("HiveBlob",          55,  EntityType.SLIME,           EntitySlime.class,      HiveBlob.class),
    BOSS_HVM_HEART     ("DarkHeart",         55,  EntityType.SLIME,           EntitySlime.class,      DarkHeart.class),
    BOSS_KSLM          ("KingSlime",         55,  EntityType.SLIME,           EntitySlime.class,      KingSlime.class),
    BOSS_KSLM_JEWEL    ("CrownJewel",        55,  EntityType.SLIME,           EntitySlime.class,      CrownJewel.class),
    BOSS_QNSLM         ("QueenSlime",        55,  EntityType.SLIME,           EntitySlime.class,      QueenSlime.class),
    BOSS_SKLT          ("Skeletron",         55,  EntityType.SLIME,           EntitySlime.class,      SkeletronHead.class),
    BOSS_SKLT_HAND     ("SkeletronHand",     55,  EntityType.SLIME,           EntitySlime.class,      SkeletronHand.class),
    BOSS_SKLTPM        ("SkeletronPrime",    55,  EntityType.SLIME,           EntitySlime.class,      SkeletronPrimeHead.class),
    BOSS_SKLTPM_HAND   ("SkeletronPrimeHand",55,  EntityType.SLIME,           EntitySlime.class,      SkeletronPrimeHand.class),
    BOSS_SLMG          ("TheSlimeGod",       55,  EntityType.SLIME,           EntitySlime.class,      TheSlimeGod.class),
    BOSS_SLMG_EB       ("EbonianSlime",      55,  EntityType.SLIME,           EntitySlime.class,      EbonianSlime.class),
    BOSS_SLMG_CR       ("CrimulanSlime",     55,  EntityType.SLIME,           EntitySlime.class,      CrimulanSlime.class),
    BOSS_TWIN_RTN      ("Retinazer",         55,  EntityType.SLIME,           EntitySlime.class,      Retinazer.class),
    BOSS_TWIN_SPZ      ("Spazmatism",        55,  EntityType.SLIME,           EntitySlime.class,      Spazmatism.class),
    BOSS_WOF_EYE       ("WallOfFleshEye",    55,  EntityType.SLIME,           EntitySlime.class,      WallOfFleshEye.class),
    BOSS_WOF_MOUTH     ("WallOfFleshMouth",  55,  EntityType.SLIME,           EntitySlime.class,      WallOfFleshMouth.class),
    MINION_CAVE_SPIDER ("MinionCaveSpider",  59,  EntityType.CAVE_SPIDER,     EntityCaveSpider.class, MinionCaveSpider.class),
    MINION_HUSK        ("MinionHusk",        23,  EntityType.HUSK,            EntityZombieHusk.class, MinionHusk .class),
    MINION_SLIME       ("MinionSlime",       55,  EntityType.SLIME,           EntitySlime.class,      MinionSlime.class),
    MONSTER_HUSK       ("MonsterHusk",       23,  EntityType.HUSK,            EntityZombieHusk.class, MonsterHusk.class),
    MONSTER_SLIME      ("MonsterSlime",      55,  EntityType.SLIME,           EntitySlime.class,      MonsterSlime.class),
    MONSTER_ZOMBIE     ("MonsterZombie",     54,  EntityType.ZOMBIE,          EntityZombie.class,     MonsterZombie.class),
    NPC_ANGLER         ("NPCAngler",         120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPCAngler.class),
    NPC_ARMS_DEALER    ("NPCArmsDealer",     120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPCArmsDealer.class),
    NPC_BLOCK_SELLER   ("NPCBlockSeller",    120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPCBlockSeller.class),
    NPC_CLOTHIER       ("NPCClothier",       120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPCClothier.class),
    NPC_DEMOLITIONIST  ("NPCDemolitionist",  120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPCDemolitionist.class),
    NPC_GOBLIN_TINKERER("NPCGoblinTinkerer", 120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPCGoblinTinkerer.class),
    NPC_GUIDE          ("NPCGuide",          120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPCGuide.class),
    NPC_NURSE          ("NPCNurse",          120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPCNurse.class),
    TERRARIA_NPC       ("TerrariaNPC",       120, EntityType.VILLAGER,        EntityVillager.class,   TerrariaNPC.class);

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

    public static void registerEntities() { for (CustomEntities ce : CustomEntities.values()) ce.register(); }
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