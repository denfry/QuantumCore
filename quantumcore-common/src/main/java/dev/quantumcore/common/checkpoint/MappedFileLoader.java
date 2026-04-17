package dev.quantumcore.common.checkpoint;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@ThreadSafe
public final class MappedFileLoader {
    public byte[] load(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            byte[] bytes = new byte[(int) channel.size()];
            buffer.get(bytes);
            return bytes;
        }
    }
}
