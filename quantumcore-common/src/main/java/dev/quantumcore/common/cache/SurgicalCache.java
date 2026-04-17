package dev.quantumcore.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.incremental.IncrementalCacheStore;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ThreadSafe
public final class SurgicalCache {
    private final IncrementalCacheStore store;
    private final Cache<String, byte[]> memory;

    public SurgicalCache(IncrementalCacheStore store) {
        this.store = store;
        this.memory = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(20_000)
            .build();
    }

    public Optional<String> getString(String key) {
        byte[] hit = memory.getIfPresent(key);
        if (hit != null) {
            return Optional.of(new String(hit, StandardCharsets.UTF_8));
        }
        Optional<byte[]> disk = store.readEntry(key);
        disk.ifPresent(bytes -> memory.put(key, bytes));
        return disk.map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    public void putString(String key, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        memory.put(key, bytes);
        store.writeEntry(key, bytes);
    }
}
