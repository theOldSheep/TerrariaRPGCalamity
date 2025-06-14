package terraria.util;

import com.earth2me.essentials.api.Economy;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftProjectile;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.event.celestialPillar.CelestialPillar;
import terraria.entity.others.Mount;
import terraria.gameplay.EventAndTime;
import terraria.gameplay.Setting;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;

public class PlayerHelper {
    // constants
    public enum GameProgress {
        PRE_WALL_OF_FLESH, PRE_PLANTERA, PRE_MOON_LORD, PRE_DEVOURER_OF_GODS, POST_DEVOURER_OF_GODS;
    }
    private static HashMap<String, Double> defaultPlayerAttrMap = new HashMap<>(60);
    private static HashSet<String> defaultPlayerEffectInflict = new HashSet<>(8);
    public static final int PLAYER_EXTRA_INVENTORY_SIZE = 54, PLAYER_MAX_OXYGEN = 100;
    public static final String ARES_EXOSKELETON_CONFIG_PAGE_NAME = "阿瑞斯外骨骼配置";
    public static final String[] ARES_EXOSKELETON_WEAPON_NAMES =
            {"阿瑞斯离子加农炮", "阿瑞斯特斯拉加农炮", "阿瑞斯镭射加农炮", "阿瑞斯高斯核弹发射井"};
    public static final String[] GEM_TECH_GEMS = {
            "黄色天钻宝石", "绿色天钻宝石", "红色天钻宝石", "紫色天钻宝石", "蓝色天钻宝石", "粉色天钻宝石"};
    public static final String TAG_HAS_SWITCHABLE_ACCESSORY = "temp_hasSwA", TAG_SWITCHED_SWITCHABLE_ACCESSORY = "temp_isSwAOn";
    static {
        // init default player attribute map
        defaultPlayerAttrMap.put("armorPenetration", 0d);
        defaultPlayerAttrMap.put("ammoConsumptionRate", 1d);
        defaultPlayerAttrMap.put("arrowConsumptionRate", 1d);
        defaultPlayerAttrMap.put("barrierMax", 0d);
        defaultPlayerAttrMap.put("bounce", 0d);
        defaultPlayerAttrMap.put("crit", 0d);
        defaultPlayerAttrMap.put("critDamage", 100d);
        defaultPlayerAttrMap.put("critMagic", 0d);
        defaultPlayerAttrMap.put("critMelee", 0d);
        defaultPlayerAttrMap.put("critRanged", 0d);
        defaultPlayerAttrMap.put("critRogue", 0d);
        defaultPlayerAttrMap.put("critStealth", 0d);
        defaultPlayerAttrMap.put("critTrueMelee", 0d);
        defaultPlayerAttrMap.put("damage", 0d);
        defaultPlayerAttrMap.put("damageStealth", 1d);
        defaultPlayerAttrMap.put("damageMulti", 1d);
        defaultPlayerAttrMap.put("damageMeleeMulti", 1d);
        defaultPlayerAttrMap.put("damageTrueMeleeMulti", 1d);
        defaultPlayerAttrMap.put("damageArrowMulti", 1d);
        defaultPlayerAttrMap.put("damageBulletMulti", 1d);
        defaultPlayerAttrMap.put("damageRangedMulti", 1d);
        defaultPlayerAttrMap.put("damageRocketMulti", 1d);
        defaultPlayerAttrMap.put("damageMagicMulti", 1d);
        defaultPlayerAttrMap.put("damageRogueMulti", 1d);
        defaultPlayerAttrMap.put("damageStealthMulti", 1d);
        defaultPlayerAttrMap.put("damageSummonMulti", 1d);
        defaultPlayerAttrMap.put("damageTakenMulti", 1d);
        defaultPlayerAttrMap.put("damageContactTakenMulti", 1d);
        defaultPlayerAttrMap.put("defence", 0d);
        defaultPlayerAttrMap.put("defenceMulti", 1d);
        defaultPlayerAttrMap.put("fishingHooks", 1d);
        defaultPlayerAttrMap.put("fishingPower", 0d);
        defaultPlayerAttrMap.put("fixedHealingMulti", 1d);
        defaultPlayerAttrMap.put("flightTimeMulti", 1d);
        defaultPlayerAttrMap.put("healthMulti", 1d);
        defaultPlayerAttrMap.put("invulnerabilityTick", 10d);
        defaultPlayerAttrMap.put("knockback", 0d);
        defaultPlayerAttrMap.put("knockbackResistance", 0d);
        defaultPlayerAttrMap.put("knockbackMeleeMulti", 1d);
        defaultPlayerAttrMap.put("knockbackMulti", 1d);
        defaultPlayerAttrMap.put("lifeSteal", 0.0d);
        defaultPlayerAttrMap.put("manaRegen", 0d);
        defaultPlayerAttrMap.put("manaRegenFixed", 0d);
        defaultPlayerAttrMap.put("manaRegenMulti", 1d);
        defaultPlayerAttrMap.put("manaUse", 0d);
        defaultPlayerAttrMap.put("manaUseMulti", 1d);
        defaultPlayerAttrMap.put("maxHealth", 200d);
        defaultPlayerAttrMap.put("maxHealthMulti", 1d);
        defaultPlayerAttrMap.put("maxMana", 20d);
        defaultPlayerAttrMap.put("meleeReachMulti", 1d);
        defaultPlayerAttrMap.put("minionDamagePenaltyFactor", 0.5d);
        defaultPlayerAttrMap.put("minionLimit", 1d);
        defaultPlayerAttrMap.put("mobLimit", 15d);
        defaultPlayerAttrMap.put("mobSpawnRate", 0.1d);
        defaultPlayerAttrMap.put("mobSpawnRateMulti", 1d);
        defaultPlayerAttrMap.put("penetration", 0d);
        defaultPlayerAttrMap.put("powerPickaxe", 0d);
        defaultPlayerAttrMap.put("projectileSpeed", 0d);
        defaultPlayerAttrMap.put("projectileSpeedMulti", 1d);
        defaultPlayerAttrMap.put("projectileSpeedArrowMulti", 1d);
        defaultPlayerAttrMap.put("reachExtra", 0d);
        defaultPlayerAttrMap.put("regen", 0d);
        defaultPlayerAttrMap.put("regenMulti", 1d);
        defaultPlayerAttrMap.put("sentryLimit", 1d);
        defaultPlayerAttrMap.put("speed", 0.2d);
        defaultPlayerAttrMap.put("speedMulti", 1d);
        defaultPlayerAttrMap.put("stealthConsumptionMulti", 1d);
        defaultPlayerAttrMap.put("stealthLimit", 0d);
        defaultPlayerAttrMap.put("stealthRegenMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMeleeMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedRangedMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedRogueMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMagicMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMiningMulti", 1d);
        defaultPlayerAttrMap.put("useSpeedMulti", 1d);
        defaultPlayerAttrMap.put("useTime", 0d);
        defaultPlayerAttrMap.put("waterAffinity", 0d);
        // init default player buff inflict map
        defaultPlayerEffectInflict.add("buffInflict");
        defaultPlayerEffectInflict.add("buffInflictMelee");
        defaultPlayerEffectInflict.add("buffInflictTrueMelee");
        defaultPlayerEffectInflict.add("buffInflictRanged");
        defaultPlayerEffectInflict.add("buffInflictRogue");
        defaultPlayerEffectInflict.add("buffInflictMagic");
        defaultPlayerEffectInflict.add("buffInflictSummon");
    }
    // getters
    public static HashMap<String, ArrayList<String>> getDefaultPlayerEffectInflict() {
        HashMap<String, ArrayList<String>> result = new HashMap<>(defaultPlayerEffectInflict.size() * 2);
        for (String key : defaultPlayerEffectInflict) result.put(key, new ArrayList<>(6));
        return result;
    }
    public static HashMap<String, ArrayList<String>> getPlayerEffectInflict(Metadatable player) {
        try {
            return (HashMap<String, ArrayList<String>>) EntityHelper.getMetadata(player, EntityHelper.MetadataName.PLAYER_BUFF_INFLICT).value();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    public static HashMap<String, Double> getDefaultPlayerAttributes() {
        return (HashMap<String, Double>) defaultPlayerAttrMap.clone();
    }
    public static double getMoney(Player ply) {
        try {
            BigDecimal money = Economy.getMoneyExact(ply.getUniqueId());
            return money == null ? 0d : money.doubleValue();
        } catch (Exception e) {
            return 0d;
        }
    }
    public static Inventory getInventory(Player ply, String key) {
        return ((HashMap<String, Inventory>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_INVENTORIES).value())
                .get(key);
    }
    public static String getArmorSet(Player ply) {
        return EntityHelper.getMetadata(ply, EntityHelper.MetadataName.ARMOR_SET).asString();
    }
    public static int getAccessoryAmount(Player ply) {
        return getPlayerDataFile(ply).getInt("stats.maxAccessories", 6);
    }
    public static HashSet<String> getAccessories(Entity entity) {
        try {
            MetadataValue value = EntityHelper.getMetadata(entity, EntityHelper.MetadataName.ACCESSORIES);
            return value != null ? (HashSet<String>) (value.value()) : new HashSet<>();
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Entity Helper] getAccessories", e);
        }
        return new HashSet<>();
    }
    public static ItemStack getFirstItem(Player ply, Predicate<ItemStack> itemPredicate, boolean testVoidBag) {
        // in the player's inventory
        Inventory plyInv = ply.getInventory();
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem != null && itemPredicate.test(currItem)) {
                return currItem;
            }
        }
        // in the player's void bag
        if (testVoidBag && PlayerHelper.hasVoidBag(ply)) {
            Inventory voidBagInv = PlayerHelper.getInventory(ply, "voidBag");
            for (ItemStack currItem : voidBagInv.getContents()) {
                if (currItem != null && itemPredicate.test(currItem)) {
                    return currItem;
                }
            }
        }
        // item not found
        return null;
    }
    public static ArrayList<ItemStack> getItems(Player ply, Predicate<ItemStack> itemPredicate, boolean testVoidBag) {
        // in the player's inventory
        ArrayList<ItemStack> result = new ArrayList<>();
        Inventory plyInv = ply.getInventory();
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem != null && itemPredicate.test(currItem)) {
                result.add(currItem);
            }
        }
        // in the player's void bag
        if (testVoidBag && PlayerHelper.hasVoidBag(ply)) {
            Inventory voidBagInv = PlayerHelper.getInventory(ply, "voidBag");
            for (ItemStack currItem : voidBagInv.getContents()) {
                if (currItem != null && itemPredicate.test(currItem)) {
                    result.add(currItem);
                }
            }
        }
        // return all found items
        return result;
    }
    public static Location getSpawnLocation(Player ply) {
        if (ply.getBedSpawnLocation() != null) {
            return ply.getBedSpawnLocation();
        }
        return Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE)
                .getHighestBlockAt(0, 0).getLocation().add(0, 1, 0);
    }
    // gets the accurate location (when mounted, ply.getLocation() may be inaccurate)
    public static Location getAccurateLocation(Player ply) {
        Location currLoc;
        if (PlayerHelper.getMount(ply) == null)
            currLoc = ply.getLocation();
        else {
            currLoc = PlayerHelper.getMount(ply).getLocation();
            currLoc.setY(ply.getLocation().getY());
        }
        return currLoc;
    }
    public static GameProgress getGameProgress(Player player) {
        ConfigurationSection bossDefeatedSection = getPlayerDataFile(player).getConfigurationSection("bossDefeated");
        if (bossDefeatedSection != null) {
            if (bossDefeatedSection.getBoolean("神明吞噬者", false))
                return GameProgress.POST_DEVOURER_OF_GODS;
            if (bossDefeatedSection.getBoolean("月球领主", false))
                return GameProgress.PRE_DEVOURER_OF_GODS;
            if (bossDefeatedSection.getBoolean("世纪之花", false))
                return GameProgress.PRE_MOON_LORD;
            if (bossDefeatedSection.getBoolean("血肉之墙", false))
                return GameProgress.PRE_PLANTERA;
        }
        return GameProgress.PRE_WALL_OF_FLESH;
    }
    public static GameProgress getGameProgress(String bossProgress) {
        switch (bossProgress) {
            case "血肉之墙":
            case "史莱姆皇后":
            case "极地之灵":
            case "双子魔眼":
            case "硫磺火元素":
            case "毁灭者":
            case "渊海灾虫":
            case "机械骷髅王":
            case "机械一王":
            case "机械二王":
            case "机械三王":
            case "灾厄之眼":
                return GameProgress.PRE_PLANTERA;
            case "世纪之花":
            case "阿娜希塔和利维坦":
            case "白金星舰":
            case "石巨人":
            case "瘟疫使者歌莉娅":
            case "光之女皇":
            case "猪鲨公爵":
            case "毁灭魔像":
            case "拜月教邪教徒":
            case "星神游龙":
                return GameProgress.PRE_MOON_LORD;
            case "月球领主":
            case "亵渎守卫":
            case "痴愚金龙":
            case "噬魂幽花":
            case "亵渎天神，普罗维登斯":
            case "无尽虚空":
            case "风暴编织者":
            case "神之使徒西格纳斯":
            case "硫海遗爵":
                return GameProgress.PRE_DEVOURER_OF_GODS;
            case "神明吞噬者":
            case "丛林龙，犽戎":
            case "星流巨械":
            case "至尊灾厄":
            case "成年幻海妖龙":
                return GameProgress.POST_DEVOURER_OF_GODS;
        }
        return GameProgress.PRE_WALL_OF_FLESH;
    }
    public static HashSet<String> getPlayerKeyPressed(Player ply) {
        return (HashSet<String>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_KEYS_PRESSED).value();
    }
    // get the internal velocity unaccounted for by the speed multi (water etc.)
    public static Vector getPlayerRawVelocity(Player ply) {
        MetadataValue mtv = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_VELOCITY_INTERNAL);
        if (mtv == null) {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_VELOCITY_INTERNAL, new Vector());
            return new Vector();
        }
        return (Vector) mtv.value();
    }

    /**
     * Returns the player's moving direction (yaw).
     * RETURNS 1e9 IF PLAYER IS NOT MOVING.
     * @param ply The player
     * @return The moving direction, yaw
     */
    public static double getPlayerMoveYaw(Player ply) {
        HashSet<String> allKeysPressed = getPlayerKeyPressed(ply);
        return getPlayerMoveYaw(ply, allKeysPressed);
    }

    /**
     * Returns the player's moving direction (yaw), with the specified pressed keys.
     * RETURNS 1e9 IF PLAYER IS NOT MOVING.
     * @param ply The player
     * @param keysPressed The keys being pressed
     * @return The derived moving direction, yaw
     */
    public static double getPlayerMoveYaw(Player ply, Collection<String> keysPressed) {
        String movementKeyDown = "";
        // Get moving direction
        boolean fwd = keysPressed.contains( Setting.getOptionString(ply, Setting.Options.CONTROL_W) ),
                rev = keysPressed.contains( Setting.getOptionString(ply, Setting.Options.CONTROL_S) ),
                lft = keysPressed.contains( Setting.getOptionString(ply, Setting.Options.CONTROL_A) ),
                rt = keysPressed.contains( Setting.getOptionString(ply, Setting.Options.CONTROL_D) );
        if (fwd && !rev)
            movementKeyDown += "W";
        else if (rev && !fwd)
            movementKeyDown += "S";
        if (lft && !rt)
            movementKeyDown += "A";
        else if (rt && !lft)
            movementKeyDown += "D";

        double horizontalMoveYaw = ply.getLocation().getYaw();
        switch (movementKeyDown) {
            case "":
                horizontalMoveYaw = 1e9;
                break;
            case "A":
                horizontalMoveYaw -= 90;
                break;
            case "S":
                horizontalMoveYaw += 180;
                break;
            case "D":
                horizontalMoveYaw += 90;
                break;
            case "WA":
                horizontalMoveYaw -= 45;
                break;
            case "WD":
                horizontalMoveYaw += 45;
                break;
            case "SA":
                horizontalMoveYaw -= 135;
                break;
            case "SD":
                horizontalMoveYaw += 135;
                break;
        }
        return horizontalMoveYaw;
    }
    public static int getPlayerHealthTier(Player ply) {
        MetadataValue metadataValue = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_HEALTH_TIER);
        if (metadataValue != null)
            return metadataValue.asInt();
        ConfigurationSection playerDataFile = getPlayerDataFile(ply);
        return playerDataFile.getInt("stats.healthTier", 5);
    }
    public static int getPlayerManaTier(Player ply) {
        MetadataValue metadataValue = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_TIER);
        if (metadataValue != null)
            return metadataValue.asInt();
        ConfigurationSection playerDataFile = getPlayerDataFile(ply);
        return playerDataFile.getInt("stats.manaTier", 1);
    }
    public static int getMaxHealthByTier(int tier) {
        if (tier < 21) return tier * 40;
        if (tier < 41) return 800 + (tier - 20) * 10;
        switch (tier) {
            case 41:
                return 1050;
            case 42:
                return 1100;
            case 43:
                return 1150;
            default:
                return 1200;
        }
    }
    public static int getMaxManaByTier(int tier) {
        if (tier < 11) return tier * 20;
        switch (tier) {
            case 11:
                return 250;
            case 12:
                return 300;
            default:
                return 350;
        }
    }
    public static int getMaxMana(Player ply) {
        return AttributeHelper.getAttrMap(ply).getOrDefault("maxMana", 20d).intValue();
    }
    public static String getPlayerDataFilePath(Player ply) {
        return TerrariaHelper.Constants.DATA_PLAYER_FOLDER_DIR + ply.getName() + ".yml";
    }
    public static YmlHelper.YmlSection getPlayerDataFile(Player ply) {
        String filePath = getPlayerDataFilePath(ply);
        return YmlHelper.getFile(filePath);
    }
    public static boolean hasDefeated(Player player, EventAndTime.Events eventToCheck) {
        return hasDefeated(player, eventToCheck.eventName);
    }
    public static boolean hasDefeated(Player player, BossHelper.BossType bossToCheck) {
        return hasDefeated(player, bossToCheck.msgName);
    }
    public static boolean hasDefeated(Player player, String progressToCheck) {
        if (progressToCheck.length() == 0)
            return true;
        switch (progressToCheck) {
            case "机械一王":
            case "机械二王":
            case "机械三王":
                int amountDefeated = 0;
                if (hasDefeated(player, BossHelper.BossType.THE_TWINS)) amountDefeated ++;
                if (hasDefeated(player, BossHelper.BossType.SKELETRON_PRIME)) amountDefeated ++;
                if (hasDefeated(player, BossHelper.BossType.THE_DESTROYER)) amountDefeated ++;
                switch (progressToCheck) {
                    case "机械一王":
                        return amountDefeated >= 1;
                    case "机械二王":
                        return amountDefeated >= 2;
                    case "机械三王":
                        return amountDefeated >= 3;
                }
                break;
            case "毕业":
                return hasDefeated(player, BossHelper.BossType.EXO_MECHS) && hasDefeated(player, BossHelper.BossType.SUPREME_WITCH_CALAMITAS);
        }
        YmlHelper.YmlSection fileSection = getPlayerDataFile(player);
        return fileSection.getBoolean("bossDefeated." + progressToCheck, false);
    }
    public static void setDefeated(Player player, String progressToAdd, boolean defeated) {
        YmlHelper.YmlSection fileSection = getPlayerDataFile(player);
        fileSection.set("bossDefeated." + progressToAdd, defeated);
    }
    public static boolean isProperlyPlaying(Player player) {
        if (!player.isOnline()) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        return !player.getScoreboardTags().contains("unauthorized");
    }
    public static boolean isTargetedByBOSS(Player player) {
        for (String bossName : BossHelper.bossMap.keySet()) {
            ArrayList<LivingEntity> bossArrayList = BossHelper.bossMap.get(bossName);
            // if a placeholder is in the boss map, move on
            if (bossArrayList.isEmpty())
                continue;
            // find the target map of the boss
            try {
                HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targets =
                        (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>)
                                EntityHelper.getMetadata(bossArrayList.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
                if (targets.containsKey(player.getUniqueId()))
                    return true;
            }
            catch (Exception ignored) {
            }
        }
        return false;
    }
    public static boolean hasPiggyBank(Player ply) {
        return hasItemInMainInv(ply, "钱币槽");
    }
    public static boolean hasVoidBag(Player ply) {
        return hasItemInMainInv(ply, "虚空袋");
    }
    public static boolean hasTrashBin(Player ply) {
        return hasItemInMainInv(ply, "垃圾桶");
    }
    public static boolean hasCritterGuide(Player ply) {
        return hasItemInMainInv(ply, "小动物友谊指南");
    }
    public static boolean hasItemInMainInv(Player ply, String type) {
        for (ItemStack item : ply.getInventory().getContents()) {
            if (ItemHelper.splitItemName(item)[1].equals(type))
                return true;
        }
        return false;
    }
    public static void updateTrashBinInfo(Player ply) {
        HashSet<String> itemsInTrash = new HashSet<>();
        Inventory trashBinInv = getInventory(ply, "trashBin");
        if (hasTrashBin(ply) && trashBinInv != null) {
            for (ItemStack currItem : trashBinInv.getContents()) {
                if (currItem == null || currItem.getType() == Material.AIR)
                    continue;
                itemsInTrash.add( ItemHelper.splitItemName(currItem)[1] );
            }
        }
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TRASH_ITEMS, itemsInTrash);
    }
    public static boolean canHoldAny(Player ply, ItemStack item) {
        if (item == null) return true;
        String itemType = ItemHelper.splitItemName(item)[1];
        // these items get consumed once picked up
        switch (itemType) {
            case "铜币":
            case "银币":
            case "金币":
            case "铂金币":
            case "心":
            case "星":
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰":
                return true;
        }
        // check airs first, they are a lot less performance costly.
        // backpack
        Inventory plyInv = ply.getInventory();
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem == null || currItem.getType() == Material.AIR) return true;
        }
        // void bag
        boolean considerVoidBag = hasVoidBag(ply) && Setting.getOptionBool(ply, Setting.Options.VOID_BAG_PICKUP);
        Inventory voidBagInv = getInventory(ply, "voidBag");
        if (considerVoidBag && voidBagInv != null) {
            for (ItemStack currItem : voidBagInv.getContents())
                if (currItem == null || currItem.getType() == Material.AIR) return true;
        }
        // then we check for stacking
        // items that can not stack do not need any additional check.
        int maxStackSize = item.getMaxStackSize();
        if (maxStackSize <= 1) return false;
        // backpack
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyInv.getItem(i);
            if (currItem.isSimilar(item) && maxStackSize > currItem.getAmount()) return true;
        }
        // void bag
        if (considerVoidBag && voidBagInv != null) {
            for (ItemStack currItem : voidBagInv.getContents())
                if (currItem.isSimilar(item) && maxStackSize > currItem.getAmount()) return true;
        }
        return false;
    }
    // setters
    public static void updateArmorSetMetadata(Player ply, String armorSet) {
        try {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ARMOR_SET, armorSet);
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Player Helper] setArmorSet ", e);
        }
    }
    public static void setMoney(Player ply, double amount) {
        try {
            Economy.setMoney(ply.getUniqueId(), BigDecimal.valueOf(amount));
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Player Helper] setMoney ", e);
        }
    }
    public static void resetPlayerFlightTime(Player ply) {
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_INDEX, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_PROGRESS, 0);
    }

    // threads
    public static void threadArmorAccessory() {
        // setup projectile attributes
        HashMap<String, Double> attrMapChlorophyte = new HashMap<>(5);
        {
            attrMapChlorophyte.put("damage", 100d);
            attrMapChlorophyte.put("knockback", 0d);
        }
        HashMap<String, Double> attrMapVolatileGelatinJr = new HashMap<>(5);
        {
            attrMapVolatileGelatinJr.put("damage", 20d);
            attrMapVolatileGelatinJr.put("knockback", 0d);
        }
        HashMap<String, Double> attrMapVolatileGelatin = new HashMap<>(5);
        {
            attrMapVolatileGelatin.put("damage", 125d);
            attrMapVolatileGelatin.put("knockback", 0d);
        }
        HashMap<String, Double> attrMapSpore = new HashMap<>(5);
        {
            attrMapSpore.put("damage", 100d);
            attrMapSpore.put("knockback", 0d);
        }
        // every 2 ticks (1/10 second)
        AtomicInteger tickIndex = new AtomicInteger();
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    // validate the current player
                    if (PlayerHelper.isProperlyPlaying(ply)) {
                        double health = ply.getHealth(), maxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        // handle armor sets
                        switch (getArmorSet(ply)) {
                            // chlorophyte armors
                            case "叶绿魔法套装":
                            case "叶绿射手套装":
                            case "叶绿战士套装": {
                                if (tickIndex.get() % 3 == 0) {
                                    double distanceSqr = 999999;
                                    Entity target = null;
                                    for (Entity e : ply.getWorld().getNearbyEntities(ply.getEyeLocation(), 25, 25, 25)) {
                                        // ignore if is not a valid enemy
                                        if (!(DamageHelper.checkCanDamage(ply, e, true)))
                                            continue;
                                        double distSqr = e.getLocation().distanceSquared(ply.getLocation());
                                        // ignore if is further than current
                                        if (distSqr > distanceSqr)
                                            continue;
                                        distanceSqr = distSqr;
                                        target = e;
                                    }
                                    if (target != null) {
                                        double projSpd = 1.5;
                                        AimHelper.AimHelperOptions aimHelperOptions = new AimHelper.AimHelperOptions()
                                                .setProjectileSpeed(projSpd)
                                                .setProjectileGravity(0.05);
                                        Location aimedLoc = AimHelper.helperAimEntity(ply, target, aimHelperOptions);

                                        Vector v = MathHelper.getDirection(ply.getEyeLocation(), aimedLoc, 1.5);
                                        EntityHelper.spawnProjectile(ply, v, attrMapChlorophyte,
                                                DamageHelper.DamageType.ARROW, "树叶");
                                    }
                                }
                                break;
                            }
                            // gem tech armor
                            case "天钻套装": {
                                for (String gem : GEM_TECH_GEMS)
                                    EntityHelper.applyEffect(ply, gem, 20);
                                break;
                            }
                        }
                        // handle accessories
                        HashSet<String> accessories = getAccessories(ply);
                        for (String accessory : accessories) {
                            switch (accessory) {
                                case "新手版挥发明胶":
                                case "挥发明胶": {
                                    int shootInterval = accessory.equals("新手版挥发明胶") ? 10 : 5;
                                    if (tickIndex.get() % shootInterval == 0) {
                                        double distanceSqr = 10000d;
                                        Entity target = null;
                                        for (Entity e : ply.getWorld().getNearbyEntities(ply.getEyeLocation(), 12, 12, 12)) {
                                            // is not a valid enemy
                                            if (!(DamageHelper.checkCanDamage(ply, e, true)))
                                                continue;
                                            // has no vision
                                            if (! ply.hasLineOfSight(e))
                                                continue;
                                            // further than current
                                            double distSqr = e.getLocation().distanceSquared(ply.getLocation());
                                            if (distSqr > distanceSqr)
                                                continue;
                                            // record target
                                            distanceSqr = distSqr;
                                            target = e;
                                        }
                                        if (target != null) {
                                            Vector v;
                                            if (target instanceof LivingEntity)
                                                v = ((LivingEntity) target).getEyeLocation().subtract(ply.getEyeLocation()).toVector();
                                            else v = target.getLocation().subtract(ply.getEyeLocation()).toVector();
                                            if (accessory.equals("新手版挥发明胶")) {
                                                v.normalize().multiply(0.5);
                                                EntityHelper.spawnProjectile(ply, v, attrMapVolatileGelatinJr,
                                                        DamageHelper.DamageType.ARROW, "小挥发明胶");
                                            } else {
                                                v.normalize().multiply(0.6);
                                                EntityHelper.spawnProjectile(ply, v, attrMapVolatileGelatin,
                                                        DamageHelper.DamageType.ARROW, "小挥发明胶");
                                            }
                                        }
                                    }
                                    break;
                                }
                                case "孢子囊": {
                                    if (tickIndex.get() % 3 == 0) {
                                        Vector velocity = MathHelper.randomVector();
                                        velocity.multiply(0.25);
                                        Location spawnLoc = ply.getEyeLocation().add(
                                                Math.random() * 10 - 5, Math.random() * 8 - 3, Math.random() * 10 - 5);
                                        EntityHelper.spawnProjectile(ply, spawnLoc, velocity, attrMapSpore,
                                                DamageHelper.DamageType.MAGIC, "孢子球");
                                    }
                                    break;
                                }
                                // equipments that provide buff
                                case "冰冻海龟壳": {
                                    if (health * 2 < maxHealth)
                                        EntityHelper.applyEffect(ply, "冰障", 20);
                                    break;
                                }
                                case "圣骑士护盾": {
                                    if (health * 4 > maxHealth)
                                        EntityHelper.applyEffect(ply, "圣骑士护盾", 20);
                                    break;
                                }
                                case "冰冻护盾":
                                case "神之壁垒": {
                                    if (health * 4 > maxHealth)
                                        EntityHelper.applyEffect(ply, "圣骑士护盾", 20);
                                    if (health * 2 < maxHealth)
                                        EntityHelper.applyEffect(ply, "冰障", 20);
                                    break;
                                }
                                case "血肉图腾": {
                                    EntityHelper.applyEffect(ply, "血肉图腾", 1220);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    TerrariaHelper.LOGGER.log(Level.SEVERE, "[Player Helper] threadArmorAccessory ", e);
                }
            }
            tickIndex.getAndIncrement();
        }, 0, 2);
    }
    public static void threadAttribute() {
        int delay = 6;
        // every 6 ticks (0.3 second)
        // note that players would have their stats updated
        // when they use their weapon after switching hot bar slot or closes an inventory.
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (isProperlyPlaying(ply))
                    setupAttribute(ply);
            }
        }, 0, delay);
    }
    public static void threadBackground() {
        // every 10 ticks (1/2 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    String last = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BACKGROUND).asString();
                    // setup proper background to display
                    String current = "";
                    MetadataValue forceBackground = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_FORCED_BACKGROUND);
                    if (forceBackground == null) {
                        // duke fishron: background change for only the last tier
                        if (BossHelper.bossMap.containsKey(BossHelper.BossType.DUKE_FISHRON.msgName)) {
                            LivingEntity fishron = BossHelper.bossMap.get(BossHelper.BossType.DUKE_FISHRON.msgName).get(0);
                            if (fishron.getHealth() / fishron.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() <= 0.4)
                                current = "猪鲨";
                        }
                        // other bosses with constant background change
                        if (BossHelper.bossMap.containsKey(BossHelper.BossType.SUPREME_WITCH_CALAMITAS.msgName) )
                            current = "血月";
                        else if (BossHelper.bossMap.containsKey(BossHelper.BossType.EXO_MECHS.msgName) )
                            current = "虚空";
                        else if (BossHelper.bossMap.containsKey(BossHelper.BossType.YHARON_DRAGON_OF_REBIRTH.msgName) )
                            current = "血月";
                        else if (BossHelper.bossMap.containsKey(BossHelper.BossType.THE_DEVOURER_OF_GODS.msgName) )
                            current = "虚空";
                        else if (BossHelper.bossMap.containsKey(BossHelper.BossType.MOON_LORD.msgName))
                            current = "虚空";
                        else if (BossHelper.bossMap.containsKey(BossHelper.BossType.CALAMITAS_CLONE.msgName))
                            current = "血月";
                        else if (BossHelper.bossMap.containsKey(BossHelper.BossType.THE_PLAGUEBRINGER_GOLIATH.msgName) )
                            current = "猪鲨";
                        // celestial pillars
                        else if (ply.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE)) {
                            if (EventAndTime.currentEvent != EventAndTime.Events.NONE)
                                current = EventAndTime.currentEvent.toString();
                            // background for celestial pillars
                            for (CelestialPillar pillar : EventAndTime.pillars.values())
                                if (pillar.getBukkitEntity().getWorld().equals(ply.getWorld()) &&
                                        pillar.getBukkitEntity().getLocation().distanceSquared(ply.getLocation()) <
                                                CelestialPillar.EFFECTED_RADIUS_SQR) {
                                    current = pillar.getName().replace("柱", "");
                                    break;
                                }
                        }
                    } else {
                        current = forceBackground.asString();
                    }
                    // setup client target time
                    int time = 0;
                    switch (current) {
                        case "虚空":
                            time = 22000;
                            break;
                        case "血月":
                            time = 22940;
                            break;
                        case "日耀":
                            time = 23000;
                            break;
                        case "星璇":
                            time = 23080;
                            break;
                        case "星云":
                            time = 23150;
                            break;
                        case "星尘":
                            time = 23300;
                            break;
                        case "猪鲨":
                            time = 23365;
                            break;
                        case "日食":
                            time = 20000;
                            break;
                        // no change in background
                        default:
                            current = "";
                    }
                    if (!last.equals(current)) {
                        if (current.equals("")) {
                            ply.resetPlayerTime();
                            ply.resetPlayerWeather();
                            ply.removePotionEffect(PotionEffectType.NIGHT_VISION);
                        } else {
                            ply.setPlayerTime(time, false);
                            ply.setPlayerWeather(WeatherType.DOWNFALL);
                            ply.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));
                        }
                        // update last background
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BACKGROUND, current);
                    }
                } catch (Exception e) {
                    TerrariaHelper.LOGGER.log(Level.SEVERE, "[Player Helper] threadBackground ", e);
                }
            }
        }, 0, 10);
    }
    public static void threadBGM() {
        final boolean printBGMDebugInfo = false;
        // every 1 tick
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                try {
                    String last = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM).asString();
                    long startedPlayingTime = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM_TIME).asLong();

                    // setup proper music to play
                    String current = "";
                    long currentTime = Calendar.getInstance().getTimeInMillis();
                    MetadataValue forceBGM = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_FORCED_BGM);
                    // if no jukebox is present
                    if (forceBGM == null) {
                        World plyWorld = ply.getWorld();
                        String worldName = plyWorld.getName();
                        double minBossDistance = 999999;
                        // boss
                        for (String bossName : BossHelper.bossMap.keySet()) {
                            ArrayList<LivingEntity> bossArrayList = BossHelper.bossMap.get(bossName);
                            // if a placeholder is in the boss map, move on
                            if (bossArrayList.isEmpty())
                                continue;
                            // if the boss has multiple phases
                            switch (bossName) {
                                case "阿娜希塔和利维坦":
                                    if (bossArrayList.get(1).isDead()) bossName += "1";
                                    else bossName += "2";
                                    break;
                                case "神明吞噬者": {
                                    LivingEntity bossPart = bossArrayList.get(0);
                                    if (bossPart.getHealth() / bossPart.getMaxHealth() >= 0.6) bossName += "1";
                                    else bossName += "2";
                                    break;
                                }
                                case "丛林龙，犽戎": {
                                    LivingEntity bossPart = bossArrayList.get(0);
                                    if (bossPart.getHealth() / bossPart.getMaxHealth() >= 0.55) bossName += "1";
                                    else bossName += "2";
                                    break;
                                }
                                case "至尊灾厄": {
                                    LivingEntity bossPart = bossArrayList.get(0);
                                    double healthRatio = bossPart.getHealth() / bossPart.getMaxHealth();
                                    if (healthRatio >= 0.5) bossName += "1";
                                    else if (healthRatio >= 0.3) bossName += "2";
                                    else if (healthRatio >= 0.01) bossName += "3";
                                    else bossName += "4";
                                    break;
                                }
                            }
                            // get the first living component of current boss
                            double currDist = 99999;
                            int index = 0;
                            Entity currBoss = bossArrayList.get(0);
                            while (currBoss.isDead()) {
                                index ++;
                                if (index >= bossArrayList.size()) {
                                    break;
                                }
                                currBoss = bossArrayList.get(index);
                            }
                            // get the nearest boss
                            if (plyWorld.equals(currBoss.getWorld()))
                                currDist = currBoss.getLocation().distanceSquared(ply.getLocation());
                            if (currDist < minBossDistance) {
                                minBossDistance = currDist;
                                current = TerrariaHelper.soundConfig.getString("boss." + bossName, "");
                            }
                        }
                        // events
                        if (current.equals("")) {
                            // other events: only play on surface world
                            if (EventAndTime.currentEvent != EventAndTime.Events.NONE &&
                                    ply.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE))
                                current = TerrariaHelper.soundConfig.getString(
                                        "event." + EventAndTime.currentEvent, "");
                            // celestial pillars
                            if (ply.getLocation().getY() >= 50 && worldName.equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE)) {
                                for (CelestialPillar pillar : EventAndTime.pillars.values())
                                    if (pillar.getBukkitEntity().getWorld().equals(plyWorld) &&
                                            pillar.getBukkitEntity().getLocation().distanceSquared(ply.getLocation()) <
                                                    CelestialPillar.EFFECTED_RADIUS_SQR) {
                                        current = "lunar_tower";
                                        break;
                                    }
                            }
                        }
                        // no event/boss : normal environment bgm
                        boolean isDayTime = WorldHelper.isDayTime(plyWorld);
                        if (current.equals("")) {
                            WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(ply);
                            switch (WorldHelper.HeightLayer.getHeightLayer(ply.getLocation())) {
                                case SPACE:
                                    current = isDayTime ? "space_night" : "space";
                                    break;
                                case UNDERGROUND:
                                case CAVERN:
                                    switch (biomeType) {
                                        case NORMAL:
                                        case CORRUPTION:
                                        case HALLOW:
                                        case DESERT:
                                        case OCEAN:
                                        case TUNDRA:
                                        case JUNGLE:
                                        case ASTRAL_INFECTION:
                                            current = biomeType.toString().toLowerCase() + "_underground";
                                            break;
                                        case ABYSS:
                                            WorldHelper.WaterRegionType waterRegionType =
                                                    WorldHelper.WaterRegionType.getWaterRegionType(ply.getLocation(), false);
                                            switch (waterRegionType) {
                                                case ABYSS_3:
                                                    current = "threats_of_the_ocean_floor";
                                                    break;
                                                case ABYSS_2:
                                                    current = "abyss_3";
                                                    break;
                                                case ABYSS_1:
                                                default:
                                                    current = "abyss_1";
                                                    break;
                                            }
                                            break;
                                        default:
                                            current = biomeType.toString().toLowerCase();
                                    }
                                    break;
                                default:
                                    // surface/underworld
                                    switch (biomeType) {
                                        case BRIMSTONE_CRAG:
                                        case UNDERWORLD:
                                        case ASTRAL_INFECTION:
                                        case CORRUPTION:
                                        case DESERT:
                                        case SULPHUROUS_OCEAN:
                                        case TUNDRA:
                                            // always the same bgm
                                            current = biomeType.toString().toLowerCase();
                                            break;
                                        case HALLOW:
                                            current = isDayTime ? "hallow" : "normal_night";
                                            break;
                                        case METEOR:
                                            current = "eerie";
                                            break;
                                        default:
                                            current = biomeType.toString().toLowerCase() + (isDayTime ? "" : "_night");
                                    }
                            }
                        }
                    }
                    else {
                        current = forceBGM.asString();
                    }
                    // these music have a full version
                    switch (current) {
                        case "return_to_slime":
                        case "unholy_ambush":
                        case "unholy_insurgency":
                        case "toxic_wisdom":
                        case "scourge_of_the_universe":
                        case "universal_collapse":
                        case "threats_of_the_ocean_floor":
                            // this if statement triggers in two cases:
                            // in the last tick, if: full was playing OR some other random music was playing
                            if (! last.equals(current)) {
                                // just started playing this music
                                current += "_full";
                                // this if statement is true only if some other random music was playing the last tick
                                // refreshing started time will prevent instantly switching out from the full version
                                if (! last.equals(current))
                                    startedPlayingTime = currentTime;
                            }
                            break;
                    }
                    // play music if needed
                    long musicDuration = TerrariaHelper.soundConfig.getLong("lengths." + current, 0L);
                    if (printBGMDebugInfo) ply.sendMessage(current + ", " + (musicDuration + startedPlayingTime - currentTime) + " ms left.");
                    boolean shouldPlayMusic = false;
                    // full song finished playing
                    if (musicDuration + startedPlayingTime <= currentTime) {
                        shouldPlayMusic = true;
                        // if the current one playing is the full version, next one shall be reduced version
                        if (current.endsWith("_full")) current = current.replace("_full", "");
                    }
                    else if (!last.equals(current))
                        shouldPlayMusic = true;
                    if (shouldPlayMusic) {
                        if (printBGMDebugInfo) ply.sendMessage(current + ", replayed!");
                        ply.stopSound("music." + last);
                        ply.playSound(ply.getEyeLocation(), "music." + current, SoundCategory.MUSIC, Float.MAX_VALUE, 1f);
                        // update last bgm metadata
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM, current);
                        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM_TIME, currentTime);
                    }
                } catch (Exception e) {
                    TerrariaHelper.LOGGER.log(Level.SEVERE, "[Player Helper] threadBGM ", e);
                }
            }
        }, 0, 1);
    }
    public static void threadMonsterCritterSpawn() {
        // every 5 ticks
        final int delay = 5;
        final double spawnRateAdjustFactor = delay / 20d;
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (!isProperlyPlaying(ply)) continue;
                // spawn rate multipliers apply to critters as well
                HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(ply);
                double spawnRateMulti = attrMap.getOrDefault("mobSpawnRateMulti", 1d);
                // critter spawn, expected 1 critter per second (may change according to spawn rate).
                if (Math.random() < spawnRateMulti * spawnRateAdjustFactor)
                    CritterHelper.naturalCritterSpawn(ply);
                // monster spawn rate
                double mobSpawnRate = attrMap.getOrDefault("mobSpawnRate", 0.1d);
                WorldHelper.HeightLayer heightLayer = WorldHelper.HeightLayer.getHeightLayer(ply.getLocation());
                boolean isBossAlive = isTargetedByBOSS(ply);
                // reduced mob spawning rate if a boss is alive
                if (isBossAlive)
                    mobSpawnRate *= 0.75;
                boolean isSurfaceOrSpace =
                        heightLayer == WorldHelper.HeightLayer.SURFACE ||
                                heightLayer == WorldHelper.HeightLayer.SPACE;
                // surface/space level
                if (isSurfaceOrSpace) {
                    // monster spawn rate when an event is present is increased
                    if (EventAndTime.currentEvent != null && EventAndTime.currentEvent != EventAndTime.Events.NONE) {
                        mobSpawnRate += 1.25;
                    }
                    // monster spawn rate massively increase when near a celestial pillar
                    for (CelestialPillar pillar : EventAndTime.pillars.values()) {
                        if (pillar.getBukkitEntity().getWorld() != ply.getWorld())
                            continue;
                        if (pillar.getBukkitEntity().getLocation().distanceSquared(ply.getLocation()) < CelestialPillar.EFFECTED_RADIUS_SQR) {
                            mobSpawnRate += 2;
                            break;
                        }
                    }
                }
                // underground or lower
                else {
                    // mob spawning rate hugely increased in dungeon or lizard temple IF BOSS NOT ALIVE
                    switch (WorldHelper.BiomeType.getBiome(ply)) {
                        case DUNGEON:
                        case TEMPLE:
                            if (! isBossAlive)
                                mobSpawnRate += 1.6;
                    }
                }
                // spawn monster. Note that mobSpawnRate above is expected monster amount per second
                mobSpawnRate *= spawnRateAdjustFactor;
                for (int i = 0; i < MathHelper.randomRound(mobSpawnRate); i ++) {
                    MonsterHelper.naturalMobSpawning(ply);
                }
            }
        }, 0, delay);
    }
    // block collision mechanism
    private static void handleContactBlockEffect(Player ply, Block block, boolean isFirstContact) {
        switch (block.getType()) {
            // meteorite
            case RED_GLAZED_TERRACOTTA:
                EntityHelper.applyEffect(ply, "燃烧", 300);
                break;
            // hellstone
            case MAGMA:
                EntityHelper.applyEffect(ply, "狱炎", 100);
                break;
            // astral ore
            case REDSTONE_BLOCK:
                EntityHelper.applyEffect(ply, "幻星感染", 200);
                break;
            // auric ore
            case YELLOW_GLAZED_TERRACOTTA:
                if (isFirstContact) {
                    Vector knockbackDir = ply.getEyeLocation().subtract( block.getLocation().add(0.5, 0.5, 0.5) ).toVector();
                    MathHelper.setVectorLength(knockbackDir, 2);
                    EntityMovementHelper.setVelocity(ply, knockbackDir);
                    block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,
                            SoundCategory.BLOCKS, 3f, 1f);
                    EntityHelper.applyEffect(ply, "带电", 100);
                }
                break;
        }
    }
    // get the blocks colliding the player, handle on-hit events and return a summary of collided block types
    private static HashMap<Material, HashSet<Integer>> handleContactBlocks(Player ply) {
        HashMap<Material, HashSet<Integer>> result = new HashMap<>();
        EntityPlayer plyNMS = ((CraftPlayer) ply).getHandle();
        net.minecraft.server.v1_12_R1.World worldNMS = plyNMS.getWorld();
        World worldBkt = ply.getWorld();
        AxisAlignedBB plyBB = plyNMS.getBoundingBox().grow(0.05, 0.05, 0.05);
        // algorithm from Net.Minecraft.Server World.class
        int i = net.minecraft.server.v1_12_R1.MathHelper.floor(plyBB.a);
        int j = net.minecraft.server.v1_12_R1.MathHelper.f(plyBB.d);
        int k = net.minecraft.server.v1_12_R1.MathHelper.floor(plyBB.b);
        int l = net.minecraft.server.v1_12_R1.MathHelper.f(plyBB.e);
        int i1 = net.minecraft.server.v1_12_R1.MathHelper.floor(plyBB.c);
        int j1 = net.minecraft.server.v1_12_R1.MathHelper.f(plyBB.f);
        if (worldNMS.areChunksLoadedBetween(new BlockPosition(i, k, i1), new BlockPosition(j, l, j1), true)) {
            BlockPosition.PooledBlockPosition blockposition_pooledblockposition = BlockPosition.PooledBlockPosition.s();

            for (int k1 = i; k1 < j; ++k1) {
                for (int l1 = k; l1 < l; ++l1) {
                    for (int i2 = i1; i2 < j1; ++i2) {
                        blockposition_pooledblockposition.f(k1, l1, i2);
                        IBlockData iblockdata = worldNMS.getType(blockposition_pooledblockposition);
                        boolean inContact = true;
                        net.minecraft.server.v1_12_R1.Material material = iblockdata.getMaterial();
                        // handle liquid surface y level
                        if (net.minecraft.server.v1_12_R1.Material.WATER.equals(material) || net.minecraft.server.v1_12_R1.Material.LAVA.equals(material)) {
                            double d0 = (float) (l1 + 0.9) - BlockFluids.b(iblockdata.get(BlockFluids.LEVEL));
                            if (d0 < plyBB.b)
                                inContact = false;
                        }
                        if (inContact) {
                            Block bukkitBlock = worldBkt.getBlockAt(k1, l1, i2);
                            Material blockMat = bukkitBlock.getType();
                            boolean isFirstContact = false;
                            if (! result.containsKey(blockMat)) {
                                result.put(blockMat, new HashSet<>());
                                isFirstContact = true;
                            }
                            result.get(blockMat).add((int) bukkitBlock.getData());
                            handleContactBlockEffect(ply, bukkitBlock, isFirstContact);
                        }
                    }
                }
            }
        }
        return result;
    }
    // account for velocity change by block collision
    private static Vector accountVelChangeMovement(Player ply, Vector vel) {
        Vector aftVel = ply.getVelocity();
        // ignore vertical component
        double afterY = aftVel.getY();
        aftVel.setY(0);
        // adjust the length of horizontal component according to previously saved move direction
        double aVLS = aftVel.lengthSquared();
        if (aVLS > 1e-9) {
            Vector projected = MathHelper.vectorProjection(aftVel, vel);
            double factor = Math.sqrt(projected.lengthSquared() / aVLS);

            aftVel.multiply(factor);
        }
        // reapply vertical component: if on ground or touched ceiling (afterY < 0 while vel.getY > 0), clear y component
        boolean hitBlock = false;
        if (ply.isOnGround()) {
            hitBlock = vel.getY() < 0;
        }
        else {
            // if vel.getY is significant, consider this as hitting a ceiling
            // if the threshold is set to 0, the player would have trouble taking off
            if (afterY < 0 && vel.getY() > 0.08)
                hitBlock = true;
        }
        aftVel.setY(hitBlock ? 0 : vel.getY());
        return aftVel;
    }
    // grappling hook
    private static Vector grapplingHookMovement(Player ply, Vector vel) {
        try {
            ArrayList<Entity> hooks = (ArrayList<Entity>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS).value();
            boolean shouldRemoveHooks = false;
            String hookItemName = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_ITEM).asString();
            String[] hookNameInfo = ItemHelper.splitItemName(ply.getInventory().getItemInOffHand());
            if (!PlayerHelper.isProperlyPlaying(ply)) {
                // not survival mode or not logged in etc
                shouldRemoveHooks = true;
            }
            else if (!hookNameInfo[1].equals(hookItemName)) {
                // hook item do not match
                shouldRemoveHooks = true;
            }
            else if (hooks.size() == 0 || !hooks.get(0).getWorld().equals(ply.getWorld())) {
                // no grappling hook or world changed
                shouldRemoveHooks = true;
            }
            // remove hooks and return original velocity if needed
            if (shouldRemoveHooks) {
                for (Entity hook : hooks) hook.remove();
                ply.setGravity(true);
                return vel;
            }
            else {
                // get center location information
                World plyWorld = ply.getWorld();
                Location center = new Location(plyWorld, 0, 0, 0);
                int hookedAmount = 0;
                YmlHelper.YmlSection config = TerrariaHelper.hookConfig;
                double hookReach = config.getDouble(hookItemName + ".reach", 12),
                        hookPullSpeed = config.getDouble(hookItemName + ".playerSpeed", 0.1);
                hookReach = hookReach * hookReach * 4;
                HashSet<Entity> hooksToRemove = new HashSet<>();
                for (Entity hook : hooks) {
                    // if the hook is too far away
                    if (ply.getLocation().subtract(hook.getLocation()).lengthSquared() > hookReach) {
                        hook.remove();
                    }
                    // if the hook is removed by any mean, schedule its removal from the list
                    if (hook.isDead()) {
                        hooksToRemove.add(hook);
                        continue;
                    }
                    // if the hook is in place
                    if (hook.getVelocity().lengthSquared() < 1e-5) {
                        // only account for the pulling force when the hook is in a solid block
                        if (((CraftProjectile) hook).getHandle().inBlock()) {
                            center.add(hook.getLocation());
                            hookedAmount++;
                        }
                        // if the block is not solid anymore, remove this hook.
                        else {
                            hook.remove();
                        }
                    }
                    // draw chain
                    Location drawCenterLoc = ply.getLocation().add(0, 1, 0);
                    Vector dVec = hook.getLocation().subtract(drawCenterLoc).toVector();
                    if (dVec.lengthSquared() > 0) {
                        double dVecLength = dVec.length();
                        // offset vector prevents color block spamming the screen
                        Vector offsetVector = dVec.clone().multiply(1.5 / dVecLength);
                        GenericHelper.handleParticleLine(dVec, drawCenterLoc.add(offsetVector),
                                new GenericHelper.ParticleLineOptions()
                                        .setVanillaParticle(false)
                                        .setSnowStormRawUse(false)
                                        .setLength(dVecLength - 1.5)
                                        .setTicksLinger(2)
                                        .setParticleColor(EntityHelper.getMetadata(hook,
                                                EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_COLOR).asString()));
                    }
                }
                // remove hooks out of range
                for (Entity hook : hooksToRemove) hooks.remove(hook);
                // handle movement
                if (hookedAmount >= 1) {
                    ply.setGravity(false);
                    resetPlayerFlightTime(ply);
                    ply.setFallDistance(0);
                    center.multiply(1 / (double) hookedAmount);
                    Vector thrust = center.subtract(ply.getEyeLocation()).toVector();
                    // if length * 1/2 > hookPullSpeed
                    if (thrust.lengthSquared() > hookPullSpeed * hookPullSpeed * 4)
                        thrust.normalize().multiply(hookPullSpeed);
                    else if (thrust.lengthSquared() > 0)
                        thrust.multiply(0.5);
                    return thrust;
                }
                // no hook attached to ground
                else {
                    ply.setGravity(true);
                    return vel;
                }
            }
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Player Helper] threadGrapplingHook ", e);
            return vel;
        }
    }
    // wing & jump speed tweak
    private static double getHorMoveSpd(Player ply, HashSet<String> accessorySet, double speedMulti, double speedMultiWing) {
        double groundSneakMulti = (ply.isOnGround() && ply.isSneaking()) ? 0.35 : 1d;
        for (String accessory : accessorySet) {
            ConfigurationSection wingSection = TerrariaHelper.wingConfig.getConfigurationSection(accessory);
            // if the accessory does not provide any flying/extra jump
            if (wingSection == null)
                continue;
            // only boots can apply for on-ground players; this "short circuit" operation will make sure wing-boots are not faster on ground
            if (ply.isOnGround()) {
                if (! wingSection.getBoolean("isBoot", false))
                    continue;
            }
            // wings will apply for players off ground
            else {
                if (! wingSection.getBoolean("isWing", false))
                    continue;
            }

            // return the speed
            double multiUse = wingSection.getBoolean("isWing", false) ? speedMultiWing : speedMulti;
            return wingSection.getDouble("horizontalSpeed", 0.5d) * multiUse * groundSneakMulti;
        }
        // default speed
        return 0.25 * speedMulti * groundSneakMulti;
    }
    private static Vector wingMovement(Player ply, Vector vel) {
        // reset thrust variable if player is on ground
        if (ply.isOnGround()) {
            resetPlayerFlightTime(ply);
        }
        // setup variables
        int thrustIndex = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_INDEX).asInt();
        int thrustProgress = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_PROGRESS).asInt();
        // if this is the first thrust, save the progress
        HashSet<String> accessorySet = (HashSet<String>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.ACCESSORIES).value();
        if (thrustIndex == 0 && thrustProgress == 0)
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES_FLIGHT_BACKUP,
                    accessorySet.clone());
        int extraJumpTime = 0;
        int thrustProgressMax = 0;
        double verticalSpeed = 0.5, horizontalSpeed;
        double maxAcceleration = 0.5;
        List<String> accessory = (List<String>) EntityHelper.getMetadata(ply,
                EntityHelper.MetadataName.ACCESSORIES_LIST).value();
        Set<String> availableAccessory = (Set<String>) EntityHelper.getMetadata(ply,
                EntityHelper.MetadataName.ACCESSORIES_FLIGHT_BACKUP).value();
        String accessoryUsed = "";
        boolean isThrusting = ply.getScoreboardTags().contains("temp_thrusting");
        boolean isWing = false;
        boolean gliding = false;
        // if the player is mounting
        Entity mount = PlayerHelper.getMount(ply);
        if (mount != null) {
            // after mounting, accessory (wings etc.) can not be used until landed.
            thrustIndex = 999999;
        }
        // the player is not on mount
        else {
            // speed multiplier
            double speedMulti = 1d, speedMultiWing = 1d;
            {
                HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(ply);
                speedMulti = attrMap.getOrDefault("speedMulti", 1d);
                speedMultiWing = speedMulti;
                // speed multiplier that exceeds 100% are only 10% as effective on wings, 20% as effective otherwise.
                if (speedMulti > 1d) {
                    speedMulti = 1 + (speedMulti - 1) * 0.2;
                    speedMultiWing = 1 + (speedMultiWing - 1) * 0.1;
                }
            }
            // set up horizontal speed
            horizontalSpeed = getHorMoveSpd(ply, accessorySet, speedMulti, speedMultiWing);
            // if the player is not flying
            if (!isThrusting) {
                // the player can not save a part of jumping progress for a "double jump" after leaving the ground
                if (thrustIndex < 0) {
                    thrustIndex = 0;
                    thrustProgress = 0;
                }
            }
            // if the space bar is being pressed and the player should continue jumping/flying
            else {
                // handle jump/fly from ground
                if (ply.isOnGround()) {
                    thrustIndex = -1;
                    // if the player has any wing accessory, do not jump.
                    for (String checkAcc : accessory) {
                        ConfigurationSection wingSection = TerrariaHelper.wingConfig.getConfigurationSection(checkAcc);
                        // if the accessory does not provide any flying/extra jump, do not consider it
                        if (wingSection == null)
                            continue;
                        // if the first flying accessory is a pair of wings, do not jump
                        if (wingSection.getBoolean("isWing", false)) {
                            thrustIndex = 0;
                        }
                        // we are only checking the first flying accessory
                        break;
                    }
                }
                // if the player is not jumping on ground
                if (thrustIndex >= 0) {
                    // determine the accessory to use
                    int thrustIndexInitial = thrustIndex;
                    for (; thrustIndex < accessory.size(); thrustIndex++) {
                        String currAcc = accessory.get(thrustIndex);
                        // the accessory was not around when the player started flying
                        // note that the first part is necessary, because
                        // the accessory is removed from set as soon as the player starts using the accessory to fly, not afterwards
                        if (thrustIndexInitial != thrustIndex && !availableAccessory.contains(currAcc))
                            continue;
                        ConfigurationSection wingSection = TerrariaHelper.wingConfig.getConfigurationSection(currAcc);
                        // if the accessory does not provide any flying/extra jump
                        if (wingSection == null)
                            continue;
                        // prevent player from exploiting one pair of wings by moving around accessories
//                                Bukkit.broadcastMessage("AVAILABLE ACCESSORIES: " + availableAccessory);
                        availableAccessory.remove(currAcc);
//                                Bukkit.broadcastMessage("AVAILABLE ACCESSORIES AFTER DELETION: " + availableAccessory);
                        // the accessory should be good to go here
                        double speedMultiplier = wingSection.contains("flightTime") ? speedMultiWing : speedMulti;
                        extraJumpTime = wingSection.getInt("extraJumpTime", 0);
                        thrustProgressMax = wingSection.getInt("flightTime", 0);
                        verticalSpeed = wingSection.getDouble("verticalSpeed", 1d) * speedMultiplier;
                        maxAcceleration = wingSection.getDouble("maxAcceleration", 0.5d) * speedMultiplier;
                        horizontalSpeed = wingSection.getDouble("horizontalSpeed", 0.5d) * speedMultiplier;
                        isWing = wingSection.getBoolean("isWing", false);
                        accessoryUsed = currAcc;
//                                Bukkit.broadcastMessage("FLYING WITH ACCESSORY, " + thrustIndex + "(" + accessoryUsed);
                        break;
                    }
                    // no rocket boots or double jump on ground
                    if (!isWing && ply.isOnGround()) {
                        extraJumpTime = 0;
                        thrustProgressMax = 0;
                    }
                }
                // if the player is jumping
                else {
                    thrustProgressMax = 6;
                    verticalSpeed *= speedMulti;
                    maxAcceleration = 0.6;
                }
                // if no valid accessory is being selected, attempt to glide
                if (extraJumpTime + thrustProgressMax <= 0) {
//                            Bukkit.broadcastMessage("ATTEMPT JUMP");
                    for (String currAcc : accessory) {
                        ConfigurationSection wingSection = TerrariaHelper.wingConfig.getConfigurationSection(currAcc);
                        if (wingSection == null)
                            continue;
                        isWing = wingSection.getBoolean("isWing", false);
                        // non-wing flying accessories should not be able to glide
                        if (!isWing)
                            continue;
                        // the accessory should be a wing here
//                                    Bukkit.broadcastMessage("GLIDE");
                        gliding = true;
                        thrustProgressMax = 999999;
                        verticalSpeed = 0.5;
                        accessoryUsed = currAcc;
                        break;
                    }
                }
            }
            // update velocity
            Vector moveDir = new Vector();
            {
                boolean movingHor = false, movingVer = false;
                // horizontal movement
                double horizontalMoveYaw = getPlayerMoveYaw(ply);
                if (horizontalMoveYaw < 1e5) {
                    movingHor = true;
                    moveDir.add(MathHelper.vectorFromYawPitch_approx(horizontalMoveYaw, 0).multiply(horizontalSpeed));
                }
                // thrust tick and misc mechanism
                if (isThrusting) {
                    thrustProgressMax *= AttributeHelper.getAttrMap(ply).getOrDefault("flightTimeMulti", 1d);
                    if (thrustProgress < extraJumpTime + thrustProgressMax) {
//                        Bukkit.broadcastMessage(thrustProgress + "/" + extraJumpTime + ", " + thrustProgressMax);
                        ply.setFallDistance(0);
                        // vertical movement direction
                        {
                            if (gliding) moveDir.add( new Vector(0, verticalSpeed * -1, 0) );
                            else moveDir.add( new Vector(0, verticalSpeed, 0) );
                        }
                        // extra jump
                        if (thrustProgress < extraJumpTime) {
                            // particles
                            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                                    for (int i = 0; i < 4; i++)
                                        ply.getWorld().spawnParticle(Particle.CLOUD,
                                                ply.getLocation().add((double) xOffset / 2, 0, (double) zOffset / 2)
                                                        .add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5),
                                                0, xOffset, 0, zOffset, 0.1);
                                }
                            }
                            // extra jump do not get consumed continuously
                            ply.removeScoreboardTag("temp_thrusting");
                        }
                        // rocket boots / wings
                        else {
                            // particles
                            switch (accessoryUsed) {
                                case "火箭靴":
                                case "幽灵靴": {
                                    ply.getWorld().spawnParticle(Particle.CLOUD,
                                            ply.getLocation()
                                                    .add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5),
                                            0, 0, -1, 0, 0.175);
                                    break;
                                }
                            }
                            // make sure that gravity does not work against the wings
                            ply.setGravity(false);
                        }
                        if (!gliding) {
                            thrustProgress++;
                        }
                    }
                    // flight time depleted
                    if (thrustProgress >= extraJumpTime + thrustProgressMax) {
//                    Bukkit.broadcastMessage("FLIGHT TIME DEPLETED!");
                        // if the player is using accessory to fly
                        ply.setGravity(true);
                        if (!ply.isInsideVehicle()) {
                            thrustProgress = 0;
                            thrustIndex++;
                        }
                        // so that the next flying accessory do not automatically start using
                        ply.removeScoreboardTag("temp_thrusting");
                    }
                }

                // gravity if applicable
                if (Math.abs(moveDir.getY()) < 1e-5) {
                    vel.subtract(new Vector(0, 0.08, 0));
                }
                else
                    movingVer = true;
                // handle speed decay multiplier
                {
                    double horVelMulti;
                    if (ply.isOnGround() && ! movingHor)
                        horVelMulti = 0.6;
                    else
                        horVelMulti = 0.9;
                    double y = vel.getY();
                    vel.multiply(horVelMulti);
                    vel.setY(y * 0.99);
                }
