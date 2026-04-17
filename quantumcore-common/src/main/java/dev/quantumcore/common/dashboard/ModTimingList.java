package dev.quantumcore.common.dashboard;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.profiling.StageProfiler;

import java.util.List;

@ThreadSafe
public final class ModTimingList {
    public List<String> renderRows(List<StageProfiler.ModTiming> timings) {
        return timings.stream()
            .map(it -> String.format("%s  %s", it.modId(), StageProfiler.formatSeconds(it.nanos())))
            .toList();
    }
}
