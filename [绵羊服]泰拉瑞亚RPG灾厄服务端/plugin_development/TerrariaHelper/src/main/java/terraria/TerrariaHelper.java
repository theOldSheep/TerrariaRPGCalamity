package terraria;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import terraria.dragoncorehelper.RandomTitle;
import terraria.listener.*;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;
import terraria.util.PlayerHelper;
import terraria.util.YmlHelper;
import terraria.worldgen.overworld.NoiseGeneratorTest;

import java.util.HashMap;


public class TerrariaHelper extends JavaPlugin {
    public static class Constants {
        public static final String WORLD_NAME_SURFACE = "world_surface", WORLD_NAME_CAVERN = "world_cavern", WORLD_NAME_UNDERWORLD = "world_underworld";
    }
    public static long worldSeed;
    public static TerrariaHelper instance;

    public TerrariaHelper() {
        super();
        instance = this;
        worldSeed = YmlHelper.getFile("plugins/Data/setting.yml").getLong("worldSeed", 114514);
    }

    private static void setupPlaceholders() {
        PlaceholderAPI.registerPlaceholderHook("terraria", new PlaceholderHook() {
            @Override
            public String onPlaceholderRequest(Player ply, String params) {
                if (params.equals("money")) {
                    double amount = PlayerHelper.getMoney(ply);
                    String result = "";
                    if (amount < 100) {
                        result = "笑死，身无分文";
                    } else {
                        int[] moneyInfo = GenericHelper.coinConversion((int) (amount + 0.01));
                        if (moneyInfo[0] > 0) result += "&f" + moneyInfo[0] + "铂金币 ";
                        if (moneyInfo[1] > 0) result += "&e" + moneyInfo[1] + "金币 ";
                        if (moneyInfo[2] > 0) result += "&7" + moneyInfo[2] + "银币 ";
                        if (moneyInfo[3] > 0) result += "&c" + moneyInfo[3] + "铜币 ";
                    }
                    return result;
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
                        return YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml").getString("stats.maxAccessories");
                    }
                    default:
                        Bukkit.broadcastMessage("UNHANDLED PLACEHOLDER " + params);
                        return "嗒嘀嗒，我不道啊";
                }
            }
        });
    }
    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new playerKeyToggleListener(), this);
        Bukkit.getPluginManager().registerEvents(new RandomTitle(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new WorldRegisterListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerWorldChangeListener(), this);
        Bukkit.getPluginManager().registerEvents(new DamageListener(), this);
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
    }

    @Override
    public void onEnable() {
        registerEvents();
        initThreads();
        setupPlaceholders();

        this.getCommand("findNoise").setExecutor(new NoiseGeneratorTest());

        getLogger().info("泰拉瑞亚RPG插件部分已启动。");
        getLogger().info("世界种子: " + worldSeed);
    }
    @Override
    public void onDisable() {
        getLogger().info("泰拉瑞亚RPG插件部分已停用。");
    }

    public static TerrariaHelper getInstance() {
        return instance;
    }
}
