package dev.quantumcore.loader;

import com.mojang.logging.LogUtils;
import dev.quantumcore.QuantumCore;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Orchestrates async loading tasks on a dedicated ForkJoinPool so expensive data loading never
 * stalls the main game thread.
 * Thread-safety: thread-safe for concurrent task submissions.
 */
public final class ParallelLoader implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean enabled;
    private final ForkJoinPool pool;
    private final EnumMap<LoadPhase, CompletableFuture<Void>> phaseBarriers = new EnumMap<>(LoadPhase.class);

    /**
     * Creates an async-mode fork-join pool to prioritize event-style continuations and avoid
     * producer starvation under heavy `CompletableFuture` chains.
     */
    public ParallelLoader(boolean enabled) {
        this.enabled = enabled;
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.pool = new ForkJoinPool(
            parallelism,
            new QuantumCoreForkJoinFactory(),
            new QuantumCoreExceptionHandler(),
            true
        );

        phaseBarriers.put(LoadPhase.PREPARE, CompletableFuture.completedFuture(null));
        phaseBarriers.put(LoadPhase.MODEL_PARSE, phaseBarriers.get(LoadPhase.PREPARE));
        phaseBarriers.put(LoadPhase.TEXTURE_STITCH, phaseBarriers.get(LoadPhase.MODEL_PARSE));
        phaseBarriers.put(LoadPhase.RECIPE_LOAD, phaseBarriers.get(LoadPhase.TEXTURE_STITCH));
        phaseBarriers.put(LoadPhase.TAG_LOAD, phaseBarriers.get(LoadPhase.RECIPE_LOAD));
        phaseBarriers.put(LoadPhase.FINALIZE, phaseBarriers.get(LoadPhase.TAG_LOAD));
    }

    /**
     * Schedules one task behind its phase gate so mods can submit work freely while still obeying
     * lifecycle boundaries.
     */
    public <T> CompletableFuture<T> submit(LoadPhase phase, Supplier<T> task) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(task, "task");
        if (!enabled) {
            try {
                return CompletableFuture.completedFuture(task.get());
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        CompletableFuture<Void> gate = phaseBarriers.get(phase);
        return gate.thenCompose(unused -> CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            try {
                return task.get();
            } catch (Throwable throwable) {
                throw new CompletionException("QuantumCore async task failed in phase " + phase, throwable);
            } finally {
                long nanos = System.nanoTime() - start;
                QuantumCore.instance().recordPhaseNanos(phase, nanos);
            }
        }, pool));
    }

    /**
     * Runs and joins a phase as a single barrier future so callers can chain progress without
     * blocking the main thread.
     */
    public CompletableFuture<Void> runPhase(LoadPhase phase, List<? extends Supplier<?>> tasks) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(tasks, "tasks");

        List<CompletableFuture<?>> futures = new ArrayList<>(tasks.size());
        for (Supplier<?> task : tasks) {
            futures.add(submit(phase, task));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .whenComplete((unused, throwable) -> {
                if (throwable != null) {
                    LOGGER.error("Phase {} completed with errors", phase, throwable);
                } else {
                    LOGGER.info("Phase {} completed with {} tasks", phase, futures.size());
                }
            });
        phaseBarriers.put(nextPhase(phase), all);
        return all;
    }

    /**
     * Aggregates independent futures into a single completion handle with centralized error
     * logging, allowing callers to continue chained async orchestration.
     */
    public CompletableFuture<Void> awaitAll(List<? extends CompletableFuture<?>> futures) {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return all.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.error("ParallelLoader awaitAll failed", throwable);
            }
        });
    }

    private LoadPhase nextPhase(LoadPhase phase) {
        return switch (phase) {
            case PREPARE -> LoadPhase.MODEL_PARSE;
            case MODEL_PARSE -> LoadPhase.TEXTURE_STITCH;
            case TEXTURE_STITCH -> LoadPhase.RECIPE_LOAD;
            case RECIPE_LOAD -> LoadPhase.TAG_LOAD;
            case TAG_LOAD -> LoadPhase.FINALIZE;
            case FINALIZE -> LoadPhase.FINALIZE;
        };
    }

    /**
     * Explicit close ensures daemon threads and queued tasks cannot leak across integrated server
     * restarts in dev environments.
     */
    @Override
    public void close() {
        pool.shutdown();
    }

    private static final class QuantumCoreForkJoinFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        private static final AtomicInteger COUNTER = new AtomicInteger(1);

        /**
         * Assigns explicit thread names so async profiling clearly attributes CPU time to
         * QuantumCore loader workers.
         */
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName("QuantumCore-Loader-" + COUNTER.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class QuantumCoreExceptionHandler implements UncaughtExceptionHandler {
        /**
         * Routes otherwise-lost worker exceptions to logs so failed async tasks are visible.
         */
        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            LOGGER.error("Uncaught QuantumCore loader error in {}", thread.getName(), throwable);
        }
    }
}
