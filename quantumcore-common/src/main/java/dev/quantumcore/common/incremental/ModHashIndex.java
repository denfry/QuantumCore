package dev.quantumcore.common.incremental;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ThreadSafe
public final class ModHashIndex {
    private final Path path;
    private final Map<String, Entry> byMod = new HashMap<>();

    public ModHashIndex(Path root) {
        this.path = root.resolve("mod-hash-index.bin");
    }

    public synchronized void load() {
        if (!Files.exists(path)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            byMod.clear();
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                String modId = in.readUTF();
                byMod.put(modId, new Entry(in.readUTF(), in.readLong()));
            }
        } catch (Exception exception) {
            byMod.clear();
            safeDelete();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(path.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
                out.writeInt(byMod.size());
                for (Map.Entry<String, Entry> entry : byMod.entrySet()) {
                    out.writeUTF(entry.getKey());
                    out.writeUTF(entry.getValue().version());
                    out.writeLong(entry.getValue().jarHash());
                }
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized boolean matches(String modId, String version, long jarHash) {
        Entry entry = byMod.get(modId);
        return entry != null && entry.version.equals(version) && entry.jarHash == jarHash;
    }

    public synchronized void put(String modId, String version, long jarHash) {
        byMod.put(modId, new Entry(version, jarHash));
    }

    public synchronized Map<String, Entry> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(byMod));
    }

    private void safeDelete() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    @ThreadSafe
    public record Entry(String version, long jarHash) {
    }
}
