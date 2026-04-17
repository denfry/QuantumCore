package dev.quantumcore.common.early;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public final class StbFontRenderer {
    public void draw(EarlyScreenSurface surface, String text, float x, float y, int argb, float scale) {
        surface.text(text, x, y, argb, scale);
    }
}