//                Bukkit.broadcastMessage(horizontalSpeed + ", " + verticalSpeed);

                // if the player is moving, change the player's velocity according to targeted move direction
                if (moveDir.lengthSquared() > 1e-5) {
                    Vector acceleration = moveDir.clone().subtract(vel);
                    double accLength = acceleration.length();
                    // maxAcceleration has already accounted for multiplier in the section above
                    if (accLength > maxAcceleration) {
                        acceleration.multiply(maxAcceleration / accLength);
                    }
                    // do not change the corresponding component (most likely unreasonably de-accelerate) if not moving in the direction.
                    if (! movingHor) {
                        acceleration.setX(0);
                        acceleration.setZ(0);
                    }
                    // if moving horizontally, drop the "dragging" component in the target direction
                    else {
                        double moveY = moveDir.getY(), velY = vel.getY();
                        moveDir.setY(0);
                        vel.setY(0);
                        // angle between move direction and velocity <= 90
                        if (moveDir.dot(vel) > -1e-5) {
                            Vector accComp = MathHelper.vectorProjection(vel, acceleration);
                            // if the acceleration is slowing down the speed
                            if (accComp.dot(vel) < 0) {
                                acceleration.subtract(accComp);
                            }
                        }
                        moveDir.setY(moveY);
                        vel.setY(velY);
                    }
                    if (! movingVer)
                        acceleration.setY(0);
                    // update the player's speed
                    vel.add(acceleration);
                }
            }


        }
        // save variables
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_INDEX, thrustIndex);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_THRUST_PROGRESS, thrustProgress);
        // return updated velocity
        return vel;
    }
    // underwater movement & oxygen bar
    private static Vector underwaterMovement(Player ply, Vector vel, HashMap<Material, HashSet<Integer>> contactBlocks) {
        // basic settings
        boolean isInLiquid = false, isInLava = false, submerged = false;
        if (contactBlocks.containsKey(Material.LAVA) || contactBlocks.containsKey(Material.STATIONARY_LAVA)) {
            isInLiquid = true;
            isInLava = true;
        }
        else if (contactBlocks.containsKey(Material.WATER) || contactBlocks.containsKey(Material.STATIONARY_WATER))
            isInLiquid = true;
        switch (ply.getEyeLocation().getBlock().getType()) {
            case WATER:
            case STATIONARY_WATER:
            case LAVA:
            case STATIONARY_LAVA:
                submerged = true;
        }
        WorldHelper.WaterRegionType waterRegion = WorldHelper.WaterRegionType.getWaterRegionType(ply.getLocation(), isInLava);
        // oxygen will deplete in the abyss no matter what
        switch (waterRegion) {
            case ABYSS_1:
            case ABYSS_2:
            case ABYSS_3:
                isInLiquid = true;
                submerged = true;
                break;
        }
        double waterAffinity = AttributeHelper.getAttrMap(ply).getOrDefault("waterAffinity", 0d);
        // oxygen handling
        {
            int oxygen = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_AIR).asInt();
            // update variable
            if (submerged) {
                double oxygenDepleteRate = Math.max(waterRegion.oxygenDepletionLevel - waterAffinity, 0);
                oxygen -= MathHelper.randomRound(oxygenDepleteRate / 2d);
                // drowning damage
                if (oxygen <= 0) {
                    oxygen = 0;
                    DamageHelper.handleDamage(ply, ply, 10 * Math.max(oxygenDepleteRate, 1),
                            DamageHelper.DamageReason.DROWNING);
                }
            }
            else {
                // the entire air bar will gradually recover in 5 seconds
                oxygen = Math.min(oxygen + 1, PLAYER_MAX_OXYGEN);
            }
            // save variable
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_AIR, oxygen);
        }
        // movement slow handling
        {
            // water affinity partially removes slow introduced by water
            double extraDragModifier = isInLava ? 1 : 0.25;
            double velMulti;
            MetadataValue mdv = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_VELOCITY_MULTI);
            if (mdv != null)
                velMulti = mdv.asDouble();
            else
                velMulti = 1d;
            if (isInLiquid) {
                // e^x / (e^x + 1), sigmoid
                double ex = Math.pow(Math.E, waterAffinity - extraDragModifier);
                double multiplier = ex / (ex + 1);
                velMulti *= multiplier;
                // water also vertically slows the player. The slow multiplier is applied over the duration of 20 ticks.
                vel.setY(vel.getY() * Math.pow(multiplier, 0.05));
            }
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_VELOCITY_MULTI, velMulti);
        }
        return vel;
    }
    // saving location info
