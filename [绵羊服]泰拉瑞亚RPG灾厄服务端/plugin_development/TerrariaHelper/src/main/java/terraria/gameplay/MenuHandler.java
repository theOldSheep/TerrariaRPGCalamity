package terraria.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import terraria.TerrariaHelper;
import terraria.util.ItemHelper;

import java.util.HashMap;
import java.util.UUID;

public class MenuHandler implements CommandExecutor, Listener {
    public static final String COMMAND = "terrariaMenu";
    static HashMap<UUID, String> PLAYER_GUI_MAP = new HashMap<>();
    static HashMap<String, Inventory> GUIS_MAP = new HashMap<>();
    // saves the commands associated with the slots
    static HashMap<String, String> COMMANDS_MAP = new HashMap<>();


    private static String getSlotMappingKey(String invCfg, int slotIdx) {
        return invCfg + "_" + slotIdx;
    }
    public static void loadGuis() {
        for (String gui : TerrariaHelper.menusConfig.getKeys(false)) {
            ConfigurationSection guiSection = TerrariaHelper.menusConfig.getConfigurationSection(gui);

            Inventory inv = Bukkit.createInventory(null, 54, guiSection.getString("title", "Menu"));
            ConfigurationSection slotsSection = guiSection.getConfigurationSection("items");
            // insert the menu items
            for (String slot : slotsSection.getKeys(false)) {
                ConfigurationSection currSlotSection = slotsSection.getConfigurationSection(slot);

                ItemStack currItem = new ItemStack(Material.PAINTING);
                ItemMeta itemMeta = currItem.getItemMeta();
                itemMeta.setDisplayName(currSlotSection.getString("name", "？？？"));
                itemMeta.setLore(currSlotSection.getStringList("lore"));
                currItem.setItemMeta(itemMeta);

                // log the slot info & save the slot to GUI
                int row = currSlotSection.getInt("row", 0);
                int col = currSlotSection.getInt("col", 0);
                int idx = row * 9 + col;
                if (currSlotSection.contains("cmd")) {
                    COMMANDS_MAP.put(getSlotMappingKey(gui, idx), currSlotSection.getString("cmd"));
                }
                inv.setItem(idx, currItem);
            }

            // save the GUI
            GUIS_MAP.put(gui, inv);
        }
    }

    public static void openGUI(Player ply, String gui) {
        if (GUIS_MAP.containsKey(gui)) {
            PLAYER_GUI_MAP.put(ply.getUniqueId(), gui);
            ply.openInventory(GUIS_MAP.get(gui));
        }
    }


    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (args.length >= 1 && commandSender instanceof Player) {
            openGUI((Player) commandSender, args[0]);
            return true;
        }
        return false;
    }


    @EventHandler(priority = EventPriority.NORMAL)
    public void onSlotClick(InventoryClickEvent e) {
        if (! (e.getWhoClicked() instanceof Player) )
            return;
        Player clickedPly = (Player) e.getWhoClicked();
        UUID plyUid = clickedPly.getUniqueId();
        if (PLAYER_GUI_MAP.containsKey(plyUid)) {
            String guiName = PLAYER_GUI_MAP.get(plyUid);
            // the clicked inventory is the GUI
            e.setCancelled(true);
            // execute command if applicable
            String cmdKey = getSlotMappingKey(guiName, e.getRawSlot());
            if (COMMANDS_MAP.containsKey(cmdKey)) {
                String cmd = COMMANDS_MAP.get(cmdKey);
                if (cmd.startsWith(COMMAND)) {
                    clickedPly.closeInventory();
                }
                clickedPly.performCommand(cmd);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInvClose(InventoryCloseEvent e) {
        PLAYER_GUI_MAP.remove(e.getPlayer().getUniqueId());
    }
}
