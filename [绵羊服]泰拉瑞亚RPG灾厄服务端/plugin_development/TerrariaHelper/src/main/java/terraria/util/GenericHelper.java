package terraria.util;

import eos.moe.dragoncore.api.CoreAPI;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GenericHelper {
    static long nextHologramIndex = 0;

    public static String trimText(String textToTrim) {
        if (textToTrim == null) return "";
        try {
            StringBuilder result = new StringBuilder();
            int isColor = -1;
            for (char c : textToTrim.toCharArray()) {
                if (c == '§') isColor = 1;
                else if (isColor == 0 && c == '#') isColor = 6;
                else if (isColor < 0) result.append(c);
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
    public static void handleParticleLine(Vector vector, double length, double width, int ticksLinger, Location startLoc, String particleColor) {
        ArrayList<String> colorList = new ArrayList<>();
        colorList.add(particleColor);
        handleParticleLine(vector, length, width, width, ticksLinger, startLoc, colorList);
    }
    public static void handleParticleLine(Vector vector, double length, double width, double stepsize, int ticksLinger, Location startLoc, String particleColor) {
        ArrayList<String> colorList = new ArrayList<>();
        colorList.add(particleColor);
        handleParticleLine(vector, length, width, stepsize, ticksLinger, startLoc, colorList);
    }
    public static void handleParticleLine(Vector vector, double length, double width, double stepsize, int ticksLinger, Location startLoc, List<String> particleColor) {
        Vector dVec = vector.clone().normalize();
        int loopTime = (int) Math.round(length / stepsize);
        dVec.multiply(length / loopTime);
        List<Color> allColors = new ArrayList<>();
        for (String currColor : particleColor) {
            String[] info = currColor.split("\\|");
            allColors.add(Color.fromRGB(Integer.parseInt(info[0]), Integer.parseInt(info[1]), Integer.parseInt(info[2])));
        }
        Location currLoc = startLoc.clone();
        for (int i = 0; i <= loopTime; i ++) {
            // tweak color
            double colorProgress = (double) i * allColors.size() / (loopTime + 1);
            int colorIndex = (int) colorProgress;
            Color c1 = allColors.get(colorIndex);
            int rInt, gInt, bInt;
            if (allColors.size() > 1) {
                Color c2 = allColors.get((colorIndex + 1) % allColors.size());
                double multi2 = colorProgress % 1;
                double multi1 = 1 - multi2;
                rInt = (int) ((c1.getRed() * multi1) + (c2.getRed() * multi2));
                gInt = (int) ((c1.getGreen() * multi1) + (c2.getGreen() * multi2));
                bInt = (int) ((c1.getBlue() * multi1) + (c2.getBlue() * multi2));
            } else {
                rInt = c1.getRed();
                gInt = c1.getGreen();
                bInt = c1.getBlue();
            }
            // spawn "particles"
            String rCode = Integer.toHexString(rInt), gCode = Integer.toHexString(gInt), bCode = Integer.toHexString(bInt);
            if (rCode.length() == 1) rCode = "0" + rCode;
            if (gCode.length() == 1) gCode = "0" + gCode;
            if (bCode.length() == 1) bCode = "0" + bCode;
            String colorCode = rCode + gCode + bCode;
            // █ ●
            displayHoloText(currLoc, "§#" + colorCode + "█", ticksLinger, (float) width, (float) width, 1f);
            // add vector to location
            currLoc.add(dVec);
        }
    }
    public static void displayHoloText(Location displayLoc, String text, int ticksDisplay, float width, float height, float alpha) {
        String holoInd = "" + (nextHologramIndex++);
        // all players in radius of 64 blocks can see the hologram
        ArrayList<Player> playersSent = new ArrayList<>(100);
        for (Player p : displayLoc.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(displayLoc) < 4096) playersSent.add(p);
        for (Player p : playersSent)
            CoreAPI.setPlayerWorldTexture(p, holoInd, displayLoc, 0, 0, 0, "[text]" + text, width, height, alpha, true, true);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            for (Player p : playersSent)
                CoreAPI.removePlayerWorldTexture(p, holoInd);
        }, ticksDisplay);
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
            case "Debuff_咒火":
            case "Debuff_神圣之火":
                colorCode = "e";
                break;
            case "Debuff_霜火":
                colorCode = "b";
                break;
            case "Debuff_中毒":
            case "Debuff_剧毒":
                colorCode = "2";
                break;
            case "Debuff_破晓":
                colorCode = "4";
                break;
            default:
                colorCode = isCrit ? "c" : "6";
        }
        int ticksDisplay = 15;
        switch (damageCause) {
            case "Drowning":
                ticksDisplay = 10;
                break;
            case "Suffocation":
                ticksDisplay = 5;
                break;
            default:
                if (damageCause.startsWith("Debuff_")) {
                    ticksDisplay = 8;
                } else if (isCrit) ticksDisplay = 30;
        }
        // display the message
        String text = ChatColor.COLOR_CHAR + colorCode + (int) Math.round(dmg);
        Location displayLoc;
        if (e instanceof LivingEntity) displayLoc = ((LivingEntity) e).getEyeLocation().add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
        else displayLoc = e.getLocation().add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
        displayHoloText(displayLoc, text, ticksDisplay, 1f, 0.75f, 1f);
    }
}
