package dev.quantumcore.common.dashboard;

import dev.quantumcore.common.concurrent.NotThreadSafe;

@NotThreadSafe
public interface DashboardSurface {
    int width();

    int height();

    void fill(int x1, int y1, int x2, int y2, int argb);

    void text(String value, int x, int y, int argb);
}
