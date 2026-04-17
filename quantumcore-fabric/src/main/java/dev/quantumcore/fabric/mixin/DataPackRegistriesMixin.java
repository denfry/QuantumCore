package dev.quantumcore.fabric.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.fabric.cache.FabricCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.DataPackRegistries")
@NotThreadSafe
public abstract class DataPackRegistriesMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void quantumcore$apply(CallbackInfo ci) {
        if (FabricCaches.surgical().getString("datapack:apply").isPresent()) {
            ci.cancel();
        }
    }
}
