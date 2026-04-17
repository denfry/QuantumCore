package dev.quantumcore.loader;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Defers heavyweight subsystem construction until first real usage so startup avoids eagerly
 * initializing optional data-heavy managers.
 * Thread-safety: thread-safe via volatile state + synchronized second check.
 */
public final class LazyHolder<T> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String name;
    private final Supplier<T> initializer;
    private volatile T value;

    /**
     * Names the lazy subsystem so deferred initialization shows up clearly in logs.
     */
    public LazyHolder(String name, Supplier<T> initializer) {
        this.name = Objects.requireNonNull(name, "name");
        this.initializer = Objects.requireNonNull(initializer, "initializer");
    }

    /**
     * Uses double-checked locking so expensive initialization occurs once while hot-path reads
     * stay lock-free after first access.
     */
    public T get() {
        T local = value;
        if (local == null) {
            synchronized (this) {
                local = value;
                if (local == null) {
                    long start = System.nanoTime();
                    local = initializer.get();
                    value = local;
                    long nanos = System.nanoTime() - start;
                    LOGGER.info("Lazy-initialized '{}' in {} ms", name, nanos / 1_000_000.0D);
                }
            }
        }
        return local;
    }

    /**
     * Allows debug callers to report deferred-init state without triggering initialization.
     */
    public boolean isInitialized() {
        return value != null;
    }
}
