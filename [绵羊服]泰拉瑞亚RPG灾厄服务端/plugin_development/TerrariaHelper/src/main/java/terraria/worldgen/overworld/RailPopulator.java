package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import terraria.TerrariaHelper;

import java.util.Arrays;
import java.util.Random;

public class RailPopulator extends BlockPopulator {
    static Material[][][] RAIL_STRUCTURE;
    static final int RAIL_HEIGHT = TerrariaHelper.settingConfig.getInt("worldGen.railYCoordinate", 100);
    static final int RAIL_STRUCT_WIDTH = 3, RAIL_STRUCT_HEIGHT = 5;
    static {
        Material[][] basicShape = {
                {Material.WOOD, Material.WOOD, Material.WOOD},
                {Material.FENCE, Material.AIR, Material.FENCE},
                {Material.AIR, Material.AIR, Material.AIR},
                {Material.AIR, Material.RAILS, Material.AIR},
                {Material.WOOD, Material.STONE, Material.WOOD},};
        RAIL_STRUCTURE = new Material[16][RAIL_STRUCT_HEIGHT][RAIL_STRUCT_WIDTH];
        // when copying, flip the actual shape for efficient later use
        for (int i = 0; i < 16; i ++) {
            for (int j = 0; j < RAIL_STRUCT_HEIGHT; j ++) {
                System.arraycopy(basicShape[RAIL_STRUCT_HEIGHT - 1 - j], 0, RAIL_STRUCTURE[i][j], 0, RAIL_STRUCT_WIDTH);
            }
        }
        int[] pillarLoc = {0, 7, 8, 15};
        for (int idx : pillarLoc) {
            RAIL_STRUCTURE[idx][1][0] = Material.FENCE;
            RAIL_STRUCTURE[idx][2][0] = Material.FENCE;
            RAIL_STRUCTURE[idx][1][2] = Material.FENCE;
            RAIL_STRUCTURE[idx][2][2] = Material.FENCE;
        }
        int[] acceleratorLoc = {1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14};
        for (int idx : acceleratorLoc) {
            // the arrays are vertically flipped already.
            RAIL_STRUCTURE[idx][1][0] = Material.REDSTONE_TORCH_ON;
            RAIL_STRUCTURE[idx][1][1] = Material.POWERED_RAIL;
            RAIL_STRUCTURE[idx][1][2] = Material.REDSTONE_TORCH_ON;
        }
    }
    public static int RAIL_GRID_BLOCK_INTERVAL = BiomeSummary.GRID_SIZE_IN_PIXELS * BiomeSummary.BLOCKS_PER_PIXEL;

    private boolean shouldChunkContainRail(int chunkCoord) {
        int min = Math.abs(chunkCoord) << 4;
        int max = min + 15;
        return (min / RAIL_GRID_BLOCK_INTERVAL != max / RAIL_GRID_BLOCK_INTERVAL) ||
                min % RAIL_GRID_BLOCK_INTERVAL == 0 ||
                max % RAIL_GRID_BLOCK_INTERVAL == 0;
    }
    private int getRailCoord(int chunkCoord) {
        int min = chunkCoord << 4;
        for (int i = min; i < min + 16; i ++) {
            if (i % RAIL_GRID_BLOCK_INTERVAL == 0)
                return i;
        }
        return min + 8;
    }
    @Override
    public void populate(World wld, Random rdm, Chunk chunk) {
        // no rail near spawn
        if (Math.abs(chunk.getX()) <= 3 && Math.abs(chunk.getZ()) <= 3)
            return;

        boolean xDirHasRail = shouldChunkContainRail(chunk.getX());
        boolean zDirHasRail = shouldChunkContainRail(chunk.getZ());
        // no rail: early exit
        if (! (xDirHasRail || zDirHasRail))
            return;

        int xDirRailCoord = getRailCoord(chunk.getX());
        int zDirRailCoord = getRailCoord(chunk.getZ());
        int xStart = chunk.getX() << 4;
        int zStart = chunk.getZ() << 4;
        // i: along rail direction; j: horizontal orthogonal; k: vertical
        int structHalfWidth = RAIL_STRUCT_WIDTH / 2;
        for (int i = 0; i < 16; i ++) {
            for (int j = 0; j < RAIL_STRUCT_WIDTH; j++) {
                for (int k = 0; k < RAIL_STRUCT_HEIGHT; k++) {
                    Material mat = RAIL_STRUCTURE[i][k][j];
                    if (xDirHasRail) {
                        wld.getBlockAt(xDirRailCoord - structHalfWidth + j, RAIL_HEIGHT + k, zStart + i).setType(mat, false);
                    }
                    if (zDirHasRail) {
                        wld.getBlockAt(xStart + i, RAIL_HEIGHT + k, zDirRailCoord - structHalfWidth + j).setType(mat, false);
                    }
                }
            }
        }

        // "station" when both directions meet. For now, it is a super simple logic.
        if (xDirHasRail && zDirHasRail) {
            for (int xOffset = -1; xOffset <= 1; xOffset ++) {
                for (int zOffset = -1; zOffset <= 1; zOffset ++) {
                    wld.getBlockAt(xDirRailCoord + xOffset, RAIL_HEIGHT, zDirRailCoord + zOffset).setType(Material.STONE, false);
                    wld.getBlockAt(xDirRailCoord + xOffset, RAIL_HEIGHT + 1, zDirRailCoord + zOffset).setType(Material.AIR, false);
                    wld.getBlockAt(xDirRailCoord + xOffset, RAIL_HEIGHT + 2, zDirRailCoord + zOffset).setType(Material.AIR, false);
                }
            }
        }
    }
}
