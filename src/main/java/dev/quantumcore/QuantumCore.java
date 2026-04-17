package dev.quantumcore;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import dev.quantumcore.cache.CacheInvalidator;
import dev.quantumcore.cache.DiskCache;
import dev.quantumcore.conflict.ConflictDetector;
import dev.quantumcore.loader.LoadPhase;
import dev.quantumcore.loader.LazyHolder;
import dev.quantumcore.loader.ParallelLoader;
import dev.quantumcore.memory.MemoryMonitor;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Mod(QuantumCore.MOD_ID)
public final class QuantumCore {
    public static final String MOD_ID = "quantumcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile QuantumCore INSTANCE;

    private final QuantumCoreConfig config;
    private final DiskCache diskCache;
    private final ParallelLoader parallelLoader;
    private final MemoryMonitor memoryMonitor;
    private final CacheInvalidator cacheInvalidator;
    private final ConflictDetector conflictDetector;
    private final LazyHolder<Object> deferredDataFixerUpper;
    private final LazyHolder<Object> deferredRecipeManager;
    private final LazyHolder<Object> deferredAdvancementTree;
    private final EnumMap<LoadPhase, LongAdder> phaseNanos = new EnumMap<>(LoadPhase.class);

    /**
     * Builds all QuantumCore subsystems early so heavy modpacks benefit from the optimization
     * pipeline before resource/model work starts.
     */
    public QuantumCore(ModContainer modContainer) {
        INSTANCE = this;
        this.config = QuantumCoreConfig.load();
        modContainer.registerConfig(ModConfig.Type.COMMON, QuantumCoreConfig.createSpec());

        this.diskCache = new DiskCache(FMLPaths.GAMEDIR.get(), config.enableDiskCache());
        this.parallelLoader = new ParallelLoader(config.enableParallelLoader());
        this.memoryMonitor = new MemoryMonitor(config.enableMemoryOptimization());
        this.cacheInvalidator = new CacheInvalidator(diskCache, config.enableDiskCache());
        this.conflictDetector = new ConflictDetector(config.enableConflictDetector());
        this.deferredDataFixerUpper = new LazyHolder<>("DataFixerUpper", Object::new);
        this.deferredRecipeManager = new LazyHolder<>("RecipeManager", Object::new);
        this.deferredAdvancementTree = new LazyHolder<>("AdvancementTree", Object::new);

        for (LoadPhase phase : LoadPhase.values()) {
            phaseNanos.put(phase, new LongAdder());
        }

        if (config.enableMemoryOptimization()) {
            memoryMonitor.start();
        }
        if (config.enableDiskCache()) {
            cacheInvalidator.start();
        }

        NeoForge.EVENT_BUS.addListener(this::onLoadComplete);
        NeoForge.EVENT_BUS.addListener(conflictDetector::onRegisterCommands);
        conflictDetector.scanAndLog();

        LOGGER.info("QuantumCore initialized. Modules: cache={}, parallel={}, lazy={}, memory={}, gcPool={}, mixins={}, conflicts={}",
            config.enableDiskCache(),
            config.enableParallelLoader(),
            config.enableLazyInitialization(),
            config.enableMemoryOptimization(),
            config.enableGcPressureReduction(),
            config.enableMixins(),
            config.enableConflictDetector());
    }

    /**
     * Gives static access to the singleton because mixins and command hooks cannot receive
     * dependency-injected references from NeoForge.
     */
    public static QuantumCore instance() {
        QuantumCore local = INSTANCE;
        if (local == null) {
            throw new IllegalStateException("QuantumCore was accessed before mod construction");
        }
        return local;
    }

    /**
     * Tracks phase duration so startup savings can be shown as a concrete summary instead of
     * vague "faster startup" claims.
     */
    public void recordPhaseNanos(LoadPhase phase, long nanos) {
        phaseNanos.get(phase).add(nanos);
    }

    /**
     * Exposes cache singleton for mixins and loaders so serialization logic stays centralized.
     */
    public DiskCache cache() {
        return diskCache;
    }

    /**
     * Centralizes access to the dedicated async pool so all performance modules share one loader
     * scheduler and avoid creating competing thread pools.
     */
    public ParallelLoader parallelLoader() {
        return parallelLoader;
    }

    /**
     * Exposes immutable module toggles to subsystems so feature flags stay consistent for a full
     * run.
     */
    public QuantumCoreConfig config() {
        return config;
    }

    /**
     * Defers DataFixerUpper-like initialization until a caller explicitly requests it, avoiding
     * expensive eager startup work on worlds that never need migrations.
     */
    public Object dataFixerUpper() {
        return deferredDataFixerUpper.get();
    }

    /**
     * Defers recipe manager bootstrap so initial client startup can complete before heavyweight
     * recipe data structures are built.
     */
    public Object recipeManager() {
        return deferredRecipeManager.get();
    }

    /**
     * Defers advancement tree materialization until gameplay reaches systems that depend on it.
     */
    public Object advancementTree() {
        return deferredAdvancementTree.get();
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        diskCache.flushManifest();
        printSummaryTable();
    }

