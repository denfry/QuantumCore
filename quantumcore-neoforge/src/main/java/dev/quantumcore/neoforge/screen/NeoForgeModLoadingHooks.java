package dev.quantumcore.neoforge.screen;

import dev.quantumcore.common.runtime.LoadStage;
import dev.quantumcore.neoforge.QuantumCoreNeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class NeoForgeModLoadingHooks {
    private static final ConcurrentHashMap<String, Long> MOD_STARTS = new ConcurrentHashMap<>();

    private NeoForgeModLoadingHooks() {
    }

    public static void register(IEventBus modEventBus) {
        if (QuantumCoreNeoForge.runtime() == null) {
            return;
        }

        List<String> mods = ModList.get().getMods().stream()
            .map(info -> info.getModId())
            .sorted()
            .toList();

        MOD_STARTS.clear();
        QuantumCoreNeoForge.runtime().profiler().enterStage(LoadStage.REGISTRIES);
        for (String modId : mods) {
            MOD_STARTS.put(modId, System.nanoTime());
        }

        modEventBus.addListener(NeoForgeModLoadingHooks::onConstruct);
        modEventBus.addListener(NeoForgeModLoadingHooks::onCommonSetup);
        modEventBus.addListener(NeoForgeModLoadingHooks::onClientSetup);
        modEventBus.addListener(NeoForgeModLoadingHooks::onLoadComplete);
    }

    private static void onConstruct(FMLConstructModEvent event) {
        // Event container access is restricted on current NeoForge API.
        // We seed starts from ModList during registration and finalize in onCommonSetup.
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        long now = System.nanoTime();
        MOD_STARTS.forEach((modId, startedAt) ->
            QuantumCoreNeoForge.runtime().profiler().onModProcessed(modId, Math.max(0L, now - startedAt)));
        MOD_STARTS.clear();
        QuantumCoreNeoForge.runtime().profiler().completeStage(LoadStage.REGISTRIES);
        QuantumCoreNeoForge.runtime().profiler().enterStage(LoadStage.MODELS);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        QuantumCoreNeoForge.runtime().profiler().completeStage(LoadStage.MODELS);
        QuantumCoreNeoForge.runtime().profiler().enterStage(LoadStage.FINALIZING);
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        QuantumCoreNeoForge.runtime().profiler().completeStage(LoadStage.FINALIZING);
    }
}
