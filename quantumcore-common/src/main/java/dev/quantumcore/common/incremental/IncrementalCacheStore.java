package dev.quantumcore.common.incremental;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.util.HashingUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ThreadSafe
public final class IncrementalCacheStore {
    private final boolean enabled;
    private final Path root;
    private final ModHashIndex modHashIndex;
    private final Cache<String, byte[]> hotCache;

    public IncrementalCacheStore(Path root, boolean enabled) {
        this.enabled = enabled;
        this.root = root;
        this.modHashIndex = new ModHashIndex(root);
        this.hotCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build();
        this.modHashIndex.load();
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean shouldUseCache(String modId, String version, Path jarPath) {
        if (!enabled) {
            return false;
        }
        try {
            long hash = HashingUtils.xxh3(jarPath);
            return modHashIndex.matches(modId, version, hash);
        } catch (IOException exception) {
            return false;
        }
    }

    public void updateModHash(String modId, String version, Path jarPath) {
        if (!enabled) {
            return;
        }
        try {
            long hash = HashingUtils.xxh3(jarPath);
            modHashIndex.put(modId, version, hash);
            modHashIndex.save();
        } catch (IOException ignored) {
        }
    }

    public Optional<byte[]> readEntry(String cacheKey) {
        if (!enabled) {
            return Optional.empty();
        }
        byte[] inMemory = hotCache.getIfPresent(cacheKey);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }
        Path file = fileFor(cacheKey);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            hotCache.put(cacheKey, bytes);
            return Optional.of(bytes);
        } catch (IOException exception) {
            safeDelete(file);
            return Optional.empty();
        }
    }

    public void writeEntry(String cacheKey, byte[] bytes) {
        if (!enabled) {
            return;
        }
        Path file = fileFor(cacheKey);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
            hotCache.put(cacheKey, bytes);
        } catch (IOException ignored) {
        }
    }

    private Path fileFor(String key) {
        String safe = key.replace(':', '_').replace('/', '_');
        return root.resolve("incremental").resolve(safe + ".bin");
    }

    private static void safeDelete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
