package terraria.worldgen;

import org.bukkit.Bukkit;
import terraria.TerrariaHelper;
import terraria.util.MathHelper;
import terraria.worldgen.overworld.BiomeSummary;

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
                TerrariaHelper.LOGGER.warning("Interpolate Initialized with UNSORTED pivots, this will not work!");
                TerrariaHelper.LOGGER.warning("Error pivot: " + pt);
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
            // the progress between the two points normalized to [0, 1]
            double xDiff = Math.max(0, x - ptLeft.x) / (ptRight.x - ptLeft.x);

            // xsin called with range [-1, 1], output normalized to [0, 1]
            double multiplier = MathHelper.xsin(xDiff * 2 - 1) / 2 + 0.5;

            // apply the xsin transformation again
            if (useSquareSmoothing) {
                multiplier = MathHelper.xsin(multiplier * 2 - 1) / 2 + 0.5;
            }

            // weighted average of both end points determined by multiplier
            return ptRight.y * multiplier + ptLeft.y * (1 - multiplier);
        }
        else if (ptLeft  != null) return ptLeft.y ;
        else if (ptRight != null) return ptRight.y;
        return 0;
    }
    public void printTestImage(String interpolateName) {
        int length = 1000, height = 250;
        double range = 2.25; // 2 is the minimum for drawing out the entire noise map
        double maxY = 0;
        for (InterpolatePoint pt : points) {
            maxY = Math.max(maxY, pt.y);
        }
        File outputFolder = BiomeSummary.OUTPUT_FOLDER;
        // Ensure the output folder exists
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }
        File dir_biome_map = new File(outputFolder,  interpolateName + ".png");

        TerrariaHelper.LOGGER.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        TerrariaHelper.LOGGER.info("START GENERATING INTERPOLATE MAP " + interpolateName);
        TerrariaHelper.LOGGER.info("Interpolate Pivots: " + Arrays.toString(points));
        BufferedImage heightMap = new BufferedImage(length, height, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < length; i++) {
            double x = (double) i * range / length - range / 2;
            double y = getY(x);
            for (int j = 0; j < height; j ++) {
                if ((height - j) * maxY / height <= y)
                    heightMap.setRGB(i, j, new Color(0, 255, 0).getRGB());
                else if (Math.abs(x) < 1)
                    heightMap.setRGB(i, j, new Color(255, 255, 255).getRGB());
                else
                    heightMap.setRGB(i, j, new Color(150, 150, 150).getRGB());
            }
        }
        for (InterpolatePoint pt : points) {
            // calculate pixel coordinates for the point
            int x = (int) ((pt.x + range / 2) / range * length);
            int y = (int) (height - (pt.y / maxY * height));

            // define the size of the point marker (radius)
            int pointRadius = 3;

            // mark the point on the image
            for (int i = x - pointRadius; i <= x + pointRadius; i++) {
                if (i < 0 || i >= length) continue;
                for (int j = y - pointRadius; j <= y + pointRadius; j++) {
                    if (j < 0 || j >= height) continue;
                    heightMap.setRGB(i, j, new Color(0, 0, 0).getRGB());
                }
            }
        }
        try {
            ImageIO.write(heightMap, "png", dir_biome_map);
        } catch (IOException e) {
            e.printStackTrace();
            TerrariaHelper.LOGGER.warning(e.getMessage());
        }
        TerrariaHelper.LOGGER.info("FINISHED GENERATING INTERPOLATE MAP " + interpolateName);
        TerrariaHelper.LOGGER.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }
}
