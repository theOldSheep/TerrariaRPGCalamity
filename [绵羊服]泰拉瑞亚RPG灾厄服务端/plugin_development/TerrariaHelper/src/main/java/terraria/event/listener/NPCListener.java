package terraria.event.listener;

import lk.vexview.api.VexViewAPI;
import lk.vexview.gui.VexGui;
import lk.vexview.gui.components.VexButton;
import lk.vexview.gui.components.VexComponents;
import lk.vexview.gui.components.VexText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.omg.CORBA.TypeCodePackage.BadKind;
import terraria.TerrariaHelper;
import terraria.gameplay.Event;
import terraria.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NPCListener implements Listener {
    private int getHealingCost(Player ply) {
        double costMulti;
        if (PlayerHelper.hasDefeated(ply, "石巨人"))
            costMulti = 200;
        else if (PlayerHelper.hasDefeated(ply, "世纪之花"))
            costMulti = 150;
        else if (PlayerHelper.hasDefeated(ply, "机械一王"))
            costMulti = 100;
        else if (PlayerHelper.hasDefeated(ply, "血肉之墙"))
            costMulti = 60;
        else if (PlayerHelper.hasDefeated(ply, "骷髅王"))
            costMulti = 25;
        else if (PlayerHelper.hasDefeated(ply, "世界吞噬者"))
            costMulti = 10;
        else if (PlayerHelper.hasDefeated(ply, "克苏鲁之眼"))
            costMulti = 3;
        else
            costMulti = 1;
        double health = ply.getHealth();
        double maxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        return (int) Math.round((maxHealth - health) * costMulti * 150);
    }
    private boolean attemptSubmitQuestFish(Player ply) {
        // if player has already submitted the quest fish for the day
        if (Event.questFishSubmitted.contains(ply.getName()))
            return false;
        ItemStack itemHeld = ply.getInventory().getItemInMainHand();
        // if the player is holding the quest fish for the day
        if (ItemHelper.splitItemName(itemHeld)[0].equals(Event.questFish)) {
            // remove the fish
            itemHeld.setAmount(itemHeld.getAmount() - 1);
            Event.questFishSubmitted.add(ply.getName());
            // give the player rewards
            List<String> itemsToGive = TerrariaHelper.fishingConfig.getStringList("questRewards");
            for (String item : itemsToGive) {
                ItemStack itemStack = ItemHelper.getItemFromDescription(item, true);
                if (itemStack.getAmount() > 0 && itemStack.getType() != Material.AIR)
                    PlayerHelper.giveItem(ply, itemStack, true);
            }
            return true;
        }
        return false;
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onVanillaTradeGuiOpen(InventoryOpenEvent evt) {
        if (evt.getInventory().getType().equals(InventoryType.MERCHANT))
            evt.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent evt) {
        Player ply = (Player) evt.getPlayer();
        MetadataValue NPCViewing = EntityHelper.getMetadata(ply, "NPCViewing");
        if (NPCViewing != null) {
            ((List<Player>) EntityHelper.getMetadata((Metadatable) NPCViewing.value(), "GUIViewers").value()).remove(ply);
        }
        EntityHelper.setMetadata(ply, "NPCViewing", null);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent evt) {
        // ignore cases below
        if (evt.isCancelled())
            return;
        Entity clickedNPC = evt.getRightClicked();
        if (!clickedNPC.getScoreboardTags().contains("isNPC"))
            return;
        Player ply = evt.getPlayer();
        if (ply.getScoreboardTags().contains("temp_useCD"))
            return;
        // update cool down and open GUI
        ItemUseHelper.applyCD(ply, 20);
        String NPCType = clickedNPC.getName();
        // prepare GUI
        int w = VexViewAPI.getPlayerClientWindowWidth(ply), h = VexViewAPI.getPlayerClientWindowHeight(ply);
        String bg = TerrariaHelper.Constants.GUI_BACKGROUND_NPC;
        VexGui gui = new VexGui(bg,
                (int) (w * 0.25), (int) (h * 0.33), (int) (w * 0.5), (int) (h * 0.33));
        ArrayList<VexComponents> comps = new ArrayList<>(4);
        ArrayList<String> texts = new ArrayList<>(50);
        ConfigurationSection msgSection = TerrariaHelper.NPCConfig.getConfigurationSection("messages." + NPCType);
        switch (NPCType) {
            case "向导": {
                // set up a list of possible lore texts
                Set<String> msgScenarios = msgSection.getKeys(false);
                for (String msgScenario : msgScenarios) {
                    // validate the scenario
                    String activate_progress = msgSection.getString(msgScenario + ".activate_progress");
                    if (activate_progress != null && (!PlayerHelper.hasDefeated(ply, activate_progress)))
                        continue;
                    String deactivate_progress = msgSection.getString(msgScenario + ".deactivate_progress");
                    if (deactivate_progress != null && PlayerHelper.hasDefeated(ply, deactivate_progress))
                        continue;
                    // include the messages in the scenario
                    texts.addAll(msgSection.getStringList(msgScenario + ".texts"));
                }
                // buttons
                comps.add(new VexButton("HELP", "帮助", bg, bg, 50, h / 3 - 30, 26, 17));
                comps.add(new VexButton("CLOSE", "关闭", bg, bg, 100, h / 3 - 30, 26, 17));
                break;
            }
            case "护士": {
                StringBuilder healBtnDisplay = new StringBuilder("治疗");
                int btnW = 26;
                // init texts
                {
                    double health = ply.getHealth();
                    double maxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    if (health < maxHealth) {
                        int healthRatioInfo = 3 - (int) (health / maxHealth * 3);
                        texts.addAll(msgSection.getStringList("hurt." + healthRatioInfo));
                        // btn display name
                        btnW += 40;
                        healBtnDisplay = new StringBuilder("治疗§7(需要 ");
                        int[] coinNeeded = GenericHelper.coinConversion(getHealingCost(ply), false);
                        String[] additionalStr = {" §r■铂 ", " §e■金 ", " §7■银 ", " §c■铜 "};
                        for (int index = 0; index < 4; index++) {
                            if (coinNeeded[index] > 0) {
                                healBtnDisplay.append(additionalStr[index].replace(
                                        "■", coinNeeded[index] + ""));
                                btnW += 30;
                            }
                        }
                        healBtnDisplay.append("§7)");
                    } else {
                        texts.addAll(msgSection.getStringList("healthy"));
                    }
                }
                // buttons
                comps.add(new VexButton("HEAL", healBtnDisplay.toString(), bg, bg, 50, h / 3 - 30, btnW, 17));
                comps.add(new VexButton("CLOSE", "关闭", bg, bg, 74 + btnW, h / 3 - 30, 26, 17));
                break;
            }
            case "渔夫": {
                // submitted already
                if (Event.questFishSubmitted.contains(ply.getName())) {
                    texts.addAll(msgSection.getStringList("questDone"));
                }
                // finish quest
                else if (attemptSubmitQuestFish(ply)) {
                    texts.addAll(msgSection.getStringList("questFinishing"));
                }
                // quest available
                else {
                    for (String toAdd : msgSection.getStringList("quest")) {
                        texts.add(toAdd.replace("<fishName>", Event.questFish));
                    }
                    for (String availableBiome : TerrariaHelper.fishingConfig.getStringList("questFish." + Event.questFish)) {
                        String[] splitInfo = availableBiome.split("_");
                        StringBuilder locationInfoStr = new StringBuilder();
                        locationInfoStr.append("§7[提示] §r");
                        locationInfoStr.append(Event.questFish);
                        locationInfoStr.append(" §7可以在 ");
                        // height
                        try {
                            locationInfoStr.append(WorldHelper.HeightLayer.valueOf(splitInfo[1]).name);
                        } catch (Exception e) {
                            locationInfoStr.append(splitInfo[1]);
                        }
                        locationInfoStr.append(" 高度的 ");
                        // biome
                        try {
                            locationInfoStr.append(WorldHelper.BiomeType.valueOf(splitInfo[0]).name);
                        } catch (Exception e) {
                            locationInfoStr.append(splitInfo[0]);
                        }
                        locationInfoStr.append(" 钓到！");
                        ply.sendMessage(locationInfoStr.toString());
                    }
                }
                comps.add(new VexButton("CLOSE", "关闭", bg, bg, 50, h / 3 - 30, 26, 17));
                break;
            }
            default: {
                String progressRequired = msgSection.getString("tier");
                // if the trade is being denied
                if (progressRequired != null && (! PlayerHelper.hasDefeated(ply, progressRequired)) ) {
                    texts.addAll(msgSection.getStringList("deny"));
                    if (NPCType.equals("裁缝") && (! WorldHelper.isDayTime(ply.getWorld())) ) {
                        comps.add(new VexButton("CURSE", "诅咒", bg, bg, 50, h / 3 - 30, 26, 17));
                        comps.add(new VexButton("CLOSE", "关闭", bg, bg, 100, h / 3 - 30, 26, 17));
                    } else {
                        comps.add(new VexButton("CLOSE", "关闭", bg, bg, 50, h / 3 - 30, 26, 17));
                    }
                }
                // if the player can trade with the NPC
                else {
                    texts.addAll(msgSection.getStringList("trade"));
                    comps.add(new VexButton("SHOP", "商店", bg, bg, 50, h / 3 - 30, 26, 17));
                    comps.add(new VexButton("CLOSE", "关闭", bg, bg, 100, h / 3 - 30, 26, 17));
                    if (NPCType.equals("哥布林工匠"))
                        comps.add(new VexButton("REFORGE", "重铸", bg, bg, 150, h / 3 - 30, 26, 17));
                }
            }
        }
        // open GUI
        String textDisplay = "";
        if (texts.size() > 0) {
            textDisplay = texts.get((int) (Math.random() * texts.size()));
        }
        textDisplay = textDisplay.replace("<name>", ply.getName());
        ArrayList<String> lines = new ArrayList<>();
        double textSize = (double)w / 600;
        // fit the text into the chat box
        {
            double totalW = 0;
            double wText = (double)w * 10 / 11;
            char[] characters = textDisplay.toCharArray();
            StringBuilder currentLine = new StringBuilder();
            for (char character : characters) {
                // chinese characters are twice as long.
                if (String.valueOf(character).matches("[u4e00-u9fa5]")) {
                    totalW += textSize * 10;
                }
                else {
                    totalW += textSize * 20;
                }
                if (totalW >= wText) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                    totalW = 0;
                }
                currentLine.append(character);
            }
            lines.add(currentLine.toString());
        }
        comps.add(new VexText(w / 22, h / 17, lines, textSize));
        gui.addAllComponents(comps);
        VexViewAPI.openGui(ply, gui);
        // keep track of NPC GUI opened
        ((List<Player>) EntityHelper.getMetadata(clickedNPC, "GUIViewers").value()).add(ply);
        EntityHelper.setMetadata(ply, "NPCViewing", clickedNPC);
    }
}
