package dev.quantumcore.common.early;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.runtime.RuntimeMode;

@ThreadSafe
public final class QuantumEarlyScreen {
    private final EarlyScreenRenderer renderer = new EarlyScreenRenderer();
    private volatile RuntimeMode mode = RuntimeMode.DEEP_BOOT;
    private volatile long startNanos = System.nanoTime();

    public void setMode(RuntimeMode mode) {
        this.mode = mode;
    }

    public void resetTimer() {
        startNanos = System.nanoTime();
    }

    public void render(EarlyScreenSurface surface, float progress, String stage) {
        renderer.render(surface, progress, stage, System.nanoTime() - startNanos, mode);
    }
}
