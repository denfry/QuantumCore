package dev.quantumcore.cache;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.Map;

/**
 * Captures cache metadata so cached binary payloads are reused only when both file content and
 * modpack fingerprint still match the state that produced them.
 * Thread-safety: not thread-safe on its own; guarded by {@link DiskCache} synchronization.
 */
public final class CacheManifest {
    private String cacheVersion;
    private String modpackFingerprint;
    private Object2LongOpenHashMap<String> sourceHashes;
    private Object2LongOpenHashMap<String> entryFingerprints;

    /**
     * Initializes maps with explicit default values so "missing key" is distinguishable from real
     * hash values.
     */
    public CacheManifest() {
        this.cacheVersion = "1";
        this.modpackFingerprint = "";
        this.sourceHashes = new Object2LongOpenHashMap<>();
        this.entryFingerprints = new Object2LongOpenHashMap<>();
        this.sourceHashes.defaultReturnValue(Long.MIN_VALUE);
        this.entryFingerprints.defaultReturnValue(Long.MIN_VALUE);
    }

    /**
     * Uses versioning and fingerprinting together so cache invalidation remains deterministic
     * across mod updates and not just local file edits.
     */
    public boolean isCompatible(String expectedVersion, String expectedFingerprint) {
        return expectedVersion.equals(cacheVersion) && expectedFingerprint.equals(modpackFingerprint);
    }

    /**
     * Resets the manifest when cache format or modpack composition changes so stale payloads are
     * never trusted.
     */
    public void resetFor(String newVersion, String newFingerprint) {
        this.cacheVersion = newVersion;
        this.modpackFingerprint = newFingerprint;
        this.sourceHashes.clear();
        this.entryFingerprints.clear();
    }

    /**
     * Returns the last known file hash so callers can decide whether cached payloads are still
     * usable.
     */
    public long sourceHash(String key) {
        return sourceHashes.getLong(key);
    }

    /**
     * Stores source hashes so change detection remains O(1) during cache lookups.
     */
    public void updateSourceHash(String key, long hash) {
        sourceHashes.put(key, hash);
    }

    /**
     * Provides quick fingerprint checks for synthetic cache entries that are not direct file paths.
     */
    public long entryFingerprint(String key) {
        return entryFingerprints.getLong(key);
    }

    /**
     * Updates entry fingerprints to bind binary payloads to the exact source state that produced
     * them.
     */
    public void updateEntryFingerprint(String key, long fingerprint) {
        entryFingerprints.put(key, fingerprint);
    }

    /**
     * Exposes version information for diagnostics when users report invalidation behavior.
     */
    public String cacheVersion() {
        return cacheVersion;
    }

    /**
     * Exposes modpack fingerprint for diagnostics when cache compatibility is questioned.
     */
    public String modpackFingerprint() {
        return modpackFingerprint;
    }

    /**
     * Enables debug dumps and tests to inspect tracked source hashes without reflection.
     */
    public Map<String, Long> sourceHashesView() {
        return sourceHashes;
    }

    /**
     * Enables debug dumps and tests to inspect entry fingerprints without reflection.
     */
    public Map<String, Long> entryFingerprintsView() {
        return entryFingerprints;
    }
}
