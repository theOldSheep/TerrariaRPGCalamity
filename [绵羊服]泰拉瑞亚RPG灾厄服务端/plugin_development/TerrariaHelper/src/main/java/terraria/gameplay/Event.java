package terraria.gameplay;

import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Event {
    public static String currentEvent = "", questFish = "";
    public static HashMap<String, Double> eventInfo = new HashMap<>();
    public static HashSet<String> questFishSubmitted = new HashSet<>();
    // celestial pillars
    public static ArrayList<Entity> pillars = new ArrayList<>(4);
}
