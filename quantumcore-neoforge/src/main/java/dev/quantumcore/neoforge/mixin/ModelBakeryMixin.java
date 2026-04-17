package dev.quantumcore.neoforge.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.neoforge.cache.NeoForgeCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.resources.model.ModelBakery")
@NotThreadSafe
public abstract class ModelBakeryMixin {
    @Inject(method = "bakeModel", at = @At("HEAD"), cancellable = true)
    private void quantumcore$bakeModel(Object id, Object state, CallbackInfoReturnable<Object> cir) {
        NeoForgeCaches.surgical().getString("bakeModel:" + String.valueOf(id)).ifPresent(cir::setReturnValue);
    }

    @Inject(method = "loadBlockModel", at = @At("HEAD"), cancellable = true)
    private void quantumcore$loadBlockModel(Object id, CallbackInfoReturnable<Object> cir) {
        NeoForgeCaches.surgical().getString("loadBlockModel:" + String.valueOf(id)).ifPresent(cir::setReturnValue);
    }
}
