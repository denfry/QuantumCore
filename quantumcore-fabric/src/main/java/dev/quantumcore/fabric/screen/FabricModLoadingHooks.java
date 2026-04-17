package dev.quantumcore.fabric.screen;

import dev.quantumcore.common.profiling.StageProfiler;
import dev.quantumcore.common.runtime.LoadStage;
import dev.quantumcore.fabric.QuantumCoreFabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

public final class FabricModLoadingHooks implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (QuantumCoreFabric.runtime() == null) {
            return;
        }

        StageProfiler profiler = QuantumCoreFabric.runtime().profiler();
        profiler.enterStage(LoadStage.REGISTRIES);

        List<String> mods = FabricLoader.getInstance().getAllMods().stream()
            .map(container -> container.getMetadata().getId())
            .sorted()
            .toList();

        Thread feeder = new Thread(() -> feedApproximateModTimings(profiler, mods), "QuantumCore-Fabric-ModFeed");
        feeder.setDaemon(true);
        feeder.start();
    }

    private static void feedApproximateModTimings(StageProfiler profiler, List<String> mods) {
        for (String modId : mods) {
            long startedAt = System.nanoTime();
            sleep(5L);
            profiler.onModProcessed(modId, System.nanoTime() - startedAt);
        }
        profiler.completeStage(LoadStage.REGISTRIES);
        profiler.enterStage(LoadStage.FINALIZING);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
