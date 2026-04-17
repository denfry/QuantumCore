package dev.quantumcore.memory;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks heap pressure in real time to warn players before major GC stalls and to aid tuning for
 * large modpacks.
 * Thread-safety: thread-safe lifecycle; monitoring task runs on a single daemon thread.
 */
public final class MemoryMonitor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double WARN_THRESHOLD = 0.80D;

    private final boolean enabled;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new MonitorThreadFactory());

    /**
     * Makes monitoring opt-in at construction so disabled mode introduces near-zero runtime
     * overhead.
     */
    public MemoryMonitor(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Starts periodic heap checks so warning logs appear before severe stutters become visible to
     * players.
     */
    public void start() {
        if (!enabled || !started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleAtFixedRate(this::sample, 5, 5, TimeUnit.SECONDS);
    }

    private void sample() {
        MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
        long max = heap.getMax();
        long used = heap.getUsed();
        if (max <= 0L) {
            return;
        }
        double ratio = (double) used / (double) max;
        if (ratio >= WARN_THRESHOLD) {
            LOGGER.warn("QuantumCore memory pressure alert: {}% heap used ({} MB / {} MB)",
                String.format("%.2f", ratio * 100.0D),
                used / (1024L * 1024L),
                max / (1024L * 1024L));
        }
    }

    /**
     * Stops monitor threads explicitly so dev reload cycles do not accumulate duplicate samplers.
     */
    public void stop() {
        scheduler.shutdownNow();
    }

    private static final class MonitorThreadFactory implements ThreadFactory {
        /**
         * Uses daemon thread creation so monitoring never prevents controlled game shutdown.
         */
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "QuantumCore-MemoryMonitor");
            thread.setDaemon(true);
            return thread;
        }
    }
}
