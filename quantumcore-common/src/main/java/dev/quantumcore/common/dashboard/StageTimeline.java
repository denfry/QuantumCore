package dev.quantumcore.common.dashboard;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.runtime.LoadStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ThreadSafe
public final class StageTimeline {
    public List<String> renderBars(Map<LoadStage, Long> timeline) {
        long max = timeline.values().stream().mapToLong(v -> v).max().orElse(1L);
        List<String> lines = new ArrayList<>();
        for (LoadStage stage : LoadStage.values()) {
            long nanos = timeline.getOrDefault(stage, 0L);
            int len = (int) Math.round((nanos * 16.0) / max);
            String bar = "#".repeat(Math.max(0, len));
            lines.add(String.format("%-12s %-16s %s", stage.name(), bar, nanos <= 0 ? "--" : String.format("%.1fs", nanos / 1_000_000_000.0)));
        }
        return lines;
    }
}
