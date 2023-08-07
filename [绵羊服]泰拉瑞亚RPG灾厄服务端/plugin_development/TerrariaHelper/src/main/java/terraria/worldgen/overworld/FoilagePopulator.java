package terraria.worldgen.overworld;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.generator.BlockPopulator;
import terraria.util.WorldHelper;

import java.util.Random;

public class FoilagePopulator extends BlockPopulator {
    public static final double GRASS_GROW_CHANCE = 0.1;
    int getTreeAmount(Biome biome) {
        boolean test = false;
        if (test)
            return 4;
        switch (biome) {
            case FOREST:
            case TAIGA_COLD:
            case ICE_FLATS:
                return 12;
            case JUNGLE:
                return 16;
            case MESA:
                return 3;
            case MUSHROOM_ISLAND:
                return 6;
            default:
                return 0;
        }
    }
    @Override
    public void populate(World wld, Random rdm, Chunk chunk) {
        // grass
        for (int i = 0; i < 16; i ++)
            for (int j = 0; j < 16; j ++)
                if (Math.random() < GRASS_GROW_CHANCE) {
                    int Y = ((CraftChunk) chunk).getHandle().heightMap[j << 4 | i];
                    Block blockToGrow = chunk.getBlock(i, Y, j);
                    WorldHelper.attemptGrowPlantAt(blockToGrow, true);
                }
        // no trees if the chunk is very close to world spawn point
        if (Math.abs(chunk.getX()) > 3 || Math.abs(chunk.getZ()) > 3) {
            // Amount of trees
            int amount = getTreeAmount(wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16));
            if (amount <= 0) return;
            amount = rdm.nextInt(amount);
            for (int i = 1; i < amount; i++) {
                int X = rdm.nextInt(15);
                int Z = rdm.nextInt(15);
                int Y = ((CraftChunk) chunk).getHandle().heightMap[Z << 4 | X];
                // if the position is not covered by water, grow a tree.
                Block blockToGrow = chunk.getBlock(X, Y, Z);
                switch (blockToGrow.getType()) {
                    case WATER:
                    case STATIONARY_WATER:
                        break;
                    default:
                        WorldHelper.attemptGenerateTreeAt(blockToGrow);
                }
            }
        }
    }
}
