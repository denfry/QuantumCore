package dev.quantumcore.common.cache;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.util.HashingUtils;
import dev.quantumcore.common.util.Lz4Codec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ThreadSafe
public final class GpuAtlasCache {
    private final Path root;
    private final boolean enabled;

    public GpuAtlasCache(Path root, boolean enabled) {
        this.root = root;
        this.enabled = enabled;
    }

    public void writeAtlas(String atlasName, ByteBuffer rgbaData, int width, int height, Map<String, AtlasManifest.SpriteUv> uvs, long inputHash) {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(root);
            byte[] raw = new byte[rgbaData.remaining()];
            rgbaData.duplicate().get(raw);
            byte[] compressed = Lz4Codec.compress(raw);
            Path atlasFile = root.resolve("atlas_" + atlasName + "_" + Long.toUnsignedString(inputHash) + ".lz4");
            Files.write(atlasFile, compressed);

            AtlasManifest manifest = new AtlasManifest(atlasName, HashingUtils.xxh3(raw), width, height, uvs);
            writeManifest(manifest, root.resolve("atlas_" + atlasName + "_" + Long.toUnsignedString(inputHash) + ".manifest"));
        } catch (Exception exception) {
            safeDeleteAtlas(atlasName, inputHash);
        }
    }

    public Optional<CachedAtlas> readAtlas(String atlasName, long inputHash) {
        if (!enabled) {
            return Optional.empty();
        }
        Path atlasFile = root.resolve("atlas_" + atlasName + "_" + Long.toUnsignedString(inputHash) + ".lz4");
        Path manifestFile = root.resolve("atlas_" + atlasName + "_" + Long.toUnsignedString(inputHash) + ".manifest");
        if (!Files.exists(atlasFile) || !Files.exists(manifestFile)) {
            return Optional.empty();
        }
        try {
            AtlasManifest manifest = readManifest(manifestFile);
            byte[] compressed = Files.readAllBytes(atlasFile);
            byte[] raw = Lz4Codec.decompress(compressed);
            if (HashingUtils.xxh3(raw) != manifest.atlasHash()) {
                safeDeleteAtlas(atlasName, inputHash);
                return Optional.empty();
            }
            return Optional.of(new CachedAtlas(ByteBuffer.wrap(raw), manifest));
        } catch (Exception exception) {
            safeDeleteAtlas(atlasName, inputHash);
            return Optional.empty();
        }
    }

    private void safeDeleteAtlas(String atlasName, long inputHash) {
        try {
            Files.deleteIfExists(root.resolve("atlas_" + atlasName + "_" + Long.toUnsignedString(inputHash) + ".lz4"));
            Files.deleteIfExists(root.resolve("atlas_" + atlasName + "_" + Long.toUnsignedString(inputHash) + ".manifest"));
        } catch (IOException ignored) {
        }
    }

    private static void writeManifest(AtlasManifest manifest, Path file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeUTF(manifest.atlasName());
            out.writeLong(manifest.atlasHash());
            out.writeInt(manifest.width());
            out.writeInt(manifest.height());
            out.writeInt(manifest.spriteUvs().size());
            for (Map.Entry<String, AtlasManifest.SpriteUv> entry : manifest.spriteUvs().entrySet()) {
                out.writeUTF(entry.getKey());
                AtlasManifest.SpriteUv uv = entry.getValue();
                out.writeFloat(uv.u0());
                out.writeFloat(uv.v0());
                out.writeFloat(uv.u1());
                out.writeFloat(uv.v1());
            }
        }
    }

    private static AtlasManifest readManifest(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            String name = in.readUTF();
            long hash = in.readLong();
            int width = in.readInt();
            int height = in.readInt();
            int size = in.readInt();
            Map<String, AtlasManifest.SpriteUv> map = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                String key = in.readUTF();
                map.put(key, new AtlasManifest.SpriteUv(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
            }
            return new AtlasManifest(name, hash, width, height, map);
        }
    }

    @ThreadSafe
    public record CachedAtlas(ByteBuffer rgbaData, AtlasManifest manifest) {
    }
}
