package dev.quantumcore.common.dashboard;

import dev.quantumcore.common.concurrent.ThreadSafe;
import dev.quantumcore.common.config.DashboardConfig;
import dev.quantumcore.common.profiling.StageProfiler;

import java.util.List;

@ThreadSafe
public final class QuantumDashboard {
    private final DashboardConfig config;
    private final CacheBadgeRenderer badgeRenderer;
    private final ModTimingList modTimingList;
    private final StageTimeline stageTimeline;

    public QuantumDashboard(DashboardConfig config) {
        this.config = config;
        this.badgeRenderer = new CacheBadgeRenderer();
        this.modTimingList = new ModTimingList();
        this.stageTimeline = new StageTimeline();
    }

    public void render(DashboardSurface surface, StageProfiler.Snapshot snapshot, float delta) {
        if (!config.enabled()) {
            return;
        }

        int w = surface.width();
        int h = surface.height();
        surface.fill(0, 0, w, h, 0xFF0A0A14);
        surface.fill(0, 0, w, 28, 0xFF111120);
        surface.text("QUANTUMCORE", 8, 10, 0xFFFFFFFF);

        int badgeX = w - 120;
        List<String> badges = badgeRenderer.badges(snapshot);
        for (String badge : badges) {
            surface.text(badge, badgeX, 10, 0xFF77FFAA);
            badgeX -= 95;
        }

        int barX = 12;
        int barY = 58;
        int barW = w - 24;
        int barH = 16;
        surface.fill(barX, barY, barX + barW, barY + barH, 0xFF1B2233);
        int fill = (int) (barW * Math.max(0F, Math.min(1F, snapshot.progress())));
        int shimmer = (int) ((Math.sin((System.nanoTime() / 1_000_000_000.0) * 4.0 + delta) + 1.0) * 24.0);
        int color = (0xFF000000 | config.accentColor()) + shimmer;
        surface.fill(barX, barY, barX + fill, barY + barH, color);

        surface.text("Loading " + snapshot.currentStage().name(), 14, 84, 0xFFE8EEF8);
        surface.text((int) (snapshot.progress() * 100) + "%", w / 2 - 12, 84, 0xFFE8EEF8);
        if (config.showEta()) {
            surface.text("ETA ~" + StageProfiler.formatSeconds(snapshot.etaNanos()), w - 100, 84, 0xFFE8EEF8);
        }

        if (config.showModTimings()) {
            surface.text("RECENT MODS", 14, 120, 0xFF9CB4D8);
            int y = 138;
            for (String row : modTimingList.renderRows(snapshot.recentMods())) {
                surface.text(row, 14, y, 0xFFD6DFEF);
                y += 12;
            }
        }

        if (config.showStageTimeline()) {
            surface.text("STAGE TIMELINE", w / 2, 120, 0xFF9CB4D8);
            int y = 138;
            for (String row : stageTimeline.renderBars(snapshot.timelineNanos())) {
                surface.text(row, w / 2, y, 0xFFD6DFEF);
                y += 12;
            }
        }

        if (snapshot.buildingCache()) {
            surface.text("First launch: building cache (next launch is faster)", 14, h - 24, 0xFFE0B95B);
        }
    }
}
