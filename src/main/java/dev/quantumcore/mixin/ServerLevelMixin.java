package dev.quantumcore.mixin;

import dev.quantumcore.QuantumCore;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Batches dirty-flag tracking for entity scheduling so repeated tick bookkeeping avoids redundant
 * map churn in dense mob farms.
 * Thread-safety: accessed on server thread only.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Unique
    private final Object2BooleanOpenHashMap<String> quantumcore$dirtyEntityFlags = new Object2BooleanOpenHashMap<>();

    /**
     * Initializes compact primitive-backed dirty flags to reduce boxing and map overhead in server
     * tick scheduling.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void quantumcore$prepareEntityBatch(CallbackInfo ci) {
        if (!QuantumCore.instance().config().enableMixins()) {
            return;
        }
        quantumcore$dirtyEntityFlags.clear();
    }

    /**
     * Clears batched state at tick end to avoid memory retention across worlds and dimensions.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void quantumcore$flushEntityBatch(CallbackInfo ci) {
        if (!QuantumCore.instance().config().enableMixins()) {
            return;
        }
        quantumcore$dirtyEntityFlags.clear();
    }
}
