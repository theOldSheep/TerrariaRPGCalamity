package terraria.util;

import net.minecraft.server.v1_12_R1.EntityItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftItem;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import terraria.entity.TerrariaItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class ItemHelper {
    public static final String placeholderItemNamePrefix = "§1§1§4§5§1§4";
    public static HashMap<String, String> attributeDisplayName = new HashMap<>();
    private static final HashMap<String, ItemStack> itemMap = new HashMap<>();
    static {
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
        attributeDisplayName.put("projectileSpeedArrowMulti", "箭矢速度");
    }
    private static final YmlHelper.YmlSection itemConfig = YmlHelper.getFile("plugins/Data/items.yml");
    private static final YmlHelper.YmlSection settingConfig = YmlHelper.getFile("plugins/Data/settings.yml");
    private static final YmlHelper.YmlSection weaponConfig = YmlHelper.getFile("plugins/Data/weapons.yml");
    private static final YmlHelper.YmlSection prefixConfig = YmlHelper.getFile("plugins/Data/prefix.yml");
    public static String[] splitItemName(String itemName) {
        try {
            itemName = GenericHelper.trimText(itemName);
            if (itemName.contains("的 ")) {
                return itemName.split("的 ");
            }
            return new String[]{"", itemName};
        } catch (Exception e) {
            return new String[]{"", ""};
        }
    }
    public static String[] splitItemName(ItemStack item) {
        try {
            return splitItemName(item.getItemMeta().getDisplayName());
        } catch (Exception e) {
            return new String[]{"", ""};
        }
    }
    public static int getWorth(String name) {
        if (name == null) name = "";
        String[] nameInfo = splitItemName(name);
        int worth = YmlHelper.getFile("plugins/Data/items.yml").getInt(nameInfo[1] + ".worth", 0);
        double worthMulti = YmlHelper.getFile("plugins/Data/prefix.yml").getDouble(
                "prefixInfo." + nameInfo[0] + ".priceMultiplier", 1);
        worth *= worthMulti;
        return (worth / 100) * 100;
    }
    public static int getWorth(ItemStack item) {
        if (item == null) return 0;
        return getWorth(item.getItemMeta().getDisplayName());
    }
    public static boolean canReforge(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            String trimLore = GenericHelper.trimText(meta.getLore().get(0));
            switch (trimLore) {
                case "[饰品]":
                    return true;
                case "[装备]":
                    return false;
            }
        }
        // now determine if the item is a proper weapon.
        String itemName = splitItemName(item)[1];
        // items that deals no damage, for example, bug net, shall not have any prefix
        if (itemConfig.getDouble(itemName + ".attributes.damage", -1) <= 0) return false;
        // thrown projectile (grenades, for example), can not have any prefix neither
        String weaponType = weaponConfig.getString(itemName + ".type", "THROW");
        return !weaponType.startsWith("THROW");
    }
    public static HashMap<String, Double> getApplicablePrefix(ItemStack item) {
        HashMap<String, Double> result = new HashMap<>(50);
        if (!canReforge(item)) return result;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            String trimLore = GenericHelper.trimText(meta.getLore().get(0));
            if (trimLore.equals("[饰品]")) {
                for (String prefix : prefixConfig.getStringList("prefixList.Accessory")) {
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
        switch (weaponConfig.getString(itemName + ".type").toLowerCase()) {
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
            for (String prefix : prefixConfig.getStringList("prefixList." + prefixClass)) {
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
        return getItemFromDescription(prefix + "的 " + itemInfo[1], false);
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
                ItemStack result = getItemFromDescription(info[0], randomizePrefixIfNoneExists);
                result.setAmount(Integer.parseInt(info[1]));
                return result;
            } else {
                String[] itemNameInfo = splitItemName(information);
                String prefix = itemNameInfo[0];
                String itemType = itemNameInfo[1];
                // get the itemstack
                ItemStack resultItem;
                try {
                    resultItem = itemMap.getOrDefault(itemType, new ItemStack(Material.valueOf(itemType))).clone();
                } catch (Exception e) {
                    resultItem = notFoundDefault;
                }
                // setup prefix
                if (prefix.length() == 0) {
                    if (randomizePrefixIfNoneExists) resultItem = randomPrefix(resultItem);
                } else {
                    List<String> lore;
                    if (itemType.equals("专家模式福袋")) {
                        lore = new ArrayList<>(1);
                        lore.add(ChatColor.COLOR_CHAR + "7" + prefix);
                    } else {
                        // generate color according to rarity
                        int rarity = itemConfig.getInt(itemNameInfo[1] + ".rarity", 0);
                        int prefixRarity = prefixConfig.getInt("prefixInfo." + prefix + ".rarity", 0);
                        // if rarity color [rarity + prefixRarity] is set, use it
                        // otherwise, resolve to rarity color [rarity]
                        String rarityColorPrefix = settingConfig.getString("rarity." + (rarity + prefixRarity),
                                settingConfig.getString("rarity." + rarity, ""));
                        resultItem.getItemMeta().setDisplayName(rarityColorPrefix + information);
                        // add prefix lore to item
                        lore = resultItem.getItemMeta().getLore();
                        lore.addAll(prefixConfig.getStringList("prefixInfo." + prefix + ".lore"));
                        resultItem.getItemMeta().setLore(lore);
                    }
                    resultItem.getItemMeta().setLore(lore);
                }
                return resultItem;
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "ItemHelper.getItemFromDescription", e);
            return notFoundDefault;
        }
    }

    private static List<String> getLoreDescription(ConfigurationSection attributeSection) {
        // the performance of this function is not very critical
        // it is called only once per item when the plugin loads
        ArrayList<String> result = new ArrayList<>(10);
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
                default:
                    if (attributeDisplayName.containsKey(attribute)) {
                        double multi = attributeSection.getDouble(attribute, 0d);
                        int multiPercentage = (int) (multi * 100);
                        String descr = attributeDisplayName.getOrDefault(attribute, attribute);
                        result.add((multi > 0 ? "+" : "") + multiPercentage + "% " + descr);
                        break;
                    }
                    result.add(attributeSection.getString(attribute) + " " + attribute);
            }
        }
        return result;
    }
    public static ItemStack getItemFromYML(String itemName) {
        try {
            if (itemConfig.contains(itemName)) {
                ConfigurationSection itemSection = itemConfig.getConfigurationSection(itemName);
                Material material = Material.valueOf(itemSection.getString("item", "AIR"));
                ItemStack item = new ItemStack(material);
                byte data = (byte) itemSection.getInt("data", 0);
                if (data != 0) item.getData().setData(data);
                int rarity = itemSection.getInt("rarity", 0);
                String rarityColor = settingConfig.getString("rarity." + rarity, "5");
                item.getItemMeta().setDisplayName(rarityColor + itemName);
                List<String> lore = itemSection.getStringList("lore");
                List<String> loreDescription = getLoreDescription(itemSection.getConfigurationSection("attributes"));
                ArrayList<String> finalLore = new ArrayList<>(20);
                if (lore.size() > 0 && lore.get(0).endsWith("]")) finalLore.add(lore.remove(0));
                finalLore.addAll(loreDescription);
                finalLore.addAll(lore);
                if (finalLore.size() > 0) item.getItemMeta().setLore(finalLore);
                item.getItemMeta().addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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
    // TODO
    public static Item dropItem(Location loc, ItemStack itemToDrop) {
        EntityItem entity = new TerrariaItem(loc, itemToDrop);
        CraftWorld wld = (CraftWorld) loc.getWorld();
        wld.addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        return new CraftItem(wld.getHandle().getServer(), entity);
    }
}
