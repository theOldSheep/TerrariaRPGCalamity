package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.libs.jline.internal.Nullable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import terraria.TerrariaHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static terraria.worldgen.overworld.OverworldBiomeGenerator.getBiome;

public class BiomeSummary {
    public static final ConfigurationSection CONFIG = TerrariaHelper.settingConfig.getConfigurationSection("worldGen.mapConfig");

    public static final int BLOCKS_PER_PIXEL = CONFIG.getInt("blocksPerPixel", 20);
    public static final int BIOME_MARKER_RADIUS_PIXEL = CONFIG.getInt("biomeMarkerRadiusPixel", 1);
    public static final int SPAWN_MARKER_RADIUS_PIXEL = CONFIG.getInt("spawnMarkerRadiusPixel", 3);
    public static final int BIOME_SCAN_RADIUS_IN_BLOCKS = CONFIG.getInt("biomeScanRadiusInBlocks", 5000);
    public static final int MINIMUM_LABELED_BIOME_AREA_BLOCKS = BLOCKS_PER_PIXEL * BLOCKS_PER_PIXEL * CONFIG.getInt("minLabeledBiomeAreaPixels", 5);
    public static final int GRID_SIZE_IN_PIXELS = CONFIG.getInt("gridSizeInPixels", 16);
    public static final File OUTPUT_FOLDER = new File(CONFIG.getString("outputFolder", "worldGenDebug"));
    public static HashMap<Biome, List<Point>> biomeCenters = new HashMap<>();
    public static final HashMap<Biome, Color> BIOME_COLORS = new HashMap<>();
    // BIOME_COLORS init
    static {
        BiomeSummary.BIOME_COLORS.put(Biome.FOREST, new Color(0, 175, 0)); //forest(normal)
        BiomeSummary.BIOME_COLORS.put(Biome.JUNGLE, new Color(0, 100, 0)); //jungle
        BiomeSummary.BIOME_COLORS.put(Biome.DESERT, new Color(255, 255, 0)); //desert
        BiomeSummary.BIOME_COLORS.put(Biome.MUTATED_DESERT, new Color(0, 50, 80)); //sunken sea
        BiomeSummary.BIOME_COLORS.put(Biome.BEACHES, new Color(255, 255, 150)); //beach
        BiomeSummary.BIOME_COLORS.put(Biome.OCEAN, new Color(0, 0, 255)); //ocean
        BiomeSummary.BIOME_COLORS.put(Biome.COLD_BEACH, new Color(130, 110, 100)); //sulphurous beach
        BiomeSummary.BIOME_COLORS.put(Biome.FROZEN_OCEAN, new Color(120, 200, 150)); //sulphurous ocean
        BiomeSummary.BIOME_COLORS.put(Biome.TAIGA_COLD, new Color(150, 200, 255)); //tundra
        BiomeSummary.BIOME_COLORS.put(Biome.MUSHROOM_ISLAND, new Color(150, 0, 150)); //corruption
        BiomeSummary.BIOME_COLORS.put(Biome.MESA, new Color(50, 25, 60)); //astral infection
        BiomeSummary.BIOME_COLORS.put(Biome.ICE_FLATS, new Color(255, 255, 255)); //hallow
    }

