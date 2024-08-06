package terraria.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

public class WorldGenHelper {
    /*
     * Helper function to fill up an array with the calculation as specified.
     */
    public static <T> void fill3DArray(T[][][] arr, ExecutorService threadPool, int numThreads,
                                       Function<int[], T> fillFunction) throws InterruptedException, ExecutionException {
        int rows = arr.length;
        int height = arr[0].length;
        int cols = arr[0][0].length;

        List<Future<?>> futures = new ArrayList<>();

        // split the tasks into sections, where each section is vertical.
        int maxSections = rows * cols;
        // chunk size refer to the chunk for the thread, not the chunk in the world.
        int chunkSize = maxSections / numThreads;

        for (int i = 0; i < numThreads; i++) {
            int startSection = i * chunkSize;
            int endSection = (i == numThreads - 1) ? maxSections : startSection + chunkSize;

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

            futures.add(threadPool.submit(task));
        }

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            future.get();
        }
    }
}
