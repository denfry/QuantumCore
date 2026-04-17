package dev.quantumcore.fabric.screen;

import dev.quantumcore.common.profiling.StageProfiler;
import dev.quantumcore.fabric.QuantumCoreFabric;
import dev.quantumcore.screen.early.EarlyScreenRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.SplashScreen")
public abstract class FabricSplashScreenMixin {
    private static final EarlyScreenRenderer QUANTUMCORE_EARLY_RENDERER = new EarlyScreenRenderer();

    @Inject(method = "init", at = @At("TAIL"))
    private void quantumcore$init(CallbackInfo ci) {
        long window = GLFW.glfwGetCurrentContext();
        if (window != 0L) {
            QUANTUMCORE_EARLY_RENDERER.init(window);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void quantumcore$render(GuiGraphics gfx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (QuantumCoreFabric.runtime() == null) {
            return;
        }
        StageProfiler.Snapshot snapshot = QuantumCoreFabric.runtime().profiler().snapshot(Math.max(0F, Math.min(0.99F, delta)));
        long startNanos = System.nanoTime() - Math.max(0L, snapshot.elapsedNanos());
        String stage = snapshot.currentStage().name().replace('_', ' ');
        QUANTUMCORE_EARLY_RENDERER.render(snapshot.progress(), stage, startNanos);
        ci.cancel();
    }
}
