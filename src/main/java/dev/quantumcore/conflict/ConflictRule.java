package dev.quantumcore.conflict;

import java.util.List;

/**
 * Declares one conflict pattern so non-developers can update compatibility guidance by editing
 * JSON rules instead of rebuilding the mod.
 * Thread-safety: immutable data carrier.
 */
public final class ConflictRule {
    private String id;
    private String severity;
    private List<String> mods;
    private String message;
    private String suggestedFix;

    /**
     * Keeps a no-args constructor for Gson so rule files can evolve without custom adapters.
     */
    public ConflictRule() {
    }

    /**
     * Preserves expressive human-readable guidance and machine-checkable mod identifiers in the
     * same object so command and startup logs can share one source of truth.
     */
    public String id() {
        return id;
    }

    /**
     * Surfaces severity to prioritize fixes for pack maintainers.
     */
    public String severity() {
        return severity;
    }

    /**
     * Lists all mod IDs that must be present to trigger this rule.
     */
    public List<String> mods() {
        return mods;
    }

    /**
     * Returns the operator-facing explanation shown in logs and command output.
     */
    public String message() {
        return message;
    }

    /**
     * Provides direct remediation guidance so users can act without searching external docs.
     */
    public String suggestedFix() {
        return suggestedFix;
    }
}
