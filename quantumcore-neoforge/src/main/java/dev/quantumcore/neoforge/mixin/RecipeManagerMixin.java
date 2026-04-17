package dev.quantumcore.neoforge.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.neoforge.cache.NeoForgeCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager")
@NotThreadSafe
public abstract class RecipeManagerMixin {
    @Inject(method = "fromJson", at = @At("HEAD"), cancellable = true)
    private static void quantumcore$fromJson(Object recipeId, Object json, CallbackInfoReturnable<Object> cir) {
        NeoForgeCaches.surgical().getString("recipe:" + String.valueOf(recipeId)).ifPresent(cir::setReturnValue);
    }
}
