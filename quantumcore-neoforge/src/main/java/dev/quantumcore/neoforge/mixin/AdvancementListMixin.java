package dev.quantumcore.neoforge.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.neoforge.cache.NeoForgeCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.advancements.AdvancementList")
@NotThreadSafe
public abstract class AdvancementListMixin {
    @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
    private void quantumcore$reload(CallbackInfo ci) {
        if (NeoForgeCaches.surgical().getString("advancements:tree").isPresent()) {
            ci.cancel();
        }
    }
}
