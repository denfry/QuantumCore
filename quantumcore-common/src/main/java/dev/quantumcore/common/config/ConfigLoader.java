package dev.quantumcore.common.config;

import dev.quantumcore.common.concurrent.ThreadSafe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ThreadSafe
public final class ConfigLoader {
    private ConfigLoader() {
    }

    public static QuantumCoreConfig load(Path path) {
        if (!Files.exists(path)) {
            writeDefault(path);
            return QuantumCoreConfig.defaults();
        }
        try {
            return parse(path);
        } catch (Exception exception) {
            safeDelete(path);
            writeDefault(path);
            return QuantumCoreConfig.defaults();
        }
    }

    private static QuantumCoreConfig parse(Path path) throws IOException {
        Map<String, String> values = new HashMap<>();
        List<String> lines = Files.readAllLines(path);
        String section = "";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim().replace("\"", "");
            values.put(section + "." + key, value);
        }

        DashboardConfig dashboard = new DashboardConfig(
            bool(values, "dashboard.enabled", true),
            bool(values, "dashboard.show_mod_timings", true),
            bool(values, "dashboard.show_stage_timeline", true),
            bool(values, "dashboard.show_memory_bar", true),
            bool(values, "dashboard.show_eta", true),
            parseHex(values.getOrDefault("dashboard.accent_color", "0x4488FF")),
            bool(values, "dashboard.completion_message", true)
        );

        return new QuantumCoreConfig(
            bool(values, "features.atlas_cache", true),
            bool(values, "features.checkpoint", false),
            bool(values, "features.incremental_cache", true),
            bool(values, "features.surgical_cache", true),
            bool(values, "features.memory_dedup", true),
            bool(values, "features.gc_pool", true),
            bool(values, "features.background_preloader", true),
            dashboard,
            integer(values, "formats.checkpoint", 1),
            integer(values, "formats.atlas", 1)
        );
    }

    private static int parseHex(String value) {
        String normalized = value.startsWith("0x") ? value.substring(2) : value;
        return Integer.parseUnsignedInt(normalized, 16);
    }

    private static boolean bool(Map<String, String> map, String key, boolean fallback) {
        return map.containsKey(key) ? Boolean.parseBoolean(map.get(key)) : fallback;
    }

    private static int integer(Map<String, String> map, String key, int fallback) {
        return map.containsKey(key) ? Integer.parseInt(map.get(key)) : fallback;
    }

    public static void writeDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, defaultToml());
        } catch (IOException ignored) {
        }
    }

    private static void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String defaultToml() {
        return """
            [features]
            atlas_cache = true
            checkpoint = false
            incremental_cache = true
            surgical_cache = true
            memory_dedup = true
            gc_pool = true
            background_preloader = true

            [dashboard]
            enabled = true
            show_mod_timings = true
            show_stage_timeline = true
            show_memory_bar = true
            show_eta = true
            accent_color = \"0x4488FF\"
            completion_message = true

            [formats]
            checkpoint = 1
            atlas = 1
            """;
    }
}
