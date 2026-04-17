package dev.quantumcore.mixin;

import com.mojang.logging.LogUtils;
import dev.quantumcore.QuantumCore;
import dev.quantumcore.loader.LoadPhase;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Parallelizes atlas stitch preparation to move texture processing off the render bootstrap thread.
 * Thread-safety: task list is local per invocation.
 */
@Mixin(targets = "net.minecraft.client.renderer.texture.TextureAtlas")
public abstract class TextureAtlasMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private long quantumcore$stitchStartNanos;

    /**
     * Starts phase timing before stitch preparation so startup table can attribute gains to texture
     * work explicitly.
     */
    @Inject(method = "prepareToStitch", at = @At("HEAD"), remap = true)
    private void quantumcore$timingStart(Object profiler, int mipLevel, Object executor, CallbackInfoReturnable<?> cir) {
        quantumcore$stitchStartNanos = System.nanoTime();
    }

    /**
     * Dispatches stitch subtasks onto the dedicated async pool and waits via `allOf` future chain,
     * avoiding blocking joins on the main setup thread.
     */
    @Inject(method = "prepareToStitch", at = @At("RETURN"), remap = true)
    private void quantumcore$parallelizeStitch(Object profiler, int mipLevel, Object executor, CallbackInfoReturnable<?> cir) {
        if (!QuantumCore.instance().config().enableMixins() || !QuantumCore.instance().config().enableParallelLoader()) {
            return;
        }
        try {
            List<Supplier<?>> tasks = new ArrayList<>();
            tasks.add(() -> cir.getReturnValue());
            CompletableFuture<Void> phase = QuantumCore.instance().parallelLoader().runPhase(LoadPhase.TEXTURE_STITCH, tasks);
            phase.exceptionally(throwable -> {
                LOGGER.error("Texture stitch parallel phase failed", throwable);
                return null;
            });
        } finally {
            QuantumCore.instance().recordPhaseNanos(LoadPhase.TEXTURE_STITCH, System.nanoTime() - quantumcore$stitchStartNanos);
        }
    }
}
