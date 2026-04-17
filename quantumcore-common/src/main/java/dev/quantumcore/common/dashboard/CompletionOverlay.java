package dev.quantumcore.common.dashboard;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public final class CompletionOverlay {
    private volatile long startedNanos;
    private volatile String message = "";

    public void start(double totalSeconds, double savedSeconds) {
        startedNanos = System.nanoTime();
        message = String.format("Loaded in %.1fs  saved %.1fs vs baseline", totalSeconds, Math.max(0.0, savedSeconds));
    }

    public boolean active() {
        return startedNanos != 0L && (System.nanoTime() - startedNanos) < 2_000_000_000L;
    }

    public String message() {
        return message;
    }

    public float alpha() {
        long age = System.nanoTime() - startedNanos;
        if (age < 800_000_000L) {
            return 1F - (age / 800_000_000F);
        }
        return 0.3F;
    }
}
