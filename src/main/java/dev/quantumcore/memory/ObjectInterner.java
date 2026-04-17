package dev.quantumcore.memory;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

/**
 * Deduplicates equal objects into shared canonical instances to reduce heap fragmentation in
 * high-entity, high-blockstate modpacks.
 * Thread-safety: thread-safe for concurrent intern operations.
 */
public final class ObjectInterner<T> {
    private final ConcurrentMap<T, WeakReference<T>> canonical = new ConcurrentHashMap<>();
    private final UnaryOperator<T> normalizer;

    /**
     * Accepts a normalizer so callers can canonicalize key shape before interning.
     */
    public ObjectInterner(UnaryOperator<T> normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    /**
     * Interns by value semantics so logically identical objects are reused across systems, cutting
     * duplicate allocations from repeated parsing and tick processing.
     */
    public T intern(T value) {
        T normalized = normalizer.apply(Objects.requireNonNull(value, "value"));
        for (; ; ) {
            WeakReference<T> existingRef = canonical.get(normalized);
            if (existingRef != null) {
                T existing = existingRef.get();
                if (existing != null) {
                    return existing;
                }
                canonical.remove(normalized, existingRef);
            }
            WeakReference<T> inserted = canonical.putIfAbsent(normalized, new WeakReference<>(normalized));
            if (inserted == null) {
                return normalized;
            }
        }
    }

    /**
     * Provides observability for memory tuning by exposing approximate canonical set size.
     */
    public int approximateSize() {
        return canonical.size();
    }
}
