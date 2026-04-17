package dev.quantumcore.common.memory;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

@ThreadSafe
public final class MemoryMonitor {
    private final boolean enabled;
    private final DeepInterner interner = new DeepInterner();
    private final NbtDeduplicator nbtDeduplicator = new NbtDeduplicator();

    public MemoryMonitor(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public <T> T intern(T value) {
        return enabled ? interner.intern(value) : value;
    }

    public byte[] deduplicateNbt(byte[] bytes) {
        return enabled ? nbtDeduplicator.deduplicate(bytes) : bytes;
    }

    public HeapSnapshot snapshot() {
        MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
        MemoryUsage usage = bean.getHeapMemoryUsage();
        return new HeapSnapshot(usage.getUsed(), usage.getCommitted(), usage.getMax(), interner.size(), nbtDeduplicator.size());
    }

    @ThreadSafe
    public record HeapSnapshot(long used, long committed, long max, int internerSize, int nbtPoolSize) {
        public double pressure() {
            return max <= 0 ? 0.0 : (double) used / (double) max;
        }
    }
}
