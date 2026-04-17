package dev.quantumcore.common.runtime;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public enum RuntimeMode {
    DEEP_BOOT,
    FAST_RESUME,
    CACHE_BUILDING
}
