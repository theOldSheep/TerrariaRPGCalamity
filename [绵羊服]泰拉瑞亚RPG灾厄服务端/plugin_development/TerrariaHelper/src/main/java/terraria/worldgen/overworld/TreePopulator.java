package terraria.worldgen.overworld;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;

import java.util.Random;

public class TreePopulator extends BlockPopulator {
    void generateTree(Block blk, Random rdm) {
        Location loc = blk.getLocation();
        World wld = loc.getWorld();
        TreeType treeType;
        boolean isTall = rdm.nextDouble() < 0.2;
        double variance = rdm.nextDouble();
        switch (blk.getBiome()) {
            case FOREST:
                treeType = isTall ? TreeType.BIG_TREE : TreeType.TREE;
                break;
            case JUNGLE:
                if (variance < 0.25) treeType = TreeType.JUNGLE_BUSH;
                else treeType = isTall ? TreeType.JUNGLE : TreeType.SMALL_JUNGLE;
                break;
            case MESA:
                treeType = TreeType.ACACIA;
                break;
            case TAIGA_COLD:
                treeType = isTall ? TreeType.TALL_REDWOOD : TreeType.REDWOOD;
                break;
            case ICE_FLATS:
                treeType = isTall ? TreeType.TALL_BIRCH : TreeType.BIRCH;
                break;
            case MUSHROOM_ISLAND:
                if (variance < 0.25) treeType = TreeType.RED_MUSHROOM;
                else if (variance < 0.5) treeType = TreeType.BROWN_MUSHROOM;
                else treeType = TreeType.SWAMP;
                break;
            default:
                return;
        }
        wld.generateTree(loc, treeType);
    }
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
            for (Y = 175; !chunk.getBlock(X, Y, Z).getType().isSolid(); Y--); // Find the highest block of the (X,Z) coordinate chosen.
            generateTree(chunk.getBlock(X, Y, Z), rdm);
        }
    }
}
