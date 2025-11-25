package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.libs.jline.internal.Nullable;
import org.bukkit.entity.Player;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Queue;

import static terraria.worldgen.overworld.OverworldBiomeGenerator.biomeCache;
import static terraria.worldgen.overworld.OverworldBiomeGenerator.getBiomeType;
import static terraria.worldgen.overworld.StructurePopulatorBiomeCenter.getBiomeFeature;

public class BiomeSummary {
    public static final ConfigurationSection CONFIG = TerrariaHelper.optimizationConfig.getConfigurationSection("worldGen.mapConfig");

    public static final int BLOCKS_PER_PIXEL = CONFIG.getInt("blocksPerPixel", 20);
    public static final int BIOME_MARKER_RADIUS_PIXEL = CONFIG.getInt("biomeMarkerRadiusPixel", 1);
    public static final int SPAWN_MARKER_RADIUS_PIXEL = CONFIG.getInt("spawnMarkerRadiusPixel", 3);
    public static final int AXIS_MARKER_LENGTH_PIXEL = CONFIG.getInt("axisMarkerLengthPixel", 25);
    public static final float AXIS_MARKER_FONT_SIZE = (float) CONFIG.getDouble("axisMarkerFontSize", 15);
    public static final int BIOME_SCAN_RADIUS_IN_BLOCKS = CONFIG.getInt("biomeScanRadiusInBlocks", 5000);
    public static final int MINIMUM_LABELED_BIOME_AREA_BLOCKS = BLOCKS_PER_PIXEL * BLOCKS_PER_PIXEL * CONFIG.getInt("minLabeledBiomeAreaPixels", 5);
    public static final int GRID_SIZE_IN_PIXELS = CONFIG.getInt("gridSizeInPixels", 16);
    public static final File OUTPUT_FOLDER = new File(CONFIG.getString("outputFolder", "worldGenDebug"));

    public static BufferedImage BIOME_IMAGE = null;
    public static HashMap<WorldHelper.BiomeType, List<Map.Entry<Point, Integer>>> BIOME_CENTERS = new HashMap<>();
    public static HashMap<WorldHelper.BiomeType, List<Point>> BIOME_STRUCTS = new HashMap<>();

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
        int halfImageSize = imageDimension / 2;
        int minBlockX = - (halfImageSize * BLOCKS_PER_PIXEL);
        int minBlockZ = - (halfImageSize * BLOCKS_PER_PIXEL);

