package terraria.worldgen;

import org.bukkit.Bukkit;
import terraria.util.MathHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Interpolate {
    public static class InterpolatePoint {
        public final double x, y;
        public InterpolatePoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
        public static InterpolatePoint create(double x, double y) {
            return new InterpolatePoint(x, y);
        }
        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }
    }
    public InterpolatePoint[] points;
    public final boolean useSquareSmoothing;
    public Interpolate(InterpolatePoint[] allPivots, boolean sqrSmoothing) {
        double lastX = -10;
        for (InterpolatePoint pt : allPivots) {
            if (Math.abs(pt.x) > 1 || pt.x <= lastX) {
                Bukkit.getLogger().warning("Interpolate Initialized with UNSORTED pivots, this will not work!");
                Bukkit.getLogger().warning("Error pivot: " + pt);
            }
            lastX = pt.x;
        }
        points = allPivots;
        useSquareSmoothing = sqrSmoothing;
    }
    public Interpolate(InterpolatePoint[] allPivots, String interpolateName, boolean useSquareSmoothing) {
        this(allPivots, useSquareSmoothing);
        printTestImage(interpolateName);
    }
    public Interpolate(InterpolatePoint[] allPivots, String interpolateName) {
        this(allPivots, interpolateName,false);
    }
    public double getY(double x) {
        InterpolatePoint ptLeft = null, ptRight = null;
        for (InterpolatePoint pt : points) {
            if (pt.x < x) ptLeft = pt;
            else {
                ptRight = pt;
                break;
            }
        }
        if (ptLeft != null && ptRight != null) {
            // distance from ptLeft, in range (0, 1)
            double xDiff = Math.max(0, x - ptLeft.x) / (ptRight.x - ptLeft.x);
            double multiplier = MathHelper.xsin(xDiff * 2 - 1) / 2 + 0.5;
            // this squares the multiplier so that it seems more smooth
            if (useSquareSmoothing) multiplier = multiplier * multiplier;
            return ptRight.y * multiplier + ptLeft.y * (1 - multiplier);
        }
        else if (ptLeft  != null) return ptLeft.y ;
        else if (ptRight != null) return ptRight.y;
        return 0;
    }
    public void printTestImage(String interpolateName) {
        int length = 1000, height = 250;
        double range = 2.25; // 2 is the minimum for drawing out the entire noise map
        File dir_biome_map = new File("worldGenDebug/" + interpolateName + ".png");
        Bukkit.getLogger().info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        Bukkit.getLogger().info("START GENERATING INTERPOLATE MAP " + interpolateName);
        Bukkit.getLogger().info("Interpolate Pivots: " + Arrays.toString(points));
        BufferedImage heightMap = new BufferedImage(length, height, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < length; i++) {
            double x = (double) i * range / length - range / 2;
            double y = getY(x);
            for (int j = 0; j < height; j ++) {
                if ((height - j) < y)
                    heightMap.setRGB(i, j, new Color(0, 255, 0).getRGB());
                else if (Math.abs(x) < 1)
                    heightMap.setRGB(i, j, new Color(255, 255, 255).getRGB());
                else
                    heightMap.setRGB(i, j, new Color(150, 150, 150).getRGB());
            }
        }
        for (InterpolatePoint pt : points) {
            int x = (int) (length * (0.5 + (pt.x / range)));
            int y = (int) (height - pt.y);
            int pointRadius = 0;
            for (int i = x - pointRadius; i <= x + pointRadius; i ++) {
                if (i < 0 || i >= length) continue;
                for (int j = y - pointRadius; j <= y + pointRadius; j++) {
                    if (j < 0 || j >= height) continue;
                    heightMap.setRGB(i, j, new Color(0, 0, 0, 25).getRGB());
                }
            }
        }
        try {
            ImageIO.write(heightMap, "png", dir_biome_map);
        } catch (IOException e) {
            e.printStackTrace();
            Bukkit.getLogger().warning(e.getMessage());
        }
        Bukkit.getLogger().info("FINISHED GENERATING INTERPOLATE MAP " + interpolateName);
        Bukkit.getLogger().info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }
}
