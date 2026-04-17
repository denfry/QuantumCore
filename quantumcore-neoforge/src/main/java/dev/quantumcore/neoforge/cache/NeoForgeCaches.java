package dev.quantumcore.neoforge.cache;

import dev.quantumcore.common.cache.SurgicalCache;
import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.neoforge.QuantumCoreNeoForge;

@ThreadSafe
public final class NeoForgeCaches {
    private static final SurgicalCache SURGICAL_CACHE = new SurgicalCache(QuantumCoreNeoForge.runtime().incrementalCacheStore());

    private NeoForgeCaches() {
    }

    public static SurgicalCache surgical() {
        return SURGICAL_CACHE;
    }
}
