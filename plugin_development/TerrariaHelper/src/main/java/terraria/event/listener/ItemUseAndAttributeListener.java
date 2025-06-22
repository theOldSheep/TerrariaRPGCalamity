package terraria.event.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import terraria.TerrariaHelper;
import terraria.util.*;

import java.util.Set;

public class ItemUseAndAttributeListener implements Listener {
    // events that could affect attribute and item use logic
    @EventHandler(priority = EventPriority.HIGHEST)
    public static void onToolChange(PlayerItemHeldEvent e) {
        Player ply = e.getPlayer();
        if (ply.getScoreboardTags().contains("temp_useCD")) e.setCancelled(true);
        if (e.isCancelled()) return;
        ply.addScoreboardTag("toolChanged");
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public static void onInventoryClick(InventoryClickEvent e) {
        Player ply = (Player) e.getWhoClicked();
        // deny inventory click event if player is not properly playing
        if (!PlayerHelper.isProperlyPlaying(ply))
            e.setCancelled(true);
        ItemStack clickedItem = e.getCurrentItem();
        // don't let the player take off auto-generated boots according to the leggings
        if (e.getSlotType() == InventoryType.SlotType.ARMOR) {
            switch (clickedItem.getType()) {
                case LEATHER_BOOTS:
                case CHAINMAIL_BOOTS:
                case IRON_BOOTS:
                case GOLD_BOOTS:
                case DIAMOND_BOOTS:
                    e.setCancelled(true);
            }
        }
        if (ply.getScoreboardTags().contains("temp_useCD"))
            e.setCancelled(true);
        // if the event has been cancelled by here, proceed no further.
        if (e.isCancelled()) return;
        ply.addScoreboardTag("toolChanged");
        // make sure the armor set message does not linger on the armor clicked
        if (clickedItem != null && ItemHelper.getItemCombatType(clickedItem).equals("装备")) {
            clickedItem.setItemMeta(ItemHelper.getRawItem(ItemHelper.splitItemName(clickedItem)[1]).getItemMeta());
        }
        // open crates
        if (e.getClick() == ClickType.RIGHT) {
            ItemStack cursor = e.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                if (ItemUseHelper.playerOpenCrate(ply, e.getCurrentItem()))
                    e.setCancelled(true);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public static void onDiscardItem(PlayerDropItemEvent e) {
        Player ply = e.getPlayer();
        if (!PlayerHelper.isProperlyPlaying(ply)) {
            e.setCancelled(true);
            return;
        }
        if (ply.getScoreboardTags().contains("temp_useCD")) e.setCancelled(true);
        if (e.isCancelled()) return;
        ply.addScoreboardTag("toolChanged");
        // make sure the armor set message does not linger on the armor dropped
        Item droppedItem = e.getItemDrop();
        if (droppedItem != null) {
            ItemStack droppedItemStack = droppedItem.getItemStack();
            if (droppedItemStack != null && ItemHelper.getItemCombatType(droppedItemStack).equals("装备")) {
                droppedItem.setItemStack(ItemHelper.getItemFromDescription(
                        ItemHelper.splitItemName(droppedItemStack)[1], false));
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public static void onHandItemSwap(PlayerSwapHandItemsEvent e) {
        Player ply = e.getPlayer();
        if (!PlayerHelper.isProperlyPlaying(ply)) {
            e.setCancelled(true);
            return;
        }
        if (ply.getScoreboardTags().contains("temp_useCD")) e.setCancelled(true);
        if (e.isCancelled()) return;
        ply.addScoreboardTag("toolChanged");
    }
    // swing item helper
    private static void toolSwing(Player ply, boolean isRightClick) {
        Set<String> scoreboardTags = ply.getScoreboardTags();
        // prevent glitch due to multiple listeners triggered at once(most noticeably cancelling auto swing)
        String CDScoreboardTag = "temp_checkedToolSwing";
        if (scoreboardTags.contains(CDScoreboardTag)) return;
        ply.addScoreboardTag(CDScoreboardTag);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                () -> ply.removeScoreboardTag(CDScoreboardTag), 2);
        // if the player is in swing cool down
        // if the player is using a loading weapon, fire the loaded ammo
        // otherwise, do nothing
        // cancel auto swing if applicable
        if (scoreboardTags.contains("temp_useCD")) {
            if (scoreboardTags.contains("temp_isLoadingWeapon")) {
                ply.removeScoreboardTag("temp_isLoadingWeapon");
                // fire loaded shots
                ItemUseHelper.playerUseItem(ply);
            }
            ply.removeScoreboardTag("temp_autoSwing");
            return;
        }
        // setup click type and let player use the item
        if (isRightClick) {
            ply.addScoreboardTag("isSecondaryAttack");
        } else {
            ply.removeScoreboardTag("isSecondaryAttack");
        }
        MetadataHelper.setMetadata(ply, MetadataHelper.MetadataName.PLAYER_ITEM_SWING_AMOUNT, 0);
        ItemUseHelper.playerUseItem(ply);
    }
    // swing item listener
    @EventHandler(priority = EventPriority.HIGHEST)
    public static void onLeftClickEntity(EntityDamageByEntityEvent e) {
        Entity ply = e.getDamager();
        if (ply instanceof Player)
            toolSwing((Player) ply, false);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public static void onRightClickEntity(PlayerInteractAtEntityEvent e) {
        if (e.getRightClicked().getType() == EntityType.VILLAGER) return;
        Player ply = e.getPlayer();
        switch (ply.getInventory().getItemInMainHand().getType()) {
            case BOW:
            case ENDER_PEARL:
            case EYE_OF_ENDER:
                e.setCancelled(true);
        }
        toolSwing(ply, true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public static void onToolSwing(PlayerInteractEvent e) {
        Player ply = e.getPlayer();
        // if the player is reviving etc., do nothing
        if (!PlayerHelper.isProperlyPlaying(ply)) return;
        boolean isRightClick = false;
        switch (e.getAction()) {
            case LEFT_CLICK_AIR:
            case LEFT_CLICK_BLOCK:
                break;
            case RIGHT_CLICK_AIR:
                isRightClick = true;
                break;
            case RIGHT_CLICK_BLOCK:
                isRightClick = true;
                if (!e.isCancelled())
                    GameplayHelper.playerRightClickBlock(ply, e.getClickedBlock());
                break;
            default:
                return;
        }
        if (e.useInteractedBlock() == Event.Result.DENY && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // crafting station
            return;
        }
        toolSwing(ply, isRightClick);
        // deny special items consumption
        if (isRightClick) {
            switch (ply.getInventory().getItemInMainHand().getType()) {
                case BOW:
                case ENDER_PEARL:
                case ARMOR_STAND:
                case EYE_OF_ENDER:
                    e.setCancelled(true);
            }
        }
    }
}
