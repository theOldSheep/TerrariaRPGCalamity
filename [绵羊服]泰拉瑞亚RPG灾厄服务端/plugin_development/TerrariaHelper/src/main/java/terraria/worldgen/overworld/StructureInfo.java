package terraria.worldgen.overworld;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import terraria.TerrariaHelper;
import terraria.util.WorldHelper;

import java.util.ArrayDeque;
import java.util.HashMap;

public class StructureInfo {
    // the interface for structure operation, ready to be implemented with lambda expressions
    interface StructureOperation {
        public void operate();
    }
    private static final long MAX_NANO_SECOND_SPIKE = TerrariaHelper.settingConfig.getInt("structureNanoSecond.structureNanoSecond", 1000000);
    // block information
    Material trueMat, falseMat;
    byte trueDt, falseDt;
    // all blocks: it remembers all blocks registered in the past history.
    private final HashMap<Block, Boolean> allBlocks;
    // this is cleared after each section placement, so that no redundant work would be done.
    private final HashMap<Block, Boolean> blocksPendingPlacement;
    // the operations that are waiting to be processed later.
    private final ArrayDeque<StructureOperation> operations;

    public StructureInfo(Material trueMat, Material falseMat, byte trueDt, byte falseDt) {
        // block placement info
        this.trueMat = trueMat;
        this.falseMat = falseMat;
        this.trueDt = trueDt;
        this.falseDt = falseDt;
        // other internal information
        allBlocks = new HashMap<>();
        blocksPendingPlacement = new HashMap<>();
        operations = new ArrayDeque<>();
    }

    // methods to help spread out operations over time
    public void planOperation(StructureOperation func) {
        operations.addLast(func);
    }

    // performs all planned operations, spreading those tasks out.
    public void performOperations() {
        // just in case single blocks are set by the user; they will still be placed.
        planSetBlocks();

        long nanoSecond = System.nanoTime();
        while (!operations.isEmpty()) {
            // run the next operation
            StructureOperation operation = operations.pollFirst();
            operation.operate();
            // return and schedule the rest for later
            long currNS = System.nanoTime();
            if (currNS >= nanoSecond + MAX_NANO_SECOND_SPIKE) {
                Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), this::performOperations, 1);
                return;
            }
        }
    }

    // set a single block, this is a helper function.
    protected void registerSingleBlock(Block blk, boolean boolReg, boolean override) {
        if (!override && allBlocks.containsKey(blk))
            return;
        allBlocks.put(blk, boolReg);
        blocksPendingPlacement.put(blk, boolReg);
    }

    // to externally set a single block, call this; in this way, the proper operation order can be preserved.
    public void planRegisterSingleBlock(Block blk, boolean boolReg, boolean override) {
        planOperation(() -> {
            registerSingleBlock(blk, boolReg, override);
        });
    }

    public void planRegisterBlockPlane(World wld, int centerX, int y, int centerZ, int radius, boolean boolReg, boolean override) {
        planRegisterBlockPlane(wld, centerX - radius, centerZ - radius, y,
                centerX + radius, centerZ + radius, boolReg, override);
    }

    public void planRegisterBlockPlane(World wld, int minX, int minZ, int y, int maxX, int maxZ, boolean boolReg, boolean override) {
        planOperation(() -> {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block blk = wld.getBlockAt(x, y, z);
                    registerSingleBlock(blk, boolReg, override);
                }
            }
        });
    }

    public void planSetBlocks() {
        planOperation(() -> {
            for (Block b : blocksPendingPlacement.keySet()) {
                // break trees/grass
                switch (b.getType()) {
                    case LOG:
                    case LOG_2:
                    case LEAVES:
                    case LEAVES_2:
                        WorldHelper.attemptDestroyVegetation(b, true, false);
                        break;
                    case DIRT:
                    case GRASS:
                        WorldHelper.attemptDestroyVegetation(b.getRelative(BlockFace.UP), true, false);
                }

                if (blocksPendingPlacement.get(b)) {
                    b.setType(trueMat, false);
                    if (trueDt != 0)
                        b.setData(trueDt);
                } else {
                    b.setType(falseMat, false);
                    if (falseDt != 0)
                        b.setData(falseDt);
                }
            }
            // clear the blocks pending placement afterward!
            blocksPendingPlacement.clear();
        });
    }

    public void planRegisterBlockPlanarCircle(World wld, int centerX, int y, int centerZ, int radius, boolean boolReg, boolean override) {
        planOperation(() -> {
            double radSqr = radius * radius + 1e-3;
            double[] offsetSqr = new double[radius + 1];
            for (int i = 0; i <= radius; i++)
                offsetSqr[i] = i * i;
            // do not attempt to "optimize" the loop - it will cause funny-looking circles.
            for (int xOffset = 0; xOffset <= radius; xOffset++) {
                for (int zOffset = 0; zOffset <= radius; zOffset++) {
                    if (offsetSqr[xOffset] + offsetSqr[zOffset] > radSqr)
                        continue;

                    registerSingleBlock(wld.getBlockAt(centerX + xOffset, y, centerZ + zOffset), boolReg, override);
                    // flipped z
                    if (zOffset != 0)
                        registerSingleBlock(wld.getBlockAt(centerX + xOffset, y, centerZ - zOffset), boolReg, override);
                    // flipped x
                    if (xOffset != 0)
                        registerSingleBlock(wld.getBlockAt(centerX - xOffset, y, centerZ + zOffset), boolReg, override);
                    // flipped both
                    if (xOffset != 0 && zOffset != 0)
                        registerSingleBlock(wld.getBlockAt(centerX - xOffset, y, centerZ - zOffset), boolReg, override);
                }
            }
        });
    }
}