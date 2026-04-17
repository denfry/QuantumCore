package dev.quantumcore.common.pool;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.util.ArrayDeque;
import java.util.function.Supplier;

@ThreadSafe
public final class ObjectPool<T> {
    private final ThreadLocal<ArrayDeque<T>> pool;
    private final Supplier<T> factory;

    public ObjectPool(Supplier<T> factory, int initialSize) {
        this.factory = factory;
        this.pool = ThreadLocal.withInitial(() -> {
            ArrayDeque<T> deque = new ArrayDeque<>(Math.max(initialSize, 4));
            for (int i = 0; i < initialSize; i++) {
                deque.push(factory.get());
            }
            return deque;
        });
    }

    public T borrow() {
        ArrayDeque<T> deque = pool.get();
        return deque.isEmpty() ? factory.get() : deque.pop();
    }

    public void release(T value) {
        pool.get().push(value);
    }
}
