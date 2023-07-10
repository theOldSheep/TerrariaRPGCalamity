package terraria.event.listener;

import lk.vexview.api.VexViewAPI;
import lk.vexview.event.ButtonClickEvent;
import lk.vexview.gui.VexGui;
import lk.vexview.gui.components.VexButton;
import lk.vexview.gui.components.VexComponents;
import lk.vexview.gui.components.VexText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import terraria.TerrariaHelper;
import terraria.gameplay.EventAndTime;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NPCListener implements Listener {
    // some listeners to prevent bug
    public void recordInteractingNPC(Player ply, Entity NPC) {
        if (NPC != null) {
            ((HashSet<Player>) EntityHelper.getMetadata(NPC, EntityHelper.MetadataName.NPC_GUI_VIEWERS).value()).add(ply);
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_NPC_INTERACTING, NPC);
        } else {
            MetadataValue NPCViewing = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_NPC_INTERACTING);
            if (NPCViewing != null) {
                ((HashSet<Player>) EntityHelper.getMetadata((Metadatable) NPCViewing.value(),
                        EntityHelper.MetadataName.NPC_GUI_VIEWERS).value()).remove(ply);
            }
            EntityHelper.setMetadata(ply, EntityHelper.MetadataName.PLAYER_NPC_INTERACTING, null);
        }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent evt) {
        Player ply = (Player) evt.getPlayer();
        recordInteractingNPC(ply, null);
        Inventory closedInv = evt.getInventory();
        if (closedInv.getTitle().equals("重铸")) {
            ItemStack reforgedItem = closedInv.getItem(4);
            if (reforgedItem != null && reforgedItem.getType() != Material.AIR)
                PlayerHelper.giveItem(ply, reforgedItem, true);
        }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onDisconnect(PlayerQuitEvent evt) {
        recordInteractingNPC(evt.getPlayer(), null);
    }
    // below: functions related to direct interaction with the NPC
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
        if (EventAndTime.questFishSubmitted.contains(ply.getName()))
            return false;
        ItemStack itemHeld = ply.getInventory().getItemInMainHand();
        // if the player is holding the quest fish for the day
        if (ItemHelper.splitItemName(itemHeld)[1].equals(EventAndTime.questFish.toString())) {
            // remove the fish
            itemHeld.setAmount(itemHeld.getAmount() - 1);
            EventAndTime.questFishSubmitted.add(ply.getName());
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
    public void handleInteractNPC(Player ply, Entity clickedNPC) {
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
                        btnW += 20;
                        healBtnDisplay = new StringBuilder("治疗§7(需要");
                        int[] coinNeeded = GenericHelper.coinConversion(getHealingCost(ply), false);
                        String[] additionalStr = {"§r■铂", "§e■金", "§7■银", "§c■铜"};
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
                comps.add(new VexButton("HEAL", healBtnDisplay.toString(), bg, bg, 20, h / 3 - 30, btnW, 17));
                comps.add(new VexButton("CLOSE", "关闭", bg, bg, 44 + btnW, h / 3 - 30, 26, 17));
                break;
            }
            case "渔夫": {
                // submitted already
                if (EventAndTime.questFishSubmitted.contains(ply.getName())) {
                    texts.addAll(msgSection.getStringList("questDone"));
                }
                // finish quest
                else if (attemptSubmitQuestFish(ply)) {
                    texts.addAll(msgSection.getStringList("questFinishing"));
                }
                // quest available
                else {
                    for (String toAdd : msgSection.getStringList("quest")) {
                        texts.add(toAdd.replace("<fishName>", EventAndTime.questFish.toString()));
                    }
                    for (String availableBiome : TerrariaHelper.fishingConfig.getStringList(
                            "questFish." + EventAndTime.questFish)) {
                        String[] splitInfo = availableBiome.split("_");
                        StringBuilder locationInfoStr = new StringBuilder();
                        locationInfoStr.append("§7[提示] §r");
                        locationInfoStr.append(EventAndTime.questFish);
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
        recordInteractingNPC(ply, clickedNPC);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onVanillaTradeGuiOpen(InventoryOpenEvent evt) {
        if (evt.getInventory().getType().equals(InventoryType.MERCHANT))
            evt.setCancelled(true);
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
        handleInteractNPC(ply, clickedNPC);
    }
    // below: GUI button features
    private int fillShopGui(Player ply, Inventory shopInv, int index, ConfigurationSection shopSection) {
        for (String gameProgress : shopSection.getKeys(false)) {
            // for event only scenarios
            if ( (EventAndTime.currentEvent.toString()).equals(gameProgress) )
                index = fillShopGui(ply, shopInv, index, shopSection.getConfigurationSection(gameProgress));
            // if the trade is available
            if (gameProgress.equals("default") || PlayerHelper.hasDefeated(ply, gameProgress)) {
                List<String> itemsSold = shopSection.getStringList(gameProgress);
                for (String itemToSellDescription : itemsSold) {
                    ItemStack itemToSell = ItemHelper.getItemFromDescription(itemToSellDescription, false);
                    shopInv.setItem(index ++, itemToSell);
                }
            }
        }
        return index;
    }
    private void openShopGUI(Player ply, Villager NPC) {
        Inventory shopInv = Bukkit.createInventory(NPC, 54, "商店");
        String NPCType = NPC.getName();
        ConfigurationSection shopSection = TerrariaHelper.NPCConfig.getConfigurationSection("shops." + NPCType);
        int index = fillShopGui(ply, shopInv, 0, shopSection);
        // open inv and setup variables
        EntityHelper.setMetadata(ply, EntityHelper.MetadataName.NPC_FIRST_SELL_INDEX, index);
        recordInteractingNPC(ply, NPC);
        ply.openInventory(shopInv);
        ply.sendMessage("§a您可以按左键买卖单个物品，shift+左键买卖整组物品，或右键查看物品价格。");
    }
    private void openReforgeGui(Player ply, Villager NPC) {
        Inventory reforgeInv = Bukkit.createInventory(NPC, 27, "重铸");
        for (int i = 0; i < 27; i ++) {
            if (i == 4)
                continue;
            ItemStack placeholder = new ItemStack(Material.STAINED_GLASS_PANE);
            ItemMeta meta = placeholder.getItemMeta();
            meta.setDisplayName("§a");
            placeholder.setItemMeta(meta);
            reforgeInv.setItem(i, placeholder);
        }
        // open inv and setup variables
        recordInteractingNPC(ply, NPC);
        ply.openInventory(reforgeInv);
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onButtonClick(ButtonClickEvent evt) {
        Player ply = evt.getPlayer();
        MetadataValue NPCViewingMetadata = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_NPC_INTERACTING);
        if (NPCViewingMetadata == null)
            return;
        Villager NPCViewing = (Villager) NPCViewingMetadata.value();
        Object btnID = evt.getButtonID();
        ply.closeInventory();
        if (btnID.equals("HELP")) {
            handleInteractNPC(ply, NPCViewing);
        }
        else if (btnID.equals("SHOP")) {
            openShopGUI(ply, NPCViewing);
        }
        else if (btnID.equals("HEAL")) {
            int moneyRequired = getHealingCost(ply);
            double plyMoney = PlayerHelper.getMoney(ply);
            if (BossHelper.bossMap.size() > 0) {
                ply.sendMessage("§cBOSS存活时无法使用护士治疗");
            }
            else if (plyMoney < moneyRequired) {
                ply.sendMessage("§c您没有足够的钱。");
            }
            else if (moneyRequired < 10) {
                ply.sendMessage("§r您没有受伤哦，无法进行治疗。");
            } else {
                plyMoney -= moneyRequired;
                PlayerHelper.heal(ply, (int) (ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() - ply.getHealth()) );
                PlayerHelper.setMoney(ply, plyMoney);
            }
        }
        else if (btnID.equals("CURSE")) {
            if (! WorldHelper.isDayTime(ply.getWorld()) ) {
                BossHelper.spawnBoss(ply, BossHelper.BossType.SKELETRON);
            }
        }
        else if (btnID.equals("REFORGE")) {
            openReforgeGui(ply, NPCViewing);
        }
    }
    // below: secondary GUI (shop, reforge) features
    private void updateReforgeInventory(Inventory reforgeInv) {
        ItemStack itemReforge = reforgeInv.getItem(4);
        ItemStack priceTipItem = reforgeInv.getItem(13);
        ItemMeta itemMeta = priceTipItem.getItemMeta();
        if (itemReforge == null || itemReforge.getType() == Material.AIR || ! ItemHelper.canReforge(itemReforge)) {
            itemMeta.setDisplayName("§c");
        } else {
            String reforgeCost = GenericHelper.getCoinDisplay(
                    GenericHelper.coinConversion(
                            ItemHelper.getReforgeCost(itemReforge), false));
            itemMeta.setDisplayName("§r花费:" + reforgeCost);
        }
        priceTipItem.setItemMeta(itemMeta);
        reforgeInv.setItem(13, priceTipItem);
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent evt) {
        InventoryView invView = evt.getView();
        Inventory upperInventory = invView.getTopInventory();
        String title = upperInventory.getTitle();
        switch (title) {
            case "重铸":
            case "商店":
                evt.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent evt) {
        InventoryView invView = evt.getView();
        Inventory upperInventory = invView.getTopInventory();
        String title = upperInventory.getTitle();
        Player ply = (Player) invView.getPlayer();
        boolean clickedInGui = evt.getRawSlot() == evt.getSlot();
        switch (title) {
            case "重铸": {
                boolean hasUpdated = false;
                if (clickedInGui) {
                    // reforge slot is not clicked
                    if (evt.getSlot() != 4) {
                        evt.setCancelled(true);
                        // attempt to reforge
                        if (evt.getSlot() == 13) {
                            ItemStack toReforge = upperInventory.getItem(4);
                            if (ItemHelper.canReforge(toReforge)) {
                                double cost = ItemHelper.getReforgeCost(toReforge);
                                double plyMoney = PlayerHelper.getMoney(ply);
                                if (plyMoney >= cost) {
                                    ply.playSound(ply.getLocation(), Sound.BLOCK_ANVIL_USE, 1, 1);
                                    // reforge
                                    String itemType = ItemHelper.splitItemName(toReforge)[1];
                                    ItemStack reforged = ItemHelper.getItemFromDescription(itemType, true);
                                    upperInventory.setItem(4, reforged);
                                    // money
                                    PlayerHelper.setMoney(ply, plyMoney - cost);
                                }
                            }
                            // update reforge inventory now
                            updateReforgeInventory(upperInventory);
                            hasUpdated = true;
                        }
                    }
                }
                if (! hasUpdated) {
                    Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(), () -> updateReforgeInventory(upperInventory) );
                }
                break;
            }
            case "商店": {
                evt.setCancelled(true);
                MetadataValue firstSellMetadata = EntityHelper.getMetadata(ply, EntityHelper.MetadataName.NPC_FIRST_SELL_INDEX);
                // if the GUI variable is not valid, close inventory
                if (firstSellMetadata == null) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), ply::closeInventory, 1);
                    return;
                }
                ItemStack clickedItem = evt.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                    return;
                }
                // clicked in the shop GUI
                int firstSell = firstSellMetadata.asInt();
                int basicWorth = ItemHelper.getWorth(clickedItem);
                int clickType = 0;
                if (evt.getClick() == ClickType.LEFT) clickType = 1;
                else if (evt.getClick() == ClickType.SHIFT_LEFT) clickType = 2;
                if (clickType > 0) {
                    double plyMoney = PlayerHelper.getMoney(ply);
                    if (clickedInGui) {
                        // buy back
                        if (evt.getSlot() >= firstSell) {
                            int amountBuyBack = Math.min(clickType == 1 ? 1 : clickedItem.getAmount(),
                                    (int) (plyMoney / basicWorth));
                            if (amountBuyBack <= 0)
                                PlayerHelper.sendActionBar(ply, "§c您的余额不足以买回该物品。");
                            else {
                                int amountLeft = clickedItem.getAmount() - amountBuyBack;
                                clickedItem.setAmount(amountBuyBack);
                                PlayerHelper.giveItem(ply, clickedItem, true);
                                clickedItem.setAmount(amountLeft);
                                // money
                                PlayerHelper.setMoney(ply, plyMoney - amountBuyBack * basicWorth);
                            }
                        }
                        // buy
                        else {
                            basicWorth *= 5;
                            int amountBuy = Math.min(clickType == 1 ? 1 : clickedItem.getMaxStackSize(),
                                    (int) (plyMoney / basicWorth));
                            if (amountBuy <= 0)
                                PlayerHelper.sendActionBar(ply, "§c您的余额不足以购买该物品。");
                            else {
                                ItemStack itemBought = clickedItem.clone();
                                itemBought.setAmount(amountBuy);
                                PlayerHelper.giveItem(ply, itemBought, true);
                                // money
                                PlayerHelper.setMoney(ply, plyMoney - amountBuy * basicWorth);
                            }
                        }
                    }
                    // clicked in player's own inventory, sell
                    else {
                        int amountSold = clickType == 1 ? 1 : clickedItem.getAmount();
                        ItemStack itemSold = clickedItem.clone();
                        itemSold.setAmount(amountSold);
                        // item rearrangement
                        clickedItem.setAmount(clickedItem.getAmount() - amountSold);
                        for (int idx = firstSell; idx < 54; idx ++) {
                            ItemStack currSlot = upperInventory.getItem(idx);
                            if (currSlot == null || currSlot.getType() == Material.AIR) {
                                upperInventory.setItem(idx, itemSold);
                                break;
                            }
                        }
                        // money
                        PlayerHelper.setMoney(ply, plyMoney + amountSold * basicWorth);
                    }
                }
                // if the player is inspecting an item sold, its price should be 5 times the basic worth.
                else if (clickedInGui && evt.getSlot() < firstSell) {
                    basicWorth *= 5;
                }
                PlayerHelper.sendActionBar(ply, "§a§l物品价值: " +
                        GenericHelper.getCoinDisplay(GenericHelper.coinConversion(basicWorth, false),
                                new String[]{" §r§l■铂 ", " §e§l■金 ", " §7§l■银 ", " §c§l■铜 "}));
                break;
            }
        }
    }
}
