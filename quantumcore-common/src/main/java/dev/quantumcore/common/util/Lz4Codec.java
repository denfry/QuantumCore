package dev.quantumcore.common.util;

import dev.quantumcore.common.concurrent.ThreadSafe;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

@ThreadSafe
public final class Lz4Codec {
    private static final LZ4Factory FACTORY = LZ4Factory.fastestInstance();

    private Lz4Codec() {
    }

    public static byte[] compress(byte[] source) {
        LZ4Compressor compressor = FACTORY.fastCompressor();
        int max = compressor.maxCompressedLength(source.length);
        byte[] out = new byte[max + Integer.BYTES];
        out[0] = (byte) (source.length);
        out[1] = (byte) (source.length >>> 8);
        out[2] = (byte) (source.length >>> 16);
        out[3] = (byte) (source.length >>> 24);
        int written = compressor.compress(source, 0, source.length, out, Integer.BYTES, max);
        byte[] packed = new byte[Integer.BYTES + written];
        System.arraycopy(out, 0, packed, 0, packed.length);
        return packed;
    }

    public static byte[] decompress(byte[] packed) {
        int len = ((packed[0] & 0xFF))
            | ((packed[1] & 0xFF) << 8)
            | ((packed[2] & 0xFF) << 16)
            | ((packed[3] & 0xFF) << 24);
        byte[] out = new byte[len];
        LZ4FastDecompressor decompressor = FACTORY.fastDecompressor();
        decompressor.decompress(packed, Integer.BYTES, out, 0, len);
        return out;
    }
}
