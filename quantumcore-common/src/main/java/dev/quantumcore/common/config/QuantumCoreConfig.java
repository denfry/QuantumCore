package dev.quantumcore.common.config;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public record QuantumCoreConfig(
    boolean atlasCacheEnabled,
    boolean checkpointEnabled,
    boolean incrementalCacheEnabled,
    boolean surgicalCacheEnabled,
    boolean memoryDedupEnabled,
    boolean gcPoolEnabled,
    boolean preloaderEnabled,
    DashboardConfig dashboard,
    int checkpointFormat,
    int atlasFormat
) {
    public static QuantumCoreConfig defaults() {
        return new QuantumCoreConfig(
            true,
            false,
            true,
            true,
            true,
            true,
            true,
            DashboardConfig.defaults(),
            1,
            1
        );
    }
}
