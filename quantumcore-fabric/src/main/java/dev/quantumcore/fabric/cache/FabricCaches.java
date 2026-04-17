package dev.quantumcore.fabric.cache;

import dev.quantumcore.common.cache.SurgicalCache;
import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.fabric.QuantumCoreFabric;

@ThreadSafe
public final class FabricCaches {
    private static final SurgicalCache SURGICAL_CACHE = new SurgicalCache(QuantumCoreFabric.runtime().incrementalCacheStore());

    private FabricCaches() {
    }

    public static SurgicalCache surgical() {
        return SURGICAL_CACHE;
    }
}
