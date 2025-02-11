package com.beckett.grading.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AsyncUtils {

    private AsyncUtils() {}

    public static void perform(List<Runnable> actions) {
        actions.stream().parallel().forEach(AsyncUtils::perform);
    }

    public static void perform(Runnable... actions) {
        Arrays.stream(actions).parallel().forEach(AsyncUtils::perform);
    }

    public static void perform(Runnable action) {
        // Create an ExecutorService with a cached thread pool
        try (ExecutorService executorService = Executors.newCachedThreadPool()) {
            try {
                executorService.submit(action);
            } finally {
                // Shutdown the executor service gracefully
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.info("Executor did not terminate in the specified time.");
                        executorService.shutdownNow(); // Force shutdown
                    }
                } catch (InterruptedException e) {
                    log.error("Executor interrupted during shutdown.");
                    executorService.shutdownNow(); // Force shutdown
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
            }
        }
    }
}
