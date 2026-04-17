package dev.quantumcore.common.cache;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.util.Collections;
import java.util.Map;

@ThreadSafe
public record AtlasManifest(
    String atlasName,
    long atlasHash,
    int width,
    int height,
    Map<String, SpriteUv> spriteUvs
) {
    public AtlasManifest {
        spriteUvs = Collections.unmodifiableMap(spriteUvs);
    }

    @ThreadSafe
    public record SpriteUv(float u0, float v0, float u1, float v1) {
    }
}
