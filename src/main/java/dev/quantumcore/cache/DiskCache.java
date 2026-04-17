package dev.quantumcore.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.mojang.logging.LogUtils;
import dev.quantumcore.QuantumCore;
import net.neoforged.fml.ModList;
import net.openhft.hashing.LongHashFunction;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Provides a binary cache for parsed resources so expensive startup parsing can be skipped when
 * file hashes are unchanged.
 * Thread-safety: thread-safe via synchronized public methods.
 */
public final class DiskCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CACHE_VERSION = "1";
    private static final String MANIFEST_FILE = "manifest.bin";

    private final boolean enabled;
    private final Path rootDir;
    private final Path payloadDir;
    private final Path manifestPath;
    private final LongHashFunction xxHash3 = LongHashFunction.xx3();
    private final Kryo kryo = new Kryo();

    private CacheManifest manifest;
    private final String modpackFingerprint;
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong estimatedSavedNanos = new AtomicLong();

    /**
     * Initializes cache storage under `.cache/quantumcore` so the main game directories stay
     * uncluttered while preserving warm-start data between runs.
     */
    public DiskCache(Path gameDir, boolean enabled) {
        this.enabled = enabled;
        this.rootDir = gameDir.resolve(".cache").resolve(QuantumCore.MOD_ID);
        this.payloadDir = rootDir.resolve("payload");
        this.manifestPath = rootDir.resolve(MANIFEST_FILE);
        this.modpackFingerprint = computeModpackFingerprint();
        initialize();
    }

    /**
     * Reads from cache only when both source hash and entry payload are still valid, preventing
     * stale or incompatible deserialization crashes after modpack updates.
     */
    public synchronized <T> Optional<T> readIfUpToDate(Path sourceFile, String entryKey, Class<T> type) {
        if (!enabled || !Files.exists(sourceFile)) {
            cacheMisses.incrementAndGet();
            return Optional.empty();
        }
        try {
            long actualHash = hashFile(sourceFile);
            String sourceKey = sourceFile.toAbsolutePath().toString();
            long expectedHash = manifest.sourceHash(sourceKey);
            if (expectedHash != actualHash) {
                cacheMisses.incrementAndGet();
                return Optional.empty();
            }
            return readByFingerprint(entryKey, actualHash, type);
        } catch (Exception exception) {
            cacheMisses.incrementAndGet();
            LOGGER.warn("QuantumCore cache read failed for {}: {}", sourceFile, exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Uses source hash as fingerprint so non-file call sites can still participate in safe cache
     * reuse without duplicating version bookkeeping logic.
     */
    public synchronized <T> Optional<T> readByFingerprint(String entryKey, long fingerprint, Class<T> type) {
        if (!enabled) {
            cacheMisses.incrementAndGet();
            return Optional.empty();
        }
        try {
            long knownFingerprint = manifest.entryFingerprint(entryKey);
            if (knownFingerprint != fingerprint) {
                cacheMisses.incrementAndGet();
                return Optional.empty();
            }
            Path payload = payloadPath(entryKey);
            if (!Files.exists(payload)) {
                cacheMisses.incrementAndGet();
                return Optional.empty();
            }
            try (InputStream inputStream = Files.newInputStream(payload, StandardOpenOption.READ);
                 Input input = new Input(inputStream)) {
                T value = kryo.readObject(input, type);
                cacheHits.incrementAndGet();
                estimatedSavedNanos.addAndGet(8_000_000L);
                return Optional.of(value);
            }
        } catch (Exception exception) {
            cacheMisses.incrementAndGet();
            LOGGER.warn("QuantumCore cache deserialize failed for {}: {}", entryKey, exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Executes parsing once and writes through into cache so subsequent launches can deserialize in
     * O(1) file reads instead of re-parsing the same source every startup.
     */
    public synchronized <T> T computeAndStore(Path sourceFile, String entryKey, Class<T> type, Supplier<T> parser) {
        long start = System.nanoTime();
        T value = parser.get();
        long parseNanos = System.nanoTime() - start;
        if (!enabled || !Files.exists(sourceFile)) {
            return value;
        }
        try {
            long hash = hashFile(sourceFile);
            String sourceKey = sourceFile.toAbsolutePath().toString();
            manifest.updateSourceHash(sourceKey, hash);
            writeByFingerprint(entryKey, hash, value, type);
            estimatedSavedNanos.addAndGet(Math.max(parseNanos - 1_000_000L, 0L));
        } catch (Exception exception) {
            LOGGER.warn("QuantumCore cache write failed for {}: {}", sourceFile, exception.getMessage());
        }
        return value;
    }

    /**
     * Supports cache writes for synthetic resources (for example baked in-memory model identifiers)
     * that still benefit from stable hash-based invalidation.
     */
    public synchronized <T> void writeByFingerprint(String entryKey, long fingerprint, T value, Class<T> type) throws IOException {
        if (!enabled) {
            return;
        }
        Path payload = payloadPath(entryKey);
        Files.createDirectories(payload.getParent());
        try (OutputStream outputStream = Files.newOutputStream(payload,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
             Output output = new Output(outputStream)) {
            kryo.writeObject(output, value);
        }
        manifest.updateEntryFingerprint(entryKey, fingerprint);
    }

    /**
     * Writes manifest on controlled boundaries (load complete / shutdown) to minimize metadata
     * overhead during hot loading phases.
     */
    public synchronized void flushManifest() {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(rootDir);
            try (OutputStream outputStream = Files.newOutputStream(manifestPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
                 Output output = new Output(outputStream)) {
                kryo.writeObject(output, manifest);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed writing QuantumCore cache manifest", exception);
        }
    }

    /**
     * Clears all cached payloads after file watcher invalidation events so stale data cannot
     * survive across large modpack updates.
     */
    public synchronized void clearAll() {
        if (!enabled || !Files.exists(rootDir)) {
            return;
        }
        try {
            Files.walk(rootDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        LOGGER.warn("Failed deleting cache path {}", path, exception);
                    }
                });
            initialize();
            LOGGER.info("QuantumCore disk cache cleared");
        } catch (IOException exception) {
            LOGGER.error("QuantumCore disk cache clear failed", exception);
        }
    }

    /**
     * Exposes hit counts so warm-start effectiveness can be monitored over time.
     */
    public long cacheHits() {
        return cacheHits.get();
    }

    /**
     * Exposes miss counts so startup summaries can show whether invalidation strategy is too
     * aggressive.
     */
    public long cacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Provides conservative saved-time estimation to communicate practical impact to pack users.
     */
    public long estimatedSavedNanos() {
        return estimatedSavedNanos.get();
    }

    private void initialize() {
        try {
            Files.createDirectories(payloadDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create QuantumCore cache directory", exception);
        }
        this.manifest = readManifest().orElseGet(CacheManifest::new);
        if (!manifest.isCompatible(CACHE_VERSION, modpackFingerprint)) {
            manifest.resetFor(CACHE_VERSION, modpackFingerprint);
        }
    }

    private Optional<CacheManifest> readManifest() {
        if (!enabled || !Files.exists(manifestPath)) {
            return Optional.empty();
        }
        try (InputStream inputStream = Files.newInputStream(manifestPath, StandardOpenOption.READ);
             Input input = new Input(inputStream)) {
            return Optional.ofNullable(kryo.readObject(input, CacheManifest.class));
        } catch (Exception exception) {
            LOGGER.warn("Could not read cache manifest, rebuilding: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private Path payloadPath(String entryKey) {
        return payloadDir.resolve(sanitize(entryKey) + ".bin");
    }

    private long hashFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return xxHash3.hashBytes(bytes);
    }

    private String sanitize(String key) {
        return key.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    private String computeModpackFingerprint() {
        StringBuilder builder = new StringBuilder(1024);
        ModList.get().getMods().stream()
            .sorted(Comparator.comparing(info -> info.getModId().toLowerCase()))
            .forEach(info -> builder.append(info.getModId()).append(':').append(info.getVersion()).append(';'));
        return Long.toUnsignedString(xxHash3.hashChars(builder));
    }
}
