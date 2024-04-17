package terraria.event.listener;

import lk.vexview.event.ButtonClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
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
import org.bukkit.metadata.MetadataValue;
import terraria.TerrariaHelper;
import terraria.util.*;

public class NPCListener implements Listener {
    // some listeners to prevent bug
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent evt) {
        Player ply = (Player) evt.getPlayer();
        NPCHelper.recordInteractingNPC(ply, null);
        Inventory closedInv = evt.getInventory();
        if (closedInv.getTitle().equals("重铸")) {
            ItemStack reforgedItem = closedInv.getItem(4);
            if (reforgedItem != null && reforgedItem.getType() != Material.AIR)
                PlayerHelper.giveItem(ply, reforgedItem, true);
        }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onDisconnect(PlayerQuitEvent evt) {
        NPCHelper.recordInteractingNPC(evt.getPlayer(), null);
    }
    // direct interaction with the NPC
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
        NPCHelper.handleInteractNPC(ply, clickedNPC);
    }
    // GUI button features
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
            NPCHelper.handleInteractNPC(ply, NPCViewing);
        }
        else if (btnID.equals("SHOP")) {
            NPCHelper.openShopGUI(ply, NPCViewing);
        }
        else if (btnID.equals("HEAL")) {
            int moneyRequired = NPCHelper.getHealingCost(ply);
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
            NPCHelper.openReforgeGui(ply, NPCViewing);
        }
    }
    // below: secondary GUI (shop, reforge) features
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
                                    ply.playSound(ply.getEyeLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                                    // reforge
                                    String itemType = ItemHelper.splitItemName(toReforge)[1];
                                    ItemStack reforged = ItemHelper.getItemFromDescription(itemType, true);
                                    upperInventory.setItem(4, reforged);
                                    // money
                                    PlayerHelper.setMoney(ply, plyMoney - cost);
                                }
                            }
                            // update reforge inventory now
                            NPCHelper.updateReforgeInventory(upperInventory);
                            hasUpdated = true;
                        }
                    }
                }
                if (! hasUpdated) {
                    Bukkit.getScheduler().runTask(TerrariaHelper.getInstance(),
                            () -> NPCHelper.updateReforgeInventory(upperInventory) );
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
                long basicWorth = ItemHelper.getWorth(clickedItem);
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
                            int amountBuy = Math.min(clickType == 1 ? clickedItem.getAmount() : clickedItem.getMaxStackSize(),
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
                        GenericHelper.getCoinDisplay(GenericHelper.coinConversion(basicWorth * clickedItem.getAmount(), false),
                                new String[]{" §r§l■铂 ", " §e§l■金 ", " §7§l■银 ", " §c§l■铜 "}));
                break;
            }
            case PlayerHelper.ARES_EXOSKELETON_CONFIG_PAGE_NAME: {
                evt.setCancelled(true);
                PlayerHelper.handleAresExoskeletonConfigClick(ply, upperInventory, evt.getSlot());
                break;
            }
        }
    }
}
