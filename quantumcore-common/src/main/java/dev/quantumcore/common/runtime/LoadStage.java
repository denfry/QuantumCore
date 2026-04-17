package dev.quantumcore.common.runtime;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public enum LoadStage {
    BOOTSTRAP,
    REGISTRIES,
    MODELS,
    TAGS,
    RECIPES,
    TEXTURES,
    FINALIZING
}
