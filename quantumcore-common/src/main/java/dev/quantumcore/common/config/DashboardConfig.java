package dev.quantumcore.common.config;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public record DashboardConfig(
    boolean enabled,
    boolean showModTimings,
    boolean showStageTimeline,
    boolean showMemoryBar,
    boolean showEta,
    int accentColor,
    boolean completionMessage
) {
    public static DashboardConfig defaults() {
        return new DashboardConfig(true, true, true, true, true, 0x4488FF, true);
    }
}