    // save the biome image for testing purposes and so on
    public static void generateBiomeImage() {
        // 1. Configuration
        int worldSizeInBlocks = BIOME_SCAN_RADIUS_IN_BLOCKS * 2 + 1;
        int imageDimension = worldSizeInBlocks / BLOCKS_PER_PIXEL;
        Color[] gridColors = {
                new Color(0, 0, 0, 32),        // Black with 12.5% transparency
                new Color(255, 255, 255, 32)   // White with 12.5% transparency
        };

        // Ensure the output folder exists
        if (!OUTPUT_FOLDER.exists()) {
            OUTPUT_FOLDER.mkdirs();
        }

        // Calculate the block range around spawn
        int halfImageWidth = imageDimension / 2;
        int halfImageHeight = imageDimension / 2;
        int minBlockX = - (halfImageWidth * BLOCKS_PER_PIXEL);
        int minBlockZ = - (halfImageHeight * BLOCKS_PER_PIXEL);

        // 2. Image Creation
        BufferedImage biomeImage = new BufferedImage(imageDimension, imageDimension, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = biomeImage.createGraphics();

        // 3. Biome Rendering Loop
        for (int imageX = 0; imageX < imageDimension; imageX++) {
            for (int imageZ = 0; imageZ < imageDimension; imageZ++) {
                // Calculate representative block coordinates
                int blockX = minBlockX + (imageX * BLOCKS_PER_PIXEL) + (BLOCKS_PER_PIXEL / 2); // Center of the block
                int blockZ = minBlockZ + (imageZ * BLOCKS_PER_PIXEL) + (BLOCKS_PER_PIXEL / 2); // Center of the block

                // Get biome and color
                Biome biome = getBiome(blockX, blockZ);
                Color biomeColor = BIOME_COLORS.getOrDefault(biome, Color.BLACK);

                g2d.setColor(biomeColor);
                g2d.fillRect(imageX, imageZ, 1, 1);
            }
        }

        // 4. Chessboard Overlay
        int halfImageGridsAmountX = (int) Math.ceil((double) halfImageWidth / GRID_SIZE_IN_PIXELS);
        int halfImageGridsAmountY = (int) Math.ceil((double) halfImageHeight / GRID_SIZE_IN_PIXELS);
        for (int x = -halfImageGridsAmountX; x <= halfImageGridsAmountX; x++) {
            int gridPixelX = halfImageWidth + x * GRID_SIZE_IN_PIXELS;
            for (int y = -halfImageGridsAmountY; y <= halfImageGridsAmountY; y++) {
                g2d.setColor(gridColors[Math.abs(x + y) % 2]);
                int gridPixelY = halfImageWidth + y * GRID_SIZE_IN_PIXELS;
                g2d.fillRect(gridPixelX, gridPixelY, GRID_SIZE_IN_PIXELS, GRID_SIZE_IN_PIXELS);
            }
        }

        // 5. Mark Spawn Point and Biome Centers
        int spawnImageX = imageDimension / 2;
        int spawnImageZ = imageDimension / 2;

        // Mark spawn point
        g2d.setColor(Color.RED);
        g2d.fillOval(spawnImageX - SPAWN_MARKER_RADIUS_PIXEL, spawnImageZ - SPAWN_MARKER_RADIUS_PIXEL,
                2 * SPAWN_MARKER_RADIUS_PIXEL + 1, 2 * SPAWN_MARKER_RADIUS_PIXEL + 1);

        // Mark biome centers
        int markerSize = BIOME_MARKER_RADIUS_PIXEL * 2 + 1; // Total marker size including the border
        biomeCenters.forEach((biome, centers) -> centers.forEach(center -> {
            int biomeImageX = (center.x + BIOME_SCAN_RADIUS_IN_BLOCKS) / BLOCKS_PER_PIXEL;
            int biomeImageZ = (center.y + BIOME_SCAN_RADIUS_IN_BLOCKS) / BLOCKS_PER_PIXEL;

            // Draw biome-colored center
            Color biomeColor = BIOME_COLORS.getOrDefault(biome, Color.BLACK);
            g2d.setColor(biomeColor);
            g2d.fillRect(biomeImageX - BIOME_MARKER_RADIUS_PIXEL, biomeImageZ - BIOME_MARKER_RADIUS_PIXEL,
                    markerSize, markerSize); // Fill without the border

            // Draw red border
            g2d.setColor(Color.RED);
            g2d.drawRect(biomeImageX - BIOME_MARKER_RADIUS_PIXEL, biomeImageZ - BIOME_MARKER_RADIUS_PIXEL,
                    markerSize, markerSize); // Outline the border
        }));

        // Dispose Graphics2D (after all drawing is done)
        g2d.dispose();

        // 6. Save Image (with dynamic filename)
        try {
            String filename = String.format("biome_map(%dx%d)[%d].png",
                    worldSizeInBlocks, worldSizeInBlocks, GRID_SIZE_IN_PIXELS * BLOCKS_PER_PIXEL);
            File outputFile = new File(OUTPUT_FOLDER, filename);
            ImageIO.write(biomeImage, "png", outputFile);
            System.out.println("Biome map generated: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class BiomeSearchGridPosition {
        protected static final int RADIUS = BIOME_SCAN_RADIUS_IN_BLOCKS / BLOCKS_PER_PIXEL;
        protected int gridX, gridZ;
        public BiomeSearchGridPosition(int gridX, int gridZ) {
            this.gridX = gridX;
            this.gridZ = gridZ;
        }
        protected int getIndexX() {return gridX + RADIUS;}
        protected int getIndexZ() {return gridZ + RADIUS;}
        protected int getBlockX() {return gridX * BLOCKS_PER_PIXEL;}
        protected int getBlockZ() {return gridZ * BLOCKS_PER_PIXEL;}
        protected boolean isWithinBounds() {
            return gridX >= -RADIUS && gridX <= RADIUS && gridZ >= -RADIUS && gridZ <= RADIUS;
        }
        protected Point toPoint() {
            return new Point(gridX, gridZ);
        }
    }
    public static void createBiomeCenters() {
        biomeCenters = new HashMap<>();
        // Radius and diameter are for the visited 2D array.
        int diameter = BiomeSearchGridPosition.RADIUS * 2 + 1;
        boolean[][] visited = new boolean[diameter][diameter];

        for (int gridX = -BiomeSearchGridPosition.RADIUS; gridX <= BiomeSearchGridPosition.RADIUS; gridX ++) {
            for (int gridZ = -BiomeSearchGridPosition.RADIUS; gridZ <= BiomeSearchGridPosition.RADIUS; gridZ ++) {
                BiomeSearchGridPosition currGridPos = new BiomeSearchGridPosition(gridX, gridZ);
                Biome biome = getBiome(currGridPos.getBlockX(), currGridPos.getBlockZ());
                if (!visited[currGridPos.getIndexX()][currGridPos.getIndexZ()] && biome != Biome.FOREST) {
                    List<Point> region = new ArrayList<>();
                    Queue<Point> queue = new LinkedList<>();
                    queue.offer(currGridPos.toPoint());
                    visited[currGridPos.getIndexX()][currGridPos.getIndexZ()] = true;

                    while (!queue.isEmpty()) {
                        Point current = queue.poll();
                        region.add(current);

                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BiomeSearchGridPosition newGridPos = new BiomeSearchGridPosition(current.x + dx, current.y + dz);
                                if (newGridPos.isWithinBounds()
                                        && getBiome(newGridPos.getBlockX(), newGridPos.getBlockZ()) == biome
                                        && !visited[newGridPos.getIndexX()][newGridPos.getIndexZ()]) {
                                    queue.offer(newGridPos.toPoint());
                                    visited[newGridPos.getIndexX()][newGridPos.getIndexZ()] = true;
                                }
                            }
                        }
                    }

                    if (region.size() * BLOCKS_PER_PIXEL * BLOCKS_PER_PIXEL >= MINIMUM_LABELED_BIOME_AREA_BLOCKS) {
                        Point centralPoint = findCentralPoint(region);
                        biomeCenters.computeIfAbsent(biome, k -> new ArrayList<>())
                                .add(new Point(centralPoint.x * BLOCKS_PER_PIXEL, centralPoint.y * BLOCKS_PER_PIXEL));
                    }
                }
            }
        }
    }
    // New helper function to find the central point
    private static Point findCentralPoint(List<Point> region) {
        int minDistanceSum = Integer.MAX_VALUE;
        Point centralPoint = null;

        for (Point candidate : region) {
            int distanceSum = 0;
            for (Point other : region) {
                distanceSum += Math.abs(candidate.x - other.x) + Math.abs(candidate.y - other.y);
            }

            if (distanceSum < minDistanceSum) {
                minDistanceSum = distanceSum;
                centralPoint = candidate;
            }
        }

        return centralPoint;
    }

    public static void testNearestBiomeCenter(Player ply) {
        Location loc = ply.getLocation();
        Bukkit.broadcastMessage("Current Location: " + loc);
        for (Biome b : biomeCenters.keySet()) {
            Location center = findClosestBiomeCenter(loc, b);
            Bukkit.broadcastMessage("Biome: " + b + ", closest center: " + center);
        }
    }
    @Nullable
    public static Location findClosestBiomeCenter(Location origin, Biome targetBiome) {
        List<Point> centers = biomeCenters.get(targetBiome);
        if (centers == null || centers.isEmpty()) {
            return null; // No centers found for this biome
        }

        Point closestCenter = null;
        double minDistanceSquared = Double.MAX_VALUE;

        for (Point center : centers) {
            double distanceSquared = origin.distanceSquared(new Location(origin.getWorld(), center.x, origin.getY(), center.y));
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                closestCenter = center;
            }
        }

        return new Location(origin.getWorld(), closestCenter.x, origin.getY(), closestCenter.y);
    }
}