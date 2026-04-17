package dev.quantumcore.fabric.mixin;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.common.dashboard.QuantumDashboard;
import dev.quantumcore.common.profiling.StageProfiler;
import dev.quantumcore.fabric.QuantumCoreFabric;
import dev.quantumcore.fabric.ui.FabricDashboardSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.LoadingOverlay")
@NotThreadSafe
public abstract class LoadingOverlayMixin {
    private static final QuantumDashboard DASHBOARD = new QuantumDashboard(QuantumCoreFabric.runtime().config().dashboard());

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void quantumcore$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!QuantumCoreFabric.runtime().config().dashboard().enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        StageProfiler.Snapshot snapshot = QuantumCoreFabric.runtime().profiler().snapshot(Math.max(0F, Math.min(0.99F, partialTick)));
        DASHBOARD.render(
            new FabricDashboardSurface(graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()),
            snapshot,
            partialTick
        );
        if (QuantumCoreFabric.completionOverlay().active()) {
            graphics.drawString(mc.font, QuantumCoreFabric.completionOverlay().message(), 12, mc.getWindow().getGuiScaledHeight() - 40, 0xFFFFFFFF);
        }
        ci.cancel();
    }
}
