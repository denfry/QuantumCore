package dev.quantumcore.common.checkpoint;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.util.Lz4Codec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

@ThreadSafe
public final class JvmCheckpoint {
    private final Path root;
    private final boolean enabled;
    private final MappedFileLoader mappedFileLoader = new MappedFileLoader();
    private final ThreadLocal<Kryo> kryo = ThreadLocal.withInitial(Kryo::new);

    public JvmCheckpoint(Path root, boolean enabled) {
        this.root = root;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Path checkpointPath(long modlistHash) {
        return root.resolve("checkpoint_" + Long.toUnsignedString(modlistHash) + ".bin");
    }

    public Path manifestPath(long modlistHash) {
        return root.resolve("checkpoint_" + Long.toUnsignedString(modlistHash) + ".manifest");
    }

    public void write(long modlistHash, int formatVersion, Object state) {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(root);
            Output output = new Output(8192, -1);
            kryo.get().writeClassAndObject(output, state);
            output.flush();
            byte[] compressed = Lz4Codec.compress(output.toBytes());
            Files.write(checkpointPath(modlistHash), compressed);
            writeManifest(new CheckpointManifest(modlistHash, formatVersion, Instant.now().toEpochMilli()), manifestPath(modlistHash));
        } catch (Exception exception) {
            delete(modlistHash);
        }
    }

    public Optional<Object> read(long modlistHash, int expectedFormat) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            CheckpointManifest manifest = readManifest(manifestPath(modlistHash));
            if (manifest.formatVersion() != expectedFormat || manifest.modlistHash() != modlistHash) {
                delete(modlistHash);
                return Optional.empty();
            }
            byte[] compressed = mappedFileLoader.load(checkpointPath(modlistHash));
            byte[] bytes = Lz4Codec.decompress(compressed);
            Input input = new Input(bytes);
            return Optional.ofNullable(kryo.get().readClassAndObject(input));
        } catch (Exception exception) {
            delete(modlistHash);
            return Optional.empty();
        }
    }

    public void delete(long modlistHash) {
        try {
            Files.deleteIfExists(checkpointPath(modlistHash));
            Files.deleteIfExists(manifestPath(modlistHash));
        } catch (IOException ignored) {
        }
    }

    private static void writeManifest(CheckpointManifest manifest, Path path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeLong(manifest.modlistHash());
            out.writeInt(manifest.formatVersion());
            out.writeLong(manifest.createdAtEpochMs());
        }
    }

    private static CheckpointManifest readManifest(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            return new CheckpointManifest(in.readLong(), in.readInt(), in.readLong());
        }
    }
}
