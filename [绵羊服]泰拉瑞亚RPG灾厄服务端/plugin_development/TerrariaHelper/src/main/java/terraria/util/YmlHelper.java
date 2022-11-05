package terraria.util;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import terraria.TerrariaHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;

public class YmlHelper {
    static HashMap<String, YmlSection> ymlCache = new HashMap<>();
    static HashMap<String, YmlSection> ymlLastSaveCache = new HashMap<>();

    public static void threadSaveYml() {
        // save the yml every 10 seconds (200 ticks)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TerrariaHelper.getInstance(), () -> {
            for (String ymlPath : ymlCache.keySet()) {
                ymlCache.get(ymlPath).saveIfModified(ymlPath);
            }
        }, 200, 0);
    }

    public static YmlSection getFile(String filePath) {
        String tweakedPath = filePath.replace('/', File.separatorChar);

        if (ymlCache.containsKey(tweakedPath))
            return ymlCache.get(tweakedPath);

        YmlSection fileContent = YmlSection.loadConfiguration(new File(tweakedPath));
        ymlCache.put(tweakedPath, fileContent);
        return fileContent;
    }
    // getter
    public static ConfigurationSection getSection(String filePath, String nodeName) {
        return getFile(filePath).getConfigurationSection(nodeName);
    }

    // helper class
    public static class YmlSection extends YamlConfiguration {
        boolean hasChanged = false;
        @Override
        public void	set(String path, Object value) {
            super.set(path, value);
            hasChanged = true;
        }

        public static YmlSection loadConfiguration(File file) {
            Validate.notNull(file, "File cannot be null");
            YmlSection config = new YmlSection();

            try {
                config.load(file);
            } catch (FileNotFoundException e) {
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE, "[YML ERROR] Cannot load " + file, e);
            }

            return config;
        }

        public void saveIfModified(String path) {
            if (hasChanged) {
                hasChanged = false;
                try {
                    save(path);
                } catch (IOException e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[YML ERROR] Cannot save into " + path, e);
                }
            }
        }
    }
}
