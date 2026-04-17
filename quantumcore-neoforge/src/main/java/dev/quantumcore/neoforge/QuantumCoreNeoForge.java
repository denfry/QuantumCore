package dev.quantumcore.neoforge;

import com.mojang.logging.LogUtils;
import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.common.dashboard.CompletionOverlay;
import dev.quantumcore.common.profiling.PerformanceReport;
import dev.quantumcore.common.profiling.StageProfiler;
import dev.quantumcore.common.runtime.LoadStage;
import dev.quantumcore.common.runtime.QuantumCoreRuntime;
import dev.quantumcore.common.runtime.QuantumCoreServices;
import dev.quantumcore.neoforge.screen.NeoForgeModLoadingHooks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(QuantumCoreNeoForge.MOD_ID)
@NotThreadSafe
public final class QuantumCoreNeoForge {
    public static final String MOD_ID = "quantumcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static QuantumCoreRuntime runtime;
    private static final CompletionOverlay COMPLETION_OVERLAY = new CompletionOverlay();

    public QuantumCoreNeoForge(IEventBus modEventBus) {
        runtime = QuantumCoreServices.init(FMLPaths.GAMEDIR.get());
        runtime.profiler().beginLaunch();
        runtime.profiler().setBuildingCache(true);
        runtime.profiler().enterStage(LoadStage.BOOTSTRAP);
        runtime.preloader().preloadRecentWorld(FMLPaths.GAMEDIR.get().resolve("saves"));

        NeoForgeModLoadingHooks.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("QuantumCore: Building cache  next launch will be fast");
    }

    public static QuantumCoreRuntime runtime() {
        return runtime;
    }

    public static CompletionOverlay completionOverlay() {
        return COMPLETION_OVERLAY;
    }

    @SubscribeEvent
    public void onLoadComplete(FMLLoadCompleteEvent event) {
        StageProfiler.Snapshot snapshot = runtime.profiler().snapshot(1.0F);
        runtime.profiler().setBuildingCache(false);
        runtime.profiler().writeProfileLog();

        double elapsed = snapshot.elapsedNanos() / 1_000_000_000.0;
        double baseline = 130.0;
        new PerformanceReport(FMLPaths.GAMEDIR.get().resolve(".cache").resolve("quantumcore")).write(elapsed, baseline, snapshot);
        COMPLETION_OVERLAY.start(elapsed, baseline - elapsed);

        LOGGER.info("========== QuantumCore Performance Table ==========");
        LOGGER.info("Atlas cache hit: {}", snapshot.atlasLoadedFromCache());
        LOGGER.info("Checkpoint restore: {}", snapshot.checkpointRestored());
        LOGGER.info("Incremental cached mods: {}", snapshot.cachedModsCount());
        LOGGER.info("Total elapsed: {}", StageProfiler.formatSeconds(snapshot.elapsedNanos()));
        LOGGER.info("Estimated saved vs baseline: {}", String.format("%.1fs", Math.max(0.0, baseline - elapsed)));
        LOGGER.info("===================================================");
    }
}
