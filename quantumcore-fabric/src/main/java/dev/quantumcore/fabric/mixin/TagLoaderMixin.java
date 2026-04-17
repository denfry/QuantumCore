package dev.quantumcore.fabric.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.fabric.cache.FabricCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.tags.TagLoader")
@NotThreadSafe
public abstract class TagLoaderMixin {
    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private void quantumcore$load(CallbackInfoReturnable<Object> cir) {
        FabricCaches.surgical().getString("tags:global").ifPresent(cir::setReturnValue);
    }
}
