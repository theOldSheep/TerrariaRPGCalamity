package terraria.util;

import lk.vexview.gui.VexGui;
import lk.vexview.gui.components.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import terraria.entity.others.TerrariaItem;

import java.util.*;
import java.util.logging.Level;

public class ItemHelper {
    public static final String placeholderItemNamePrefix = "§1§1§4§5§1§4";
    public static HashMap<String, String> attributeDisplayName = new HashMap<>();
    private static HashMap<String, ItemStack> itemMap;
    public static HashMap<String, VexGui> craftingGuiMap;
    public static HashMap<String, String> craftingGuisRecipeIndexMap;
    public static HashMap<String, Integer> craftingGuiLengthMap;
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
    private static void setupRecipesForStation(String station, int level) {
        String bg = TerrariaHelper.Constants.GUI_BACKGROUND;
        ConfigurationSection blockSection = TerrariaHelper.recipeConfig.getConfigurationSection(station);
        ArrayList<ScrollingListComponent> itemSlots = new ArrayList<>();
        int recipeIndex = 1;
        // the recipe added by the block itself
        {
            Set<String> nodes = blockSection.getKeys(false);
            for (String recipeName : nodes) {
                if (recipeName.equals("containedStations")) continue;
                ConfigurationSection recipeSection = blockSection.getConfigurationSection(recipeName);
                int requireLevel = recipeSection.getInt("requireLevel", 0);
                if (requireLevel <= level) {
                    ItemStack resultItem = getItemFromDescription(recipeSection.getString("resultItem", ""), false);
                    resultItem.setAmount(1);
                    VexSlot slotComp = new VexSlot(recipeIndex, 5, (recipeIndex - 1) * 20, resultItem);
                    itemSlots.add(slotComp);
                    craftingGuisRecipeIndexMap.put(station + "_" + level + "_" + recipeIndex, station + "." + recipeName);
                    recipeIndex++;
                }
            }
        }
        // add recipes from contained stations
        // for example, draedon's forge, can work as multiple other work stations
        ConfigurationSection containedStationSection = blockSection.getConfigurationSection("containedStations");
        if (containedStationSection != null) {
            Set<String> containedStations = containedStationSection.getKeys(false);
            for (String subStation : containedStations) {
                int subStationLevel = containedStationSection.getInt(subStation, 1);
                // loop through the station info for each
                ConfigurationSection subStationSection = TerrariaHelper.recipeConfig.getConfigurationSection(subStation);
                Set<String> nodes = subStationSection.getKeys(false);
                for (String recipeName : nodes) {
                    if (recipeName.equals("containedStations")) continue;
                    ConfigurationSection recipeSection = subStationSection.getConfigurationSection(recipeName);
                    int requireLevel = recipeSection.getInt("requireLevel", 0);
                    if (requireLevel <= subStationLevel) {
                        ItemStack resultItem = getItemFromDescription(recipeSection.getString("resultItem", ""), false);
                        resultItem.setAmount(1);
                        VexSlot slotComp = new VexSlot(recipeIndex, 5, (recipeIndex - 1) * 20, resultItem);
                        itemSlots.add(slotComp);
                        // the level below is used as the player crafts, so it should remain as level instead of subStationLevel
                        // the subStationLevel is used to validate the recipe only
                        craftingGuisRecipeIndexMap.put(station + "_" + level + "_" + recipeIndex, subStation + "." + recipeName);
                        recipeIndex++;
                    }
                }

            }
        }
        // set up the VexGui
        VexGui gui = new VexGui(bg, 0, 0, 200, 150);
        VexScrollingList scrList = new VexScrollingList((int) (gui.getWidth() * 0.75), 17, 30, gui.getHeight() - 34, itemSlots.size() * 20);
        for (ScrollingListComponent comp : itemSlots)
            scrList.addComponent(comp);
        gui.addComponent(scrList);
        // save the GUI to mapping
        String craftGuiMappingKey = station + "_" + level;
        craftingGuiMap.put(craftGuiMappingKey, gui);
        craftingGuiLengthMap.put(craftGuiMappingKey, recipeIndex);
    }
    public static void setupItemRecipe(boolean printDebugMessage) {
        Bukkit.clearRecipes();
        Set<String> items = TerrariaHelper.itemConfig.getKeys(false);
        Set<String> craftStations = TerrariaHelper.recipeConfig.getKeys(false);
        itemMap = new HashMap<>();
        craftingGuiMap = new HashMap<>();
        craftingGuisRecipeIndexMap = new HashMap<>();
        craftingGuiLengthMap = new HashMap<>();
        // items
        for (String itemInfo : items) {
            ItemStack item = getItemFromYML(itemInfo);
            if (item.getType() == Material.AIR) {
                if (printDebugMessage) Bukkit.getLogger().log(Level.SEVERE, "Could not read " + itemInfo + " from yml.");
                continue;
            }
            if (printDebugMessage) Bukkit.getLogger().log(Level.FINEST, "Loading " + itemInfo + " from yml.");
            switch (item.getType()) {
                case ARROW:
                case SLIME_BALL:
                case BLAZE_POWDER:
                case FLINT: {
                    if (printDebugMessage) Bukkit.getLogger().log(Level.FINEST, itemInfo + " is ammunition, adding item description.");
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
            itemMap.put(itemInfo, item);
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
            ItemStack item = itemMap.get(mat);
            if (item == null) {
                if (printDebugMessage) Bukkit.getLogger().log(Level.SEVERE, mat + " is supposed to be an crafting material, but it is not found.");
                continue;
            }
            if (printDebugMessage) Bukkit.getLogger().log(Level.FINEST, mat + " is considered a material, adding item description.");
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
                if (printDebugMessage) Bukkit.getLogger().log(Level.FINEST, "Setting up workbench " + block + "_" + level);
                setupRecipesForStation(block, level);
            }
        }
    }
    private static void setupAttributeDisplayName() {
        attributeDisplayName.put("damageTakenMulti", "受到伤害");
        attributeDisplayName.put("damageContactTakenMulti", "受到接触伤害");
        attributeDisplayName.put("damageMulti", "伤害");
        attributeDisplayName.put("damageMeleeMulti", "近战伤害");
        attributeDisplayName.put("damageRangedMulti", "远程伤害");
        attributeDisplayName.put("damageMagicMulti", "魔法伤害");
        attributeDisplayName.put("damageSummonMulti", "召唤伤害");
        attributeDisplayName.put("damageArrowMulti", "箭矢伤害");
        attributeDisplayName.put("damageBulletMulti", "子弹伤害");
        attributeDisplayName.put("damageRocketMulti", "火箭伤害");
        attributeDisplayName.put("damageTrueMeleeMulti", "真近战伤害");
        attributeDisplayName.put("manaUseMulti", "魔力消耗");
        attributeDisplayName.put("mobSpawnRateMulti", "怪物生成速度");
        attributeDisplayName.put("speedMulti", "移动速度");
        attributeDisplayName.put("meleeReachMulti", "近战攻击距离");
        attributeDisplayName.put("regenMulti", "生命回复速度");
        attributeDisplayName.put("useSpeedMulti", "攻击速度");
        attributeDisplayName.put("useSpeedMagicMulti", "魔法攻击速度");
        attributeDisplayName.put("useSpeedMeleeMulti", "近战攻击速度");
        attributeDisplayName.put("useSpeedRangedMulti", "远程攻击速度");
        attributeDisplayName.put("knockbackMeleeMulti", "近战击退");
        attributeDisplayName.put("projectileSpeedMulti", "弹射物速度");
        attributeDisplayName.put("projectileSpeedArrowMulti", "箭矢速度");
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
        return (getWorth(item) * 5 / 4 / 100) * 100;
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
        return getItemFromDescription(information, true, new ItemStack(Material.AIR));
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
                    resultItem = itemMap.get(itemType);
                    if (resultItem == null || resultItem.getType() == Material.AIR) {
                        // check if the itemType is a material
                        try {
                            Material.valueOf(itemType);
                        } catch (Exception e) {
                            Bukkit.getLogger().log(Level.SEVERE, "item " + itemType + " not found in mapping.");
                        }
                        return new ItemStack(Material.valueOf(itemType));
                    }
                    resultItem = resultItem.clone();
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "ItemHelper.getItemFromDescription", e);
                    return notFoundDefault;
                }
                // setup prefix
                if (prefix.length() == 0) {
                    if (randomizePrefixIfNoneExists) resultItem = randomPrefix(resultItem);
                } else {
                    ItemMeta meta = resultItem.getItemMeta();
                    List<String> lore;
                    if (itemType.equals("专家模式福袋")) {
                        lore = new ArrayList<>(1);
                        lore.add(ChatColor.COLOR_CHAR + "7" + prefix);
                    } else {
                        // generate color according to rarity
                        int itemBaseRarity = TerrariaHelper.itemConfig.getInt(itemType + ".rarity", 0);
                        int prefixRarity = TerrariaHelper.prefixConfig.getInt("prefixInfo." + prefix + ".rarity", 0);
                        // if rarity color [itemBaseRarity + prefixRarity] is set, use it
                        // otherwise, resolve to rarity color [itemBaseRarity]
                        String rarityColorPrefix = TerrariaHelper.settingConfig.getString("rarity." + (itemBaseRarity + prefixRarity),
                                TerrariaHelper.settingConfig.getString("rarity." + itemBaseRarity, "§r"));
                        meta.setDisplayName(rarityColorPrefix + information);
                        // add prefix lore to item
                        if (meta.hasLore()) lore = meta.getLore();
                        else lore = new ArrayList<>();
                        lore.addAll(TerrariaHelper.prefixConfig.getStringList("prefixInfo." + prefix + ".lore"));
                    }
                    meta.setLore(lore);
                    resultItem.setItemMeta(meta);
                }
                return resultItem;
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "ItemHelper.getItemFromDescription", e);
            return notFoundDefault;
        }
    }
    public static ItemStack getRawItem(String information) {
        if (information != null && itemMap.containsKey(information))
            return itemMap.get(information).clone();
        return new ItemStack(Material.AIR);
    }
    private static List<String> getLoreDescription(ConfigurationSection attributeSection) {
        // the performance of this function is not very critical
        // it is called only once per item when the plugin loads
        ArrayList<String> result = new ArrayList<>(10);
        if (attributeSection == null) return result;
        Set<String> attributes = attributeSection.getKeys(false);
        String[] attributeLoreOrder = {
                // usually found in weapon lore
                "damage", "knockback", "crit", "useTime", "manaUse", "armorPenetration", "powerPickaxe",
                // multipliers, usually found in armor/accessory lore
                "damageTakenMulti", "damageContactTakenMulti", "damageMulti", "damageMeleeMulti", "damageRangedMulti", "damageMagicMulti", "damageSummonMulti", "damageArrowMulti", "damageBulletMulti", "damageRocketMulti", "damageTrueMeleeMulti",
                "manaUseMulti", "ammoConsumptionRate", "arrowConsumptionRate", "mobSpawnRateMulti", "speedMulti", "meleeReachMulti", "regenMulti",
                "useSpeedMulti", "useSpeedMagicMulti", "useSpeedMeleeMulti", "useSpeedRangedMulti", "knockbackMeleeMulti", "projectileSpeedArrowMulti",
                "maxMana", "regen", "manaRegen", "critMelee", "critMagic", "critRanged", "critTrueMelee",
                // other attributes usually found in armor/accessory lore
                "defence", "invulnerabilityTick", "minionLimit", "sentryLimit", "mobLimit", "knockbackResistance",
                // these attributes are not displayed; however, they are put in this list to prevent sending warning message.
                "damageType", "buffInflictMelee", "bounce", "projectileSpeed", "projectileSpeedMulti", "penetration",
        };
        // send warning message if the attribute is not handled
        for (String attribute : attributes) {
            boolean found = false;
            for (int i = 0; i < attributeLoreOrder.length; i ++)
                if (attributeLoreOrder[i].equals(attribute)) {
                    found = true;
                    break;
                }
            if (!found) Bukkit.getLogger().log(Level.WARNING, "Unhandled attribute when initializing item from yml: " + attribute);
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
                case "useTime": {
                    int useTime = attributeSection.getInt(attribute, 0);
                    if (useTime <= 8) result.add("超快的使用速度");
                    else if (useTime <= 20) result.add("很快的使用速度");
                    else if (useTime <= 25) result.add("快的使用速度");
                    else if (useTime <= 30) result.add("普通的使用速度");
                    else if (useTime <= 35) result.add("慢的使用速度");
                    else if (useTime <= 45) result.add("很慢的使用速度");
                    else if (useTime <= 55) result.add("极慢的使用速度");
                    else result.add("蜗牛的使用速度");
                    break;
                }
                case "manaUse": {
                    int mana = attributeSection.getInt(attribute, 0);
                    result.add("消耗" + mana + "魔力");
                    break;
                }
                case "armorPenetration": {
                    int penetration = attributeSection.getInt(attribute, 0);
                    result.add("无视敌人" + penetration + "防御");
                    break;
                }
                case "powerPickaxe": {
                    int power = attributeSection.getInt(attribute, 0);
                    result.add(power + "% 镐力");
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
                case "regen": {
                    if (attributeSection.getDouble(attribute, 0) < 0) result.add("减缓生命恢复");
                    else result.add("加快生命恢复");
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
                    if (attributeSection.getDouble(attribute, 0) < 0) result.add("增加最高刷怪量");
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
                // these attributes are not shown.
                case "damageType":
                case "bounce":
                case "projectileSpeed":
                case "penetration":
                case "buffInflictMelee":
                    break;
                default:
                    if (attributeDisplayName.containsKey(attribute)) {
                        double multi = attributeSection.getDouble(attribute, 0d);
                        int multiPercentage = (int) (multi * 100);
                        String descr = attributeDisplayName.getOrDefault(attribute, attribute);
                        result.add((multi > 0 ? "+" : "") + multiPercentage + "% " + descr);
                        break;
                    }
                    result.add(attributeSection.getString(attribute) + " " + attribute);
                    Bukkit.getLogger().log(Level.WARNING, "Raw attribute name used when initializing item from yml （" + attribute + ")");
            }
        }
        return result;
    }
    public static ItemStack getItemFromYML(String itemName) {
        try {
            if (TerrariaHelper.itemConfig.contains(itemName)) {
                ConfigurationSection itemSection = TerrariaHelper.itemConfig.getConfigurationSection(itemName);
                Material material = Material.valueOf(itemSection.getString("item", "AIR"));
                ItemStack item = new ItemStack(material);
                // air has no item meta.
                if (material == Material.AIR) return item;
                ItemMeta meta = item.getItemMeta();
                byte data = (byte) itemSection.getInt("data", 0);
                if (data != 0) item.getData().setData(data);
                int rarity = itemSection.getInt("rarity", 0);
                String rarityColor = TerrariaHelper.settingConfig.getString("rarity." + rarity, "§r");
                meta.setDisplayName(rarityColor + itemName);
                List<String> lore = itemSection.getStringList("lore");
                List<String> loreDescription = getLoreDescription(itemSection.getConfigurationSection("attributes"));
                ArrayList<String> finalLore = new ArrayList<>(20);
                while (lore.size() > 0 && lore.get(0).endsWith("]")) finalLore.add(lore.remove(0));
                finalLore.addAll(loreDescription);
                finalLore.addAll(lore);
                if (finalLore.size() > 0) meta.setLore(finalLore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
                return item;
            } else {
                return new ItemStack(Material.valueOf(itemName));
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "ItemHelper.getItemFromYml", e);
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
            Bukkit.getLogger().log(Level.SEVERE, "ItemHelper.getItemDescription", e);
            return item.getType().toString();
        }
    }
    public static Item dropItem(Location loc, String itemToDropDescription) {
        return dropItem(loc, itemToDropDescription, true, true);
    }
    public static Item dropItem(Location loc, String itemToDropDescription, boolean randomizePrefixIfNoneExists, boolean canMerge) {
        String[] itemInfo = itemToDropDescription.split(":");
        // 物品名:最小数量:最大数量:几率
        int itemAmount = 1;
        switch (itemInfo.length) {
            case 4:
                double chance = Double.parseDouble(itemInfo[3]);
                if (Math.random() > chance) return null;
                break;
            case 3:
                int itemMax = Integer.parseInt(itemInfo[2]);
                int itemMin = Integer.parseInt(itemInfo[1]);
                itemAmount = (int) (itemMin + (itemMax - itemMin + 1) * Math.random());
                break;
            case 2:
                itemAmount = Integer.parseInt(itemInfo[1]);
                break;
        }
        ItemStack itemToDrop = getItemFromDescription(itemInfo[0], randomizePrefixIfNoneExists);
        itemToDrop.setAmount(itemAmount);
        return dropItem(loc, itemToDrop, canMerge);
    }
    public static Item dropItem(Location loc, ItemStack itemToDrop) {
        return dropItem(loc, itemToDrop, true);
    }
    public static Item dropItem(Location loc, ItemStack itemToDrop, boolean canMerge) {
        if (itemToDrop.getAmount() <= 0) return null;
        if (itemToDrop.getType() == Material.AIR) return null;
        TerrariaItem entity = new TerrariaItem(loc, itemToDrop);
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