        // 2. Image Creation
        BIOME_IMAGE = new BufferedImage(imageDimension, imageDimension, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = BIOME_IMAGE.createGraphics();

        // 3. Biome Rendering Loop
        for (int imageX = 0; imageX < imageDimension; imageX++) {
            for (int imageZ = 0; imageZ < imageDimension; imageZ++) {
                // Calculate representative block coordinates
                int blockX = minBlockX + (imageX * BLOCKS_PER_PIXEL) + (BLOCKS_PER_PIXEL / 2); // Center of the block
                int blockZ = minBlockZ + (imageZ * BLOCKS_PER_PIXEL) + (BLOCKS_PER_PIXEL / 2); // Center of the block

                // Get biome and color
                WorldHelper.BiomeType biomeType = getBiomeType(blockX, blockZ);

                g2d.setColor(biomeType.color);
                g2d.fillRect(imageX, imageZ, 1, 1);
            }
        }

        // 4. Chessboard Overlay
        int halfImageGridsAmount = (int) Math.ceil((double) halfImageSize / GRID_SIZE_IN_PIXELS);
        for (int x = -halfImageGridsAmount; x <= halfImageGridsAmount; x++) {
            int gridPixelX = halfImageSize + x * GRID_SIZE_IN_PIXELS;
            for (int y = -halfImageGridsAmount; y <= halfImageGridsAmount; y++) {
                g2d.setColor(gridColors[Math.abs(x + y) % 2]);
                int gridPixelY = halfImageSize + y * GRID_SIZE_IN_PIXELS;
                g2d.fillRect(gridPixelX, gridPixelY, GRID_SIZE_IN_PIXELS, GRID_SIZE_IN_PIXELS);
            }
        }

        // 5. Mark Spawn Point and Biome Centers
        int spawnImageX = imageDimension / 2;
        int spawnImageZ = imageDimension / 2;

        // Mark spawn point & axis labels
        g2d.setColor(Color.RED);
        g2d.fillOval(spawnImageX - SPAWN_MARKER_RADIUS_PIXEL, spawnImageZ - SPAWN_MARKER_RADIUS_PIXEL,
                2 * SPAWN_MARKER_RADIUS_PIXEL + 1, 2 * SPAWN_MARKER_RADIUS_PIXEL + 1);
        g2d.fillRect(spawnImageX - 1, spawnImageZ,
                2, AXIS_MARKER_LENGTH_PIXEL);
        g2d.fillRect(spawnImageX, spawnImageZ - 1,
                AXIS_MARKER_LENGTH_PIXEL, 2);
        Font currentFont = g2d.getFont();
        Font newFont = currentFont.deriveFont(AXIS_MARKER_FONT_SIZE);
        g2d.setFont(newFont);
        g2d.drawString("X", spawnImageX + AXIS_MARKER_LENGTH_PIXEL - AXIS_MARKER_FONT_SIZE, spawnImageZ);
        g2d.drawString("Z", spawnImageX - AXIS_MARKER_FONT_SIZE, spawnImageZ + AXIS_MARKER_LENGTH_PIXEL);

        // Mark biome structures
        int markerSize = BIOME_MARKER_RADIUS_PIXEL * 2 + 1; // Total marker size including the border
        BIOME_STRUCTS.forEach((biomeType, centers) -> centers.forEach(center -> {
            int biomeImageX = (center.x + BIOME_SCAN_RADIUS_IN_BLOCKS) / BLOCKS_PER_PIXEL;
            int biomeImageZ = (center.y + BIOME_SCAN_RADIUS_IN_BLOCKS) / BLOCKS_PER_PIXEL;

            // Draw lighter center
            g2d.setColor(biomeType.color.brighter());
            g2d.fillRect(biomeImageX - BIOME_MARKER_RADIUS_PIXEL, biomeImageZ - BIOME_MARKER_RADIUS_PIXEL,
                    markerSize, markerSize); // Fill without the border

            // Draw darker border
            g2d.setColor(biomeType.color.darker());
            g2d.drawRect(biomeImageX - BIOME_MARKER_RADIUS_PIXEL, biomeImageZ - BIOME_MARKER_RADIUS_PIXEL,
                    markerSize, markerSize); // Outline the border
        }));

        // Mark biome centers
        BIOME_CENTERS.forEach((biomeType, centers) -> centers.forEach(centerPair -> {
            Point center = centerPair.getKey();
            int biomeImageX = (center.x + BIOME_SCAN_RADIUS_IN_BLOCKS) / BLOCKS_PER_PIXEL;
            int biomeImageZ = (center.y + BIOME_SCAN_RADIUS_IN_BLOCKS) / BLOCKS_PER_PIXEL;

            // Draw biome-colored center
            g2d.setColor(biomeType.color);
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
            ImageIO.write(BIOME_IMAGE, "png", outputFile);
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
        BIOME_CENTERS = new HashMap<>();
        BIOME_STRUCTS = new HashMap<>();
        // Radius and diameter are for the visited 2D array.
        int diameter = BiomeSearchGridPosition.RADIUS * 2 + 1;
        boolean[][] visited = new boolean[diameter][diameter];

        // get biome "centers"; stepsize is the grid size in blocks
        for (int gridX = -BiomeSearchGridPosition.RADIUS; gridX <= BiomeSearchGridPosition.RADIUS; gridX ++) {
            for (int gridZ = -BiomeSearchGridPosition.RADIUS; gridZ <= BiomeSearchGridPosition.RADIUS; gridZ ++) {
                BiomeSearchGridPosition currGridPos = new BiomeSearchGridPosition(gridX, gridZ);
                WorldHelper.BiomeType biomeType = getBiomeType(currGridPos.getBlockX(), currGridPos.getBlockZ());
                if (!visited[currGridPos.getIndexX()][currGridPos.getIndexZ()] && biomeType != WorldHelper.BiomeType.NORMAL) {
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
                                        && getBiomeType(newGridPos.getBlockX(), newGridPos.getBlockZ()) == biomeType
                                        && !visited[newGridPos.getIndexX()][newGridPos.getIndexZ()]) {
                                    queue.offer(newGridPos.toPoint());
                                    visited[newGridPos.getIndexX()][newGridPos.getIndexZ()] = true;
                                }
                            }
                        }
                    }

                    if (region.size() * BLOCKS_PER_PIXEL * BLOCKS_PER_PIXEL >= MINIMUM_LABELED_BIOME_AREA_BLOCKS) {
                        Point centralPoint = findCentralPoint(region);
                        Point ptBlock = new Point(centralPoint.x * BLOCKS_PER_PIXEL, centralPoint.y * BLOCKS_PER_PIXEL);
                        BIOME_CENTERS.computeIfAbsent(biomeType, k -> new ArrayList<>())
                                .add(new AbstractMap.SimpleImmutableEntry<>(ptBlock, region.size()));
                    }
                }
            }
        }

