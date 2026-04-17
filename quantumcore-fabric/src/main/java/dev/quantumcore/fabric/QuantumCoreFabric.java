package dev.quantumcore.fabric;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.common.dashboard.CompletionOverlay;
import dev.quantumcore.common.profiling.PerformanceReport;
import dev.quantumcore.common.profiling.StageProfiler;
import dev.quantumcore.common.runtime.QuantumCoreRuntime;
import dev.quantumcore.common.runtime.QuantumCoreServices;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

@NotThreadSafe
public final class QuantumCoreFabric implements ModInitializer, ClientModInitializer {
    private static QuantumCoreRuntime runtime;
    private static final CompletionOverlay COMPLETION_OVERLAY = new CompletionOverlay();

    public static QuantumCoreRuntime runtime() {
        return runtime;
    }

    public static CompletionOverlay completionOverlay() {
        return COMPLETION_OVERLAY;
    }

    @Override
    public void onInitialize() {
        runtime = QuantumCoreServices.init(FabricLoader.getInstance().getGameDir());
        runtime.profiler().beginLaunch();
        runtime.profiler().setBuildingCache(true);
    }

    @Override
    public void onInitializeClient() {
        runtime.preloader().preloadRecentWorld(FabricLoader.getInstance().getGameDir().resolve("saves"));

        StageProfiler.Snapshot snapshot = runtime.profiler().snapshot(1.0F);
        runtime.profiler().setBuildingCache(false);
        runtime.profiler().writeProfileLog();

        double elapsed = snapshot.elapsedNanos() / 1_000_000_000.0;
        double baseline = 130.0;
        new PerformanceReport(FabricLoader.getInstance().getGameDir().resolve(".cache").resolve("quantumcore")).write(elapsed, baseline, snapshot);
        COMPLETION_OVERLAY.start(elapsed, baseline - elapsed);
    }
}
