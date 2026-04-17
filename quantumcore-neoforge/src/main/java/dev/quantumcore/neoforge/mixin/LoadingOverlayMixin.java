package dev.quantumcore.neoforge.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.common.dashboard.QuantumDashboard;
import dev.quantumcore.common.profiling.StageProfiler;
import dev.quantumcore.neoforge.QuantumCoreNeoForge;
import dev.quantumcore.neoforge.ui.NeoForgeDashboardSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.LoadingOverlay")
@NotThreadSafe
public abstract class LoadingOverlayMixin {
    private static final QuantumDashboard DASHBOARD = new QuantumDashboard(QuantumCoreNeoForge.runtime().config().dashboard());

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void quantumcore$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!QuantumCoreNeoForge.runtime().config().dashboard().enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        StageProfiler.Snapshot snapshot = QuantumCoreNeoForge.runtime().profiler().snapshot(Math.max(0F, Math.min(0.99F, partialTick)));
        DASHBOARD.render(
            new NeoForgeDashboardSurface(graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()),
            snapshot,
            partialTick
        );
        if (QuantumCoreNeoForge.completionOverlay().active()) {
            graphics.drawString(mc.font, QuantumCoreNeoForge.completionOverlay().message(), 12, mc.getWindow().getGuiScaledHeight() - 40, 0xFFFFFFFF);
        }
        ci.cancel();
    }
}
