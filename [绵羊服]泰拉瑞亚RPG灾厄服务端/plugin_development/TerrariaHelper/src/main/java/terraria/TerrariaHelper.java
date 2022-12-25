package terraria;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import terraria.event.listener.*;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;
import terraria.util.PlayerHelper;
import terraria.util.YmlHelper;
import terraria.worldgen.overworld.NoiseGeneratorTest;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class TerrariaHelper extends JavaPlugin {
    public static class Constants {
        public static final String WORLD_NAME_SURFACE = "world_surface", WORLD_NAME_CAVERN = "world_cavern", WORLD_NAME_UNDERWORLD = "world_underworld";
    }
    public static long worldSeed;
    public static TerrariaHelper instance;
    // YML configs
    public static final YmlHelper.YmlSection armorSetConfig = YmlHelper.getFile("plugins/Data/armorSet.yml");
    public static final YmlHelper.YmlSection blockConfig = YmlHelper.getFile("plugins/Data/blocks.yml");
    public static final YmlHelper.YmlSection buffConfig = YmlHelper.getFile("plugins/Data/buff.yml");
    public static final YmlHelper.YmlSection consumableConfig = YmlHelper.getFile("plugins/Data/consumeable.yml");
    public static final YmlHelper.YmlSection entityConfig = YmlHelper.getFile("plugins/Data/entities.yml");
    public static final YmlHelper.YmlSection itemConfig = YmlHelper.getFile("plugins/Data/items.yml");
    public static final YmlHelper.YmlSection prefixConfig = YmlHelper.getFile("plugins/Data/prefix.yml");
    public static final YmlHelper.YmlSection projectileConfig = YmlHelper.getFile("plugins/Data/projectiles.yml");
    public static final YmlHelper.YmlSection recipeConfig = YmlHelper.getFile("plugins/Data/recipes.yml");
    public static final YmlHelper.YmlSection settingConfig = YmlHelper.getFile("plugins/Data/setting.yml");
    public static final YmlHelper.YmlSection soundConfig = YmlHelper.getFile("plugins/Data/sounds.yml");
    public static final YmlHelper.YmlSection weaponConfig = YmlHelper.getFile("plugins/Data/weapons.yml");

    public TerrariaHelper() {
        super();
        instance = this;
        worldSeed = YmlHelper.getFile("plugins/Data/setting.yml").getLong("worldSeed", 114514);
    }

    private static void setupPlaceholders() {
        PlaceholderAPI.registerPlaceholderHook("terraria", new PlaceholderHook() {
            @Override
            public String onPlaceholderRequest(Player ply, String params) {
                switch (params) {
                    case "money": {
                        double amount = PlayerHelper.getMoney(ply);
                        String result = "";
                        if (amount < 100) {
                            result = "笑死，身无分文";
                        } else {
                            int[] moneyInfo = GenericHelper.coinConversion((int) (amount + 0.01), false);
                            if (moneyInfo[0] > 0) result += "&f" + moneyInfo[0] + "铂金币 ";
                            if (moneyInfo[1] > 0) result += "&e" + moneyInfo[1] + "金币 ";
                            if (moneyInfo[2] > 0) result += "&7" + moneyInfo[2] + "银币 ";
                            if (moneyInfo[3] > 0) result += "&c" + moneyInfo[3] + "铜币 ";
                        }
                        return result;
                    }
                    case "effects": {
                        StringBuilder result = new StringBuilder();
                        String separator = "~";
                        HashMap<String, Integer> effectMap = EntityHelper.getEffectMap(ply);
                        for (Map.Entry<String, Integer> effect : effectMap.entrySet()) {
                            if (result.length() > 0) result.append(separator);
                            String effectDisplayName = effect.getKey();
                            String effectLore = buffConfig.getString("effects." + effectDisplayName + ".tooltip", "我不道啊？！");
                            int effectDisplayTime = effect.getValue();
                            // tweak the display for certain special buffs
                            if (EntityHelper.getEffectLevelMax(effectDisplayName) > 1) {
                                int effectLevel = EntityHelper.getEffectLevel(effectDisplayName, effectDisplayTime);
                                effectDisplayTime -= (effectLevel - 1) * EntityHelper.getEffectLevelDuration(effectDisplayName);
                                // tweak name
                                effectDisplayName += effectLevel;
                            }
                            result.append(effectDisplayName).append(separator)
                                    .append(effectLore).append(separator)
                                    .append(effectDisplayTime);
                        }
                        long offset = ((Calendar.getInstance().getTimeInMillis() / 500)) % 50;
                        Set<String> allBuff = buffConfig.getConfigurationSection("effects").getKeys(false);
                        for (int i = 0; i < 0; i ++) {
                            for (String effectDisplayName : allBuff) {
                                if (offset > 0) {
                                    offset --;
                                    continue;
                                }
                                if (result.length() > 0) result.append(separator);
                                String effectLore = buffConfig.getString("effects." + effectDisplayName + ".tooltip", "我不道啊？！");
                                int effectDisplayTime = 160;
                                // tweak the display for certain special buffs
                                if (EntityHelper.getEffectLevelMax(effectDisplayName) > 1) {
                                    int effectLevel = EntityHelper.getEffectLevel(effectDisplayName, effectDisplayTime);
                                    effectDisplayTime -= (effectLevel - 1) * EntityHelper.getEffectLevelDuration(effectDisplayName);
                                    // tweak name
                                    effectDisplayName += effectLevel;
                                }
                                result.append(effectDisplayName).append(separator)
                                        .append(effectLore).append(separator)
                                        .append(effectDisplayTime);
                            }
                        }
                        return result.toString();
                    }
                }
                HashMap<String, Double> attrMap = EntityHelper.getAttrMap(ply);
                if (attrMap == null) attrMap = new HashMap<>(1);
                switch (params) {
                    case "defence":
                        return (int)Math.round(attrMap.getOrDefault("defence", 0d) * attrMap.getOrDefault("defenceMulti", 1d)) + "";
                    case "max_mana":
                        return (int)Math.round(attrMap.getOrDefault("maxMana", 20d)) + "";
                    case "mana_tier":
                        return (int)Math.round(attrMap.getOrDefault("manaTier", 1d)) + "";
                    case "health_tier":
                        return (int)Math.round(attrMap.getOrDefault("healthTier", 5d)) + "";
                    case "accessory_amount": {
                        return PlayerHelper.getAccessoryAmount(ply) + "";
                    }
                    default:
                        Bukkit.broadcastMessage("UNHANDLED PLACEHOLDER " + params);
                        return "嗒嘀嗒，我不道啊";
                }
            }
        });
    }
    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new ArrowHitEvent(), this);
        Bukkit.getPluginManager().registerEvents(new BlockBreakListener(), this);
        Bukkit.getPluginManager().registerEvents(new CraftingListener(), this);
        Bukkit.getPluginManager().registerEvents(new DamageListener(), this);
        Bukkit.getPluginManager().registerEvents(new DropItemSpawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new ItemUseAndAttributeListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerKeyToggleListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerWorldChangeListener(), this);
        Bukkit.getPluginManager().registerEvents(new RandomTitleListener(), this);
        Bukkit.getPluginManager().registerEvents(new ServerStopListener(), this);
        Bukkit.getPluginManager().registerEvents(new WorldRegisterListener(), this);
    }
    private void initThreads() {
        YmlHelper.threadSaveYml();
        PlayerHelper.threadGrapplingHook();
        PlayerHelper.threadArmorAccessory();
        PlayerHelper.threadThrustRegen();
        PlayerHelper.threadBackground();
        PlayerHelper.threadBGM();
        PlayerHelper.threadRegen();
        PlayerHelper.threadAttribute();
        // thread to save player inventories every 5 seconds
        Bukkit.getScheduler().scheduleSyncRepeatingTask(getInstance(),
                () -> {
                    for (Player ply : Bukkit.getOnlinePlayers()) {
                        PlayerHelper.saveInventories(ply);
                    }
                }, 100, 100);
    }

    @Override
    public void onEnable() {
        registerEvents();
        initThreads();
        setupPlaceholders();

        this.getCommand("findNoise").setExecutor(new NoiseGeneratorTest());

        getLogger().info("\n\n\n");
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        getLogger().info("泰拉瑞亚RPG插件部分已启动。");
        getLogger().info("世界种子: " + worldSeed);
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        getLogger().info("\n\n\n");
    }
    @Override
    public void onDisable() {
        getLogger().info("泰拉瑞亚RPG插件部分已停用。");
    }

    public static TerrariaHelper getInstance() {
        return instance;
    }
}
