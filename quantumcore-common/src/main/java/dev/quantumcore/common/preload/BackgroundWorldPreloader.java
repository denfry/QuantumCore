package dev.quantumcore.common.preload;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.profiling.StageProfiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ForkJoinPool;

@ThreadSafe
public final class BackgroundWorldPreloader {
    private final boolean enabled;
    private final StageProfiler profiler;
    private final ForkJoinPool pool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

    public BackgroundWorldPreloader(boolean enabled, StageProfiler profiler) {
        this.enabled = enabled;
        this.profiler = profiler;
    }

    public void preloadRecentWorld(Path savesDir) {
        if (!enabled || !Files.isDirectory(savesDir)) {
            return;
        }
        pool.execute(() -> {
            long start = System.nanoTime();
            try {
                Path latest = Files.list(savesDir)
                    .filter(Files::isDirectory)
                    .max(Comparator.comparingLong(this::lastModified))
                    .orElse(null);
                if (latest == null) {
                    return;
                }
                Path levelDat = latest.resolve("level.dat");
                if (Files.exists(levelDat)) {
                    Files.readAllBytes(levelDat);
                }
                profiler.onModProcessed("world_preload", System.nanoTime() - start);
            } catch (Exception ignored) {
            }
        });
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }
}
