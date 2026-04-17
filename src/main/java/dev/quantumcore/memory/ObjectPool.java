package dev.quantumcore.memory;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reuses short-lived hot-path objects to reduce allocator churn and GC pause spikes during world
 * ticking and collision-heavy gameplay.
 * Thread-safety: thread-safe via per-thread deque isolation.
 */
public final class ObjectPool<T> {
    private final int maxPerThread;
    private final Supplier<T> factory;
    private final Consumer<T> reset;
    private final ThreadLocal<ArrayDeque<T>> localPool;

    /**
     * Captures pool sizing and reset strategy so pooled objects can be safely reused without
     * leaking stale state across borrow/release cycles.
     */
    public ObjectPool(int maxPerThread, Supplier<T> factory, Consumer<T> reset) {
        if (maxPerThread <= 0) {
            throw new IllegalArgumentException("maxPerThread must be positive");
        }
        this.maxPerThread = maxPerThread;
        this.factory = Objects.requireNonNull(factory, "factory");
        this.reset = Objects.requireNonNull(reset, "reset");
        this.localPool = ThreadLocal.withInitial(() -> new ArrayDeque<>(maxPerThread));
    }

    /**
     * Borrows from the calling thread's pool first to avoid cross-thread contention and preserve
     * deterministic low-latency object reuse.
     */
    public T borrow() {
        ArrayDeque<T> deque = localPool.get();
        T value = deque.pollFirst();
        return value != null ? value : factory.get();
    }

    /**
     * Returns objects to the caller thread pool only after reset so stale data cannot leak into
     * later logic paths.
     */
    public void release(T value) {
        if (value == null) {
            return;
        }
        reset.accept(value);
        ArrayDeque<T> deque = localPool.get();
        if (deque.size() < maxPerThread) {
            deque.offerFirst(value);
        }
    }

    /**
     * Provides allocation pressure insight for profiling by exposing per-thread retained pool size.
     */
    public int currentThreadPoolSize() {
        return localPool.get().size();
    }
}
