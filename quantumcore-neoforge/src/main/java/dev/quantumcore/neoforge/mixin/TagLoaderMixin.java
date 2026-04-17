package dev.quantumcore.neoforge.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.neoforge.cache.NeoForgeCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.tags.TagLoader")
@NotThreadSafe
public abstract class TagLoaderMixin {
    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private void quantumcore$load(CallbackInfoReturnable<Object> cir) {
        NeoForgeCaches.surgical().getString("tags:global").ifPresent(cir::setReturnValue);
    }
}
