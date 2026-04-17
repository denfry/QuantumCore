package dev.quantumcore.loader;

/**
 * Defines deterministic barriers between async loading steps so parallelism does not break
 * NeoForge lifecycle ordering requirements.
 */
public enum LoadPhase {
    PREPARE,
    MODEL_PARSE,
    TEXTURE_STITCH,
    RECIPE_LOAD,
    TAG_LOAD,
    FINALIZE
}
