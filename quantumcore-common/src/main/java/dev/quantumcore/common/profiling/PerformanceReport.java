package dev.quantumcore.common.profiling;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ThreadSafe
public final class PerformanceReport {
    private final Path statsPath;

    public PerformanceReport(Path cacheRoot) {
        this.statsPath = cacheRoot.resolve("stats.json");
    }

    public void write(double loadSeconds, double baselineSeconds, StageProfiler.Snapshot snapshot) {
        try {
            Files.createDirectories(statsPath.getParent());
            double saved = Math.max(0.0, baselineSeconds - loadSeconds);
            String json = """
                {
                  "loadSeconds": %.3f,
                  "baselineSeconds": %.3f,
                  "savedSeconds": %.3f,
                  "atlasFromCache": %s,
                  "checkpointRestored": %s,
                  "cachedMods": %d
                }
                """.formatted(loadSeconds, baselineSeconds, saved, snapshot.atlasLoadedFromCache(), snapshot.checkpointRestored(), snapshot.cachedModsCount());
            Files.writeString(statsPath, json);
        } catch (IOException ignored) {
        }
    }
}
