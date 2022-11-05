package terraria.util;

import org.bukkit.inventory.ItemStack;

public class ItemHelper {
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

}
