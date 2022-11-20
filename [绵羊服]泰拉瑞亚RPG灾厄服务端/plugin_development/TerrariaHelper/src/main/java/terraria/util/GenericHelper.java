package terraria.util;

import eos.moe.dragoncore.api.CoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GenericHelper {
    static int nextHologramIndex = 0;
    static HashMap<String, World> holograms = new HashMap<>();

    public static String trimText(String textToTrim) {
        try {
            StringBuilder result = new StringBuilder();
            int isColor = -1;
            for (char c : textToTrim.toCharArray()) {
                if (c == '§') isColor = 1;
                if (isColor == 0 && c == '#') isColor = 6;
                if (isColor < 0) result.append(c);
                isColor --;
            }
            return result.toString();
        } catch (Exception e) {
            return textToTrim;
        }
    }
    public static int[] coinConversion(int copperAmount) {
        int copper = copperAmount;
        int[] result = new int[]{0, 0, 0, 0};
        if (copper >= 1000000) {
            result[0] = copper / 1000000;
            copper = copper % 1000000;
        }
        if (copper >= 10000) {
            result[1] = copper / 10000;
            copper = copper % 10000;
        }
        if (copper >= 100) {
            result[2] = copper / 100;
            copper = copper % 100;
        }
        result[3] = copper;
        return result;
    }
    public static void dropMoney(Location loc, int amount) {
        int[] stackSize = coinConversion(amount);
        if (stackSize[0] > 0) loc.getWorld().dropItemNaturally(loc,
                ItemHelper.getItemFromDescription("铂金币:" + stackSize[0], false));
        if (stackSize[1] > 0) loc.getWorld().dropItemNaturally(loc,
                ItemHelper.getItemFromDescription("金币:" + stackSize[1], false));
        if (stackSize[2] > 0) loc.getWorld().dropItemNaturally(loc,
                ItemHelper.getItemFromDescription("银币:" + stackSize[2], false));
        if (stackSize[3] > 0) loc.getWorld().dropItemNaturally(loc,
                ItemHelper.getItemFromDescription("铜币:" + stackSize[3], false));
    }
    public static double getHorizontalDistance(Location locationA, Location locationB) {
        double distX = Math.abs(locationA.getX() - locationA.getX());
        double distZ = Math.abs(locationA.getZ() - locationA.getZ());
        return Math.max(distX, distZ);
    }
    public static void handleParticleLine(Vector vector, double length, double width, Location startLoc, List<String> particleColor) {
    }
    public static void handleParticleLine(Vector vector, double length, double width, double stepsize, double stepLoopTime, Location startLoc, List<String> particleColor) {
    }
    public static void handleParticleLine(Vector vector, double length, double width, Location startLoc, String particleColor) {
        ArrayList<String> colorList = new ArrayList<>();
        colorList.add(particleColor);
        handleParticleLine(vector, length, width, startLoc, colorList);
    }
    public static void handleParticleLine(Vector vector, double length, double width, double stepsize, double stepLoopTime, Location startLoc, String particleColor) {
        ArrayList<String> colorList = new ArrayList<>();
        colorList.add(particleColor);
        handleParticleLine(vector, length, width, stepsize, stepLoopTime, startLoc, colorList);
    }
    public static void displayHolo(Entity e, double dmg, boolean isCrit, String damageCause) {
        String colorCode;
        switch (damageCause) {
            case "回血":
                colorCode = "a";
                break;
            case "回蓝":
                colorCode = "9";
                break;
            case "debuff_咒火":
            case "debuff_神圣之火":
                colorCode = "e";
                break;
            case "debuff_霜火":
                colorCode = "b";
                break;
            case "debuff_中毒":
            case "debuff_剧毒":
                colorCode = "2";
                break;
            case "debuff_破晓":
                colorCode = "4";
                break;
            default:
                colorCode = isCrit ? "c" : "6";
        }
        int ticksDisplay = 30;
        switch (damageCause) {
            case "Drowning":
                ticksDisplay = 10;
                break;
            case "Suffocation":
                ticksDisplay = 5;
                break;
            default:
                if (damageCause.startsWith("debuff_")) {
                    ticksDisplay = 8;
                } else if (isCrit) ticksDisplay = 50;
        }
        // display the message
        String text = ChatColor.COLOR_CHAR + colorCode + (int) Math.round(dmg);
        Location displayLoc;
        if (e instanceof LivingEntity) displayLoc = ((LivingEntity) e).getEyeLocation().add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
        else displayLoc = e.getLocation().add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
        String holoInd = "HOLOIND_" + (nextHologramIndex++);
        // all players in radius of 64 blocks can see the hologram
        ArrayList<Player> playersSent = new ArrayList<>(100);
        for (Player p : e.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(displayLoc) < 4096) playersSent.add(p);
        for (Player p : playersSent)
            CoreAPI.setPlayerWorldTexture(p, holoInd, displayLoc, 0, 0, 0, "[text]" + text, 1, 0.75f, 1, true, true);
        holograms.put(holoInd, displayLoc.getWorld());
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            for (Player p : playersSent)
                CoreAPI.removePlayerWorldTexture(p, holoInd);
            holograms.remove(holoInd);
        }, ticksDisplay);
    }
}
