package dev.quantumcore.common.early;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.runtime.RuntimeMode;

@ThreadSafe
public final class EarlyScreenRenderer {
    public void render(EarlyScreenSurface surface, float progress, String stage, long elapsedNanos, RuntimeMode mode) {
        surface.clear(0.04F, 0.04F, 0.08F, 1.0F);
        surface.text("QUANTUMCORE", 18, 16, 0xFFFFFFFF, 1.3F);
        surface.text(stage, surface.width() * 0.5F - 80, surface.height() * 0.5F - 8, 0xFFE8EEF8, 1.0F);

        float spinnerCx = surface.width() * 0.5F;
        float spinnerCy = surface.height() * 0.5F + 28;
        float radius = 16;
        double angle = (elapsedNanos / 1_000_000_000.0) * 4.0;
        float x2 = (float) (spinnerCx + Math.cos(angle) * radius);
        float y2 = (float) (spinnerCy + Math.sin(angle) * radius);
        surface.line(spinnerCx, spinnerCy, x2, y2, 0xFF65D7B5);

        float barY = surface.height() - 18;
        surface.rect(0, barY, surface.width(), 8, 0x33223344);
        surface.rect(0, barY, surface.width() * Math.max(0F, Math.min(1F, progress)), 8, mode == RuntimeMode.FAST_RESUME ? 0xFF44E4AA : 0xFF4488FF);
        surface.text(String.format("%.1fs", elapsedNanos / 1_000_000_000.0), surface.width() - 52, surface.height() - 28, 0xFFCCD6EE, 0.9F);

        if (mode == RuntimeMode.FAST_RESUME) {
            surface.text("FAST RESUME", surface.width() * 0.5F - 48, 30, 0xFF44E4AA, 1.0F);
        }
    }
}
