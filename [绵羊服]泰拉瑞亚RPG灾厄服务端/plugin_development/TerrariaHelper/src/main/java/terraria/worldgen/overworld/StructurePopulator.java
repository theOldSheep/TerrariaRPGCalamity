package terraria.worldgen.overworld;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;

import java.util.Random;

public class StructurePopulator extends BlockPopulator {
    boolean isSurface;

    public StructurePopulator(boolean surfaceOrUnderground) {
        super();
        this.isSurface = surfaceOrUnderground;
    }

    protected static void createSpawnShelter(World wld) {
        int radius = 24;
        for (int i = -radius; i <= radius; i ++) {
            for (int j = -radius; j <= radius; j ++) {
                wld.getBlockAt(i, OverworldChunkGenerator.LAND_HEIGHT - 1, j).setType(Material.GRASS);
            }
            wld.getBlockAt(i, OverworldChunkGenerator.LAND_HEIGHT, radius).setType(Material.FENCE);
            wld.getBlockAt(i, OverworldChunkGenerator.LAND_HEIGHT, -radius).setType(Material.FENCE);
            wld.getBlockAt(radius, OverworldChunkGenerator.LAND_HEIGHT, i).setType(Material.FENCE);
            wld.getBlockAt(-radius, OverworldChunkGenerator.LAND_HEIGHT, i).setType(Material.FENCE);
        }
    }

    @Override
    public void populate(World wld, Random rdm, Chunk chunk) {
        int chunkX = chunk.getX(), chunkZ = chunk.getZ();
        if (isSurface && chunkX == 0 && chunkZ == 0) {
            createSpawnShelter(wld);
        }
    }
}
