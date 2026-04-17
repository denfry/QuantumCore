package dev.quantumcore.common.memory;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ThreadSafe
public final class DeepInterner {
    private final Map<Object, WeakReference<Object>> weakPool = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T intern(T value) {
        WeakReference<Object> existingRef = weakPool.get(value);
        if (existingRef != null) {
            Object existing = existingRef.get();
            if (existing != null) {
                return (T) existing;
            }
        }
        weakPool.put(value, new WeakReference<>(value));
        return value;
    }

    public int size() {
        return weakPool.size();
    }
}
