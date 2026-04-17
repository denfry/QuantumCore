package dev.quantumcore.common.checkpoint;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public record CheckpointManifest(long modlistHash, int formatVersion, long createdAtEpochMs) {
}
