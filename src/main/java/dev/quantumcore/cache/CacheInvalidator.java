package dev.quantumcore.cache;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches common modpack content directories so cache invalidation occurs immediately after file
 * changes and does not rely on users manually clearing caches.
 * Thread-safety: thread-safe lifecycle via atomic state; watcher loop is single-threaded.
 */
public final class CacheInvalidator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final DiskCache diskCache;
    private final boolean enabled;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new WatcherThreadFactory());

    /**
     * Binds to the disk cache instance so watcher events can invalidate persisted entries
     * immediately.
     */
    public CacheInvalidator(DiskCache diskCache, boolean enabled) {
        this.diskCache = diskCache;
        this.enabled = enabled;
    }

    /**
     * Starts a lightweight watcher so edited assets invalidate stale cache entries before the next
     * major reload path.
     */
    public void start() {
        if (!enabled || !started.compareAndSet(false, true)) {
            return;
        }
        executor.execute(this::watchLoop);
    }

    private void watchLoop() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            registerIfPresent(Path.of("config"), watchService);
            registerIfPresent(Path.of("resourcepacks"), watchService);
            registerIfPresent(Path.of("mods"), watchService);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    LOGGER.info("QuantumCore detected filesystem change ({}), invalidating cache", kind.name());
                    diskCache.clearAll();
                    break;
                }
                key.reset();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            LOGGER.warn("Cache invalidation watcher could not start: {}", exception.getMessage());
        }
    }

    private void registerIfPresent(Path path, WatchService watchService) throws IOException {
        if (Files.isDirectory(path)) {
            path.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        }
    }

    private static final class WatcherThreadFactory implements ThreadFactory {
        /**
         * Uses daemon threads so filesystem monitoring never blocks JVM shutdown.
         */
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "QuantumCore-CacheInvalidator");
            thread.setDaemon(true);
            return thread;
        }
    }
}
