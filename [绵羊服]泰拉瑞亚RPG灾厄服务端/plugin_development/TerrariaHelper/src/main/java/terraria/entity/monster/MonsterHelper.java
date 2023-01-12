package terraria.entity.monster;


import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;

public class MonsterHelper {
    public static class MonsterStats {
        public double health;
        public HashMap<String, Double> attrMap;
        public ConfigurationSection configSection;
        // constructor
        public MonsterStats() {
            health = 100d;
            attrMap = new HashMap<>();
            configSection = null;
        }
    }
}
