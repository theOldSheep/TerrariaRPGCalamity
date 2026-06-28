package terraria.worldgen;

import terraria.TerrariaHelper;

import java.lang.reflect.Array;
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
                                           Class<T> clazz, Function<int[], T> fillFunction,
                                           boolean timingEnabled, long[] roughTimingHolder, long[] fineFillTimingHolder, int timingIdx)
            throws ExecutionException, InterruptedException {
        long timing = System.nanoTime();

        int estX = Math.floorDiv(dimX, sketchStep) + (dimX % sketchStep == 0 ? 0 : 1);
        int estY = Math.floorDiv(dimY, sketchStep) + (dimY % sketchStep == 0 ? 0 : 1);
        int estZ = Math.floorDiv(dimZ, sketchStep) + (dimZ % sketchStep == 0 ? 0 : 1);

        T[][][] estimates = createGeneric3DArray(clazz, estX, estY, estZ);
        fill3DArray(estimates, (info) ->
                fillFunction.apply(new int[]{info[0] * sketchStep, info[1] * sketchStep, info[2] * sketchStep}));

        if (timingEnabled) {
            roughTimingHolder[timingIdx] += (System.nanoTime() - timing);
            timing = System.nanoTime();
        }

        T[][][] result = createGeneric3DArray(clazz, dimX, dimY, dimZ);
        fill3DArray(result, (info) -> {
            // Check if all surrounding estimates are identical
            T sample = getConsensus(estimates, info, sketchStep);
            return (sample != null) ? sample : fillFunction.apply(info);
        });

        if (timingEnabled) {
            fineFillTimingHolder[timingIdx] += (System.nanoTime() - timing);
        }
        return result;
    }

    public static <T> T[][][] createGeneric3DArray(Class<T> clazz, int x, int y, int z) {
        // This creates an array of arrays of arrays
        Object array = Array.newInstance(clazz, x, y, z);
        return (T[][][]) array;
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