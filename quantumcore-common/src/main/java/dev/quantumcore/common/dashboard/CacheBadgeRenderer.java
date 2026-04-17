package dev.quantumcore.common.dashboard;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.profiling.StageProfiler;

import java.util.ArrayList;
import java.util.List;

@ThreadSafe
public final class CacheBadgeRenderer {
    public List<String> badges(StageProfiler.Snapshot snapshot) {
        List<String> values = new ArrayList<>();
        values.add(snapshot.atlasLoadedFromCache() ? "[ATLAS OK]" : "[ATLAS --]");
        values.add(snapshot.checkpointRestored() ? "[CKPT OK]" : "[CKPT --]");
        values.add("[" + snapshot.cachedModsCount() + " CACHED]");
        if (snapshot.buildingCache()) {
            values.add("[BUILDING]");
        }
        return values;
    }
}
