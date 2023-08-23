package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;
import terraria.TerrariaHelper;

import java.util.Random;

public class OverworldBlockGenericPopulator extends BlockPopulator {
    // this block populator is the last step to set up the vast majority of the solid blocks within a chunk (i.e. stained terracotta)
    @Override
    public void populate(World wld, Random rdm, Chunk chunk) {
        int startX = chunk.getX() * 16, startZ = chunk.getZ() * 16;
        // TODO: implement underground desert, sunken sea and abyss landscape
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 16; j++) {
                int blockX = startX + i, blockZ = startZ + j;
                Biome biome = OverworldBiomeGenerator.getBiome(TerrariaHelper.worldSeed, blockX, blockZ);
                // additional setup for terracotta color etc.
                for (int y = 1; y < 255; y++) {
                    Block currBlock = chunk.getBlock(i, y, j);
                    switch (currBlock.getType()) {
                        case SAND:
                            switch (biome) {
                                case COLD_BEACH:
                                case DEEP_OCEAN:
                                case FROZEN_OCEAN:
                                    currBlock.setData((byte) 1);
                                    break;
                            }
                            break;
                        case STAINED_CLAY:
                            switch (biome) {
                                case DESERT: // sunken sea : blue stained clay (not spawned in surface desert)
                                    currBlock.setData((byte) 11);
                                    break;
                                case ICE_FLATS: // hallow : white stained clay
                                    currBlock.setData((byte) 0);
                                    break;
                                case MESA: // astral infection : black stained clay
                                    currBlock.setData((byte) 15);
                                    break;
                                case MUSHROOM_ISLAND: // corruption : grey stained clay
                                    currBlock.setData((byte) 7);
                                    break;
                            }
                            break;
                        case PRISMARINE:
                            // very bottom: void stone
                            if (y < 100)
                                currBlock.setData((byte) 2);
                            // other parts of abyss:
                            else
                                currBlock.setData((byte) 1);
                            break;
                        case DIRT:
                            switch (biome) {
                                case ICE_FLATS: // hallow : podzol (surface)
                                    if (!currBlock.getRelative(BlockFace.UP).getType().isSolid())
                                        currBlock.setData((byte) 2);
                                    break;
                                case MESA: // astral infection : coarse_dirt
                                    currBlock.setData((byte) 1);
                                    break;
                            }
                            break;
                    }
                }
            }
    }
}
