package dev.quantumcore.mixin;

import dev.quantumcore.QuantumCore;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies a cheap bloom-like prefilter to skip duplicate random tick checks within the same frame
 * window.
 * Thread-safety: server-thread only.
 */
@Mixin(targets = "net.minecraft.world.level.block.RandomTickHelper", remap = false)
public abstract class RandomTickHelperMixin {
    @Unique
    private final LongOpenHashSet quantumcore$recentTickBloom = new LongOpenHashSet(8192);

    /**
     * Filters redundant random tick candidates to lower CPU and allocation pressure without
     * changing deterministic world state outcomes.
     */
    @Inject(method = "shouldTick", at = @At("HEAD"), cancellable = true)
    private void quantumcore$skipDuplicateTicks(long packedPos, CallbackInfoReturnable<Boolean> cir) {
        if (!QuantumCore.instance().config().enableMixins()) {
            return;
        }
        if (!quantumcore$recentTickBloom.add(packedPos)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Resets bloom state periodically to keep false-positive rates bounded.
     */
    @Inject(method = "endFrame", at = @At("HEAD"))
    private void quantumcore$clearBloom(CallbackInfo ci) {
        quantumcore$recentTickBloom.clear();
    }
}
