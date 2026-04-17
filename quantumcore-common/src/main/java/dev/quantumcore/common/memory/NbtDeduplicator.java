package dev.quantumcore.common.memory;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.util.HashingUtils;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ThreadSafe
public final class NbtDeduplicator {
    private final Map<String, SoftReference<byte[]>> pool = new ConcurrentHashMap<>();

    public byte[] deduplicate(byte[] nbtPayload) {
        String key = HashingUtils.sha1Hex(nbtPayload);
        SoftReference<byte[]> current = pool.get(key);
        if (current != null) {
            byte[] hit = current.get();
            if (hit != null) {
                return hit;
            }
        }
        pool.put(key, new SoftReference<>(nbtPayload));
        return nbtPayload;
    }

    public int size() {
        return pool.size();
    }
}
