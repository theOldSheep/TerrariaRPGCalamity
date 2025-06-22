package terraria;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import terraria.entity.CustomEntities;
import terraria.event.listener.*;
import terraria.gameplay.*;
import terraria.util.*;
import terraria.worldgen.overworld.NoiseGeneratorTest;
import terraria.worldgen.overworld.OverworldChunkGenerator;
import terraria.worldgen.overworld.cavern.CavernChunkGenerator;
import terraria.worldgen.underworld.UnderworldChunkGenerator;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class TerrariaHelper extends JavaPlugin {
    public static Logger LOGGER = Bukkit.getLogger();

    public static class Constants {
        public static final int WORM_BOSS_CHUNK_LOAD_SEGMENT_INTERVAL = 5;
        public static final String
                DATA_FOLDER_DIR = "plugins" + File.separator + "Terraria" + File.separator + "Data" + File.separator + "",
                DATA_PLAYER_FOLDER_DIR = "plugins" + File.separator + "Terraria" + File.separator + "PlayerData" + File.separator + "";
        public static final String
                WORLD_NAME_SURFACE = "world_surface",
                WORLD_NAME_CAVERN = "world_cavern",
                WORLD_NAME_UNDERWORLD = "world_underworld",
                GUI_BACKGROUND = "[local]GuiBG.png",
                GUI_BACKGROUND_NPC = "[local]GuiNPCBG.png";
    }
    public static long WORLD_SEED;
    public static TerrariaHelper instance;
    // YML configs
    public static YmlHelper.YmlSection animalConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "animals.yml");
    public static YmlHelper.YmlSection armorSetConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "armorSet.yml");
    public static YmlHelper.YmlSection blockConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "blocks.yml");
    public static YmlHelper.YmlSection buffConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "buff.yml");
    public static YmlHelper.YmlSection crateConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "crates.yml");
    public static YmlHelper.YmlSection entityConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "entities.yml");
    public static YmlHelper.YmlSection fishingConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "fishing.yml");
    public static YmlHelper.YmlSection hookConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "hooks.yml");
    public static YmlHelper.YmlSection itemConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "items.yml");
    public static YmlHelper.YmlSection menusConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "menus.yml");
    public static YmlHelper.YmlSection messagesConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "messages.yml");
    public static YmlHelper.YmlSection mobSpawningConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "mobSpawning.yml");
    public static YmlHelper.YmlSection mountConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "mounts.yml");
    public static YmlHelper.YmlSection NPCConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "NPC.yml");
    public static YmlHelper.YmlSection optimizationConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "optimization.yml");
    public static YmlHelper.YmlSection potionItemConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "potionItem.yml");
    public static YmlHelper.YmlSection prefixConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "prefix.yml");
    public static YmlHelper.YmlSection projectileConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "projectiles.yml");
    public static YmlHelper.YmlSection recipeConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "recipes.yml");
    public static YmlHelper.YmlSection settingConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "setting.yml");
    public static YmlHelper.YmlSection soundConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "sounds.yml");
    public static YmlHelper.YmlSection weaponConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "weapons.yml");
    public static YmlHelper.YmlSection wingConfig = YmlHelper.getFile(Constants.DATA_FOLDER_DIR + "wings.yml");

    public TerrariaHelper() {
        super();
        // manipulate around the seed to generate a long number
        String seedRaw = optimizationConfig.getString("worldGen.params.seed", "SEED");
        if (seedRaw.length() <= 1) {
            seedRaw += "SALT";
        }
        WORLD_SEED = seedRaw.substring(0, seedRaw.length() / 2).hashCode();
        WORLD_SEED <<= 32;
        WORLD_SEED += seedRaw.substring(seedRaw.length() / 2).hashCode();

        instance = this;
        LOGGER = getLogger();
    }

    private static void setupPlaceholders() {
        PlaceholderAPI.registerPlaceholderHook("terraria", new PlaceholderHook() {
            @Override
            public String onPlaceholderRequest(Player ply, String params) {
                switch (params) {
                    case "energy_shield": {
                        return (int) PlayerHelper.getEnergyShielding(ply) + "";
                    }
                    case "stealth": {
                        double maxStealth = PlayerHelper.getMaxStealth(ply);
                        if (maxStealth < 1) return "-1";
                        return (int) (MetadataHelper.getMetadata(ply, MetadataHelper.MetadataName.PLAYER_STEALTH).asDouble() / maxStealth * 100) + "";
                    }
                    case "oxygen": {
                        return ((double) MetadataHelper.getMetadata(ply, MetadataHelper.MetadataName.PLAYER_AIR).asInt() / PlayerHelper.PLAYER_MAX_OXYGEN) + "";
                    }
                    case "pov_locked": {
                        return ply.getScoreboardTags().contains("temp_lockPOV") ? "1" : "0";
                    }
                    case "money": {
                        double amount = PlayerHelper.getMoney(ply);
                        if (amount < 100) {
                            return "笑死，身无分文";
                        } else {
                            return GenericHelper.getCoinDisplay(
                                    GenericHelper.coinConversion((long) (amount + 0.01), false) );
                        }
                    }
                    case "effects": {
                        StringBuilder result = new StringBuilder();
                        String separator = "~";
                        // switchable accessory
                        MetadataValue switchableName = MetadataHelper.getMetadata(ply, MetadataHelper.MetadataName.ACCESSORY_SWITCHABLE_DISPLAY);
                        if (switchableName != null) {
                            result.append(switchableName.asString()).append(separator)
                                    .append(" ").append(separator)
                                    .append(210);
                        }
                        // effects
                        HashMap<String, Integer> effectMap = (HashMap<String, Integer>) EntityHelper.getEffectMap(ply).clone();
                        List<String> effects = new ArrayList<>(effectMap.keySet());
                        for (String effectDisplayName : effects) {
                            int effectDisplayTime = effectMap.getOrDefault(effectDisplayName, 0);
                            if (effectDisplayTime <= 0) continue;
                            if (result.length() > 0) result.append(separator);
                            String effectLore = buffConfig.getString("effects." + effectDisplayName + ".tooltip", "我不道啊？！");
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
                        Set<String> allBuff = buffConfig.getConfigurationSection("effects").getKeys(false);
                        long offset = ((Calendar.getInstance().getTimeInMillis() / 500)) % allBuff.size();
                        // test purpose: see if all buff sprites are good to go
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
                    case "mana_tier":
                        return PlayerHelper.getPlayerManaTier(ply) + "";
                    case "health_tier":
                        return PlayerHelper.getPlayerHealthTier(ply) + "";
                    case "accessory_amount": {
                        return PlayerHelper.getAccessoryAmount(ply) + "";
                    }
                    case "ui_size": {
                        return (int) (Setting.getOptionDouble(ply, Setting.Options.UI_SIZE) * 100) + "";
                    }
                }
                // BELOW: attributes that would require attribute map to be computed
                HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(ply);
                if (attrMap == null) attrMap = new HashMap<>(1);
                switch (params) {
                    case "defence":
                        return (int)Math.round(attrMap.getOrDefault("defence", 0d) * attrMap.getOrDefault("defenceMulti", 1d)) + "";
                    case "def_dmg": {
                        double def = attrMap.getOrDefault("defence", 0d);
                        double defDmg = PlayerHelper.getDefenceDamage(ply);
                        return (int) (defDmg * 100 / (def + defDmg) ) + "";
                    }
                    case "max_mana":
                        return (int)Math.round(attrMap.getOrDefault("maxMana", 20d)) + "";
                    case "stealth_use":
                        return (int)Math.round(attrMap.getOrDefault("stealthConsumptionMulti", 1d) * 100) + "";
                    default:
                        Bukkit.broadcastMessage("UNHANDLED PLACEHOLDER " + params);
                        return "哒哒哒哒嘀哒哒，我不道啊";
                }
            }
        });
    }
    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new ArrowHitListener(), this);
        Bukkit.getPluginManager().registerEvents(new BossSpawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new BossTickListener(), this);
        Bukkit.getPluginManager().registerEvents(new CraftingListener(), this);
        Bukkit.getPluginManager().registerEvents(new DamageListener(), this);
        Bukkit.getPluginManager().registerEvents(new DropItemSpawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new EntitySpawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new ItemUseAndAttributeListener(), this);
        Bukkit.getPluginManager().registerEvents(new MenuHandler(), this);
        Bukkit.getPluginManager().registerEvents(new NPCListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerChatListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerKeyToggleListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
        Bukkit.getPluginManager().registerEvents(new RandomTitleListener(), this);
        Bukkit.getPluginManager().registerEvents(new ResidenceListener(), this);
        Bukkit.getPluginManager().registerEvents(new ServerStopListener(), this);
        Bukkit.getPluginManager().registerEvents(new VanillaMechanicListener(), this);
    }
    private void initThreads() {
        YmlHelper.threadSaveYml();
        PlayerHelper.threadArmorAccessory();
        PlayerHelper.threadAttribute();
        PlayerHelper.threadBackground();
        PlayerHelper.threadBGM();
        PlayerHelper.threadMonsterCritterSpawn();
        PlayerHelper.threadExtraTicking();
        PlayerHelper.threadRegen();
        PlayerHelper.threadSaveInventories();
        PlayerHelper.threadSpecialBiome();
        EventAndTime.threadTimeAndEvent();
        MetadataHelper.threadMetadataGC();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        Bukkit.broadcastMessage(worldName);
        if (worldName.equalsIgnoreCase("test"))
            return OverworldChunkGenerator.getInstance();
        if (worldName.equalsIgnoreCase(Constants.WORLD_NAME_SURFACE))
            return OverworldChunkGenerator.getInstance();
        if (worldName.equalsIgnoreCase(Constants.WORLD_NAME_CAVERN))
            return CavernChunkGenerator.getInstance();
        if (worldName.equalsIgnoreCase(Constants.WORLD_NAME_UNDERWORLD))
            return UnderworldChunkGenerator.getInstance();
        getLogger().log(Level.SEVERE, "UNKNOWN WORLD GENERATOR REQUESTED! (" + worldName + ")");
        return super.getDefaultWorldGenerator(worldName, id);
    }
    @Override
    public void onEnable() {
        WorldHelper.initWorlds();

        registerEvents();
        initThreads();
        setupPlaceholders();

        MenuHandler.loadGuis();

        CustomEntities.registerEntities();

        this.getCommand("findNoise").setExecutor(new NoiseGeneratorTest());
        this.getCommand(SettingCommandExecutor.COMMAND).setExecutor(new SettingCommandExecutor());
        this.getCommand(RestartCommandExecutor.COMMAND).setExecutor(new RestartCommandExecutor());
        this.getCommand(MenuHandler.COMMAND).setExecutor(new MenuHandler());

        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        getLogger().info("泰拉瑞亚RPG插件部分已启动。");
        getLogger().info("世界种子: " + WORLD_SEED);
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
    }
    @Override
    public void onDisable() {
        CustomEntities.unregisterEntities();
        getLogger().info("泰拉瑞亚RPG插件部分已停用。");
    }

    public static TerrariaHelper getInstance() {
        return instance;
    }
}
