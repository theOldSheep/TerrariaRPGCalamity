package terraria.util;

import lk.vexview.api.VexViewAPI;
import lk.vexview.event.ButtonClickEvent;
import lk.vexview.gui.VexGui;
import lk.vexview.gui.components.VexButton;
import lk.vexview.gui.components.VexComponents;
import lk.vexview.gui.components.VexText;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
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
import terraria.entity.npc.*;
import terraria.event.listener.NPCListener;
import terraria.gameplay.EventAndTime;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class NPCHelper {
    public static HashMap<String, NPCType> NPCTypeMap = new HashMap<>();
    public enum NPCType {
        ANGLER("渔夫", TerrariaNPCAngler.class),
        ARMS_DEALER("军火商", TerrariaNPCArmsDealer.class),
        BLOCK_SELLER("建材商人", TerrariaNPCBlockSeller.class),
        CLOTHIER("裁缝", TerrariaNPCClothier.class),
        DEMOLITIONIST("爆破专家", TerrariaNPCDemolitionist.class),
        GOBLIN_TINKERER("哥布林工匠", TerrariaNPCGoblinTinkerer.class),
        GUIDE("向导", TerrariaNPCGuide.class),
        NURSE("护士", TerrariaNPCNurse.class),
        // TODO
        BANDIT("强盗", TerrariaNPCBandit.class),
        ;
        public final String displayName;
        public final Class<? extends TerrariaNPC> NPCClass;
        NPCType (String displayName, Class<? extends TerrariaNPC> NPCClass) {
            this.displayName = displayName;
            this.NPCClass = NPCClass;
            NPCTypeMap.put(displayName, this);
        }
        @Override
        public String toString() {
            return displayName;
        }
    }
    public static HashMap<NPCHelper.NPCType, LivingEntity> NPCMap = new HashMap<>();
    public static Entity spawnNPC(String type) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return spawnNPC(NPCTypeMap.get(type));
    }
    public static Entity spawnNPC(NPCType type) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        org.bukkit.World surfaceWorld = Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE);
        TerrariaNPC nmsNPC;

        Class<? extends TerrariaNPC> NPCClass = type.NPCClass;
        nmsNPC = NPCClass.getConstructor(World.class)
                .newInstance( ((CraftWorld) surfaceWorld).getHandle() );

        // add to world
        ((CraftWorld) surfaceWorld).addEntity(nmsNPC, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // return the NPC spawned
        return nmsNPC.getBukkitEntity();
    }

    /*
     *
     *     BELOW: HELPER FUNCTIONS FOR NPC INTERACTION
     *
     */

    public static void recordInteractingNPC(Player ply, Entity NPC) {
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
    // below: functions related to direct interaction with the NPC
    public static int getHealingCost(Player ply) {
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
    public static boolean attemptSubmitQuestFish(Player ply) {
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
    public static void handleInteractNPC(Player ply, Entity clickedNPC) {
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
                int btnW = 30;
                // init texts
                {
                    double health = ply.getHealth();
                    double maxHealth = ply.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    if (health < maxHealth) {
                        int healthRatioInfo = 3 - (int) (health / maxHealth * 3);
                        texts.addAll(msgSection.getStringList("hurt." + healthRatioInfo));
                        // width for quotes
                        btnW += 10;
                        // btn display name
                        healBtnDisplay = new StringBuilder("治疗§7(需要");
                        long[] coinNeeded = GenericHelper.coinConversion(getHealingCost(ply), false);
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
                        String[] splitInfo = availableBiome.split(":");
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
                    else if (NPCType.equals("强盗")) {
                        StringBuilder refundBtnDisplay;
                        int btnW = 26;
                        // init texts
                        {
                            // btn display name
                            refundBtnDisplay = new StringBuilder("返现");
                            long refundAmount = (long) NPCListener.getReforgeRefundAmount(ply);
                            if (refundAmount > 0) {
                                btnW += 5;
                                refundBtnDisplay.append("§7(");
                                long[] coinNeeded = GenericHelper.coinConversion(refundAmount, false);
                                String[] additionalStr = {"§r■铂", "§e■金", "§7■银", "§c■铜"};
                                for (int index = 0; index < 4; index++) {
                                    if (coinNeeded[index] > 0) {
                                        refundBtnDisplay.append(additionalStr[index].replace(
                                                "■", coinNeeded[index] + ""));
                                        btnW += 26;
                                    }
                                }
                                refundBtnDisplay.append("§7)");
                            }
                        }
                        comps.add(new VexButton("REFUND", refundBtnDisplay.toString(), bg, bg, 150, h / 3 - 30, btnW, 17));
                    }
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
                if (String.valueOf(character).matches("[\\u4e00-\\u9fa5]")) {
                    totalW += textSize * 20;
                }
                else {
                    totalW += textSize * 10;
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
    // below: GUI button features
    public static int fillShopGui(Player ply, Inventory shopInv, int index, ConfigurationSection shopSection) {
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
    public static void openShopGUI(Player ply, Villager NPC) {
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
    public static void openReforgeGui(Player ply, Villager NPC) {
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
    // below: secondary GUI (shop, reforge) features
    public static void updateReforgeInventory(Inventory reforgeInv) {
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
}
