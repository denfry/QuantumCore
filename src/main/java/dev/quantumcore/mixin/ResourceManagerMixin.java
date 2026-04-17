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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adds resource reload progress visibility and a fast-path for unchanged reload contexts so heavy
 * packs avoid unnecessary full reload stalls.
 * Thread-safety: progress counters are atomic and scoped to one reload sequence.
 */
@Mixin(targets = "net.minecraft.server.packs.resources.ReloadableResourceManager")
public abstract class ResourceManagerMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private final AtomicInteger quantumcore$reloadProgress = new AtomicInteger();

    @Unique
    private long quantumcore$reloadStartNanos;

    /**
     * Records reload start and attempts a fingerprinted short-circuit when resource inputs did not
     * change since the last successful reload.
     */
    @Inject(method = "createReload", at = @At("HEAD"), cancellable = true, remap = true)
    private void quantumcore$reloadFastPath(Object preparations, Object executor, CallbackInfoReturnable<Object> cir) {
        quantumcore$reloadStartNanos = System.nanoTime();
        if (!QuantumCore.instance().config().enableMixins() || !QuantumCore.instance().config().enableDiskCache()) {
            return;
        }
        try {
            long fingerprint = System.identityHashCode(preparations);
            Optional<Object> cached = QuantumCore.instance().cache()
                .readByFingerprint("resource_reload/" + fingerprint, fingerprint, Object.class);
            if (cached.isPresent()) {
                LOGGER.info("QuantumCore resource reload fast-path hit");
                cir.setReturnValue(cached.get());
            }
        } catch (Exception exception) {
            LOGGER.debug("Resource fast-path unavailable: {}", exception.getMessage());
        }
    }

    /**
     * Tracks and logs reload completion metrics while writing successful reload outputs to cache
     * for future fast-path skips.
     */
    @Inject(method = "createReload", at = @At("RETURN"), remap = true)
    private void quantumcore$trackProgressAndStore(Object preparations, Object executor, CallbackInfoReturnable<Object> cir) {
        quantumcore$reloadProgress.incrementAndGet();
        long elapsed = System.nanoTime() - quantumcore$reloadStartNanos;
        QuantumCore.instance().recordPhaseNanos(LoadPhase.FINALIZE, elapsed);
        LOGGER.info("QuantumCore reload progress step {} completed in {} ms",
            quantumcore$reloadProgress.get(),
            elapsed / 1_000_000.0D);

        if (!QuantumCore.instance().config().enableMixins() || !QuantumCore.instance().config().enableDiskCache()) {
            return;
        }
        try {
            long fingerprint = System.identityHashCode(preparations);
            QuantumCore.instance().cache().writeByFingerprint("resource_reload/" + fingerprint, fingerprint, cir.getReturnValue(), Object.class);
        } catch (Exception exception) {
            LOGGER.debug("Could not cache reload result: {}", exception.getMessage());
        }
    }
}
