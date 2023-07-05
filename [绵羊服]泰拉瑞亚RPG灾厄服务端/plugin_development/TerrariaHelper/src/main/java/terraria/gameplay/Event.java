package terraria.gameplay;

import org.bukkit.entity.Entity;
import terraria.TerrariaHelper;
import terraria.entity.boss.event.CelestialPillar;
import terraria.util.EntityHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;

public class Event {
    public static String currentEvent = "", questFish = "";
    public static HashMap<String, Double> eventInfo = new HashMap<>();
    public static HashSet<String> questFishSubmitted = new HashSet<>();
    // celestial pillars
    public static HashMap<CelestialPillar.PillarTypes, CelestialPillar> pillars = new HashMap<>(8);

    private static final HashMap<String, InfoMapKeys> infoKeyMapping = new HashMap<>();
    public enum InfoMapKeys {
        // 1: true, 0: false
        EVENT_PROGRESS("invadeProgress"),
        MAX_EVENT_PROGRESS("invadeProgressMax"),
        IS_INVASION("isInvasion"),
        ;

        // fields
        final String keyName;
        // constructors
        InfoMapKeys(String keyName) {
            this.keyName = keyName;
            // test for collision
            if (infoKeyMapping.containsKey(keyName)) {
                TerrariaHelper.getInstance().getLogger().log(
                        Level.SEVERE, "Event Info Map Key Collision: " + keyName +
                                " between " + infoKeyMapping.get(keyName) + " and " + this);
            }
            infoKeyMapping.put(keyName, this);
        }

        @Override
        public String toString() {
            return keyName;
        }
    }
}