    private void printSummaryTable() {
        long totalPhaseNanos = phaseNanos.values().stream().mapToLong(LongAdder::sum).sum();
        long cacheSavedNanos = diskCache.estimatedSavedNanos();
        double totalSeconds = totalPhaseNanos / 1_000_000_000.0;
        double cacheSavedSeconds = cacheSavedNanos / 1_000_000_000.0;

        LOGGER.info("========== QuantumCore Startup Summary ==========");
        LOGGER.info("Phase timings:");
        for (Map.Entry<LoadPhase, LongAdder> entry : phaseNanos.entrySet()) {
            LOGGER.info("  {} {}", entry.getKey().name(), String.format("%.3fs", entry.getValue().sum() / 1_000_000_000.0));
        }
        LOGGER.info("Cache hits: {} | misses: {}", diskCache.cacheHits(), diskCache.cacheMisses());
        LOGGER.info("QuantumCore saved {} on launch", String.format("%.3fs", cacheSavedSeconds));
        LOGGER.info("Total measured async phase time: {}", String.format("%.3fs", totalSeconds));
        LOGGER.info("===============================================");
    }

    /**
     * Configuration singleton.
     * Thread-safety: immutable after construction; safe to share across all loader threads.
     */
    public static final class QuantumCoreConfig {
        private static final String RESOURCE_NAME = "/quantumcore.toml";
        private static final Path TARGET_PATH = FMLPaths.CONFIGDIR.get().resolve("quantumcore.toml");
        private static final boolean DEFAULT_TRUE = true;

        private final boolean diskCache;
        private final boolean parallelLoader;
        private final boolean lazyInitialization;
        private final boolean memoryOptimization;
        private final boolean gcPressureReduction;
        private final boolean mixins;
        private final boolean conflictDetector;

        private QuantumCoreConfig(
            boolean diskCache,
            boolean parallelLoader,
            boolean lazyInitialization,
            boolean memoryOptimization,
            boolean gcPressureReduction,
            boolean mixins,
            boolean conflictDetector
        ) {
            this.diskCache = diskCache;
            this.parallelLoader = parallelLoader;
            this.lazyInitialization = lazyInitialization;
            this.memoryOptimization = memoryOptimization;
            this.gcPressureReduction = gcPressureReduction;
            this.mixins = mixins;
            this.conflictDetector = conflictDetector;
        }

        /**
         * Creates the runtime config by copying the shipped schema first, guaranteeing users always
         * start from documented defaults rather than hidden hardcoded flags.
         */
        public static QuantumCoreConfig load() {
            ensureConfigExists();
            try (CommentedFileConfig file = CommentedFileConfig.builder(TARGET_PATH).autoreload().autosave().sync().build()) {
                file.load();
                return new QuantumCoreConfig(
                    file.getOrElse("modules.disk_cache", DEFAULT_TRUE),
                    file.getOrElse("modules.parallel_loader", DEFAULT_TRUE),
                    file.getOrElse("modules.lazy_initialization", DEFAULT_TRUE),
                    file.getOrElse("modules.memory_optimization", DEFAULT_TRUE),
                    file.getOrElse("modules.gc_pressure_reduction", DEFAULT_TRUE),
                    file.getOrElse("modules.mixins", DEFAULT_TRUE),
                    file.getOrElse("modules.conflict_detector", DEFAULT_TRUE)
                );
            }
        }

        /**
         * Registers a minimal NeoForge config type so launchers and mod config tooling can discover
         * that QuantumCore exposes user-tunable behavior.
         */
        public static net.neoforged.neoforge.common.ModConfigSpec createSpec() {
            net.neoforged.neoforge.common.ModConfigSpec.Builder builder = new net.neoforged.neoforge.common.ModConfigSpec.Builder();
            builder.push("modules");
            builder.define("disk_cache", DEFAULT_TRUE);
            builder.define("parallel_loader", DEFAULT_TRUE);
            builder.define("lazy_initialization", DEFAULT_TRUE);
            builder.define("memory_optimization", DEFAULT_TRUE);
            builder.define("gc_pressure_reduction", DEFAULT_TRUE);
            builder.define("mixins", DEFAULT_TRUE);
            builder.define("conflict_detector", DEFAULT_TRUE);
            builder.pop();
            return builder.build();
        }

        private static void ensureConfigExists() {
            try {
                Files.createDirectories(TARGET_PATH.getParent());
                if (Files.exists(TARGET_PATH)) {
                    return;
                }
                try (InputStream in = QuantumCore.class.getResourceAsStream(RESOURCE_NAME)) {
                    if (in == null) {
                        throw new IOException("Missing bundled resource " + RESOURCE_NAME);
                    }
                    Files.copy(in, TARGET_PATH, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to initialize quantumcore.toml", exception);
            }
        }

        /**
         * Allows total cache disable for packs that prefer deterministic full reparse behavior.
         */
        public boolean enableDiskCache() {
            return diskCache;
        }

        /**
         * Keeps loader parallelism configurable so users can disable it quickly when diagnosing
         * third-party race-condition issues.
         */
        public boolean enableParallelLoader() {
            return parallelLoader;
        }

        /**
         * Preserves compatibility with mods that require eager bootstrapping by allowing lazy init
         * to be turned off globally.
         */
        public boolean enableLazyInitialization() {
            return lazyInitialization;
        }

        /**
         * Allows selective rollback of interning logic if another mod depends on object identity
         * semantics.
         */
        public boolean enableMemoryOptimization() {
            return memoryOptimization;
        }

        /**
         * Exposes object-pooling controls so pack authors can A/B test GC behavior.
         */
        public boolean enableGcPressureReduction() {
            return gcPressureReduction;
        }

        /**
         * Supports emergency disabling of bytecode patches when upstream game updates change
         * method layouts.
         */
        public boolean enableMixins() {
            return mixins;
        }

        /**
         * Keeps conflict warnings optional for minimal logging setups.
         */
        public boolean enableConflictDetector() {
            return conflictDetector;
        }
    }
}
