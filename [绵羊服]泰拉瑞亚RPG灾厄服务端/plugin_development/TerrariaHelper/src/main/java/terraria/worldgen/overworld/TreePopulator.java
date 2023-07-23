package terraria.worldgen.overworld;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import terraria.util.WorldHelper;

import java.util.Random;

public class TreePopulator extends BlockPopulator {
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
        // Amount of trees
        int amount = getTreeAmount(wld.getBiome(chunk.getX() * 16, chunk.getZ() * 16));
        if (amount <= 0) return;
        amount = rdm.nextInt(amount);
        for (int i = 1; i < amount; i++) {
            int X = rdm.nextInt(15);
            int Z = rdm.nextInt(15);
            int Y;
            // Find the highest block of the (X,Z) coordinate chosen.
            for (Y = 175; !chunk.getBlock(X, Y, Z).getType().isSolid(); Y--);
            WorldHelper.attemptGenerateTree( chunk.getBlock(X, Y + 1, Z) );
        }
    }
}
