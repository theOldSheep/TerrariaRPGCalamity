package terraria.util;

import lk.vexview.gui.VexGui;
import lk.vexview.gui.components.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftItem;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import terraria.TerrariaHelper;
import terraria.entity.others.TerrariaDroppedItem;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class ItemHelper {
    public static final String PLACEHOLDER_ITEM_NAME_PREFIX = "§1§1§4§5§1§4";
    public static HashMap<String, String> ATTRIBUTE_DISPLAY_NAME = new HashMap<>();
    private static HashMap<String, ItemStack> ITEM_MAP;
    public static HashMap<String, VexGui> CRAFTING_GUI_MAP;
    public static HashMap<String, String> CRAFTING_GUIS_RECIPE_INDEX_MAP;
    public static HashMap<String, Integer> CRAFTING_GUI_LENGTH_MAP;
    // recipe init helpers
    private static int getMaxRecipeLevel(String station) {
        int maxLevel = 1;
        ConfigurationSection blockSection = TerrariaHelper.recipeConfig.getConfigurationSection(station);
        Set<String> nodes = blockSection.getKeys(false);
        for (String recipeName : nodes) {
            int requireLevel = blockSection.getInt(recipeName + ".requireLevel", 0);
            if (requireLevel > maxLevel) {
                maxLevel = requireLevel;
            }
        }
        return maxLevel;
    }
    // process all recipes for a craft station (shallow, only its main items)
    private static void processRecipes(ConfigurationSection section, String effectiveStation, int effectiveStationLevel,
                                       String savedStation, int savedLevel,
                                       HashMap<String, HashMap<Integer, List<VexSlot>>> itemSlotsMap, AtomicInteger recipeIndex) {
        for (String recipeName : section.getKeys(false)) {
            if (recipeName.equals("containedStations")) continue;

            ConfigurationSection recipeSection = section.getConfigurationSection(recipeName);
            int requireLevel = recipeSection.getInt("requireLevel", 0);

            if (requireLevel <= effectiveStationLevel) {
                ItemStack resultItem = getItemFromDescription(recipeSection.getString("resultItem", ""), false);
                resultItem.setAmount(1);
                String recipeCategory = recipeSection.getString("recipeCategory");
                HashMap<Integer, List<VexSlot>> subMap = itemSlotsMap.computeIfAbsent(recipeCategory, k -> new HashMap<>());
                List<VexSlot> itemSlots = subMap.computeIfAbsent(getItemRarity(resultItem), k -> new ArrayList<>());
                int currentIndex = recipeIndex.getAndIncrement();
                VexSlot slotComp = new VexSlot(currentIndex, 5, (currentIndex - 1) * 20, resultItem);
                itemSlots.add(slotComp);
                CRAFTING_GUIS_RECIPE_INDEX_MAP.put(savedStation + "_" + savedLevel + "_" + currentIndex, effectiveStation + "." + recipeName);
            }
        }
    }
    // setup all recipes
    private static void setupRecipesForStation(String station, int level) {
        String guiBackground = TerrariaHelper.Constants.GUI_BACKGROUND;
        ConfigurationSection stationSection = TerrariaHelper.recipeConfig.getConfigurationSection(station);

        HashMap<String, HashMap<Integer, List<VexSlot>>> itemSlotsMap = new HashMap<>();
        AtomicInteger recipeIndex = new AtomicInteger(1);

        // main station
        processRecipes(stationSection, station, level, station, level, itemSlotsMap, recipeIndex);

        // contained stations (if any)
        ConfigurationSection containedStationsSection = stationSection.getConfigurationSection("containedStations");
        if (containedStationsSection != null) {
            for (String subStation : containedStationsSection.getKeys(false)) {
                int subStationLevel = containedStationsSection.getInt(subStation, 1);
                ConfigurationSection subStationSection = TerrariaHelper.recipeConfig.getConfigurationSection(subStation);
                processRecipes(subStationSection, subStation, subStationLevel, station, level, itemSlotsMap, recipeIndex);
            }
        }

        // configure the VexGui
        VexGui gui = new VexGui(guiBackground, 0, 0, 200, 150);
        VexScrollingList scrList = new VexScrollingList(
                (int) (gui.getWidth() * 0.75), 17, 30, gui.getHeight() - 34, 0
        );
        List<String> categoryOrder = TerrariaHelper.recipeConfig.getStringList("categoryOrder");
        // null rarity exists!
        categoryOrder.add(null);
        for (String category : categoryOrder) {
            HashMap<Integer, List<VexSlot>> rarityMap = itemSlotsMap.get(category);
            if (rarityMap != null) {
                List<Integer> rarities = new ArrayList<>(rarityMap.keySet());
                // revert 1000+ rarity (special ones) so they are displayed correctly
                for (int i = 0; i < rarities.size(); i ++) {
                    int currRarity = rarities.get(i);
                    if (currRarity >= 1000) rarities.set(i, -currRarity);
                }
                Collections.sort(rarities); // sort the rarities in ascending order
                for (int i = 0; i < rarities.size(); i ++) {
                    int currRarity = rarities.get(i);
                    if (currRarity <= -1000) rarities.set(i, -currRarity);
                }
                // put slots according to the rarity display order
                for (int rarity : rarities) {
                    List<VexSlot> itemSlots = rarityMap.get(rarity);
                    itemSlots.forEach(scrList::addComponent);
                }
            }
        }
        gui.addComponent(scrList);
        // store the GUI and recipe count
        String craftGuiMappingKey = station + "_" + level;
        CRAFTING_GUI_MAP.put(craftGuiMappingKey, gui);
        CRAFTING_GUI_LENGTH_MAP.put(craftGuiMappingKey, recipeIndex.get());
    }


    public static void setupItemRecipe(boolean printDebugMessage) {
        Bukkit.clearRecipes();
        Set<String> items = TerrariaHelper.itemConfig.getKeys(false);
        Set<String> craftStations = TerrariaHelper.recipeConfig.getKeys(false);
        craftStations.remove("categoryOrder");
        ITEM_MAP = new HashMap<>();
        CRAFTING_GUI_MAP = new HashMap<>();
        CRAFTING_GUIS_RECIPE_INDEX_MAP = new HashMap<>();
        CRAFTING_GUI_LENGTH_MAP = new HashMap<>();
        // items
        for (String itemInfo : items) {
            ItemStack item = getItemFromYML(itemInfo);
            if (item.getType() == Material.AIR) {
                if (printDebugMessage) TerrariaHelper.LOGGER.log(Level.SEVERE, "Could not read " + itemInfo + " from yml.");
                continue;
            }
            if (printDebugMessage) TerrariaHelper.LOGGER.log(Level.FINEST, "Loading " + itemInfo + " from yml.");
            switch (item.getType()) {
                case ARROW:
                case SLIME_BALL:
                case BLAZE_POWDER:
                case FLINT: {
                    if (printDebugMessage) TerrariaHelper.LOGGER.log(Level.FINEST, itemInfo + " is ammunition, adding item description.");
                    // these are reserved for ammunition.
                    ItemMeta meta = item.getItemMeta();
                    List<String> lore;
                    if (meta.hasLore()) lore = meta.getLore();
                    else lore = new ArrayList<>();
                    int idx;
                    for (idx = 0; idx < lore.size(); idx ++) {
                        String currLine = GenericHelper.trimText(lore.get(idx));
                        if (currLine.startsWith("[") && currLine.endsWith("]")) continue;
                        break;
                    }
                    lore.add(idx, "&r[弹药]");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
            ITEM_MAP.put(itemInfo, item);
        }
        // setup material tooltip
        Set<String> allMaterials = new HashSet<>();
        for (String block : craftStations) {
            ConfigurationSection blockSection = TerrariaHelper.recipeConfig.getConfigurationSection(block);
            Set<String> nodes = blockSection.getKeys(false);
            for (String recipeIndex : nodes) {
                List<String> currMaterials = blockSection.getStringList(recipeIndex + ".requireItem");
                if (currMaterials == null) continue;
                for (String itemInfo : currMaterials) {
                    if (itemInfo.contains(":")) itemInfo = itemInfo.split(":")[0];
                    allMaterials.add(itemInfo);
                }
            }
        }
        for (String mat : allMaterials) {
            // no cloning here needed, as we are intended to tweak the item value.
            ItemStack item = ITEM_MAP.get(mat);
            if (item == null) {
                if (printDebugMessage) TerrariaHelper.LOGGER.log(Level.SEVERE, mat + " is supposed to be an crafting material, but it is not found.");
                continue;
            }
            if (printDebugMessage) TerrariaHelper.LOGGER.log(Level.FINEST, mat + " is considered a material, adding item description.");
            ItemMeta meta = item.getItemMeta();
            List<String> lore;
            if (meta.hasLore()) lore = meta.getLore();
            else lore = new ArrayList<>();
            int idx;
            for (idx = 0; idx < lore.size(); idx ++) {
                String currLine = GenericHelper.trimText(lore.get(idx));
                if (currLine.startsWith("[") && currLine.endsWith("]")) continue;
                break;
            }
            lore.add(idx, "&r[材料]");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        // recipes
        for (String block : craftStations) {
            int levelMax = getMaxRecipeLevel(block);
            for (int level = 1; level <= levelMax; level ++) {
                if (printDebugMessage) TerrariaHelper.LOGGER.log(Level.FINEST, "Setting up workbench " + block + "_" + level);
                setupRecipesForStation(block, level);
            }
        }
    }
    private static void setupAttributeDisplayName() {
        ATTRIBUTE_DISPLAY_NAME.put("damageTakenMulti", "受到伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageContactTakenMulti", "受到接触伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageMulti", "伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageMeleeMulti", "近战伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageRangedMulti", "远程伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageMagicMulti", "魔法伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageSummonMulti", "召唤伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageArrowMulti", "箭矢伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageBulletMulti", "子弹伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageRocketMulti", "火箭伤害");
        ATTRIBUTE_DISPLAY_NAME.put("damageTrueMeleeMulti", "真近战伤害");
        ATTRIBUTE_DISPLAY_NAME.put("flightTimeMulti", "飞行时长");
        ATTRIBUTE_DISPLAY_NAME.put("manaUseMulti", "魔力消耗");
        ATTRIBUTE_DISPLAY_NAME.put("maxHealthMulti", "最大生命值");
        ATTRIBUTE_DISPLAY_NAME.put("mobSpawnRateMulti", "怪物生成速度");
        ATTRIBUTE_DISPLAY_NAME.put("speedMulti", "移动速度");
        ATTRIBUTE_DISPLAY_NAME.put("meleeReachMulti", "近战攻击距离");
        ATTRIBUTE_DISPLAY_NAME.put("regenMulti", "生命回复速度");
        ATTRIBUTE_DISPLAY_NAME.put("fixedHealingMulti", "治疗强度");
        ATTRIBUTE_DISPLAY_NAME.put("useSpeedMulti", "攻击速度");
        ATTRIBUTE_DISPLAY_NAME.put("useSpeedMagicMulti", "魔法攻击速度");
        ATTRIBUTE_DISPLAY_NAME.put("useSpeedMeleeMulti", "近战攻击速度");
        ATTRIBUTE_DISPLAY_NAME.put("useSpeedMiningMulti", "挖掘速度");
        ATTRIBUTE_DISPLAY_NAME.put("useSpeedRangedMulti", "远程攻击速度");
        ATTRIBUTE_DISPLAY_NAME.put("knockbackMeleeMulti", "近战击退");
        ATTRIBUTE_DISPLAY_NAME.put("projectileSpeedMulti", "弹射物速度");
        ATTRIBUTE_DISPLAY_NAME.put("projectileSpeedArrowMulti", "箭矢速度");
    }
    static {
        setupAttributeDisplayName();
        setupItemRecipe(true);
    }
    public static String[] splitItemName(String itemName) {
        if (itemName == null) return new String[]{"", ""};
        itemName = GenericHelper.trimText(itemName);
        if (itemName.contains("的 ")) {
            return itemName.split("的 ");
        }
        return new String[]{"", itemName};
    }
    public static String[] splitItemName(ItemStack item) {
        if (item != null && item.getType() != Material.AIR && item.getItemMeta().hasDisplayName())
            return splitItemName(item.getItemMeta().getDisplayName());
        return new String[]{"", ""};
    }
    public static int getWorth(String name) {
        if (name == null) name = "";
        String[] nameInfo = splitItemName(name);
        int worth = TerrariaHelper.itemConfig.getInt(nameInfo[1] + ".worth", 0);
        double worthMulti = TerrariaHelper.prefixConfig.getDouble(
                "prefixInfo." + nameInfo[0] + ".priceMultiplier", 1);
        worth *= worthMulti;
        return (worth / 100) * 100;
    }
    public static int getWorth(ItemStack item) {
        if (item == null) return 0;
        return getWorth(item.getItemMeta().getDisplayName());
    }
    public static int getReforgeCost(ItemStack item) {
        // 20% of the item's worth
        return (getWorth(item) / 500) * 100;
    }
    public static String getItemCombatType(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            String trimLore = GenericHelper.trimText(meta.getLore().get(0));
            switch (trimLore) {
                case "[饰品]":
                    return "饰品";
                case "[装备]":
                    return "装备";
            }
        }
        return "武器";
    }
    public static boolean canReforge(ItemStack item) {
        switch (getItemCombatType(item)) {
            case "饰品":
                return true;
            case "装备":
                return false;
        }
        // now determine if the item is a proper weapon.
        String itemName = splitItemName(item)[1];
        // items that deals no damage, for example, bug net, shall not have any prefix
        if (TerrariaHelper.itemConfig.getDouble(itemName + ".attributes.damage", -1) <= 0) return false;
        // thrown projectile (grenades, for example), can not have any prefix neither
        String weaponType = TerrariaHelper.weaponConfig.getString(itemName + ".type", "THROW");
        return !weaponType.startsWith("THROW");
    }
    public static HashMap<String, Double> getApplicablePrefix(ItemStack item) {
        HashMap<String, Double> result = new HashMap<>(50);
        if (!canReforge(item)) return result;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            String trimLore = GenericHelper.trimText(meta.getLore().get(0));
            if (trimLore.equals("[饰品]")) {
                for (String prefix : TerrariaHelper.prefixConfig.getStringList("prefixList.Accessory")) {
                    String[] prefixInfo = prefix.split(":");
                    result.put(prefixInfo[0], Double.parseDouble(prefixInfo[1]));
                }
                return result;
            }
        }
        // the item must be a weapon then.
        ArrayList<String> prefixClassesApplicable = new ArrayList<>(4);
        prefixClassesApplicable.add("Universal");
        String itemName = splitItemName(item)[1];
        switch (TerrariaHelper.weaponConfig.getString(itemName + ".type").toLowerCase()) {
            case "stab":
            case "swing":
            case "whip":
            case "boomerang":
            case "yoyo":
                prefixClassesApplicable.add("Melee");
                break;
            case "magic_projectile":
            case "magic_special":
            case "summon":
                prefixClassesApplicable.add("Magic");
                break;
            case "bow":
            case "gun":
            case "rocket":
            case "special_ammo":
                prefixClassesApplicable.add("Ranged");
                break;
        }
        for (String prefixClass : prefixClassesApplicable) {
            for (String prefix : TerrariaHelper.prefixConfig.getStringList("prefixList." + prefixClass)) {
                String[] prefixInfo = prefix.split(":");
                result.put(prefixInfo[0], Double.parseDouble(prefixInfo[1]));
            }
        }
        return result;
    }
    public static ItemStack regularizeItemDropped(ItemStack item) {
        return regularizeItemDropped(item, Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE).getSpawnLocation());
    }
    public static ItemStack regularizeItemDropped(ItemStack item, Location dropLoc) {
        if (item.getItemMeta().hasDisplayName())
            return item;
        String regularizedItemType = null;
        int itemAmount = item.getAmount();
        switch (item.getType()) {
            case TORCH:
                regularizedItemType = "火把";
                break;
            case SAND:
                regularizedItemType = "沙块";
                break;
            case ANVIL:
                regularizedItemType = "铁砧";
                break;
            case BED:
                regularizedItemType = "床";
                break;
            case WOOD_DOOR:
            case WOODEN_DOOR:
                regularizedItemType = "木门";
                break;
            case LADDER:
                regularizedItemType = "梯子";
                break;
            case RAILS:
                regularizedItemType = "铁轨";
                break;
            case POWERED_RAIL:
                regularizedItemType = "充能铁轨";
                break;
            case LEVER:
                regularizedItemType = "拉杆";
                break;
            case SIGN:
            case WALL_SIGN:
            case SIGN_POST:
                regularizedItemType = "牌子";
                break;
            case SEEDS:
                item.setType(Material.AIR);
                if (Math.random() < 0.1)
                    regularizedItemType = "草种";
                if (WorldHelper.BiomeType.getBiome(dropLoc) == WorldHelper.BiomeType.JUNGLE) {
                    double rdm = Math.random();
                    if (rdm < 0.025) {
                        regularizedItemType = "大自然的恩赐";
                    }
                }
                break;
            case CACTUS:
                regularizedItemType = "仙人掌";
                break;
            case RED_MUSHROOM:
                regularizedItemType = "蘑菇";
                break;
            case BROWN_MUSHROOM:
                regularizedItemType = "发光蘑菇";
                break;
            case YELLOW_FLOWER:
                regularizedItemType = "太阳花";
                break;
            case RED_ROSE:
                int data = item.getData().getData();
                switch (data) {
                    case 0:
                        regularizedItemType = "火焰花";
                        break;
                    case 1:
                        regularizedItemType = "死亡草";
                        break;
                    case 2:
                        regularizedItemType = "月光草";
                        break;
                    case 3:
                        regularizedItemType = "寒颤棘";
                        break;
                    case 4:
                        regularizedItemType = "波浪叶";
                        break;
                    case 5:
                        regularizedItemType = "闪耀根";
                        break;
                    case 6:
                        regularizedItemType = "丛林孢子";
                        itemAmount = MathHelper.randomRound(itemAmount * 2.5);
                        break;
                }
                break;
            // these items simply should not be dropped
            case SAPLING:
            case SNOW_BALL:
            case REDSTONE_TORCH_ON:
            case REDSTONE_TORCH_OFF:
            // pumpkin stem being destroyed by flowing water / by hand
            case PUMPKIN_SEEDS:
            case MELON_SEEDS:
                item.setType(Material.AIR);
                break;
        }
        if (regularizedItemType == null)
            return item;
        return getItemFromDescription(regularizedItemType + ":" + itemAmount, false);
    }
    public static ItemStack randomPrefix(ItemStack item) {
        String[] itemInfo = splitItemName(item);
        // 5% chance to not have any prefix
        String prefix = MathHelper.selectWeighedRandom(getApplicablePrefix(item));
        if (Math.random() < 0.05) prefix = "";
        if (prefix.length() > 0)
            return getItemFromDescription(prefix + "的 " + itemInfo[1], false);
        return getItemFromDescription(itemInfo[1], false);
    }
    public static ItemStack getItemFromDescription(String information) {
        return getItemFromDescription(information, true);
    }
    public static ItemStack getItemFromDescription(String information, boolean randomizePrefixIfNoneExists) {
        return getItemFromDescription(information, randomizePrefixIfNoneExists, new ItemStack(Material.AIR));
    }
    public static ItemStack getItemFromDescription(String information, boolean randomizePrefixIfNoneExists, ItemStack notFoundDefault) {
        try {
            if (information.contains(":")) {
                // the amount info is specified.
                // take one recursive call to get the item then set the amount.
                String[] info = information.split(":");
                ItemStack result = getItemFromDescription(info[0], randomizePrefixIfNoneExists, notFoundDefault);
                if (!result.isSimilar(notFoundDefault)) {
                    // 物品名:最小数量:最大数量:几率
                    int itemAmount = 1;
                    switch (info.length) {
                        case 4:
                            double chance = Double.parseDouble(info[3]);
                            if (Math.random() > chance) {
                                itemAmount = 0;
                                break;
                            }
                        case 3:
                            int itemMax = Integer.parseInt(info[2]);
                            int itemMin = Integer.parseInt(info[1]);
                            itemAmount = (int) (itemMin + (itemMax - itemMin + 1) * Math.random());
                            break;
                        case 2:
                            itemAmount = Integer.parseInt(info[1]);
                    }
                    result.setAmount(itemAmount);
                }
                return result;
            } else {
                String[] itemNameInfo = splitItemName(information);
                String prefix = itemNameInfo[0];
                String itemType = itemNameInfo[1];
                // get the itemstack
                ItemStack resultItem;
                try {
                    resultItem = ITEM_MAP.get(itemType);
                    // attempt to return the item from vanilla material
                    if (resultItem == null || resultItem.getType() == Material.AIR) {
                        try {
                            Material.matchMaterial(itemType);
                        } catch (Exception e) {
                            TerrariaHelper.LOGGER.log(Level.SEVERE, "item " + itemType + " not found in material.");
                        }
                        return new ItemStack(Material.matchMaterial(itemType));
                    }
                    resultItem = resultItem.clone();
                } catch (Exception e) {
                    TerrariaHelper.LOGGER.log(Level.SEVERE, "ItemHelper.getItemFromDescription (" + information + ")", e);
                    return notFoundDefault;
                }
                // setup prefix
                if (prefix.length() == 0) {
                    if (randomizePrefixIfNoneExists) resultItem = randomPrefix(resultItem);
                } else {
                    ItemMeta meta = resultItem.getItemMeta();
                    List<String> lore;
                    if (meta.hasLore()) lore = meta.getLore();
                    else lore = new ArrayList<>();
                    String finalItemName = information;
                    // treat prefix as the boss type for treasure bags
                    if (itemType.equals("专家模式福袋")) {
                        finalItemName = itemType;
                        lore.set(0, "§7" + prefix);
                    }
                    // add prefix lore to other items
                    else {
                        lore.addAll(TerrariaHelper.prefixConfig.getStringList("prefixInfo." + prefix + ".lore"));
                    }
                    // generate color according to rarity
                    int itemBaseRarity = getItemRarity(itemType);
                    int prefixRarity = TerrariaHelper.prefixConfig.getInt("prefixInfo." + prefix + ".rarity", 0);
                    // if rarity color [itemBaseRarity + prefixRarity] is set, use it
                    // otherwise, resolve to rarity color [itemBaseRarity]
                    String rarityColorPrefix = TerrariaHelper.settingConfig.getString("rarity." + (itemBaseRarity + prefixRarity),
                            TerrariaHelper.settingConfig.getString("rarity." + itemBaseRarity, "§r"));
                    // finish up
                    meta.setDisplayName(rarityColorPrefix + finalItemName);
                    meta.setLore(lore);
                    resultItem.setItemMeta(meta);
                }
                return resultItem;
            }
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "ItemHelper.getItemFromDescription " + information, e);
            return notFoundDefault;
        }
    }
    public static ItemStack getRawItem(String information) {
        if (information != null && ITEM_MAP.containsKey(information))
            return ITEM_MAP.get(information).clone();
        return new ItemStack(Material.AIR);
    }
    public static int getItemRarity(ItemStack item) {
        return getItemRarity(splitItemName(item)[1]);
    }
    public static int getItemRarity(String itemType) {
        return TerrariaHelper.itemConfig.getInt(itemType + ".rarity", 0);
    }
    public static int getItemRarityFromFullDescription(String fullItemDesc) {
        return getItemRarity(splitItemName(fullItemDesc)[1]);
    }
    private static List<String> getLoreDescription(ConfigurationSection attributeSection) {
        // the performance of this function is not very critical
        // it is called only once per item when the plugin loads
        ArrayList<String> result = new ArrayList<>(10);
        if (attributeSection == null) return result;
        Set<String> attributes = attributeSection.getKeys(false);
        String[] attributeLoreOrder = {
                // usually found in weapon lore
                "damage", "knockback", "crit", "critDamage", "useTime", "manaUse",
                "powerPickaxe", "fishingPower", "reachExtra",
                // multipliers, usually found in armor/accessory lore
                "damageTakenMulti", "damageContactTakenMulti",
                "damageMulti", "damageMeleeMulti", "damageRangedMulti", "damageMagicMulti",
                "damageSummonMulti", "damageArrowMulti", "damageBulletMulti", "damageRocketMulti", "damageTrueMeleeMulti",
                "manaUseMulti", "ammoConsumptionRate", "arrowConsumptionRate", "mobSpawnRateMulti",
                "speedMulti", "flightTimeMulti", "meleeReachMulti", "regenMulti", "fixedHealingMulti", "maxHealthMulti",
                "useSpeedMulti", "useSpeedMagicMulti", "useSpeedMeleeMulti", "useSpeedRangedMulti", "useSpeedMiningMulti",
                "knockbackMeleeMulti", "projectileSpeedArrowMulti",
                // barrier/mana/health/crit/regen
                "barrierMax", "maxMana", "maxHealth", "regen", "manaRegen", "critMelee", "critMagic", "critRanged", "critTrueMelee",
                // other attributes usually found in armor/accessory lore
                "armorPenetration", "defence", "invulnerabilityTick", "waterAffinity",
                "minionLimit", "sentryLimit", "mobLimit", "knockbackResistance",
                // these attributes are not displayed; however, they are put in this list to prevent sending warning message.
                "damageType", "fishingHooks",
                "buffInflict", "buffInflictMagic", "buffInflictMelee", "buffInflictRanged",
                "buffInflictSummon", "buffInflictTrueMelee", "buffImmune",
                "bounce", "projectileSpeed", "projectileSpeedMulti", "penetration",
        };
        // send warning message if the attribute is not handled
        for (String attribute : attributes) {
            boolean found = false;
            for (int i = 0; i < attributeLoreOrder.length; i ++)
                if (attributeLoreOrder[i].equals(attribute)) {
                    found = true;
                    break;
                }
            if (!found) TerrariaHelper.LOGGER.log(Level.WARNING, "Unhandled attribute when initializing item from yml: " + attribute);
        }
        // loop through all attributes in the order wanted and add their lore lines
        for (String attribute : attributeLoreOrder) {
            if (!attributes.contains(attribute)) continue;
            switch (attribute) {
                case "damage": {
                    int damage = attributeSection.getInt(attribute, 0);
                    switch (attributeSection.getString("damageType", "")) {
                        case "Melee":
                            result.add(damage + " 基础近战伤害");
                            break;
                        case "Arrow":
                        case "Bullet":
                        case "Rocket":
                            result.add(damage + " 基础远程伤害");
                            break;
                        case "Magic":
                            result.add(damage + " 基础魔法伤害");
                            break;
                        case "Summon":
                            result.add(damage + " 基础召唤伤害");
                            break;
                        default:
                            result.add(damage + " 基础无职业伤害");
                    }
                    break;
                }
                case "knockback": {
                    double knockback = attributeSection.getDouble(attribute, 0d);
                    if (knockback < 0.01) result.add("无击退威力");
                    else if (knockback < 1.5) result.add("极弱的击退威力");
                    else if (knockback < 3) result.add("很弱的击退威力");
                    else if (knockback < 4) result.add("较弱的击退威力");
                    else if (knockback < 6) result.add("普通的击退威力");
                    else if (knockback < 7) result.add("较强的击退威力");
                    else if (knockback < 9) result.add("很强的击退威力");
                    else if (knockback < 11) result.add("极强的击退威力");
                    else result.add("疯狂的击退威力");
                    break;
                }
                case "crit": {
                    int crit = attributeSection.getInt(attribute, 0);
                    if (attributes.contains("damageType")) result.add(crit + "% 基础暴击率");
                    else result.add(crit + "% 暴击率");
                    break;
                }
                case "critDamage": {
                    int critDamage = attributeSection.getInt(attribute, 0);
                    result.add(critDamage + "% 额外暴击倍率");
                    break;
                }
                case "useTime": {
                    int useTime = attributeSection.getInt(attribute, 0);
                    if (useTime <= 5) result.add("超快的使用速度");
                    else if (useTime <= 9) result.add("很快的使用速度");
                    else if (useTime <= 14) result.add("快的使用速度");
                    else if (useTime <= 22) result.add("普通的使用速度");
                    else if (useTime <= 29) result.add("慢的使用速度");
                    else if (useTime <= 37) result.add("很慢的使用速度");
                    else if (useTime <= 45) result.add("极慢的使用速度");
                    else result.add("蜗牛的使用速度");
                    break;
                }
                case "manaUse": {
                    int mana = attributeSection.getInt(attribute, 0);
                    result.add("消耗" + mana + "魔力");
                    break;
                }
                case "powerPickaxe":
                case "fishingPower": {
                    int power = attributeSection.getInt(attribute, 0);
                    String suffixName = attribute.equals("powerPickaxe") ? "镐力" : "渔力";
                    result.add(power + "% " + suffixName);
                    break;
                }
                case "reachExtra": {
                    int reach = attributeSection.getInt(attribute, 0);
                    result.add((reach > 0 ? "+" : "") + reach + " 触及范围");
                    break;
                }

                case "armorPenetration": {
                    int penetration = attributeSection.getInt(attribute, 0);
                    result.add("无视敌人" + penetration + "防御");
                    break;
                }
                case "defence": {
                    int defence = attributeSection.getInt(attribute, 0);
                    result.add(defence + " 防御");
                    break;
                }
                case "ammoConsumptionRate":
                case "arrowConsumptionRate": {
                    double multi = attributeSection.getDouble(attribute, 0d);
                    if (multi < 0) {
                        int multiPercentage = (int) (Math.abs(multi * 100));
                        result.add(multiPercentage + "% 几率不消耗" +
                                (attribute.equals("ammoConsumptionRate") ? "弹药" : "箭矢"));
                    }
                    break;
                }
                case "maxMana": {
                    int amount = attributeSection.getInt(attribute, 0);
                    result.add((amount > 0 ? "+" : "") + amount + " 魔力上限");
                    break;
                }
                case "maxHealth": {
                    int amount = attributeSection.getInt(attribute, 0);
                    result.add(amount + " 最大生命值");
                    break;
                }
                case "barrierMax": {
                    int amount = attributeSection.getInt(attribute, 0);
                    result.add(amount + " 保护矩阵能量上限");
                    break;
                }
                case "waterAffinity": {
                    double attrVal = attributeSection.getDouble(attribute, 0);
                    if (attrVal < 0) result.add("减少水下适应性");
                    else if (attrVal <= 2) result.add("增加水下适应性");
                    else result.add("增加大量水下适应性");
                    break;
                }
                case "regen": {
                    int amount = attributeSection.getInt(attribute, 0);
                    result.add((amount > 0 ? "+" : "") + amount + " 额外自然生命恢复");
                    break;
                }
                case "manaRegen": {
                    if (attributeSection.getDouble(attribute, 0) < 0) result.add("减缓魔力恢复");
                    else result.add("加快魔力恢复");
                    break;
                }
                case "critMelee":
                case "critMagic":
                case "critRanged":
                case "critTrueMelee": {
                    int crit = attributeSection.getInt(attribute, 0);
                    switch (attribute) {
                        case "critMelee":
                            result.add((crit > 0 ? "+" : "") + crit + "% 近战暴击率");
                            break;
                        case "critMagic":
                            result.add((crit > 0 ? "+" : "") + crit + "% 魔法暴击率");
                            break;
                        case "critRanged":
                            result.add((crit > 0 ? "+" : "") + crit + "% 远程暴击率");
                            break;
                        default:
                            result.add((crit > 0 ? "+" : "") + crit + "% 真近战暴击率");
                    }
                    break;
                }
                case "invulnerabilityTick": {
                    if (attributeSection.getDouble(attribute, 0) < 0) result.add("缩短无敌帧时间");
                    else result.add("延长无敌帧时间");
                    break;
                }
                case "minionLimit": {
                    int minionLimit = attributeSection.getInt(attribute, 0);
                    result.add((minionLimit > 0 ? "+" : "") + minionLimit + " 仆从上限");
                    break;
                }
                case "sentryLimit": {
                    int sentryLimit = attributeSection.getInt(attribute, 0);
                    result.add((sentryLimit > 0 ? "+" : "") + sentryLimit + " 哨兵上限");
                    break;
                }
                case "mobLimit": {
                    if (attributeSection.getDouble(attribute, 0) > 0) result.add("增加最高刷怪量");
                    else result.add("减少最高刷怪量");
                    break;
                }
                case "knockbackResistance": {
                    double resistance = attributeSection.getDouble(attribute, 0);
                    int resistancePercentage = (int) (resistance * 100);
                    if (resistance >= 1) result.add("免疫击退");
                    else result.add((resistance > 0 ? "+" : "") + resistancePercentage + "% 击退抗性");
                    break;
                }
                case "buffImmune": {
                    String buffImmune = attributeSection.getString(attribute, "锟斤拷");
                    result.add("免疫" + buffImmune.replace("|", "，"));
                    break;
                }
                // these attributes are not shown.
                case "fishingHooks":
                case "damageType":
                case "bounce":
                case "projectileSpeed":
                case "penetration":
                case "buffInflict":
                case "buffInflictMagic":
                case "buffInflictMelee":
                case "buffInflictRanged":
                case "buffInflictSummon":
                case "buffInflictTrueMelee":
                    break;
                // multiplier-style attributes
                default:
                    if (ATTRIBUTE_DISPLAY_NAME.containsKey(attribute)) {
                        double multi = attributeSection.getDouble(attribute, 0d);
                        int multiPercentage = (int) (multi * 100);
                        String descr = ATTRIBUTE_DISPLAY_NAME.getOrDefault(attribute, attribute);
                        result.add((multi > 0 ? "+" : "") + multiPercentage + "% " + descr);
                        break;
                    }
                    result.add(attributeSection.getString(attribute) + " " + attribute);
                    TerrariaHelper.LOGGER.log(Level.WARNING, "Raw attribute name used when initializing item from yml （" + attribute + ")");
            }
        }
        return result;
    }
    public static ItemStack getItemFromYML(String itemName) {
        try {
            if (TerrariaHelper.itemConfig.contains(itemName)) {
                ConfigurationSection itemSection = TerrariaHelper.itemConfig.getConfigurationSection(itemName);
                Material material = Material.valueOf(itemSection.getString("item", "AIR"));
                byte data = (byte) itemSection.getInt("data", 0);
                ItemStack item = new ItemStack(material, 1, (short) 0, data);
                // air has no item meta.
                if (material == Material.AIR) return item;
                // item meta
                ItemMeta meta = item.getItemMeta();
                int rarity = itemSection.getInt("rarity", 0);
                String rarityColor = TerrariaHelper.settingConfig.getString("rarity." + rarity, "§r");
                meta.setDisplayName(rarityColor + itemName);
                // item lore
                List<String> lore = itemSection.getStringList("lore");
                List<String> loreDescription = getLoreDescription(itemSection.getConfigurationSection("attributes"));
                List<String> loreDescriptionFormI = getLoreDescription(itemSection.getConfigurationSection("attributesFormI"));
                // [accessory] etc. Should always be the first line
                ArrayList<String> finalLore = new ArrayList<>(10);
                while (lore.size() > 0 && lore.get(0).endsWith("]")) finalLore.add(lore.remove(0));
                // generic attribute lore lines go after that
                finalLore.addAll(loreDescription);
                // switchable accessories
                if (loreDescriptionFormI.size() > 0) {
                    List<String> loreDescriptionFormII = getLoreDescription(itemSection.getConfigurationSection("attributesFormII"));
                    finalLore.add("§r▣算作转换类饰品");
                    finalLore.add("§7▷ 转换类饰品：仅限装备一个。按下切换按键[默认为C]可于两种加成形态间转换，冷却为5秒");
                    finalLore.add("§b一形态：");
                    loreDescriptionFormI.forEach( (line) -> finalLore.add("§b  -> " + line) );
                    finalLore.add("§c二形态：");
                    loreDescriptionFormII.forEach((line) -> finalLore.add("§c  -> " + line) );
                }
                // add the descriptive lore lines at the very end
                finalLore.addAll(lore);
                if (finalLore.size() > 0) meta.setLore(finalLore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
                return item;
            } else {
                return new ItemStack(Material.valueOf(itemName));
            }
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "ItemHelper.getItemFromYml", e);
            return new ItemStack(Material.AIR);
        }
    }
    public static String getItemDescription(ItemStack item) {
        if (item == null) return "AIR";
        try {
            ItemMeta meta = item.getItemMeta();
            String itemInfo;
            if (meta != null && meta.hasDisplayName()) {
                itemInfo = GenericHelper.trimText(meta.getDisplayName());
                if (itemInfo.equals("专家模式福袋")) {
                    String bossType = "史莱姆王";
                    if (meta.hasLore()) bossType = GenericHelper.trimText(meta.getLore().get(0));
                    itemInfo = bossType + "的 " + itemInfo;
                }
            } else itemInfo = item.getType().toString();
            int itemAmount = item.getAmount();
            return itemInfo + (itemAmount > 1 ? (":" + itemAmount) : "");
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "ItemHelper.getItemDescription", e);
            return item.getType().toString();
        }
    }
    public static Item dropItem(Location loc, String itemToDropDescription) {
        return dropItem(loc, itemToDropDescription, true);
    }
    public static Item dropItem(Location loc, String itemToDropDescription, boolean canMerge) {
        return dropItem(loc, itemToDropDescription, true, canMerge);
    }
    public static Item dropItem(Location loc, String itemToDropDescription, boolean randomizePrefixIfNoneExists, boolean canMerge) {
        return dropItem(loc, itemToDropDescription, randomizePrefixIfNoneExists, canMerge, true);
    }
    public static Item dropItem(Location loc, String itemToDropDescription, boolean randomizePrefixIfNoneExists, boolean canMerge, boolean noOverStack) {
        ItemStack itemToDrop = getItemFromDescription(itemToDropDescription, randomizePrefixIfNoneExists);
        return dropItem(loc, itemToDrop, canMerge, noOverStack);
    }
    public static Item dropItem(Location loc, ItemStack itemToDrop) {
        return dropItem(loc, itemToDrop, true);
    }
    public static Item dropItem(Location loc, ItemStack itemToDrop, boolean canMerge) {
        return dropItem(loc, itemToDrop, canMerge, true);
    }
    public static Item dropItem(Location loc, ItemStack itemToDrop, boolean canMerge, boolean noOverStack) {
        int itemAmount = itemToDrop.getAmount();
        if (itemAmount <= 0) return null;
        if (itemToDrop.getType() == Material.AIR) return null;
        // extras are dropped in separate stacks
        if (noOverStack) {
            int maxStackSize = itemToDrop.getType().getMaxStackSize();
            if (itemAmount > maxStackSize) {
                itemToDrop.setAmount(maxStackSize);
                while (itemAmount > maxStackSize) {
                    // drop extras
                    itemAmount -= maxStackSize;
                    dropItemWithoutCheck(loc, itemToDrop, canMerge);
                }
                // update current drop amount
                itemToDrop.setAmount(itemAmount);
            }
        }
        // drop item
        return dropItemWithoutCheck(loc, itemToDrop, canMerge);
    }
    public static CraftItem dropItemWithoutCheck(Location loc, ItemStack itemToDrop, boolean canMerge) {
        TerrariaDroppedItem entity = new TerrariaDroppedItem(loc, itemToDrop);
        if (!canMerge)
            entity.canBeMerged = false;
        CraftWorld wld = (CraftWorld) loc.getWorld();
        wld.addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        return new CraftItem(wld.getHandle().getServer(), entity);
    }
    public static int addItemToGenericInventory(ItemStack item, Inventory inventory) {
        if (item == null) return 0;
        // returns how many remaining
        HashMap<Integer, ItemStack> remainingItemMap = inventory.addItem(item);
        if (remainingItemMap.isEmpty()) return 0;
        else return remainingItemMap.get(0).getAmount();
    }
}
