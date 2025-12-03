package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import terraria.TerrariaHelper;

import java.util.Random;

public class RailPopulator extends BlockPopulator {
    static Material[][][] RAIL_STRUCTURE;
    static final int RAIL_HEIGHT = TerrariaHelper.optimizationConfig.getInt("worldGen.params.railYCoordinate", 100);
    static final int RAIL_STRUCT_WIDTH = 3, RAIL_STRUCT_HEIGHT = 5;
    static {
        Material[][] basicShape = {
                {Material.AIR, Material.AIR, Material.AIR},
                {Material.AIR, Material.AIR, Material.AIR},
                {Material.AIR, Material.AIR, Material.AIR},
                {Material.REDSTONE_TORCH_ON, Material.POWERED_RAIL, Material.REDSTONE_TORCH_ON},
                {Material.WOOD, Material.STONE, Material.WOOD},};
        RAIL_STRUCTURE = new Material[16][RAIL_STRUCT_HEIGHT][RAIL_STRUCT_WIDTH];
        // when copying, flip the actual shape for efficient later use
        for (int i = 0; i < 16; i ++) {
            for (int j = 0; j < RAIL_STRUCT_HEIGHT; j ++) {
                System.arraycopy(basicShape[RAIL_STRUCT_HEIGHT - 1 - j], 0, RAIL_STRUCTURE[i][j], 0, RAIL_STRUCT_WIDTH);
            }
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
        // do not spawn rails if rail height is negative
        if (RAIL_HEIGHT < 0)
            return;

        boolean xDirHasRail = shouldChunkContainRail(chunk.getX());
        boolean zDirHasRail = shouldChunkContainRail(chunk.getZ());
        // no rail: early exit
        if (! (xDirHasRail || zDirHasRail))
            return;

        int xDirRailCenterCoord = getRailCoord(chunk.getX());
        int zDirRailCenterCoord = getRailCoord(chunk.getZ());
        int xStart = chunk.getX() << 4;
        int zStart = chunk.getZ() << 4;
        // i: along rail direction; j: horizontal orthogonal; k: vertical
        int structHalfWidth = RAIL_STRUCT_WIDTH / 2;
        for (int i = 0; i < 16; i ++) {
            // x and z dir height
            int xDirCoord = zStart + i;
            int zDirCoord = xStart + i;
            int xDirHt = 0, zDirHt = 0;
            if (Math.abs(xDirCoord) > 45 || Math.abs(zDirCoord) > 45) {
                xDirHt = Math.min(Math.max(100 + Math.abs(xDirCoord) + Math.abs(xDirRailCenterCoord) - 60, 100), RAIL_HEIGHT);
                zDirHt = Math.min(Math.max(100 + Math.abs(zDirCoord) + Math.abs(zDirRailCenterCoord ) - 60, 100), RAIL_HEIGHT);
            }
            // place blocks
            for (int j = 0; j < RAIL_STRUCT_WIDTH; j++) {
                for (int k = 0; k < RAIL_STRUCT_HEIGHT; k++) {
                    Material mat = RAIL_STRUCTURE[i][k][j];
                    if (xDirHasRail && xDirHt >= 100) {
                        wld.getBlockAt(xDirRailCenterCoord - structHalfWidth + j, xDirHt + k, xDirCoord).setType(mat, false);
                    }
                    if (zDirHasRail && zDirHt >= 100) {
                        wld.getBlockAt(zDirCoord, zDirHt + k, zDirRailCenterCoord - structHalfWidth + j).setType(mat, false);
                    }
                }
            }
        }

        // "station" when both directions meet. For now, it is a super simple logic.
        if (xDirHasRail && zDirHasRail && !(chunk.getX() == 0 && chunk.getZ() == 0)) {
            for (int xOffset = -1; xOffset <= 1; xOffset ++) {
                for (int zOffset = -1; zOffset <= 1; zOffset ++) {
                    wld.getBlockAt(xDirRailCenterCoord + xOffset, RAIL_HEIGHT, zDirRailCenterCoord + zOffset).setType(Material.STONE, false);
                    wld.getBlockAt(xDirRailCenterCoord + xOffset, RAIL_HEIGHT + 1, zDirRailCenterCoord + zOffset).setType(Material.AIR, false);
                    wld.getBlockAt(xDirRailCenterCoord + xOffset, RAIL_HEIGHT + 2, zDirRailCenterCoord + zOffset).setType(Material.AIR, false);
                }
            }
        }
    }
}
