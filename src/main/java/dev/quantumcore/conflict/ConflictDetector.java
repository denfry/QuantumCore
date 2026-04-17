package dev.quantumcore.conflict;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects known mod conflicts during startup and exposes in-game diagnostics to reduce support
 * churn for large curated modpacks.
 * Thread-safety: effectively immutable after initialization and safe for concurrent reads.
 */
public final class ConflictDetector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Type RULE_LIST = new TypeToken<List<ConflictRule>>() {}.getType();

    private final boolean enabled;
    private final List<ConflictRule> rules;
    private volatile List<ConflictRule> lastDetected = List.of();

    /**
     * Loads conflict rules once at startup so detection stays cheap during command execution.
     */
    public ConflictDetector(boolean enabled) {
        this.enabled = enabled;
        this.rules = loadRules();
    }

    /**
     * Performs startup detection early so users can see actionable fix advice in logs before world
     * load amplifies unstable combinations.
     */
    public void scanAndLog() {
        if (!enabled) {
            return;
        }
        lastDetected = detectConflicts();
        if (lastDetected.isEmpty()) {
            LOGGER.info("QuantumCore conflict detector: no known conflicts found");
            return;
        }

        LOGGER.warn("QuantumCore detected {} potential conflicts:", lastDetected.size());
        for (ConflictRule rule : lastDetected) {
            LOGGER.warn("[{}] {} | Suggested fix: {}", rule.severity(), rule.message(), rule.suggestedFix());
        }
    }

    /**
     * Registers `/quantumcore conflicts` so pack maintainers can inspect conflict state in-game
     * without reading external logs.
     */
    public void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled) {
            return;
        }
        event.getDispatcher().register(
            Commands.literal("quantumcore")
                .then(Commands.literal("conflicts")
                    .executes(ctx -> showConflicts(ctx.getSource())))
        );
    }

    /**
     * Re-evaluates rules against loaded mods so command output reflects runtime modlist state.
     */
    public List<ConflictRule> detectConflicts() {
        Set<String> loadedMods = new HashSet<>();
        ModList.get().getMods().forEach(mod -> loadedMods.add(mod.getModId()));

        List<ConflictRule> conflicts = new ArrayList<>();
        for (ConflictRule rule : rules) {
            if (rule.mods() == null || rule.mods().isEmpty()) {
                continue;
            }
            boolean allPresent = true;
            for (String modId : rule.mods()) {
                if (!loadedMods.contains(modId)) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                conflicts.add(rule);
            }
        }
        return conflicts;
    }

    private int showConflicts(CommandSourceStack source) {
        List<ConflictRule> conflicts = detectConflicts();
        if (conflicts.isEmpty()) {
            source.sendSuccess(() -> Component.literal("QuantumCore: no known conflicts detected."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("QuantumCore conflicts: " + conflicts.size()), false);
        for (ConflictRule conflict : conflicts) {
            source.sendSuccess(
                () -> Component.literal("[" + conflict.severity() + "] " + conflict.message() + " | Fix: " + conflict.suggestedFix()),
                false
            );
        }
        return conflicts.size();
    }

    private List<ConflictRule> loadRules() {
        try (InputStream input = ConflictDetector.class.getResourceAsStream("/conflict_rules.json")) {
            if (input == null) {
                LOGGER.warn("No conflict_rules.json found, conflict detector is running with empty rules");
                return List.of();
            }
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                List<ConflictRule> parsed = new Gson().fromJson(reader, RULE_LIST);
                return parsed != null ? parsed : List.of();
            }
        } catch (Exception exception) {
            LOGGER.error("Failed loading conflict rules", exception);
            return List.of();
        }
    }
}
