package terraria.worldgen;

import terraria.TerrariaHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

public class WorldGenHelper {
    static final int OPT_THREADS = Math.max(TerrariaHelper.optimizationConfig.getInt("worldGen.opt.optThreads", 16), 1);
    static ExecutorService THREAD_POOL = Executors.newFixedThreadPool(OPT_THREADS);

    /**
     * Generic helper for 3D array generation with upsampling.
     */
    public static <T> T[][][] getChunkData(int dimX, int dimY, int dimZ, int sketchStep,
                                           Function<int[], T> fillFunction,
                                           Function<T[][][], T> interpolateFunction)
            throws ExecutionException, InterruptedException {

        int estX = Math.floorDiv(dimX, sketchStep) + (dimX % sketchStep == 0 ? 0 : 1);
        int estY = Math.floorDiv(dimY, sketchStep) + (dimY % sketchStep == 0 ? 0 : 1);
        int estZ = Math.floorDiv(dimZ, sketchStep) + (dimZ % sketchStep == 0 ? 0 : 1);

        T[][][] estimates = (T[][][]) new Object[estX][estY][estZ];
        fill3DArray(estimates, (info) ->
                fillFunction.apply(new int[]{info[0] * sketchStep, info[1] * sketchStep, info[2] * sketchStep}));

        T[][][] result = (T[][][]) new Object[dimX][dimY][dimZ];
        fill3DArray(result, (info) -> {
            // Check if all surrounding estimates are identical
            T sample = getConsensus(estimates, info, sketchStep);
            return (sample != null) ? sample : fillFunction.apply(info);
        });
        return result;
    }

    private static <T> T getConsensus(T[][][] estimates, int[] info, int sketchStep) {
        int eX = info[0] / sketchStep, eY = info[1] / sketchStep, eZ = info[2] / sketchStep;
        T first = estimates[eX][eY][eZ];
        for (int i = eX; i <= Math.min(eX + 1, estimates.length - 1); i++)
            for (int j = eY; j <= Math.min(eY + 1, estimates[0].length - 1); j++)
                for (int k = eZ; k <= Math.min(eZ + 1, estimates[0][0].length - 1); k++)
                    if (!estimates[i][j][k].equals(first)) return null;
        return first;
    }

    public static <T> void fill3DArray(T[][][] arr, Function<int[], T> fillFunction) throws InterruptedException, ExecutionException {
        int rows = arr.length, height = arr[0].length, cols = arr[0][0].length;
        List<Future<?>> futures = new ArrayList<>();
        int chunkSize = Math.max((rows * cols) / OPT_THREADS, 1);

        for (int i = 0; i < OPT_THREADS; i++) {
            int start = i * chunkSize, end = (i == OPT_THREADS - 1) ? rows * cols : start + chunkSize;
            futures.add(THREAD_POOL.submit(() -> {
                for (int s = start; s < end; s++) {
                    int x = s % rows, z = s / rows;
                    for (int y = 0; y < height; y++) arr[x][y][z] = fillFunction.apply(new int[]{x, y, z});
                }
            }));
        }
        for (Future<?> f : futures) f.get();
    }

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

//    /**
//     * Helper function to fill up an array with the calculation as specified.
//     */
//    public static <T> void fill3DArray(T[][][] arr, Function<int[], T> fillFunction) throws InterruptedException, ExecutionException {
//        int rows = arr.length;
//        int height = arr[0].length;
//        int cols = arr[0][0].length;
//
//        List<Future<?>> futures = new ArrayList<>();
//
//        // split the tasks into sections, where each section is vertical.
//        int maxSections = rows * cols;
//        // chunk size refer to the chunk for the thread, not the chunk in the world.
//        int chunkSize = Math.max(maxSections / OPT_THREADS, 1);
//
//        for (int i = 0; i < OPT_THREADS; i++) {
//            int startSection = i * chunkSize;
//            int endSection = (i == OPT_THREADS - 1) ? maxSections : startSection + chunkSize;
//
//            Runnable task = () -> {
//                // increase x, then "carry digit" to z
//                int[] posInfo = new int[]{startSection % rows, 0, startSection / rows};
//                for (int sectionInd = startSection; sectionInd < endSection; sectionInd++) {
//                    for (int h = 0; h < height; h++) {
//                        posInfo[1] = h;
//                        arr[posInfo[0]][h][posInfo[2]] = fillFunction.apply(posInfo);
//                    }
//                    // increment
//                    if (++posInfo[0] >= rows) {
//                        posInfo[0] = 0;
//                        posInfo[2]++;
//                    }
//                }
//            };
//
//            futures.add(THREAD_POOL.submit(task));
//        }
//
//        // Wait for all tasks to complete
//        for (Future<?> future : futures) {
//            future.get();
//        }
//    }
}