package terraria.event.listener;

import lk.vexview.api.VexViewAPI;
import lk.vexview.event.ButtonClickEvent;
import lk.vexview.event.VexSlotClickEvent;
import lk.vexview.gui.OpenedVexGui;
import lk.vexview.gui.VexGui;
import lk.vexview.gui.components.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import terraria.TerrariaHelper;
import terraria.util.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CraftingListener implements Listener {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final String BACKGROUND_IMAGE = "[local]GuiBG.png";
    private static final YmlHelper.YmlSection recipeConfig = YmlHelper.getFile(
            TerrariaHelper.Constants.DATA_FOLDER_DIR + "recipes.yml");
    private static LinkedHashMap<String, Integer> getRecipeIngredientMap(String station, String vexSlotIndex) {
        String recipeConfigName = ItemHelper.CRAFTING_GUIS_RECIPE_INDEX_MAP.get(station + "_" + vexSlotIndex);
        return getRecipeIngredientMap(recipeConfig.getConfigurationSection(recipeConfigName));
    }
    private static LinkedHashMap<String, Integer> getRecipeIngredientMap(ConfigurationSection recipeConfigSection) {
        LinkedHashMap<String, Integer> ingredients = new LinkedHashMap<>();
        for (String item : recipeConfigSection.getStringList("requireItem")) {
            if (item.contains(":")) {
                String[] itemInfo = item.split(":");
                ingredients.put(itemInfo[0], Integer.parseInt(itemInfo[1]));
            } else {
                ingredients.put(item, 1);
            }
        }
        return ingredients;
    }
    private static HashMap[] getPlayerIngredientMap(Player player, Set<String> interestedItems) {
        // check player backpack
        HashMap<String, Integer> playerItemMap = new HashMap<>();
        HashMap<String, List<ItemStack>> playerItems = new HashMap<>();
        Inventory plyBackpack = player.getInventory();
        for (int i = 0; i < 36; i ++) {
            ItemStack currItem = plyBackpack.getItem(i);
            if (currItem == null) continue;
            String[] itemInfo = ItemHelper.splitItemName(currItem);
            String itemType = itemInfo[1];
            if (itemType.equals("")) continue;
            if (interestedItems == null || interestedItems.contains(itemType)) {
                playerItemMap.putIfAbsent(itemType, 0);
                playerItems.putIfAbsent(itemType, new ArrayList<>());
                int amountCurrent = playerItemMap.get(itemType) + currItem.getAmount();
                playerItemMap.put(itemType, amountCurrent);
                playerItems.get(itemType).add(currItem);
            }
        }
        // check void bag
        boolean hasVoidBag = PlayerHelper.hasVoidBag(player);
        Inventory plyVoidBag = PlayerHelper.getInventory(player, "voidBag");
        if (hasVoidBag) {
            for (int i = 0; i < PlayerHelper.PLAYER_EXTRA_INVENTORY_SIZE; i ++) {
                ItemStack currItem = plyVoidBag.getItem(i);
                if (currItem == null) continue;
                String[] itemInfo = ItemHelper.splitItemName(currItem);
                String itemType = itemInfo[1];
                if (itemType.equals("")) continue;
                if (interestedItems == null || interestedItems.contains(itemType)) {
                    playerItemMap.putIfAbsent(itemType, 0);
                    playerItems.putIfAbsent(itemType, new ArrayList<>());
                    int amountCurrent = playerItemMap.get(itemType) + currItem.getAmount();
                    playerItemMap.put(itemType, amountCurrent);
                    playerItems.get(itemType).add(currItem);
                }
            }
        }
        return new HashMap[]{playerItemMap, playerItems};
    }
    private static int getMaxCraftAmount(HashMap<String, Integer> recipeIngredients, HashMap<String, Integer> availableItems) {
        // get maximum amount that can be crafted
        int amountToCraft = 6400;
        for (String itemType : recipeIngredients.keySet()) {
            int amountPlyHas = availableItems.getOrDefault(itemType, 0);
            int amountEachCrafting = recipeIngredients.getOrDefault(itemType, 0);
            int craftAmount = amountPlyHas / amountEachCrafting;
            if (amountToCraft > craftAmount) amountToCraft = craftAmount;
        }
        return amountToCraft;
    }
    private void handlePlayerCraft(Player player, int recipeIndex, String station, boolean shouldCraftAll) {
        player.sendMessage("§a正在处理合成请求中....");
        String recipeConfigName = ItemHelper.CRAFTING_GUIS_RECIPE_INDEX_MAP.get(station + "_" + recipeIndex);
        // get config section for later use
        ConfigurationSection recipeConfigSection = recipeConfig.getConfigurationSection(recipeConfigName);
        // setup ingredient required
        HashMap<String, Integer> ingredients = getRecipeIngredientMap(recipeConfigSection);
        if (ingredients.size() < 1) return;
        // get the items the player has
        HashMap[] ingredientMapArray = getPlayerIngredientMap(player, ingredients.keySet());
        HashMap<String, Integer> playerItemMap = ingredientMapArray[0];
        HashMap<String, List<ItemStack>> playerItems = ingredientMapArray[1];
        // get maximum amount that can be crafted
        int amountToCraft = getMaxCraftAmount(ingredients, playerItemMap);
        // check if the player could craft any
        if (amountToCraft <= 0) {
            player.sendMessage("§c材料不足....");
            return;
        }
        String resultItemType = recipeConfigSection.getString("resultItem");
        ItemStack resultItem = ItemHelper.getItemFromDescription(resultItemType, true);
        // upper cap the total amount of result item to 4800 if the player would like to craft as many as possible
        if (shouldCraftAll) amountToCraft = Math.min(4800 / resultItem.getAmount(), amountToCraft);
            // set max amount crafted if the player would like to craft only one
        else amountToCraft = 1;
        // removes all ingredients required
        for (String itemType : ingredients.keySet()) {
            int amountToTakeLeft = ingredients.get(itemType) * amountToCraft;
            for (ItemStack currItem : playerItems.get(itemType)) {
                int currItemAmount = currItem.getAmount();
                if (currItemAmount <= amountToTakeLeft) {
                    // if the item is depleted
                    currItem.setAmount(0);
                } else {
                    // if the item contains more than required
                    currItem.setAmount(currItemAmount - amountToTakeLeft);
                }
                // if the requirement has been met, break the loop and continue to the next item
                amountToTakeLeft -= currItemAmount;
                if (amountToTakeLeft <= 0) break;
            }
        }
        // gives the result item to the player
        player.sendMessage("§a合成成功！~");
        // if the result is sword etc. that could be reforged, give items individually to the player
        if (ItemHelper.canReforge(resultItem) ) {
            for (int i = 0; i < amountToCraft; i ++) {
                resultItem = ItemHelper.randomPrefix(resultItem);
                PlayerHelper.giveItem(player, resultItem, true);
            }
        }
        // otherwise, give all items at once to the player to prevent lag and a loud pickup sound effect buildup
        else {
            resultItem.setAmount(resultItem.getAmount() * amountToCraft);
            PlayerHelper.giveItem(player, resultItem, true);
        }
    }
    // when the player clicks the crafting button in the gui
    @EventHandler(priority = EventPriority.LOW)
    public void onVexButtonClick(ButtonClickEvent e) {
        Player player = e.getPlayer();
        String station = MetadataHelper.getMetadata(player, MetadataHelper.MetadataName.PLAYER_CRAFTING_STATION).asString();
        if (station.equals("CLOSED")) return;
        int recipeIndex = MetadataHelper.getMetadata(player, MetadataHelper.MetadataName.PLAYER_CRAFTING_RECIPE_INDEX).asInt();
        switch (e.getButton().getName()) {
            case "点我合成":
                handlePlayerCraft(player, recipeIndex, station, false);
                break;
            case "合成全部":
                handlePlayerCraft(player, recipeIndex, station, true);
                break;
            case "选择配方":
                player.sendMessage("§c请选择左侧的物品查看配方后合成");
        }
    }
    // update selected recipe after clicking on a result item preview
    @EventHandler(priority = EventPriority.LOW)
    public void onVexSlotClick(VexSlotClickEvent e) {
        Player player = e.getPlayer();
        if (player.getScoreboardTags().contains("tempCraftRecipeSelect")) return;
        String station = MetadataHelper.getMetadata(player, MetadataHelper.MetadataName.PLAYER_CRAFTING_STATION).asString();
        int recipeIndex = e.getID();
        // get next index to display(prevent index collision)
        int index = ItemHelper.CRAFTING_GUI_LENGTH_MAP.get(station);
        // if the slot clicked > index, that is, the player clicked on a descriptive itemstack on the right
        if (recipeIndex >= index) return;
        // test if the slot clicked is for display purpose only
        VexGui gui = VexViewAPI.getPlayerCurrentGui(player).getVexGui();
        VexSlot slot = gui.getSlotById(recipeIndex);
        if (slot == null || slot.getX() > 10) return;
        // get config section for later use
        String recipeConfigName = ItemHelper.CRAFTING_GUIS_RECIPE_INDEX_MAP.get(station + "_" + recipeIndex);
        ConfigurationSection recipeConfigSection = recipeConfig.getConfigurationSection(recipeConfigName);
        // setup ingredient required
        HashMap<String, Integer> ingredients = getRecipeIngredientMap(recipeConfigSection);
        // setup all ingredient slots
        List<VexComponents> newComps = new ArrayList<>();
        int slotSize = 12;
        double fontSize = 1;
        int dx = (int) (((gui.getWidth() * 0.9) - 40) / (ingredients.size() + 1));
        int xOffset = gui.getWidth() / 10 + 30;
        int yOffset = gui.getHeight() / 3;
        for (String requiredItem : ingredients.keySet()) {
            xOffset += dx;
            ItemStack item = ItemHelper.getItemFromDescription(requiredItem, false);
            VexSlot newSlot = new VexSlot(++index, xOffset, yOffset, item);
            newComps.add(newSlot);
            // if required amount > 1, add a text to specify the amount of current ingredient needed
            // this is due to the improper display of items with stack size > 127
            int requiredAmount = ingredients.get(requiredItem);
            if (requiredAmount > 1) {
                List<String> textDisplay = new ArrayList<>();
                textDisplay.add("" + requiredAmount);
                VexText amountText = new VexText(xOffset + slotSize, yOffset + slotSize, textDisplay, fontSize);
                newComps.add(amountText);
            }
        }
        // setup result slot
        {
            String craftingResult = recipeConfigSection.getString("resultItem");
            xOffset = (int) ((gui.getWidth() * 0.55) + 10);
            yOffset = gui.getHeight() / 5;
            ItemStack parsedItem = ItemHelper.getItemFromDescription(craftingResult, false);
            int resultAmount = parsedItem.getAmount();
            // prevent duplicated number display; use a new text field to display amount larger than 127
            parsedItem.setAmount(1);
            newComps.add(new VexSlot(++index, xOffset, yOffset, parsedItem) );
            if (resultAmount > 1) {
                List<String> textDisplay = new ArrayList<>();
                textDisplay.add("" + resultAmount);
                VexText amountText = new VexText(xOffset + slotSize, yOffset + slotSize, textDisplay, fontSize);
                newComps.add(amountText);
            }
        }
        // buttons
        VexButton btn1 = gui.getButtonById("CRAFT");
        VexButton btn2 = gui.getButtonById("CRAFT_ALL");
        btn1.setName("点我合成");
        btn2.setName("合成全部");
        // update the gui displayed to player
        OpenedVexGui currGui = VexViewAPI.getPlayerCurrentGui(player);
        List<DynamicComponent> compsToRemove = new ArrayList<>();
        for (VexComponents cmp : currGui.getVexGui().getComponents()) {
            if (cmp instanceof VexScrollingList) continue;
            if (cmp instanceof DynamicComponent)
                compsToRemove.add((DynamicComponent) cmp);
        }
        for (DynamicComponent cmp : compsToRemove)
            currGui.removeDynamicComponent(cmp);
        for (VexComponents cmp : newComps)
            currGui.addDynamicComponent((DynamicComponent) cmp);
        currGui.addDynamicComponent(btn1);
        currGui.addDynamicComponent(btn2);
        // setup cool down to prevent accidental selection of new recipes, glitch / spamming
        MetadataHelper.setMetadata(player, MetadataHelper.MetadataName.PLAYER_CRAFTING_RECIPE_INDEX, recipeIndex);
        EntityHelper.handleEntityTemporaryScoreboardTag(player, "tempCraftRecipeSelect", 5);
    }
    enum CraftingFilter {
        SHOW_ALL, SHOW_MATERIAL_USAGE, SHOW_CRAFTABLE;
    }
    public boolean handleCrafting(Player ply, Block block) {
        if (!PlayerHelper.isProperlyPlaying(ply)) return false;
        if (ply.getScoreboardTags().contains("useCD")) return false;
        if (ply.getScoreboardTags().contains("tempCraftingCD")) return false;
        // setup work station info
        String station;
        CraftingFilter filterType;
        ItemStack plyTool = ply.getInventory().getItemInMainHand();
        if (plyTool == null) plyTool = new ItemStack(Material.AIR);
        if (ply.isSneaking()) {
            filterType = plyTool.getType() == Material.AIR ? CraftingFilter.SHOW_ALL : CraftingFilter.SHOW_MATERIAL_USAGE;
        }
        else
            filterType = CraftingFilter.SHOW_CRAFTABLE;
        int level = 1;
        if (block == null) {
            station = "AIR";
            filterType = CraftingFilter.SHOW_ALL;
        } else {
            station = block.getType().toString();
            switch (block.getType()) {
                case AIR:
                    // this is due to some glitch after mining some block, ignore it.
                    // AIR crafting station is opened by clicking the button in backpack, handled above.
                    return false;
                case FURNACE:
                case BURNING_FURNACE: {
                    station = "FURNACE";
                    break;
                }
                // most crafting stations ideally uses stained-glass blocks of different data (color), for block texture purposes.
                // do not use solid block for this purpose; block face culling will make it look really wierd.
                case STAINED_GLASS: {
                    switch ( block.getData() ) {
                        // 工匠合成台
                        case 1:
                            station = "TINKER_WORKBENCH";
                            break;
                        // 秘银砧
                        case 2:
                            station = "ANVIL";
                            level = 2;
                            break;
                        // 星宇砧
                        case 3:
                            station = "ANVIL";
                            level = 3;
                            break;
                        // 地狱熔炉
                        case 4:
                            station = "FURNACE";
                            level = 2;
                            break;
                        // 精金熔炉
                        case 5:
                            station = "FURNACE";
                            level = 3;
                            break;
                        // 远古操纵机
                        case 6:
                            station = "远古操纵机";
                            break;
                        // 嘉登熔炉
                        case 7:
                            station = "嘉登熔炉";
                            break;
                    }
                    break;
                }
            }
        }
        String guiKey = station + "_" + level;
        VexGui originalGui = ItemHelper.CRAFTING_GUI_MAP.get(guiKey);
        if (originalGui == null) return false;
        // setup gui to display
        int windowWidth = VexViewAPI.getPlayerClientWindowWidth(ply);
        int windowHeight = VexViewAPI.getPlayerClientWindowHeight(ply);
        VexGui guiToOpen = new VexGui(BACKGROUND_IMAGE, windowWidth / 6,  windowHeight / 5,
                (int) (windowWidth * 0.666666), (int) (windowHeight * 0.8));
        // get all recipes for the crafting station
        List<ScrollingListComponent> itemSlots;
        CompletableFuture<List<ScrollingListComponent>> futureItemSlots;
        switch (filterType) {
            case SHOW_ALL:
                itemSlots = originalGui.getList().getComponents();
                futureItemSlots = CompletableFuture.completedFuture(itemSlots); // No filtering needed, return the original slots
                break;
            case SHOW_CRAFTABLE:
            default:
                ply.sendMessage("§a提示：潜行右键合成站点时会显示该物品参与的合成配方（空手则展示所有配方）");
            case SHOW_MATERIAL_USAGE:
                // Save relevant information before proceeding
                final String toolType = ItemHelper.splitItemName(plyTool)[1];
                final String finalStationLevel = station + "_" + level;
                final CraftingFilter finalFilterType = filterType;
                final HashMap<String, Integer> availableItems = getPlayerIngredientMap(ply, null)[0];
                final List<ScrollingListComponent> itemSlotsAll = originalGui.getList().getComponents();
                // We need to perform filtering, so start the async process
                futureItemSlots = CompletableFuture.supplyAsync(() -> {
                    ArrayList<ScrollingListComponent> filteredSlots = new ArrayList<>();

                    // Perform the filtering in the background thread
                    for (ScrollingListComponent cmp : itemSlotsAll) {
                        if (!(cmp instanceof VexSlot)) continue;

                        HashMap<String, Integer> recipeIngredients =
                                getRecipeIngredientMap(finalStationLevel, ((VexSlot) cmp).getID() + "");

                        // Apply the appropriate filter condition
                        boolean matchesFilter;
                        switch (finalFilterType) {
                            case SHOW_MATERIAL_USAGE:
                                matchesFilter = recipeIngredients.containsKey(toolType);
                                break;
                            case SHOW_CRAFTABLE:
                            default:
                                matchesFilter = getMaxCraftAmount(recipeIngredients, availableItems) > 0;
                        }

                        if (matchesFilter) filteredSlots.add(cmp);
                    }

                    return filteredSlots; // Return the filtered slots
                }, executor);
        }
        // Wait for the future to complete, but with a timeout to avoid blocking the main thread
        try {
            itemSlots = futureItemSlots.get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Bukkit.getLogger().severe("Error while filtering recipes: " + e.getMessage());
            itemSlots = new ArrayList<>(); // Empty list to avoid NPE
        }

        // scrolling list
        VexScrollingList scrList = new VexScrollingList(guiToOpen.getWidth() / 10, 17, 30,
                guiToOpen.getHeight() - 34, itemSlots.size() * 20);
        int yOffset = 0;
        for (ScrollingListComponent comp : itemSlots) {
            VexSlot slotOld = (VexSlot) comp;
            VexSlot slotToAdd = new VexSlot(slotOld.getID(), 5, yOffset, slotOld.getItem());
            yOffset += 20;
            scrList.addComponent(slotToAdd);
        }
        guiToOpen.addComponent(scrList);
        // buttons
        guiToOpen.addComponent(new VexButton("CRAFT", "选择配方", BACKGROUND_IMAGE, BACKGROUND_IMAGE,
                guiToOpen.getWidth() - 120, guiToOpen.getHeight() - 50, 35, 17 + level));
        guiToOpen.addComponent(new VexButton("CRAFT_ALL", "选择配方", BACKGROUND_IMAGE, BACKGROUND_IMAGE,
                guiToOpen.getWidth() - 60, guiToOpen.getHeight() - 50, 35, 17 + level));
        // open gui
        MetadataHelper.setMetadata(ply, MetadataHelper.MetadataName.PLAYER_CRAFTING_RECIPE_INDEX, -1);
        MetadataHelper.setMetadata(ply, MetadataHelper.MetadataName.PLAYER_CRAFTING_STATION, guiKey);
        VexViewAPI.openGui(ply, guiToOpen);
        // setup cool down to prevent accidentally opening several guis and causing error (or spamming the server)
        EntityHelper.handleEntityTemporaryScoreboardTag(ply, "tempCraftingCD", 10);
        return true;
    }
    // opens a new crafting gui to the player when a work station is interacted
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent e) {
        // do nothing if the player is not right-clicking the block, or the event is canceled
        if (e.isCancelled() || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        // if no block is being clicked, just in case
        if (block == null) return;
        // deny further player interaction if a GUI is successfully opened
        boolean guiOpened = handleCrafting(e.getPlayer(), block);
        if (guiOpened) {
            e.setCancelled(true);
            e.setUseInteractedBlock(Event.Result.DENY);
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onCraftingGridClick(InventoryClickEvent e) {
        if (e.getSlotType() == InventoryType.SlotType.RESULT)
            handleCrafting((Player) e.getWhoClicked(), null);
    }
    // prevents the player from opening GUI of some special work stations
    @EventHandler(priority = EventPriority.LOW)
    public void onGuiOpen(InventoryOpenEvent e) {
        Inventory eventInv = e.getInventory();
        if (eventInv != null)
            switch (eventInv.getType()) {
                case FURNACE:
                case WORKBENCH:
                case ANVIL:
                case ENCHANTING:
                case BREWING:
                    e.setCancelled(true);
            }
    }
}
