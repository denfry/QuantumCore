package dev.quantumcore.neoforge.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.neoforge.cache.NeoForgeCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.DataPackRegistries")
@NotThreadSafe
public abstract class DataPackRegistriesMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void quantumcore$apply(CallbackInfo ci) {
        if (NeoForgeCaches.surgical().getString("datapack:apply").isPresent()) {
            ci.cancel();
        }
    }
}
