package terraria.util;

import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class ArmourerWorkshopHelper {
    public static void setArmorModel(ItemStack item, ConfigurationSection armourerSection) {
        NBTItem itemNBT = new NBTItem(item, true);
        // {armourersWorkshop:{
        //      identifier:{skinType:\"armourers:head\",globalId:0,localId:526652617},
        //      dyeData:{}}}
        ReadWriteNBT workshopCpd = itemNBT.getOrCreateCompound("armourersWorkshop");
        ReadWriteNBT identifierCpd = workshopCpd.getOrCreateCompound("identifier");
        identifierCpd.setLong("localId", armourerSection.getLong("localId", 0L));
    }
}
