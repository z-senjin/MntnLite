package net.runelite.client.plugins.microbot.testing.release;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.testing.TestResultWriter;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@PluginDescriptor(
        name = ReleaseSmokeHarnessPlugin.SCRIPT_NAME,
        description = "Runs the release end-to-end smoke checks in test mode",
        tags = {"microbot", "test", "release", "e2e"},
        enabledByDefault = false,
        hidden = true
)
@Slf4j
public class ReleaseSmokeHarnessPlugin extends Plugin {

    static final String SCRIPT_NAME = "Release Smoke Harness";
    static final String PREFIX = "microbot.test.release.";
    static final long DEFAULT_QUICK_TOTAL_MS = 900_000L;
    static final long DEFAULT_FULL_TOTAL_MS = 2_100_000L;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private MicrobotPluginManager microbotPluginManager;

    @Inject
    private WorldService worldService;

    private ExecutorService executor;

    @Override
    protected void startUp() {
        if (!isHarnessTarget()) {
            return;
        }
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("ReleaseE2E-harness-%d")
                .build());
        executor.submit(this::runHarness);
    }

    @Override
    protected void shutDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void runHarness() {
        ReleaseSmokeResult result = new ReleaseSmokeResult(SCRIPT_NAME);
        Path outputDir = Paths.get(TestResultWriter.getDefaultOutputDir());
        try {
            ReleaseTier tier = ReleaseTier.parse(property("tier", "quick"));
            List<String> requested = parseIds(property("checks", ""));
            boolean failFast = Boolean.parseBoolean(property("failFast", "false"));
            long totalTimeout = longProperty("totalTimeoutMs", tier == ReleaseTier.FULL ? DEFAULT_FULL_TOTAL_MS : DEFAULT_QUICK_TOTAL_MS);

            ReleaseSmokeChecks.Settings settings = new ReleaseSmokeChecks.Settings();
            settings.expectedVersion = property("expectedVersion", "");
            settings.hubPlugin = property("hubPlugin", ReleaseSmokeChecks.DEFAULT_HUB_PLUGIN);
            settings.hopWorld = (int) longProperty("hopWorld", -1);
            settings.loginTimeoutMs = longProperty("loginTimeoutMs", settings.loginTimeoutMs);

            result.tier = tier.name();
            result.failFast = failFast;
            result.totalTimeoutMs = totalTimeout;
            result.requestedChecks = requested;
            result.expectedVersion = settings.expectedVersion;
            result.microbotVersion = RuneLiteProperties.getMicrobotVersion();
            result.microbotCommit = RuneLiteProperties.getMicrobotCommit();
            result.runeliteVersion = RuneLiteProperties.getVersion();

            ReleaseSmokeChecks checks = new ReleaseSmokeChecks(settings, pluginManager, configManager, microbotPluginManager, worldService);
            List<ReleaseCheck> selected = ReleaseCheckRunner.select(checks.all(), tier, requested);

            if (!sleepUntil(() -> ReleaseSmokeLive.location() != null, 60_000)) {
                result.addError("Player location never became available after login");
                result.finish("login_failure");
                writeAndExit(result, outputDir);
                return;
            }
            result.world = Microbot.getClient().getWorld();

            log.info("[ReleaseE2E] Running {} check(s), tier={}, failFast={}, totalTimeoutMs={}: {}", selected.size(), tier, failFast,
                    totalTimeout, selected.stream().map(ReleaseCheck::getId).collect(Collectors.joining(", ")));
            ReleaseCheckRunner runner = new ReleaseCheckRunner(new ReleaseCheckRunner.Options()
                    .failFast(failFast)
                    .totalTimeoutMs(totalTimeout));
            runner.run(selected, outcome -> {
                result.record(outcome);
                log.info("[ReleaseE2E] {} {} in {}ms{}", outcome.id, outcome.status, outcome.durationMs,
                        outcome.detail == null ? "" : ": " + outcome.detail);
            });
            result.finish("completed");
        } catch (IllegalArgumentException e) {
            result.addError(e.getMessage());
            result.finish("invalid_arguments");
        } catch (Throwable t) {
            log.warn("[ReleaseE2E] Harness crashed", t);
            result.addError(ReleaseCheckRunner.describe(t));
            result.finish("crash");
        }
        writeAndExit(result, outputDir);
    }

    private static void writeAndExit(ReleaseSmokeResult result, Path outputDir) {
        TestResultWriter.write(result, outputDir.toString());
        try {
            Path summary = ReleaseSmokeReport.write(result, outputDir);
            log.info("[ReleaseE2E] Summary written to {}\n{}", summary, ReleaseSmokeReport.render(result));
        } catch (Exception e) {
            log.warn("[ReleaseE2E] Could not write summary", e);
        }
        System.exit(result.exitCode);
    }

    static List<String> parseIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,\\s]+"))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toList());
    }

    private static String property(String key, String defaultValue) {
        String value = System.getProperty(PREFIX + key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static long longProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(property(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isHarnessTarget() {
        return "true".equals(System.getProperty("microbot.test.mode"))
                && System.getProperty("microbot.test.script", "").contains(SCRIPT_NAME);
    }
}