private static void saveMovementData(Player ply, Vector velocity, Vector acceleration) {
    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_VELOCITY_ACTUAL, velocity);
    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_ACCELERATION, acceleration);
}
    // movement, block collision, oxygen bar etc.
    public static void threadExtraTicking() {
        AtomicLong idx = new AtomicLong(0);
        // every 1 tick
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                // validate the current player
                if (!PlayerHelper.isProperlyPlaying(ply))
                    continue;

                // movement
                {
                    // get contact blocks
                    HashMap<Material, HashSet<Integer>> contactBlocks = handleContactBlocks(ply);
                    // player that are sneaking on ground move more slowly
                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_VELOCITY_MULTI,
                            ply.isOnGround() && ply.isSneaking() ? 0.5 : 1);

                    // get player speed
                    Vector plySpd = getPlayerRawVelocity(ply);
                    // calculate acceleration info before the speed is updated
                    Vector plyWorldSpd = ply.getVelocity();
                    Vector plyAcc = plyWorldSpd.clone().subtract((Vector) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_VELOCITY_ACTUAL).value());
                    // account for speed direction changed by basic tick (block collision)
                    plySpd = accountVelChangeMovement(ply, plySpd);

                    // wing/jump movement
                    plySpd = wingMovement(ply, plySpd);
                    // grappling hook (override wing/jump)
                    plySpd = grapplingHookMovement(ply, plySpd);
                    // underwater speed adjusting & oxygen bar
                    plySpd = underwaterMovement(ply, plySpd, contactBlocks);

                    // update speed
                    EntityMovementHelper.setVelocity(ply, plySpd);
                    // save location info
                    saveMovementData(ply, plyWorldSpd.clone(), plyAcc);
                }
            }
            // camera
            PlayerPOVHelper.tick(idx.get());
            idx.getAndIncrement();
        }, 0, 1);
    }
    public static void threadRegen() {
        boolean debugMessage = false;
        int delay = 2;
        double perTickMulti = (double)delay / 20;
        // every 2 ticks (1/10 second)
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (ply.getScoreboardTags().contains("unauthorized")) continue;
                try {
                    MetadataValue respawnCD = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.RESPAWN_COUNTDOWN);
                    // respawn countdown & revive
                    if (respawnCD != null) {
                        int ticksRemaining = respawnCD.asInt();
                        ticksRemaining -= delay;
                        ticksRemaining = Math.min(ticksRemaining, getReviveTicks(ply));
                        if (ticksRemaining > 0) {
                            ply.setGameMode(GameMode.SPECTATOR);
                            ply.setFlySpeed(0);
                            ply.setFallDistance(0);
                            // update action bar only when needed
                            if ((ticksRemaining + delay) / 20 != ticksRemaining / 20) sendActionBar(ply, "§a重生倒计时： " + ticksRemaining / 20);
                            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.RESPAWN_COUNTDOWN, ticksRemaining);
                        } else {
                            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.RESPAWN_COUNTDOWN, null);
                            ply.setGameMode(GameMode.SURVIVAL);
                            ply.teleport(getSpawnLocation(ply), PlayerTeleportEvent.TeleportCause.PLUGIN);
                            ply.sendTitle("", "", 0, 0, 0);
                            sendActionBar(ply, "");
                            // reset minion index, sentry index etc.
                            initPlayerStats(ply, false);
                            // revive invulnerability ticks - 3 seconds
                            int dmgImmuneTicks = 60;
                            DamageHelper.DamageType[] dmgImmuneTypes = {
                                    DamageHelper.DamageType.MELEE, DamageHelper.DamageType.ARROW,
                                    DamageHelper.DamageType.BULLET, DamageHelper.DamageType.ROCKET, DamageHelper.DamageType.MAGIC};
                            for (DamageHelper.DamageType type : dmgImmuneTypes)
                                EntityHelper.handleEntityTemporaryScoreboardTag(ply,
                                        DamageHelper.getInvulnerabilityTickName(type), dmgImmuneTicks);
                        }
                    } else {
                        if (!isProperlyPlaying(ply)) continue; // waiting to revive etc.
                        // basic variable setup
                        HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(ply);
                        HashSet<String> accessories = getAccessories(ply);
                        HashMap<String, Integer> effectMap = EntityHelper.getEffectMap(ply);
                        Vector velocity = ply.getVelocity();
                        // gravity = 0.08, gravity squared = 0.0064
                        boolean moved = velocity.lengthSquared() > 0.01;
                        // attempt regenerating the player's damage barrier
                        {
                            if (attrMap.getOrDefault("barrierMax", 0d) > 0d &&
                                    (! effectMap.containsKey("保护矩阵充能")) && (! effectMap.containsKey("保护矩阵")) )
                                EntityHelper.applyEffect(ply, "保护矩阵", 1);
                        }
                        // make sure mana do not exceed maximum
                        {
                            int level = Math.min(ply.getLevel(), getMaxMana(ply));
                            ply.setLevel(level);
                        }
                        boolean usingItem = ply.getScoreboardTags().contains("temp_useCD");
                        // health regen
                        {
                            // init variables
                            double healthRegenTime = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.REGEN_TIME).asDouble();
                            double effectiveRegenTime;
                            if (healthRegenTime <= 100) effectiveRegenTime = 0;
                            else if (healthRegenTime < 600) effectiveRegenTime = (healthRegenTime - 100) / 100;
                            else effectiveRegenTime = 5 + ((healthRegenTime - 600) / 200);
                            effectiveRegenTime = Math.floor(effectiveRegenTime);
                            if (debugMessage) ply.sendMessage(effectiveRegenTime + "|" + healthRegenTime);
                            double maxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            double regenerationRate = ((maxHealth * 0.85 / 800) + 0.15) * effectiveRegenTime;
                            regenerationRate *= moved ? 0.5 : 1.25;
                            // additional hardcoded accessories etc
                            double additionalHealthRegen = attrMap.getOrDefault("regen", 0d); // from accessories etc
                            if (!moved && accessories.contains("闪亮石")) {
                                regenerationRate += 8;
                                healthRegenTime += delay * 4;
                            }
                            // regen
                            double regenAmount = (regenerationRate + additionalHealthRegen) * perTickMulti;
//                            System.out.println(regenAmount + "(" + regenerationRate + ", " + additionalHealthRegen + "x" + perTickMulti);
//                            System.out.println(velocity.lengthSquared() + ", " + moved);
                            if (accessories.contains("再生护符")
                                    && ply.getScoreboardTags().contains(PlayerHelper.TAG_SWITCHED_SWITCHABLE_ACCESSORY)
                                    && (ply.getHealth() + regenAmount) * 2 >= maxHealth) {
                                regenAmount = - Math.abs(regenAmount);
                            }
                            if (regenAmount > 0) {
                                regenAmount *= attrMap.getOrDefault("regenMulti", 1d);
                            }
                            double healthAmount = Math.min(ply.getHealth() + regenAmount, maxHealth);
                            if (healthAmount > 0) {
                                ply.setHealth(healthAmount);
                                healthRegenTime += delay;
                                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.REGEN_TIME, Math.min(healthRegenTime, 1201));
                            } else {
                                DamageHelper.handleDeath(ply, ply, ply, DamageHelper.DamageType.NEGATIVE_REGEN, DamageHelper.DamageReason.DEBUFF, null);
                            }
                        }
                        // mana regen
                        {
                            // fixed mana regen; do NOT get increased whatsoever.
                            int fixedManaRegen = MathHelper.randomRound( attrMap.getOrDefault("manaRegenFixed", 0d) * perTickMulti );
                            restoreMana(ply, fixedManaRegen, false);
                            // below: natural mana regen
                            double manaRegenDelay = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_DELAY).asDouble();
                            double manaRegenCounter = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_COUNTER).asDouble();
                            boolean hasManaRegenBand = accessories.contains("魔力再生手环");
                            boolean hasManaRegenPotionEffect = effectMap.containsKey("魔力再生");
                            if (manaRegenDelay > 0) {
                                // waiting for regen CD
                                manaRegenDelay -= moved ? delay : delay * 2;
                                if (hasManaRegenBand) manaRegenDelay -= delay;
                                if (hasManaRegenPotionEffect) manaRegenDelay -= delay;
                                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_DELAY, manaRegenDelay);
                            } else {
                                // regeneration
                                double manaRegenBonus = attrMap.getOrDefault("manaRegen", 0d);
                                double maxMana = getMaxMana(ply);
                                // players with mana regen buff regenerates mana as if their mana is full
                                double manaRatio = hasManaRegenPotionEffect ? 1d : (double) ply.getLevel() / maxMana;
                                double manaRegenRate = ((maxMana * (moved ? 1d / 6 : 1d / 2)) + 1 + manaRegenBonus) * (manaRatio * 0.8 + 0.2) * 1.15;
                                // if the player is currently using mana-consuming item: no natural mana regen; bonus regen is reduced
                                if (attrMap.getOrDefault("manaUse", 0d) > 0 && usingItem) {
                                    manaRegenRate = manaRegenBonus * (manaRatio * 0.8 + 0.2);
                                }
                                if (ply.getLevel() < maxMana) {
                                    manaRegenCounter += manaRegenRate * delay * attrMap.getOrDefault("manaRegenMulti", 1d);
                                    double regenAmount = Math.floor(manaRegenCounter / 40);
                                    manaRegenCounter %= 40;
                                    int levelResult = ply.getLevel() + (int) regenAmount;
                                    ply.setLevel((int) Math.min(levelResult, maxMana));
                                    if (ply.getLevel() >= maxMana)
                                        ply.playSound(ply.getLocation(), Sound.BLOCK_NOTE_PLING, SoundCategory.PLAYERS,2, 2);
                                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_COUNTER, manaRegenCounter);
                                }
                            }
                        }
                        // stealth regen
                        if (! usingItem) {
                            double stealthMax = getMaxStealth(ply);
                            double stealthRegen = stealthMax * delay / 80 * attrMap.getOrDefault("stealthRegenMulti", 1d);
                            restoreStealth(ply, stealthRegen);
                        }
                    }
                } catch (Exception e) {
                    TerrariaHelper.LOGGER.log(Level.SEVERE, "[Player Helper] threadRegen ", e);
                }
            }
        }, 0, delay);
    }
    public static void threadSaveInventories() {
        // thread to save player inventories every 5 seconds; trashcan item update is also handled here.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(),
                () -> {
                    for (Player ply : Bukkit.getOnlinePlayers()) {
                        PlayerHelper.saveData(ply);
                        updateTrashBinInfo(ply);
                    }
                }, 100, 100);
    }
    private static int getSpecialBiomeBlockType(Block footBlock, BlockFace dir, boolean isInUndergroundOrCavern) {
        Block blockToCheck = footBlock;
        // lizard/dungeon
        if (isInUndergroundOrCavern) {
            for (int i = 0; i < 50; i ++) {
                if (blockToCheck.getType() == Material.SMOOTH_BRICK) {
                    int data = blockToCheck.getData();
                    switch (data) {
                        case 1:
                            // lizard
                            return 2;
                        case 2:
                            // dungeon
                            return 1;
                    }
                }
                blockToCheck = blockToCheck.getRelative(dir);
            }
        }
        // meteor
        else {
            for (int i = 0; i < 5; i ++) {
                // meteor
                if (blockToCheck.getType() == Material.RED_GLAZED_TERRACOTTA)
                    return 3;
                blockToCheck = blockToCheck.getRelative(dir);
            }
        }
        // not a special biome
        return 0;
    }
    public static void threadSpecialBiome() {
        BlockFace[] BLOCK_FACES_TO_CHECK = {
                BlockFace.UP, BlockFace.DOWN,
                BlockFace.WEST, BlockFace.EAST,
                BlockFace.NORTH, BlockFace.SOUTH
        };
        // every 20 ticks (1 second)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (Player ply : Bukkit.getOnlinePlayers()) {
                if (isProperlyPlaying(ply)) {
                    // check height layer
                    boolean isInUndergroundOrCavern;
                    switch (WorldHelper.HeightLayer.getHeightLayer(ply.getLocation())) {
                        case UNDERGROUND:
                        case CAVERN:
                            isInUndergroundOrCavern = true;
                            break;
                        default:
                            isInUndergroundOrCavern = false;
                    }
                    WorldHelper.BiomeType plyBiome = WorldHelper.BiomeType.NORMAL;
                    // check dungeon and lizard temple
                    int[] specialBlocks = {0, 0, 0, 0};
                    Block footBlock = ply.getLocation().getBlock();
                    for (BlockFace dir : BLOCK_FACES_TO_CHECK) {
                        int index = getSpecialBiomeBlockType(footBlock, dir , isInUndergroundOrCavern);
                        specialBlocks[ index ] ++;
                    }
                    // dungeon; apply debuff when skeletron is not yet defeated
                    if (specialBlocks[1] >= 3) {
                        plyBiome = WorldHelper.BiomeType.DUNGEON;
                        if (! hasDefeated(ply, BossHelper.BossType.SKELETRON)) {
                            EntityHelper.applyEffect(ply, "带电", 300);
                        }
                    }
                    // lizard temple; apply debuff when plantera is not yet defeated
                    else if (specialBlocks[2] >= 3) {
                        plyBiome = WorldHelper.BiomeType.TEMPLE;
                        if (! hasDefeated(ply, BossHelper.BossType.PLANTERA)) {
                            EntityHelper.applyEffect(ply, "龙焰", 300);
                        }
                    }
                    else if (specialBlocks[3] > 1)
                        plyBiome = WorldHelper.BiomeType.METEOR;
                    EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BIOME, plyBiome);
                }
            }
        }, 0, 20);
    }
    // others
    public static int getReviveTicks(Player ply) {
        // respawn time, default to 3 seconds and increases if during event / boss is alive
        int respawnSec = EventAndTime.currentEvent == EventAndTime.Events.NONE ? 3 : 5;
        for (ArrayList<LivingEntity> bossList : BossHelper.bossMap.values()) {
            HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targets =
                    (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>)
                            EntityHelper.getMetadata(bossList.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
            // if the current boss has the player as target, 15 seconds respawn time for each player, up to 1 minute
            if (targets.containsKey(ply.getUniqueId())) {
                // note: max would take the longest respawn time across all active bosses
                respawnSec = Math.max(respawnSec, Math.min(targets.size() * 15, 60));
            }
        }
        return respawnSec * 20;
    }
    public static double getEnergyShielding(Player ply) {
        HashMap<String, Integer> plyEffects = EntityHelper.getEffectMap(ply);
        return plyEffects.getOrDefault("保护矩阵", 0) * 0.5d;
    }
    public static double getDefenceDamage(Player ply) {
        HashMap<String, Integer> plyEffects = EntityHelper.getEffectMap(ply);
        double result = 0;
        result += plyEffects.getOrDefault("防御损毁", 0);
        result += plyEffects.getOrDefault("血炎防御损毁", 0);
        return result;
    }
    public static void initPlayerStats(Player ply, boolean joinOrRespawn) {
        // metadata and scoreboard tag
        EntityHelper.initEntityMetadata(ply);
        {
            List<String> scoreboardTagsToRemove = new ArrayList<>();
            for (String scoreboardTag : ply.getScoreboardTags()) {
                if (scoreboardTag.startsWith("temp"))
                    scoreboardTagsToRemove.add(scoreboardTag);
            }
            for (String scoreboardTag : scoreboardTagsToRemove) {
                ply.removeScoreboardTag(scoreboardTag);
            }
        }
        // vanilla attribute and vanilla status setup
        ply.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_DIGGING, 999999999, 9, false, false), true);
        ply.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(Double.MAX_VALUE);
        ply.setFoodLevel(0);
        ply.setGravity(true);
        // crafting variables
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_CRAFTING_STATION, "CLOSED");
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_CRAFTING_RECIPE_INDEX, -1);
        // combat variables
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_INTERNAL_ITEM_START_USE_CD, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_NEXT_MINION_INDEX, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_NEXT_SENTRY_INDEX, 0);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.DPS_DMG_TOTAL, 0d);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.DPS_HITS, 0);
        MetadataValue dpsVersionLast = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.DPS_VERSION);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.DPS_VERSION, dpsVersionLast == null ? 0 : dpsVersionLast.asInt() + 1);
        // object variables
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MINION_LIST, new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_SENTRY_LIST, new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES, new HashSet<String>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES_LIST, new ArrayList<String>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_NEG_REGEN_CAUSE, new HashMap<String, Double>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_ACCELERATION, new Vector());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_VELOCITY_ACTUAL, new Vector());
        // the accessory set cached when first thrusting, to prevent buggy behaviour.
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES_FLIGHT_BACKUP, new HashSet<String>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ATTRIBUTE_MAP, PlayerHelper.getDefaultPlayerAttributes());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BUFF_INFLICT, PlayerHelper.getDefaultPlayerEffectInflict());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS, new ArrayList<Entity>());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_KEYS_PRESSED, new HashSet<String>());
        // movement and control variable
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_ITEM, "");
        resetPlayerFlightTime(ply);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_DIRECTION, "");
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_VELOCITY_INTERNAL, new Vector());
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_VELOCITY_MULTI, 1d);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_DASH_KEY_PRESSED_MS, Calendar.getInstance().getTimeInMillis());
        updateArmorSetMetadata(ply, "");
        // bgm, biome and background
        // prevent duplicated soundtrack etc.
        if (joinOrRespawn) {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BACKGROUND, "TEMP_NONE");
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM, "normal");
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_LAST_BGM_TIME, 0L);
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BIOME, WorldHelper.BiomeType.NORMAL);
        }
        // health, mana, regeneration, air bar
        if (joinOrRespawn) {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_HEALTH_TIER, getPlayerHealthTier(ply));
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_TIER, getPlayerManaTier(ply));
        }
        // DPS display
        if (joinOrRespawn) {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.THROTTLE_DPS_ACTION_BAR, null);
        }
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.REGEN_TIME, 0d);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_DELAY, 0d);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MANA_REGEN_COUNTER, 0d);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_STEALTH, 0d);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_AIR, PLAYER_MAX_OXYGEN);
        // extra setups on join
        if (joinOrRespawn) {
            // load inventories
            loadInventories(ply);
            // setup trash
            updateTrashBinInfo(ply);
            // join team
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM, "red");
            // reset monsters spawned to 0
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_MONSTER_SPAWNED_AMOUNT, 0);
            // remove teleportation target
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_TELEPORT_TARGET, null);
        }
        // reset attribute
        setupAttribute(ply);
    }
    public static void setupAttribute(Player ply) {
        try {
            ply.removeScoreboardTag("toolChanged");
            // reset buff immunity etc. Do not reset damage type, doing such will cause faulty minion damage.
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.BUFF_IMMUNE, new HashMap<String, Integer>());
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_BUFF_INFLICT, getDefaultPlayerEffectInflict());
            // re-initialize attribute map

            // attrMap is being overridden after newAttrMap is ready to prevent client glitch (especially on max mana)
            HashMap<String, Double> formerAttrMap = AttributeHelper.getAttrMap(ply);
            double originalMaxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();

            HashMap<String, Double> newAttrMap = getDefaultPlayerAttributes();
            HashMap<String, Double> negRegenCause = (HashMap<String, Double>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_NEG_REGEN_CAUSE).value();
            newAttrMap.put("maxHealth", (double) getMaxHealthByTier(getPlayerHealthTier(ply)) );
            newAttrMap.put("maxMana", (double) getMaxManaByTier(getPlayerManaTier(ply)) );
            // potion effect
            HashMap<String, Integer> effectMap = EntityHelper.getEffectMap(ply);
            for (Map.Entry<String, Integer> effectInfo : effectMap.entrySet()) {
                String effect = effectInfo.getKey();
                int ticksRemaining = effectInfo.getValue();
                switch (effect) {
                    case "魔力烧蚀":
                    case "魔力熔蚀":
                        double potency = ticksRemaining / 400d;
                        // max damage: 8.5 * 8.5 = 72.25
                        // sqrt(i) * (8.5)^(i + 1) = sqrt(i) * e^( ln(8.5) * (i + 1) )
                        double healthLoss = Math.sqrt(potency) * Math.exp(2.14 * (potency + 1));

                        // the greater effect also converts the health loss into mana regen
                        if (effect.equals("魔力熔蚀")) {
                            AttributeHelper.tweakAttribute(ply, newAttrMap, "manaRegenFixed",
                                    (healthLoss * 1.75) + "", true);
                        }
                        AttributeHelper.tweakAttribute(ply, newAttrMap, "regen", healthLoss + "", false);
                        negRegenCause.put(effect, healthLoss);
                        break;
                    case "魔力疾病":
                        // maximum: 20s = 400 tick, -400/800 = -50% damage
                        AttributeHelper.tweakAttribute(ply, newAttrMap, "damageMagicMulti",
                                ((double)-ticksRemaining / 800) + "", true);
                        break;
                    default: {
                        String attributesPath = "effects." + effect + ".attributes";
                        ConfigurationSection effectSection = TerrariaHelper.buffConfig.getConfigurationSection(attributesPath);
                        if (effectSection != null) {
                            int buffLevel = EntityHelper.getEffectLevel(effect, ticksRemaining);
                            Set<String> attributesTweaked = effectSection.getKeys(false);
                            for (String attr : attributesTweaked) {
                                // other buff may contain non-string attributes, but leveled buff usually do not.
                                // so it is safe to read the attribute from level > 1 as double.
                                // if a buff can be leveled but its level is 1, it does not matter as multiplication is not required.
                                if (buffLevel > 1)
                                    AttributeHelper.tweakAttribute(ply, newAttrMap, attr,
                                            (effectSection.getDouble(attr) * buffLevel) + "", true);
                                else
                                    AttributeHelper.tweakAttribute(ply, newAttrMap, attr,
                                            effectSection.getString(attr), true);
                                if (attr.equals("regen")) {
                                    double attrVal = effectSection.getDouble(attr);
                                    if (attrVal < 0)
                                        negRegenCause.put(effect, -attrVal);
                                }
                            }
                        }
                    }
                }
            }
            // defence damage
            AttributeHelper.tweakAttribute(ply, newAttrMap, "defence", getDefenceDamage(ply) + "", false);

            PlayerInventory plyInv = ply.getInventory();
            ItemStack plyTool = plyInv.getItemInMainHand();
            // weapon
            {
                String toolCombatType = ItemHelper.getItemCombatType(plyTool);
                if (toolCombatType.equals("武器")) {
                    AttributeHelper.tweakAttribute(ply, newAttrMap, plyTool, true);
                }
            }
            // armor
            {
                List<ItemStack> armors = new ArrayList<>();
                // helmet
                {
                    ItemStack helm = plyInv.getHelmet();
                    if (helm != null) {
                        helm = ItemHelper.getRawItem(ItemHelper.splitItemName(helm)[1]);
                        armors.add(helm);
                        plyInv.setHelmet(helm);
                    }
                }
                // chest plate
                {
                    ItemStack chestPlate = plyInv.getChestplate();
                    if (chestPlate != null) {
                        chestPlate = ItemHelper.getRawItem(ItemHelper.splitItemName(chestPlate)[1]);
                        armors.add(chestPlate);
                        plyInv.setChestplate(chestPlate);
                    }
                }
                // leggings
                {
                    ItemStack leggings = plyInv.getLeggings();
                    if (leggings != null) {
                        leggings = ItemHelper.getRawItem(ItemHelper.splitItemName(leggings)[1]);
                        armors.add(leggings);
                        plyInv.setLeggings(leggings);
                    }
                    // update the player's boots according to the leggings
                    {
                        ItemStack boots = new ItemStack(Material.AIR);
                        if (leggings != null) {
                            boots = ItemHelper.getRawItem(ItemHelper.splitItemName(leggings)[1] + "靴子");
                        }
                        plyInv.setBoots(boots);
                    }
                }
                // basic attributes
                for (ItemStack armorPiece : armors) {
                    AttributeHelper.tweakAttribute(ply, newAttrMap, armorPiece, true);
                }
                // set bonus
                String armorSet = "";
                if (armors.size() == 3) {
                    List<String> possibleArmorSets = null;
                    for (ItemStack armorPiece : armors) {
                        String currPieceType = ItemHelper.splitItemName(armorPiece)[1];
                        List<String> currPieceSets = TerrariaHelper.armorSetConfig.getStringList("pieces." + currPieceType);
                        if (possibleArmorSets == null) {
                            possibleArmorSets = currPieceSets;
                        } else {
                            List<String> setsToRemove = new ArrayList<>();
                            for (String possibleArmorSet : possibleArmorSets) {
                                if (!currPieceSets.contains(possibleArmorSet))
                                    setsToRemove.add(possibleArmorSet);
                            }
                            possibleArmorSets.removeAll(setsToRemove);
                            if (possibleArmorSets.size() == 0) break;
                        }
                    }
                    // if a unique set bonus is found
                    if (possibleArmorSets != null && possibleArmorSets.size() == 1) {
                        armorSet = possibleArmorSets.get(0);
                        ConfigurationSection setBonusSection = TerrariaHelper.armorSetConfig.getConfigurationSection(
                                "sets." + armorSet);
                        // add lore
                        List<String> loreToAdd = setBonusSection.getStringList("lore");
                        for (ItemStack armorPiece : armors) {
                            ItemMeta meta = armorPiece.getItemMeta();
                            List<String> lore = meta.getLore();
                            lore.addAll(loreToAdd);
                            meta.setLore(lore);
                            armorPiece.setItemMeta(meta);
                        }
                        plyInv.setHelmet(armors.get(0));
                        plyInv.setChestplate(armors.get(1));
                        plyInv.setLeggings(armors.get(2));
                        // tweak attributes
                        AttributeHelper.tweakAllAttributes(ply, newAttrMap,
                                setBonusSection.getConfigurationSection("attributes"), true);
                    }
                }
                updateArmorSetMetadata(ply, armorSet);
                // special armor sets
                switch (armorSet) {
                    case "皇天套装": {
                        double plyHealthRatio = ply.getHealth() / ply.getMaxHealth();
                        if (plyHealthRatio < 0.5) {
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "damageMulti", "0.05", true);
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "crit", "5", true);
                        }
                        break;
                    }
                    case "血炎盗贼套装":
                    case "金源盗贼套装": {
                        double plyHealthRatio = ply.getHealth() / ply.getMaxHealth();
                        if (plyHealthRatio > 0.8) {
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "defence", "30", true);
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "damageRogueMulti", "0.05", true);
                        }
                        else {
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "damageRogueMulti", "0.1", true);
                        }
                        break;
                    }
                    case "血炎召唤套装":
                    case "金源召唤套装": {
                        double plyHealthRatio = ply.getHealth() / ply.getMaxHealth();
                        if (plyHealthRatio > 0.9)
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "damageSummonMulti", "0.15", true);
                        else if (plyHealthRatio <= 0.5) {
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "defence", "30", true);
                        }
                        break;
                    }
                }
                // god slayer and auric rogue armor set
                switch (armorSet) {
                    case "弑神者盗贼套装":
                    case "金源盗贼套装": {
                        double plyHealthRatio = ply.getHealth() / ply.getMaxHealth();
                        if (plyHealthRatio > 0.99999) {
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "damageRogueMulti", "0.1", true);
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "critRogue", "10", true);
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "useSpeedRogueMulti", "0.1", true);
                            AttributeHelper.tweakAttribute(ply, newAttrMap,
                                    "stealthRegenMulti", "0.1", true);
                        }
                        break;
                    }
                }
            }
            // accessories
            {
                Set<String> accessories = new HashSet<>(12);
                List<String> accessoryList = new ArrayList<>(7);
                int accessoryAmount = getAccessoryAmount(ply);
                boolean hasSwitchable = false;
                String switchableDisplayName = null;
                // setup accessory list
                for (int idx = 1; idx <= accessoryAmount; idx ++) {
                    ItemStack currAcc = DragoncoreHelper.getSlotItem(ply, "accessory" + idx);
                    if (currAcc == null || currAcc.getType() == Material.AIR) continue;
                    String currAccType = ItemHelper.splitItemName(currAcc)[1];
                    // only one switchable accessory would be active
                    double regen = TerrariaHelper.itemConfig.getDouble(currAccType + ".attributes.regen", 0);
                    if (TerrariaHelper.itemConfig.contains(currAccType + ".attributesFormI") ) {
                        if (hasSwitchable)
                            continue;
                        hasSwitchable = true;
                        StringBuilder accRegenKey = new StringBuilder(currAccType + ".");
                        if (ply.getScoreboardTags().contains(TAG_SWITCHED_SWITCHABLE_ACCESSORY)) {
                            accRegenKey.append("attributesFormII");
                            switchableDisplayName = currAccType + "II";
                        }
                        else {
                            accRegenKey.append("attributesFormI");
                            switchableDisplayName = currAccType + "I";
                        }
                        accRegenKey.append(".regen");
                        regen += TerrariaHelper.itemConfig.getDouble(accRegenKey.toString(), 0);
                    }
                    // special accessory activation restrictions and conditional attributes
                    switch (currAccType) {
                        case "钨钢屏障生成仪": {
                            if (effectMap.containsKey("保护矩阵")) {
                                AttributeHelper.tweakAttribute(ply, newAttrMap, "defence", "20", true);
                            }
                            break;
                        }
                        case "嘉登之心": {
                            if (effectMap.containsKey("保护矩阵")) {
                                AttributeHelper.tweakAttribute(ply, newAttrMap, "regen", "6", true);
                                AttributeHelper.tweakAttribute(ply, newAttrMap, "damageTakenMulti", "-0.15", true);
                            }
                            break;
                        }
                        case "太阳石": {
                            if (!WorldHelper.isDayTime(ply.getWorld()))
                                continue;
                            break;
                        }
                        case "月亮石": {
                            if (WorldHelper.isDayTime(ply.getWorld()))
                                continue;
                            break;
                        }
                        case "魔能谐振仪": {
                            if (ply.getLevel() * 2 > getMaxMana(ply)) {
                                AttributeHelper.tweakAttribute(ply, newAttrMap,
                                        "regen", "3", false);
                                negRegenCause.put(currAccType, 3d);
                            }
                            break;
                        }
                        case "恼怒项链": {
                            if (ply.getHealth() * 2 < ply.getMaxHealth())
                                AttributeHelper.tweakAttribute(ply, newAttrMap,
                                        "damageMulti", "0.2", true);
                            break;
                        }
                        case "归一心元石": {
                            HashMap<String, String> toTweak = new HashMap<>(17);
                            if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.SUPREME_WITCH_CALAMITAS)) {
                                toTweak.put("maxHealthMulti", "0.1");
                                toTweak.put("useSpeedMeleeMulti", "0.05");
                                toTweak.put("regen", "6");
                                toTweak.put("crit", "5");
                                toTweak.put("damageMulti", "0.1");
                                toTweak.put("damageTakenMulti", "-0.05");
                                toTweak.put("defence", "20");
                                toTweak.put("speedMulti", "0.1");
                                toTweak.put("flightTimeMulti", "0.2");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.EXO_MECHS)) {
                                toTweak.put("maxHealthMulti", "0.09");
                                toTweak.put("useSpeedMeleeMulti", "0.049");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "4");
                                toTweak.put("damageMulti", "0.098");
                                toTweak.put("damageTakenMulti", "-0.049");
                                toTweak.put("defence", "18");
                                toTweak.put("speedMulti", "0.098");
                                toTweak.put("flightTimeMulti", "0.196");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.YHARON_DRAGON_OF_REBIRTH)) {
                                toTweak.put("maxHealthMulti", "0.09");
                                toTweak.put("useSpeedMeleeMulti", "0.048");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "4");
                                toTweak.put("damageMulti", "0.095");
                                toTweak.put("damageTakenMulti", "-0.047");
                                toTweak.put("defence", "18");
                                toTweak.put("speedMulti", "0.096");
                                toTweak.put("flightTimeMulti", "0.193");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.THE_DEVOURER_OF_GODS)) {
                                toTweak.put("maxHealthMulti", "0.09");
                                toTweak.put("useSpeedMeleeMulti", "0.047");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "4");
                                toTweak.put("damageMulti", "0.095");
                                toTweak.put("damageTakenMulti", "-0.047");
                                toTweak.put("defence", "18");
                                toTweak.put("speedMulti", "0.095");
                                toTweak.put("flightTimeMulti", "0.189");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS)) {
                                toTweak.put("maxHealthMulti", "0.08");
                                toTweak.put("useSpeedMeleeMulti", "0.042");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "4");
                                toTweak.put("damageMulti", "0.084");
                                toTweak.put("damageTakenMulti", "-0.042");
                                toTweak.put("defence", "16");
                                toTweak.put("speedMulti", "0.084");
                                toTweak.put("flightTimeMulti", "0.168");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.MOON_LORD)) {
                                toTweak.put("maxHealthMulti", "0.07");
                                toTweak.put("useSpeedMeleeMulti", "0.039");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "3");
                                toTweak.put("damageMulti", "0.079");
                                toTweak.put("damageTakenMulti", "-0.039");
                                toTweak.put("defence", "14");
                                toTweak.put("speedMulti", "0.079");
                                toTweak.put("flightTimeMulti", "0.157");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.ASTRUM_DEUS)) {
                                toTweak.put("maxHealthMulti", "0.07");
                                toTweak.put("useSpeedMeleeMulti", "0.038");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "3");
                                toTweak.put("damageMulti", "0.077");
                                toTweak.put("damageTakenMulti", "-0.038");
                                toTweak.put("defence", "14");
                                toTweak.put("speedMulti", "0.077");
                                toTweak.put("flightTimeMulti", "0.154");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.EMPRESS_OF_LIGHT)) {
                                toTweak.put("maxHealthMulti", "0.06");
                                toTweak.put("useSpeedMeleeMulti", "0.035");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "3");
                                toTweak.put("damageMulti", "0.07");
                                toTweak.put("damageTakenMulti", "-0.035");
                                toTweak.put("defence", "12");
                                toTweak.put("speedMulti", "0.07");
                                toTweak.put("flightTimeMulti", "0.139");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.PLANTERA)) {
                                toTweak.put("maxHealthMulti", "0.06");
                                toTweak.put("useSpeedMeleeMulti", "0.03");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "3");
                                toTweak.put("damageMulti", "0.061");
                                toTweak.put("damageTakenMulti", "-0.03");
                                toTweak.put("defence", "12");
                                toTweak.put("speedMulti", "0.061");
                                toTweak.put("flightTimeMulti", "0.121");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.BRIMSTONE_ELEMENTAL)) {
                                toTweak.put("maxHealthMulti", "0.05");
                                toTweak.put("useSpeedMeleeMulti", "0.028");
                                toTweak.put("regen", "4");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.055");
                                toTweak.put("damageTakenMulti", "-0.028");
                                toTweak.put("defence", "10");
                                toTweak.put("speedMulti", "0.057");
                                toTweak.put("flightTimeMulti", "0.114");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.CRYOGEN)) {
                                toTweak.put("maxHealthMulti", "0.04");
                                toTweak.put("useSpeedMeleeMulti", "0.024");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.048");
                                toTweak.put("damageTakenMulti", "-0.024");
                                toTweak.put("defence", "8");
                                toTweak.put("speedMulti", "0.048");
                                toTweak.put("flightTimeMulti", "0.096");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.WALL_OF_FLESH)) {
                                toTweak.put("maxHealthMulti", "0.04");
                                toTweak.put("useSpeedMeleeMulti", "0.022");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.045");
                                toTweak.put("damageTakenMulti", "-0.022");
                                toTweak.put("defence", "8");
                                toTweak.put("speedMulti", "0.045");
                                toTweak.put("flightTimeMulti", "0.089");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.THE_SLIME_GOD)) {
                                toTweak.put("maxHealthMulti", "0.04");
                                toTweak.put("useSpeedMeleeMulti", "0.021");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.043");
                                toTweak.put("damageTakenMulti", "-0.021");
                                toTweak.put("defence", "8");
                                toTweak.put("speedMulti", "0.043");
                                toTweak.put("flightTimeMulti", "0.086");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.THE_HIVE_MIND)) {
                                toTweak.put("maxHealthMulti", "0.03");
                                toTweak.put("useSpeedMeleeMulti", "0.018");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.036");
                                toTweak.put("damageTakenMulti", "-0.018");
                                toTweak.put("defence", "6");
                                toTweak.put("speedMulti", "0.036");
                                toTweak.put("flightTimeMulti", "0.071");
                            }
                            else if (PlayerHelper.hasDefeated(ply, BossHelper.BossType.EYE_OF_CTHULHU)) {
                                toTweak.put("maxHealthMulti", "0.03");
                                toTweak.put("useSpeedMeleeMulti", "0.015");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "2");
                                toTweak.put("damageMulti", "0.03");
                                toTweak.put("damageTakenMulti", "-0.015");
                                toTweak.put("defence", "6");
                                toTweak.put("speedMulti", "0.03");
                                toTweak.put("flightTimeMulti", "0.061");
                            }
                            else {
                                toTweak.put("maxHealthMulti", "0.02");
                                toTweak.put("useSpeedMeleeMulti", "0.012");
                                toTweak.put("regen", "2");
                                toTweak.put("crit", "1");
                                toTweak.put("damageMulti", "0.025");
                                toTweak.put("damageTakenMulti", "-0.012");
                                toTweak.put("defence", "4");
                                toTweak.put("speedMulti", "0.025");
                                toTweak.put("flightTimeMulti", "0.05");
                            }
                            for (String attributeKey : toTweak.keySet()) {
                                AttributeHelper.tweakAttribute(ply, newAttrMap,
                                        attributeKey, toTweak.get(attributeKey), true);
                            }
                            break;
                        }
                    }
                    // attribute
                    AttributeHelper.tweakAttribute(ply, newAttrMap, currAcc, true);
                    if (regen < 0)
                        negRegenCause.put(currAccType, -regen);
                    // save accessory info
                    accessories.add(currAccType);
                    accessoryList.add(currAccType);
                }
                if (hasSwitchable) {
                    ply.addScoreboardTag(TAG_HAS_SWITCHABLE_ACCESSORY);
                }
                else {
                    ply.removeScoreboardTag(TAG_HAS_SWITCHABLE_ACCESSORY);
                }
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORY_SWITCHABLE_DISPLAY, switchableDisplayName);
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES, accessories);
                EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ACCESSORIES_LIST, accessoryList);
            }
            // extra handling
            if (ItemHelper.splitItemName(plyTool)[1].equals("月神Prime")) {
                AttributeHelper.tweakAttribute(ply, newAttrMap, "critDamage",
                        (newAttrMap.getOrDefault("crit", 4d) * 0.5) + "", true);
            }
            // stealth increases rogue damage & crit
            double maxStealth = newAttrMap.getOrDefault("stealthLimit", 0d);
            if (maxStealth > 1) {
                double currStealth = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_STEALTH).asDouble();
                double stealthRatio = currStealth / maxStealth;
                AttributeHelper.tweakAttribute(ply, newAttrMap, "critRogue",
                        ( (currStealth * 0.1 + newAttrMap.getOrDefault("critStealth", 0d)) * stealthRatio) + "",
                        true);
                AttributeHelper.tweakAttribute(ply, newAttrMap, "damageRogueMulti",
                        ( (currStealth * 0.001 + newAttrMap.getOrDefault("damageStealthMulti", 1d) - 1 ) * stealthRatio ) + "",
                        true);
            }

            // post-initialization

            // setup max health; the ratio of health is preserved.
            double newMaxHealth = newAttrMap.getOrDefault("maxHealth", 200d) *
                    newAttrMap.getOrDefault("maxHealthMulti", 1d);
            if (Math.abs(originalMaxHealth - newMaxHealth) > 1e-5) {
                double healthRatio = ply.getHealth() / originalMaxHealth;
                ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newMaxHealth);
                ply.setHealth( Math.min(healthRatio * newMaxHealth, newMaxHealth) );
            }
            // the on-ground movement is handled with velocity...
            if (ply.getWalkSpeed() != 0f)
                ply.setWalkSpeed(0f);
            // save new attribute map
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.ATTRIBUTE_MAP, newAttrMap);
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Player Helper] setupAttribute ", e);
        }
    }
    public static void loadInventories(Player ply) {
        HashMap<String, Inventory> inventories = new HashMap<>();
        YmlHelper.YmlSection plyFile = getPlayerDataFile(ply);
        // storage inventories (piggy bank, void bag)
        List<String> otherInvs = TerrariaHelper.settingConfig.getStringList("plyInventories.playerInventories");
        for (String invName : otherInvs) {
            String title = TerrariaHelper.settingConfig.getString("plyInventories.playerInventoryTitles." + invName, "");
            List<String> contents = plyFile.getStringList("inventory." + invName);
            if (contents == null) contents = new ArrayList<>(1);
            Inventory inv = Bukkit.createInventory(ply, PLAYER_EXTRA_INVENTORY_SIZE, title);
            int slot = 0;
            for (String rowInfo : contents) {
                for (String itemInfo : rowInfo.split("\t")) {
                    inv.setItem(slot, ItemHelper.getItemFromDescription(itemInfo, false));
                    slot++;
                }
            }
            inventories.put(invName, inv);
        }
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_INVENTORIES, inventories);
    }
    public static void saveData(Player ply) {
        // save inventory
        saveInventories(ply);
        // save max health and max mana tier
        ConfigurationSection playerDataFile = getPlayerDataFile(ply);
        playerDataFile.set("stats.healthTier", getPlayerHealthTier(ply));
        playerDataFile.set("stats.manaTier", getPlayerManaTier(ply));
    }
    public static void saveInventories(Player ply) {
        HashMap<String, Inventory> inventories = (HashMap<String, Inventory>) EntityHelper.getMetadata(ply,
                EntityHelper.MetadataName.PLAYER_INVENTORIES).value();
        YmlHelper.YmlSection plyFile = getPlayerDataFile(ply);
        for (String invType : inventories.keySet()) {
            Inventory currInv = inventories.get(invType);
            StringBuilder rowData = new StringBuilder();
            ArrayList<String> result = new ArrayList<>(PLAYER_EXTRA_INVENTORY_SIZE);
            for (int i = 0; i < PLAYER_EXTRA_INVENTORY_SIZE; i ++) {
                // tab separates the items
                if (i % 9 != 0)
                    rowData.append("\t");
                rowData.append(ItemHelper.getItemDescription(currInv.getItem(i)));
                // save the items in each row
                if (i % 9 == 8) {
                    result.add(rowData.toString());
                    rowData = new StringBuilder();
                }
            }
            plyFile.set("inventory." + invType, result);
        }
    }
    public static void handleInsignia(Player ply) {
        if (! getAccessories(ply).contains("进升证章"))
            return;
        if (EntityHelper.hasEffect(ply, "进升证章冷却"))
            return;
        if (EntityMovementHelper.getMount(ply) != null)
            return;
        resetPlayerFlightTime(ply);
        EntityHelper.applyEffect(ply, "进升证章冷却", 800);
    }
    public static void handleToggleSwitchable(Player ply) {
        if (ply.getScoreboardTags().contains(TAG_HAS_SWITCHABLE_ACCESSORY)) {
            if (EntityHelper.hasEffect(ply, "饰品转换冷却"))
                return;
            EntityHelper.applyEffect(ply, "饰品转换冷却", 100);
            // switch successful
            if (ply.getScoreboardTags().contains(TAG_SWITCHED_SWITCHABLE_ACCESSORY)) {
                ply.removeScoreboardTag(TAG_SWITCHED_SWITCHABLE_ACCESSORY);
                ply.playSound(ply.getEyeLocation(), Sound.BLOCK_NOTE_HAT, SoundCategory.PLAYERS,5.0f, 1f);
            }
            else {
                ply.addScoreboardTag(TAG_SWITCHED_SWITCHABLE_ACCESSORY);
                ply.playSound(ply.getEyeLocation(), Sound.BLOCK_NOTE_BELL, SoundCategory.PLAYERS,5.0f, 1.25f);
            }
            PlayerHelper.setupAttribute(ply);
        }
    }
    public static void handleArmorSetActiveEffect(Player ply) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
        switch (getArmorSet(ply)) {
            case "硫火套装": {
                EntityHelper.applyEffect(ply, "硫火狂怒", 200);
                break;
            }
            case "瘟疫死神套装": {
                EntityHelper.applyEffect(ply, "瘟疫狂暴", 100);
                break;
            }
            case "龙蒿近战套装":
            case "金源近战套装": {
                EntityHelper.applyEffect(ply, "龙蒿披风", 200);
                break;
            }
            case "血炎远程套装":
            case "金源远程套装": {
                if (! EntityHelper.hasEffect(ply, "幽火游魂冷却")) {
                    EntityHelper.applyEffect(ply, "幽火游魂冷却", 600);
                    // projectiles
                    HashMap<String, Double> projAttrMap = (HashMap<String, Double>) AttributeHelper.getAttrMap(ply).clone();
                    projAttrMap.put("damage", 600d);
                    for (int i = 0; i < 12; i ++) {
                        EntityHelper.spawnProjectile(ply, MathHelper.randomVector().multiply(2.5), projAttrMap, "血炎灵魂");
                    }
                }
                break;
            }
            case "蓝色欧米茄套装": {
                EntityHelper.applyEffect(ply, "深渊狂乱", 100);
                break;
            }
            case "始源林海魔法套装":
            case "始源林海召唤套装":
            case "金源魔法套装":
            case "金源召唤套装": {
                EntityHelper.applyEffect(ply, "始源林海无敌", 120);
                break;
            }
            case "魔影套装": {
                EntityHelper.applyEffect(ply, "魔影激怒", 600);
                break;
            }
        }
    }
    public static void handleGrapplingHook(Player ply) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
        List<Entity> hooks = (ArrayList<Entity>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS).value();
        String hookItemName = ItemHelper.splitItemName(ply.getInventory().getItemInOffHand())[1];
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_ITEM, hookItemName);
        YmlHelper.YmlSection config = TerrariaHelper.hookConfig;
        int hookAmount = config.getInt(hookItemName + ".amount", 0);
        if (hooks.size() >= hookAmount) {
            // removed the first hook on blocks if trying to launch more hooks than the player has
            Entity removed = null;
            for (Entity hook : hooks) {
                if (hook.getVelocity().lengthSquared() < 1e-5) {
                    hook.remove();
                    removed = hook;
                    break;
                }
            }
            if (removed != null) hooks.remove(removed);
            else return;
        }
        // velocity
        double hookSpeed = config.getDouble(hookItemName + ".velocity", 10) / 6;
        EntityPlayer nms_ply = ((CraftPlayer) ply).getHandle();
        double yaw = nms_ply.yaw,
                pitch = nms_ply.pitch;
        Vector velocity = MathHelper.vectorFromYawPitch_approx(yaw, pitch);
        velocity.multiply(hookSpeed);
        Entity hookEntity = EntityHelper.spawnProjectile(ply, velocity, new HashMap<>(), "钩爪");
        // pre-set particle item
        List<String> hookColors = config.getStringList(hookItemName + ".particleItem");
        for (Entity hook : hooks) {
            hookColors.remove(EntityHelper.getMetadata(hook, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_COLOR).asString());
        }
        String color = hookColors.size() > 0 ? hookColors.get(0) : "t/bt";
        EntityHelper.setMetadata(hookEntity, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOK_COLOR, color);
        // mark hook entity as a hook
        hookEntity.addScoreboardTag("isHook");
        hooks.add(hookEntity);
    }
    public static Entity getMount(Player ply) {
        Mount mountNMS = Mount.MOUNTS_MAP.get(ply.getUniqueId());
        return mountNMS == null ? null : mountNMS.getBukkitEntity();
    }
    public static void handleMount(Player ply) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
        // if the player has a mount, unmount the player
        if (Mount.MOUNTS_MAP.containsKey(ply.getUniqueId())) {
            Mount mount = Mount.MOUNTS_MAP.get(ply.getUniqueId());
            mount.die();
        }
        // otherwise, handle minecart or spawn the mount as needed
        else {
            // minecart
            Entity vehicle = ply.getVehicle();
            if (vehicle != null) {
                vehicle.eject();
            }
            // spawn mount
            else {
                if (ply.getScoreboardTags().contains("temp_mountCD"))
                    return;
                EntityHelper.handleEntityTemporaryScoreboardTag(ply, "temp_mountCD", 10);
                ItemStack plyMountItem = DragoncoreHelper.getSlotItem(ply, "mount");
                String splitName = ItemHelper.splitItemName(plyMountItem)[1];
                if (splitName.length() > 0) {
                    ConfigurationSection mountSection = TerrariaHelper.mountConfig.getConfigurationSection(splitName);
                    if (mountSection != null) {
                        new Mount(ply, mountSection);
                    }
                }
            }
        }
    }
    public static int giveItem(Player ply, ItemStack item, boolean dropExtra) {
        if (item == null) return 0;
        int amountInitial = item.getAmount();
        int amountRemaining = amountInitial;
        String itemType = ItemHelper.splitItemName(item)[1];
        switch (itemType) {
            case "铜币":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS,1, 1);
                setMoney(ply, getMoney(ply) + 100d * amountRemaining);
                return 0;
            case "银币":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 1, 1);
                setMoney(ply, getMoney(ply) + 10000d * amountRemaining);
                return 0;
            case "金币":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 1, 1);
                setMoney(ply, getMoney(ply) + 1000000d * amountRemaining);
                return 0;
            case "铂金币":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 1, 1);
                setMoney(ply, getMoney(ply) + 100000000d * amountRemaining);
                return 0;
            case "心":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 1, 1);
                heal(ply, 20);
                return 0;
            case "星":
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 1, 1);
                restoreMana(ply, 100);
                return 0;
            case "生命强化焰":
            case "伤害强化焰":
            case "魔力强化焰": {
                ply.getWorld().playSound(ply.getEyeLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 1, 1);
                String effect;
                switch (itemType) {
                    case "生命强化焰":
                        effect = "生命星云";
                        break;
                    case "伤害强化焰":
                        effect = "伤害星云";
                        break;
                    default:
                        effect = "魔力星云";
                }
                String team = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM).asString();
                for (Player plyEffect : ply.getWorld().getPlayers()) {
                    if (isProperlyPlaying(plyEffect) && EntityHelper.getMetadata(plyEffect, EntityHelper.MetadataName.PLAYER_TEAM).asString().equals(team) && plyEffect.getLocation().distanceSquared(ply.getLocation()) < 3600)
                        EntityHelper.applyEffect(ply, effect, 480);
                }
                return 0;
            }
        }
        try {
            MetadataValue mdv = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TRASH_ITEMS);
            // destroy trashed items
            if (mdv != null && ((HashSet<String>) mdv.value()).contains(itemType)) {
                amountRemaining = 0;
                ply.getWorld().playSound(ply.getEyeLocation(), Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 1, 1);
            }
            // give non-trashed items; return the leftover amount
            else {
                amountRemaining = ItemHelper.addItemToGenericInventory(item, ply.getInventory());
                // put the item in the player's void bag, if the player has a void bag in the inventory
                if (amountRemaining > 0 && hasVoidBag(ply) && Setting.getOptionBool(ply, Setting.Options.VOID_BAG_PICKUP)) {
                    Inventory voidBagInv = getInventory(ply, "voidBag");
                    if (voidBagInv != null)
                        amountRemaining = ItemHelper.addItemToGenericInventory(item, voidBagInv);
                }
                if (dropExtra && amountRemaining > 0) {
                    ItemStack itemToDrop = item.clone();
                    itemToDrop.setAmount(amountRemaining);
                    ItemHelper.dropItem(ply.getEyeLocation(), itemToDrop);
                }
                if (amountRemaining < amountInitial)
                    ply.getWorld().playSound(ply.getEyeLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1, 1);
            }
            return amountRemaining;
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "PlayerHelper.giveItem", e);
            return amountRemaining;
        }
    }

    /**
     * Heals the player by the specified amount. Displays the actual healing amount in hologram.
     * @param ply The player to heal
     * @param amount The amount to heal
     * @return The actual healed amount, considering player's health vs max health, fixed healing multiplier etc.
     */
    public static double heal(LivingEntity ply, double amount) {
        return heal(ply, amount, false);
    }
    /**
     * Heals the player by the specified amount.
     * @param ply The player to heal
     * @param amount The amount to heal
     * @param displayActualAmount Whether to display the actual healed amount in hologram
     * @return The actual healed amount, considering player's health vs max health, fixed healing multiplier etc.
     */
    // heals the player by the amount; can specify whether to display the actual amount and damage source of negative regen.
    public static double heal(LivingEntity ply, double amount, boolean displayActualAmount) {
        return heal(ply, amount, displayActualAmount, "");
    }
    /**
     * Heals the player by the specified amount.
     * @param ply The player to heal
     * @param amount The amount to heal
     * @param displayActualAmount Whether to display the actual healed amount in hologram
     * @param dmgReason If negative healing kills the player, the death cause is debuff;
     *                  specifies the debuff name used to generate death message
     * @return The actual healed amount, considering player's health vs max health, fixed healing multiplier etc.
     */
    public static double heal(LivingEntity ply, double amount, boolean displayActualAmount, String dmgReason) {
        HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(ply);
        double healAmount;
        if (amount < 0)
            healAmount = amount;
        else
            healAmount = Math.min(ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() - ply.getHealth(),
                    amount * attrMap.getOrDefault("fixedHealingMulti", 1d));
        double newHealth = ply.getHealth() + healAmount;
        // negative healing that kills the player
        if (newHealth < 1e-9) {
            // negative regen death messages are handled differently
            // such abrupt dmg would slightly better align with debuff dmg anyway
            DamageHelper.handleDeath(ply, ply, ply, DamageHelper.DamageType.DEBUFF,
                    DamageHelper.DamageReason.DEBUFF, dmgReason);
            // update the healed amount to exactly the damage dealt.
            healAmount = - ply.getHealth();
        }
        // set the player's health as usual otherwise
        else {
            ply.setHealth(newHealth);
        }
        GenericHelper.displayHolo(ply, displayActualAmount ? healAmount : amount, false, "回血");
        return healAmount;
    }
    public static void restoreMana(Player ply, double amount) {
        restoreMana(ply, amount, true);
    }
    public static void restoreMana(Player ply, double amount, boolean hologram) {
        int restoreAmount = (int) Math.min(getMaxMana(ply) - ply.getLevel(), amount);
        ply.setLevel(ply.getLevel() + restoreAmount);
        if (hologram) {
            GenericHelper.displayHolo(ply, amount, false, "回蓝");
        }
    }
    public static double getMaxStealth(Player ply) {
        return AttributeHelper.getAttrMap(ply).getOrDefault("stealthLimit", 0d);
    }
    public static void restoreStealth(Player ply, double amount) {
        restoreStealth(ply, amount, true);
    }
    public static void restoreStealth(Player ply, double amount, boolean notifyPlyOnFull) {
        double stealthMax = getMaxStealth(ply);
        double stealth = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_STEALTH).asDouble();
        if (stealth < stealthMax) {
            stealth += amount;
            // on fully recovery
            if (stealth >= stealthMax) {
                stealth = stealthMax;
                // play notification sound
                if (notifyPlyOnFull)
                    ply.playSound(ply.getLocation(), Sound.BLOCK_NOTE_PLING, SoundCategory.PLAYERS,2, 2);
            }
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_STEALTH, stealth);
        }
        // clip stealth to its maximum
        else if (stealth > stealthMax) {
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_STEALTH, stealthMax);
        }
    }
    public static void sendActionBar(Player player, String message) {
        IChatBaseComponent actionBar = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + message + "\"}");
        PacketPlayOutTitle packet = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.ACTIONBAR, actionBar);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
    private static void spectreProjectileTick(Vector currDir, LivingEntity target, int idx, double speed, Player dPly, Location loc, double num, boolean healingOrDamage, String color) {
        if (!target.getWorld().equals(loc.getWorld())) return;
        // if the target becomes invalid, terminate this projectile and start a new identical one
        boolean shouldRetarget = false;
        if (target instanceof Player && !isProperlyPlaying((Player) target)) shouldRetarget = true;
        else if (target.getHealth() <= 0) shouldRetarget = true;
        if (shouldRetarget) {
            createSpectreProjectile(dPly, loc, num, healingOrDamage, color);
            return;
        }
        // ticking mechanism
        Location targetLoc = target.getEyeLocation();
        if (idx > 5) {
            if (idx == 6) speed = 1.5;
            double distSqr = loc.distanceSquared(targetLoc);
            if (distSqr < speed * speed) {
                // if the projectile reaches its target
                currDir = targetLoc.clone().subtract(loc).toVector();
                speed = Math.sqrt(distSqr);
                distSqr = -1;
            } else {
                // if the projectile does not yet reach its target
                Vector dV = targetLoc.clone().subtract(loc).toVector();
                if (dV.lengthSquared() > 0) {
                    // set vector length of currDir to: min( sqrt(dV.length()) , 4)
                    if (currDir.lengthSquared() > 0)
                        MathHelper.setVectorLengthSquared(currDir, Math.min(dV.length(), 16));
                    MathHelper.setVectorLength(dV, 4);
                    currDir.add(dV);
                    speed += 0.15;
                }
            }
            // hits its target
            if (distSqr < 0) {
                if (healingOrDamage) {
                    heal((Player) target, num);
                } else {
                    DamageHelper.handleDamage(dPly, target, num, DamageHelper.DamageReason.SPECTRE);
                }
                return;
            }
        } else
            speed += 0.2;
        MathHelper.setVectorLength(currDir, speed);
        GenericHelper.handleParticleLine(currDir, loc,
                new GenericHelper.ParticleLineOptions()
                        .setVanillaParticle(false)
                        .setSnowStormRawUse(false)
                        .setLength(speed)
                        .setWidth(0.2)
                        .setTicksLinger(4)
                        .setAlpha(0.75f)
                        .setParticleColor(color));
        loc.add(currDir);
        Vector finalCurrDir = currDir;
        double finalSpeed = speed;
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () ->
                spectreProjectileTick(finalCurrDir, target, idx + 1, finalSpeed, dPly, loc, num, healingOrDamage, color),
                2);
    }
    public static void createSpectreProjectile(Player dPly, Location loc, double num, boolean healingOrDamage, String color) {
        Entity target = null;
        if (healingOrDamage) {
            String team = EntityHelper.getMetadata(dPly, EntityHelper.MetadataName.PLAYER_TEAM).asString();
            double targetHealth = 1e9;
            // get the teammate nearby that has the lowest health
            for (Player ply : dPly.getWorld().getPlayers()) {
                // only heal teammates within 48 blocks radius
                if (isProperlyPlaying(ply) &&
                        ply.getHealth() < targetHealth && (ply.getHealth() + 1) < ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() &&
                        ply.getLocation().distanceSquared(dPly.getLocation()) < 2304 &&
                        EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_TEAM).asString().equals(team)) {
                    target = ply;
                    targetHealth = ply.getHealth();
                }
            }
        } else {
            // target a random target that is valid.
            ArrayList<Entity> candidates = new ArrayList<>(35);
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 24, 24, 24)) {
                if (DamageHelper.checkCanDamage(dPly, e)) {
                    candidates.add(e);
                }
            }
            if (candidates.size() > 0)
                target = candidates.get((int) (Math.random() * candidates.size()));
        }
        if (target != null) {
            spectreProjectileTick(MathHelper.randomVector(), (LivingEntity) target, 0, 0.5, dPly, loc, num, healingOrDamage, color);
        }
    }
    public static void playerMagicArmorSet(Player dPly, LivingEntity v, double dmg) {
        if (!v.getScoreboardTags().contains("isMonster")) return;
        String armorSet = getArmorSet(dPly);
        String spectreCD = "tempSpectreCD";
        switch (armorSet) {
            case "星云套装": {
                if (dPly.getScoreboardTags().contains("tempNebulaCD")) break;
                ItemStack nebulaItem = new ItemStack(Material.FLINT_AND_STEEL);
                double rdm = Math.random();
                ItemMeta meta = nebulaItem.getItemMeta();
                if (rdm < 1d/9)
                    meta.setDisplayName("§c生命强化焰");
                else if (rdm < 2d/9)
                    meta.setDisplayName("§d伤害强化焰");
                else if (rdm < 1d/3)
                    meta.setDisplayName("§9魔力强化焰");
                else break;
                nebulaItem.setItemMeta(meta);
                v.getWorld().dropItemNaturally(v.getLocation().add(0, 1.5d, 0), nebulaItem);
                dPly.addScoreboardTag("tempNebulaCD");
                Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                    if (dPly.isOnline()) dPly.removeScoreboardTag("tempNebulaCD");
                }, 10);
                break;
            }
            case "幽灵吸血套装":
            case "幽灵输出套装": {
                if (dmg > 2) {
                    if (dPly.getScoreboardTags().contains(spectreCD) ) break;
                    int coolDownTicks;
                    if (armorSet.equals("幽灵吸血套装")) {
                        int projectilePower = (int) Math.min( 200, Math.ceil(dmg * 0.08) );
                        createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0), projectilePower, true, "t/bt");
                        // 40 health/second = 2 health/tick
                        coolDownTicks = (int) Math.ceil(projectilePower / 2d);
                    } else {
                        int projectilePower = (int) Math.ceil(dmg * 0.5);
                        createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0), projectilePower, false, "t/bt");
                        // 1000 dmg/second = 50 dmg/tick
                        coolDownTicks = (int) Math.ceil(projectilePower / 50d);
                    }
                    // apply CD
                    EntityHelper.handleEntityTemporaryScoreboardTag(dPly, spectreCD, coolDownTicks);
                }
                break;
            }
            case "始源林海魔法套装":
            case "金源魔法套装": {
                String coolDownTag = "temp_silvaMagicBlast";
                if (! dPly.getScoreboardTags().contains(coolDownTag)) {
                    // cool down (5 second)
                    EntityHelper.handleEntityTemporaryScoreboardTag(dPly, coolDownTag, 100);
                    // explosion
                    HashMap<String, Double> explosionAttribute = (HashMap<String, Double>) AttributeHelper.getAttrMap(dPly).clone();
                    double explosionDmg = 1600 + explosionAttribute.getOrDefault("damage", 10d) * 0.6;
                    explosionAttribute.put("damage", Math.min(explosionDmg, 2800d));
                    EntityHelper.spawnProjectile(dPly, v.getEyeLocation(), new Vector(),
                            explosionAttribute, DamageHelper.DamageType.MAGIC, "始源林海爆炸");
                }
                break;
            }
        }
        HashSet<String> accessories = PlayerHelper.getAccessories(dPly);
        HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(dPly);
        if (accessories.contains("魔能谐振仪")) {
            if (dmg > 2 && !(dPly.getScoreboardTags().contains(spectreCD))) {
                int projectilePower = (int) Math.min( 50, Math.ceil(dmg * 0.125) );
                double manaRatio = (double) dPly.getLevel() / getMaxMana(dPly);
                createSpectreProjectile(dPly, v.getLocation().add(0, 1.5d, 0),
                        Math.ceil(projectilePower * manaRatio), true, "t/ot");
                // 10 health/second = 0.5 health/tick
                int coolDownTicks = (int) Math.ceil(projectilePower / 0.5);
                EntityHelper.handleEntityTemporaryScoreboardTag(dPly, spectreCD, coolDownTicks);
            }
        }
    }
    public static void handleDash(Player ply, double yaw, double pitch) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
        // can not dash if player has dash cool down
        if (ply.getScoreboardTags().contains("temp_dashCD"))
            return;
        Collection<Entity> hooks = (Collection<Entity>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_GRAPPLING_HOOKS).value();
        // can not dash if any grappling hook is on the wall
        for (Entity hook : hooks) {
            if (hook.isOnGround()) return;
        }
        double dashSpeed = 0;
        int dashCD = -1;
        // armor set dash
        String armorSet = getArmorSet(ply);
        switch (armorSet) {
            case "水晶刺客套装":
                dashSpeed = 1.15;
                dashCD = 30;
                break;
            case "耀斑套装":
                dashSpeed = 1.35;
                dashCD = 30;
                break;
        }
        // accessory dash
        if (dashCD < 0) {
            Collection<String> accessories = getAccessories(ply);
            for (String accessory : accessories) {
                switch (accessory) {
                    case "克苏鲁之眼盾":
                        dashSpeed = 1;
                        dashCD = 35;
                        break;
                    case "宝光盾牌":
                        dashSpeed = 1.2;
                        dashCD = 30;
                        break;
                    case "阿斯加德之英勇":
                        dashSpeed = 1.3;
                        dashCD = 28;
                        break;
                    case "极乐之庇护":
                        dashSpeed = 1.45;
                        dashCD = 26;
                        break;
                    case "阿斯加德之庇护":
                        dashSpeed = 1.6;
                        dashCD = 25;
                        break;
                }
                if (dashCD > 0) break;
            }
        }
        // dash if applicable
        if (dashCD > 0) {
            HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(ply);
            // speed multiplier above 100% will contribute 25%
            double speedMulti = attrMap.getOrDefault("speedMulti", 1d);
            if (speedMulti > 1)
                speedMulti = 1 + (speedMulti - 1) * 0.25;
            dashSpeed *= attrMap.getOrDefault("speedMulti", 1d) * speedMulti;

            Vector dashVelocity = MathHelper.vectorFromYawPitch_approx(yaw, pitch).multiply(dashSpeed);
            EntityMovementHelper.setVelocity(ply, getPlayerRawVelocity(ply).add(dashVelocity));
            ply.addScoreboardTag("temp_dashCD");
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> ply.removeScoreboardTag("temp_dashCD"), dashCD);
            // god slayer armor bonus
            switch (armorSet) {
                case "弑神者近战套装":
                case "弑神者远程套装":
                case "弑神者盗贼套装":
                case "金源近战套装":
                case "金源远程套装":
                case "金源盗贼套装": {
                    EntityHelper.applyEffect(ply, "弑神者冲刺", 20);
                    break;
                }
            }
        }
    }
    public static void initAresExoskeletonConfig(Player ply, boolean force) {
        // if the metadata is in place AND it is not forced to be renewed, do nothing.
        if (EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_EXOSKELETON) != null && (! force))
            return;
        // init the cannon config
        ArrayList<Short> cannons = new ArrayList<>(4);
        for (short i = 0; i < 4; i ++)
            cannons.add(i);
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_EXOSKELETON, cannons);
    }
    public static ArrayList<Short> getAresExoskeletonConfig(Player ply) {
        // make sure the config is present.
        initAresExoskeletonConfig(ply, false);
        // fetch the value.
        return (ArrayList<Short>) EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_EXOSKELETON).value();
    }
    public static void showAresExoskeletonConfig(Player ply) {
        // make sure the config is present.
        ArrayList<Short> config = getAresExoskeletonConfig(ply);
        // init the inventory
        Inventory inv = Bukkit.createInventory(ply, 9, ARES_EXOSKELETON_CONFIG_PAGE_NAME);
        ItemStack placeholder = new ItemStack(Material.STAINED_GLASS_PANE);
        for (int slot = 0; slot < 9; slot ++) {
            if (slot < config.size())
                inv.setItem(slot, ItemHelper.getItemFromDescription( ARES_EXOSKELETON_WEAPON_NAMES[ config.get(slot) ] ));
            else
                inv.setItem(slot, placeholder);
        }
        // show inventory
        ply.openInventory(inv);
    }
    public static void handleAresExoskeletonConfigClick(Player ply, Inventory inv, int idx) {
        // make sure the config is present.
        ArrayList<Short> config = getAresExoskeletonConfig(ply);
        // check for out of bound
        if (idx >= 4)
            return;
        // get item
        short newItemIdx = (short) ((config.get(idx) + 1) % 4);
        config.set(idx, newItemIdx);
        inv.setItem(idx, ItemHelper.getItemFromDescription( ARES_EXOSKELETON_WEAPON_NAMES[ newItemIdx ] ));
    }
}
