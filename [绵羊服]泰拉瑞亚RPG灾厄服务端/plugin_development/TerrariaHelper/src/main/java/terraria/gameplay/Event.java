package terraria.gameplay;

import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;

public class Event {
    public static String currentEvent = "";
    public static HashMap<String, Double> eventInfo = new HashMap<>();
    // celestial pillars
    public static ArrayList<Entity> pillars = new ArrayList<>(4);
}
