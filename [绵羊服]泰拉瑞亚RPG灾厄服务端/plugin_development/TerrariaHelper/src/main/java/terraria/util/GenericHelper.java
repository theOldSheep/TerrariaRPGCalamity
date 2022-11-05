package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

public class GenericHelper {
    public static final double DEG_TO_RAD = Math.PI / 180;

    public static String trimText(String textToTrim) {
        try {
            return textToTrim;
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
    public static Vector vectorFromYawPitch(double yaw, double pitch) {
        // source code from Skript
        double y = -1 * Math.sin(pitch * DEG_TO_RAD);
        double div = Math.cos(pitch * DEG_TO_RAD);
        double x = -1 * Math.sin(yaw * DEG_TO_RAD);
        double z = Math.cos(yaw * DEG_TO_RAD);
        x *= div;
        z *= div;
        return new Vector(x,y,z);
    }
    public static Vector vectorFromYawPitch_quick(double yaw, double pitch) {
        // algorithm from Skript
        // uses xsin and xcos so that it is quicker. a bit less accurate though.
        double y = -1 * MathHelper.xsin_degree(pitch);
        double div = MathHelper.xcos_degree(pitch);
        double x = -1 * MathHelper.xsin_degree(yaw);
        double z = MathHelper.xcos_degree(yaw);
        x *= div;
        z *= div;
        return new Vector(x,y,z);
    }

}
