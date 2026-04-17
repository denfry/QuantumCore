package dev.quantumcore.common.runtime;

import dev.quantumcore.common.cache.GpuAtlasCache;
import dev.quantumcore.common.checkpoint.JvmCheckpoint;
import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.config.ConfigLoader;
import dev.quantumcore.common.config.QuantumCoreConfig;
import dev.quantumcore.common.incremental.IncrementalCacheStore;
import dev.quantumcore.common.memory.MemoryMonitor;
import dev.quantumcore.common.preload.BackgroundWorldPreloader;
import dev.quantumcore.common.profiling.StageProfiler;

import java.nio.file.Path;

@ThreadSafe
public final class QuantumCoreRuntime {
    private final QuantumCoreConfig config;
    private final StageProfiler profiler;
    private final GpuAtlasCache atlasCache;
    private final JvmCheckpoint checkpoint;
    private final IncrementalCacheStore incrementalCacheStore;
    private final MemoryMonitor memoryMonitor;
    private final BackgroundWorldPreloader preloader;

    public QuantumCoreRuntime(Path gameDir) {
        Path configPath = gameDir.resolve("config").resolve("quantumcore.toml");
        this.config = ConfigLoader.load(configPath);
        this.profiler = new StageProfiler(gameDir.resolve("logs"));
        this.atlasCache = new GpuAtlasCache(gameDir.resolve(".cache").resolve("quantumcore"), config.atlasCacheEnabled());
        this.checkpoint = new JvmCheckpoint(gameDir.resolve(".cache").resolve("quantumcore"), config.checkpointEnabled());
        this.incrementalCacheStore = new IncrementalCacheStore(gameDir.resolve(".cache").resolve("quantumcore"), config.incrementalCacheEnabled());
        this.memoryMonitor = new MemoryMonitor(config.memoryDedupEnabled());
        this.preloader = new BackgroundWorldPreloader(config.preloaderEnabled(), profiler);
    }

    public QuantumCoreConfig config() {
        return config;
    }

    public StageProfiler profiler() {
        return profiler;
    }

    public GpuAtlasCache atlasCache() {
        return atlasCache;
    }

    public JvmCheckpoint checkpoint() {
        return checkpoint;
    }

    public IncrementalCacheStore incrementalCacheStore() {
        return incrementalCacheStore;
    }

    public MemoryMonitor memoryMonitor() {
        return memoryMonitor;
    }

    public BackgroundWorldPreloader preloader() {
        return preloader;
    }
}