        // get biome structure spawn location; granularity should be minecraft chunk (16 blocks)
        int chunksRad = BIOME_SCAN_RADIUS_IN_BLOCKS / 16;
        for (int chunkX = -chunksRad; chunkX <= chunksRad; chunkX ++) {
            for (int chunkZ = -chunksRad; chunkZ <= chunksRad; chunkZ ++) {
                OverworldBiomeGenerator.BiomeFeature biomeFeature = getBiomeFeature(chunkX, chunkZ);
                if (StructurePopulatorBiomeCenter.shouldGenerateStructure(biomeFeature, chunkX, chunkZ)) {
                    BIOME_STRUCTS.computeIfAbsent(biomeFeature.evaluatedBiome, k -> new ArrayList<>())
                            .add(new Point((chunkX << 4) + 8, (chunkZ << 4) + 8));
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

    // utility function - find nearest biome centers for the player
    @Nullable
    public static void findClosestBiomeCenters(Player ply) {
        Location origin = ply.getLocation();

        for (WorldHelper.BiomeType targetBiome : BIOME_CENTERS.keySet()) {
            // PQ holds the original center entries: <Point, Integer (Size)>
            PriorityQueue<Map.Entry<Point, Integer>> topCenters = new PriorityQueue<>(
                    3,
                    // we want the LARGEST d^2 at the top (min-heap).
                    (entryA, entryB) -> {
                        // calculate d^2 for entry A
                        Point centerA = entryA.getKey();
                        double distanceSquaredA = origin.distanceSquared(
                                new Location(origin.getWorld(), centerA.x, origin.getY(), centerA.y)
                        );

                        // calculate d^2 for entry B
                        Point centerB = entryB.getKey();
                        double distanceSquaredB = origin.distanceSquared(
                                new Location(origin.getWorld(), centerB.x, origin.getY(), centerB.y)
                        );

                        // comparator returns 0, positive, or negative based on the comparison of distances.
                        // reverse the order (B vs A) so the LARGEST distance is at the peek (min-heap root).
                        return Double.compare(distanceSquaredB, distanceSquaredA);
                    }
            );

            List<Map.Entry<Point, Integer>> centers = BIOME_CENTERS.get(targetBiome);
            for (Map.Entry<Point, Integer> centerPair : centers) {
                Point center = centerPair.getKey();
                double distanceSquared = origin.distanceSquared(new Location(origin.getWorld(), center.x, origin.getY(), center.y));

                // if the list has fewer than 3 points, add the current center.
                if (topCenters.size() < 3) {
                    topCenters.add(centerPair);
                } else {
                    // peek returns the FARTHEST point currently in the list
                    Point farthestCenter = topCenters.peek().getKey();
                    double farthestDistanceSquared = origin.distanceSquared(
                            new Location(origin.getWorld(), farthestCenter.x, origin.getY(), farthestCenter.y)
                    );

                    // if the current center is CLOSER than the FARTHEST center in the list, replace it.
                    if (distanceSquared < farthestDistanceSquared) {
                        topCenters.poll(); // Remove the current farthest point
                        topCenters.add(centerPair); // Add the new, closer point
                    }
                }
            }

            // convert PriorityQueue to a List and sort it explicitly by distance (closest first) for printing
            List<Map.Entry<Point, Integer>> sortedCenters = new ArrayList<>();
            while (!topCenters.isEmpty()) {
                sortedCenters.add(0, topCenters.poll()); // Add to the front to reverse order (closest first)
            }

            // print the results
            StringBuilder msgBuilder = new StringBuilder();
            msgBuilder.append(String.format("§#%02x%02x%02x", targetBiome.color.getRed(), targetBiome.color.getGreen(), targetBiome.color.getBlue()));
            msgBuilder.append("[");
            msgBuilder.append(targetBiome.displayName);
            msgBuilder.append("] ");

            for (int i = 0; i < sortedCenters.size(); i++) {
                Map.Entry<Point, Integer> entry = sortedCenters.get(i);
                Point center = entry.getKey();

                msgBuilder.append("(");
                msgBuilder.append(center.x);
                msgBuilder.append(",");
                msgBuilder.append(center.y);
                msgBuilder.append(")[规模: ");
                msgBuilder.append(entry.getValue());
                msgBuilder.append("]");

                if (i < sortedCenters.size() - 1) {
                    msgBuilder.append(" ");
                }
            }

            ply.sendMessage(msgBuilder.toString());
        }
    }

    /**
     * Finds the closest biome structure point of the player's current biome type.
     * @param ply The player.
     */
    @Nullable
    public static void findClosestBiomeStructure(Player ply) {
        Location origin = ply.getLocation();

        // get the player's current biome type
        WorldHelper.BiomeType playerBiome = getBiomeType(origin.getBlockX(), origin.getBlockZ());

        // check if any structures are tracked for this biome
        if (!BIOME_STRUCTS.containsKey(playerBiome)) {
            ply.sendMessage(String.format("§e%s 生物群系不会生成特殊结构", playerBiome.displayName));
            return;
        }

        List<Point> structures = BIOME_STRUCTS.get(playerBiome);
        // find the single closest structure
        Point closestStructure = null;
        double minDistanceSquared = Double.MAX_VALUE;

        for (Point structure : structures) {
            double distanceSquared = origin.distanceSquared(
                    new Location(origin.getWorld(), structure.x, origin.getY(), structure.y)
            );

            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                closestStructure = structure;
            }
        }

        // print the result
        if (closestStructure != null) {
            ply.sendMessage(String.format(
                    "§a%s 的最近结构位置: 坐标 (%d, %d)",
                    playerBiome.displayName,
                    closestStructure.x,
                    closestStructure.y
            ));
        } else {
            ply.sendMessage(String.format("§c未能找到 %s 的最近结构点", playerBiome.displayName));
        }
    }
}