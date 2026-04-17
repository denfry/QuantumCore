package dev.quantumcore.mixin;

import com.mojang.logging.LogUtils;
import dev.quantumcore.QuantumCore;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Serves baked model payloads from disk cache when the fingerprint matches, drastically reducing
 * repeated JSON parsing and model graph rebuild on subsequent launches.
 * Thread-safety: mixin delegates to synchronized cache APIs.
 */
@Mixin(targets = "net.minecraft.client.resources.model.ModelBakery")
public abstract class ModelBakeryMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Adds read-through cache lookup before model baking work starts so unchanged models skip
     * expensive processing paths.
     */
    @Inject(method = "bake", at = @At("HEAD"), cancellable = true, remap = true)
    private void quantumcore$tryReadFromCache(ResourceLocation modelId, Object context, CallbackInfoReturnable<Object> cir) {
        if (!QuantumCore.instance().config().enableMixins() || !QuantumCore.instance().config().enableDiskCache()) {
            return;
        }
        try {
            long fingerprint = modelId.toString().hashCode();
            Optional<Object> cached = QuantumCore.instance()
                .cache()
                .readByFingerprint("model/" + modelId, fingerprint, Object.class);
            cached.ifPresent(cir::setReturnValue);
        } catch (Exception exception) {
            LOGGER.debug("Model cache lookup skipped for {}: {}", modelId, exception.getMessage());
        }
    }

    /**
     * Writes fresh bake results back to cache so later starts can short-circuit this path.
     */
    @Inject(method = "bake", at = @At("RETURN"), remap = true)
    private void quantumcore$writeThrough(ResourceLocation modelId, Object context, CallbackInfoReturnable<Object> cir) {
        if (!QuantumCore.instance().config().enableMixins() || !QuantumCore.instance().config().enableDiskCache()) {
            return;
        }
        try {
            Object baked = cir.getReturnValue();
            if (baked != null) {
                long fingerprint = modelId.toString().hashCode();
                QuantumCore.instance().cache().writeByFingerprint("model/" + modelId, fingerprint, baked, Object.class);
            }
        } catch (Exception exception) {
            LOGGER.debug("Model cache write skipped for {}: {}", modelId, exception.getMessage());
        }
    }
}
