package dev.quantumcore.fabric.ui;

import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.common.dashboard.DashboardSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

@NotThreadSafe
public final class FabricDashboardSurface implements DashboardSurface {
    private final GuiGraphics graphics;
    private final int width;
    private final int height;

    public FabricDashboardSurface(GuiGraphics graphics, int width, int height) {
        this.graphics = graphics;
        this.width = width;
        this.height = height;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        graphics.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void text(String value, int x, int y, int argb) {
        graphics.drawString(Minecraft.getInstance().font, value, x, y, argb);
    }
}
