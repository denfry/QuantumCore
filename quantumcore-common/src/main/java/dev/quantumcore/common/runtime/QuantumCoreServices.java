package dev.quantumcore.common.runtime;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

@ThreadSafe
public final class QuantumCoreServices {
    private static final AtomicReference<QuantumCoreRuntime> RUNTIME = new AtomicReference<>();

    private QuantumCoreServices() {
    }

    public static QuantumCoreRuntime init(Path gameDir) {
        QuantumCoreRuntime runtime = new QuantumCoreRuntime(gameDir);
        RUNTIME.set(runtime);
        return runtime;
    }

    public static QuantumCoreRuntime runtime() {
        QuantumCoreRuntime runtime = RUNTIME.get();
        if (runtime == null) {
            throw new IllegalStateException("QuantumCore runtime has not been initialized");
        }
        return runtime;
    }
}
