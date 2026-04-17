package dev.quantumcore.common.profiling;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.runtime.LoadStage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

@ThreadSafe
public final class StageProfiler {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path logDir;
    private final EnumMap<LoadStage, Long> stageDurations = new EnumMap<>(LoadStage.class);
    private final ConcurrentHashMap<String, Long> modNanos = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<ModTiming> recentMods = new ConcurrentLinkedDeque<>();
    private final AtomicReference<LoadStage> currentStage = new AtomicReference<>(LoadStage.BOOTSTRAP);
    private volatile long stageStartNanos = System.nanoTime();
    private volatile long launchStartNanos = System.nanoTime();
    private volatile boolean atlasLoadedFromCache;
    private volatile boolean checkpointRestored;
    private volatile int cachedModsCount;
    private volatile boolean buildingCache;

    public StageProfiler(Path logDir) {
        this.logDir = logDir;
        for (LoadStage stage : LoadStage.values()) {
            stageDurations.put(stage, 0L);
        }
    }

    public void beginLaunch() {
        launchStartNanos = System.nanoTime();
        stageStartNanos = launchStartNanos;
    }

    public void enterStage(LoadStage stage) {
        long now = System.nanoTime();
        LoadStage prev = currentStage.getAndSet(stage);
        stageDurations.compute(prev, (key, val) -> val == null ? now - stageStartNanos : val + (now - stageStartNanos));
        stageStartNanos = now;
    }

    public void completeStage(LoadStage stage) {
        long now = System.nanoTime();
        stageDurations.compute(stage, (key, val) -> val == null ? now - stageStartNanos : val + (now - stageStartNanos));
        stageStartNanos = now;
    }

    public void onModProcessed(String modId, long nanos) {
        modNanos.merge(modId, nanos, Long::sum);
        recentMods.addFirst(new ModTiming(modId, nanos));
        while (recentMods.size() > 5) {
            recentMods.removeLast();
        }
    }

    public void setAtlasLoadedFromCache(boolean value) {
        atlasLoadedFromCache = value;
    }

    public void setCheckpointRestored(boolean value) {
        checkpointRestored = value;
    }

    public void setCachedModsCount(int count) {
        cachedModsCount = count;
    }

    public void setBuildingCache(boolean value) {
        buildingCache = value;
    }

    public Snapshot snapshot(float stageProgress) {
        long elapsedNanos = System.nanoTime() - launchStartNanos;
        int completed = (int) stageDurations.values().stream().filter(v -> v > 0).count();
        double avg = completed == 0 ? 0.0 : stageDurations.values().stream().filter(v -> v > 0).mapToLong(v -> v).average().orElse(0.0);
        long remainingStages = LoadStage.values().length - completed;
        long eta = (long) (avg * Math.max(0, remainingStages - 1));

        List<ModTiming> mods = new ArrayList<>(recentMods);
        Map<LoadStage, Long> copy = Collections.unmodifiableMap(new EnumMap<>(stageDurations));
        return new Snapshot(
            currentStage.get(),
            stageProgress,
            elapsedNanos,
            eta,
            mods,
            copy,
            atlasLoadedFromCache,
            checkpointRestored,
            cachedModsCount,
            buildingCache
        );
    }

    public void writeProfileLog() {
        try {
            Files.createDirectories(logDir);
            Path file = logDir.resolve("quantumcore-profile-" + FORMATTER.format(LocalDateTime.now()) + ".txt");
            StringBuilder out = new StringBuilder();
            out.append("QuantumCore Stage Profile\n");
            for (Map.Entry<LoadStage, Long> entry : stageDurations.entrySet()) {
                out.append(entry.getKey().name()).append(": ").append(formatSeconds(entry.getValue())).append("\n");
            }
            out.append("Mod timings:\n");
            modNanos.forEach((id, nanos) -> out.append(id).append(": ").append(formatSeconds(nanos)).append("\n"));
            Files.writeString(file, out.toString());
        } catch (IOException ignored) {
        }
    }

    public static String formatSeconds(long nanos) {
        return String.format("%.1fs", nanos / 1_000_000_000.0D);
    }

    @ThreadSafe
    public record Snapshot(
        LoadStage currentStage,
        float progress,
        long elapsedNanos,
        long etaNanos,
        List<ModTiming> recentMods,
        Map<LoadStage, Long> timelineNanos,
        boolean atlasLoadedFromCache,
        boolean checkpointRestored,
        int cachedModsCount,
        boolean buildingCache
    ) {
    }

    @ThreadSafe
    public record ModTiming(String modId, long nanos) {
    }
}
