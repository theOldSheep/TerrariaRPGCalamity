package terraria.worldgen;

import org.bukkit.block.Biome;
import terraria.TerrariaHelper;
import terraria.worldgen.overworld.OverworldBiomeGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;

public class WorldGenHelper {
    static final int OPT_THREADS = Math.max( TerrariaHelper.optimizationConfig.getInt("worldGen.opt.optThreads", 16), 1);
    static ExecutorService THREAD_POOL = Executors.newFixedThreadPool(OPT_THREADS);

    /**
     * Helper function to fill a 16x16x256 boolean flag map for cave/stone flags
     * Optimized with thread and upsampling
     * WARNING: j=0 corresponds to y=1, skip bedrock at y=0.
     */
    public static Boolean[][][] getChunkFlag(int sketchStep, Function<int[], Boolean> fillFunction,
                                            boolean timingEnabled, long[] roughTimingHolder, int timingIdx) throws ExecutionException, InterruptedException {
        long timing = System.nanoTime();
        // if n-1 is multiple of CAVE_ROUGH_SKETCH_DIAMETER, then nth position will be an estimate - no need for expansion
        int estimationWidth = Math.floorDiv(16, sketchStep) + (15 % sketchStep == 0 ? 0 : 1);
        int estimationHeight = Math.floorDiv(256, sketchStep) + (255 % sketchStep == 0 ? 0 : 1);
        if (16 % sketchStep != 0)
            estimationWidth++;
        if (256 % sketchStep != 0)
            estimationHeight++;

        // create estimates
        Boolean[][][] estimates = new Boolean[estimationWidth][estimationHeight][estimationWidth];
        fill3DArray(estimates, (info) ->
            fillFunction.apply(new int[]{info[0] * sketchStep, info[1] * sketchStep, info[2] * sketchStep}));

        if (timingEnabled) {
            roughTimingHolder[timingIdx] += (System.nanoTime() - timing);
        }

        // setup actual results
        Boolean[][][] result = new Boolean[16][255][16];
        WorldGenHelper.fill3DArray(result, (info) -> {
            byte upsampleResult = upsampleResult(estimates, info, sketchStep);
            if (upsampleResult == 1) {
                return true;
            }
            if (upsampleResult == -1) {
                return false;
            }
            // upsample needed - estimates do not agree
            return fillFunction.apply(info);
        });

        return result;
    }

    private static byte upsampleResult(Boolean[][][] estimates, int[] info, int sketchStep) {
        int estimateX = Math.floorDiv(info[0], sketchStep);
        int estimateY = Math.floorDiv(info[1], sketchStep);
        int estimateZ = Math.floorDiv(info[2], sketchStep);
        int phaseX = info[0] % sketchStep;
        int phaseY = info[1] % sketchStep;
        int phaseZ = info[2] % sketchStep;

        boolean allFalse = true, allTrue = true;
        // for phase = 0 (i.e. on a "planar" face / edge / vertex of estimation cube, do not check further
        for (int i = estimateX; i <= estimateX + (phaseX == 0 ? 0 : 1); i ++)
            for (int j = estimateY; j <= estimateY + (phaseY == 0 ? 0 : 1); j ++)
                for (int k = estimateZ; k <= estimateZ + (phaseZ == 0 ? 0 : 1); k ++)
                    if (estimates[i][j][k]) allFalse = false;
                    else allTrue = false;
        if (allFalse) return -1;
        if (allTrue) return 1;
        return 0;
    }

    /**
     * Helper function to fill up an array with the calculation as specified.
     */
    public static <T> void fill3DArray(T[][][] arr, Function<int[], T> fillFunction) throws InterruptedException, ExecutionException {
        int rows = arr.length;
        int height = arr[0].length;
        int cols = arr[0][0].length;

        List<Future<?>> futures = new ArrayList<>();

        // split the tasks into sections, where each section is vertical.
        int maxSections = rows * cols;
        // chunk size refer to the chunk for the thread, not the chunk in the world.
        int chunkSize = Math.max(maxSections / OPT_THREADS, 1);

        for (int i = 0; i < OPT_THREADS; i++) {
            int startSection = i * chunkSize;
            int endSection = (i == OPT_THREADS - 1) ? maxSections : startSection + chunkSize;

            Runnable task = () -> {
                // increase x, then "carry digit" to z
                int[] posInfo = new int[]{startSection % rows, 0, startSection / rows};
                for (int sectionInd = startSection; sectionInd < endSection; sectionInd++) {
                    for (int h = 0; h < height; h++) {
                        posInfo[1] = h;
                        arr[posInfo[0]][h][posInfo[2]] = fillFunction.apply(posInfo);
                    }
                    // increment
                    if (++posInfo[0] >= rows) {
                        posInfo[0] = 0;
                        posInfo[2]++;
                    }
                }
            };

            futures.add(THREAD_POOL.submit(task));
        }

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            future.get();
        }
    }
}
