package dev.quantumcore.common.util;

import dev.quantumcore.common.concurrent.ThreadSafe;
import net.openhft.hashing.LongHashFunction;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@ThreadSafe
public final class HashingUtils {
    private static final LongHashFunction XXH3 = LongHashFunction.xx3();

    private HashingUtils() {
    }

    public static long xxh3(byte[] data) {
        return XXH3.hashBytes(data);
    }

    public static long xxh3(Path path) throws IOException {
        return XXH3.hashBytes(Files.readAllBytes(path));
    }

    public static long xxh3(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return xxh3(bytes);
    }

    public static String sha1Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 not available", exception);
        }
    }
}
