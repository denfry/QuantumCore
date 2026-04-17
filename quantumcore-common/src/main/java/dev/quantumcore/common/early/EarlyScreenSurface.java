package dev.quantumcore.common.early;

import dev.quantumcore.common.concurrent.NotThreadSafe;

@NotThreadSafe
public interface EarlyScreenSurface {
    int width();

    int height();

    void clear(float r, float g, float b, float a);

    void line(float x1, float y1, float x2, float y2, int argb);

    void rect(float x, float y, float width, float height, int argb);

    void text(String value, float x, float y, int argb, float scale);
}
