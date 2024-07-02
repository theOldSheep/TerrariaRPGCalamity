package terraria.worldgen.overworld;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.generator.BlockPopulator;
import terraria.TerrariaHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import javax.swing.plaf.TableHeaderUI;
import java.util.HashMap;
import java.util.Random;

public class FoliagePopulator extends BlockPopulator {
    public static final double GRASS_GROW_CHANCE = 0.1;
    double getTreeAmount(double temp, double moisture) {
        double result = 6d;
        // dry+hot=desert, dry+cold=tundra(even less tree?), moist+hot=jungle, moist+cold=tundra
        result += Math.min(moisture * 16 * Math.abs(temp), 4);
        return Math.max(result, 2);
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
            OverworldBiomeGenerator.BiomeFeature biomeFeature = OverworldBiomeGenerator.getBiomeFeature(chunk.getX() * 16, chunk.getZ() * 16);
            double temp = biomeFeature.features[OverworldBiomeGenerator.BiomeFeature.TEMPERATURE];
            double moisture = biomeFeature.features[OverworldBiomeGenerator.BiomeFeature.HUMIDITY];

            double amount = getTreeAmount( temp, moisture );
            // slightly randomize the amount generated
            if (amount > 0) {
                amount /= 2;
                amount = amount + rdm.nextDouble() * amount;
            }

            int finalAmount = MathHelper.randomRound( amount );
            // prevent chunks loading each other aggressively because of tree generation
            Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), () -> {
                HashMap<WorldHelper.LeafShape, Double>[] treeStylePref = null;
                for (int i = 1; i < finalAmount; i++) {
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
                            treeStylePref = WorldHelper.attemptGenerateTreeAt(blockToGrow, temp, moisture, treeStylePref);
                    }
                }
            }, 1);
        }
    }
}
