package net.runelite.client.plugins.microbot.testing.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ReleaseSmokeReport {

    public static final String SUMMARY_FILE = "summary.md";
    private static final int MAX_DETAIL_LENGTH = 240;

    private ReleaseSmokeReport() {
    }

    public static String render(ReleaseSmokeResult result) {
        StringBuilder out = new StringBuilder();
        out.append("## Microbot release E2E: ").append(result.verdict)
                .append(" (").append(result.tier == null ? "?" : result.tier.toLowerCase(Locale.ROOT)).append(" tier)\n\n");
        out.append("- Microbot ").append(nullToDash(result.microbotVersion))
                .append(" (commit ").append(nullToDash(result.microbotCommit)).append("), RuneLite ")
                .append(nullToDash(result.runeliteVersion)).append('\n');
        if (result.expectedVersion != null && !result.expectedVersion.isBlank()) {
            out.append("- Expected version: ").append(result.expectedVersion).append('\n');
        }
        out.append("- Started: ").append(result.timestamp).append(", total ").append(formatDuration(result.durationMs))
                .append(result.failFast ? ", fail-fast" : "").append('\n');
        out.append("- Result: ").append(result.passed).append(" passed, ").append(result.failed).append(" failed, ")
                .append(result.skipped).append(" skipped, ").append(result.notRun).append(" not run")
                .append(" (exit ").append(result.exitReason).append(", code ").append(result.exitCode).append(")\n");
        for (String error : result.errors) {
            out.append("- Harness error: ").append(escape(error)).append('\n');
        }
        out.append('\n');
        out.append("| # | Check | Tier | Status | Duration | Detail |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (CheckOutcome outcome : result.results) {
            out.append("| ").append(outcome.order)
                    .append(" | `").append(outcome.id).append("` ")
                    .append(escape(outcome.description))
                    .append(" | ").append(outcome.tier == null ? "" : outcome.tier.name().toLowerCase(Locale.ROOT))
                    .append(" | ").append(outcome.status)
                    .append(" | ").append(outcome.status == CheckStatus.NOT_RUN ? "-" : formatDuration(outcome.durationMs))
                    .append(" | ").append(escape(truncate(outcome.detail)))
                    .append(" |\n");
        }
        return out.toString();
    }

    public static Path write(ReleaseSmokeResult result, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(SUMMARY_FILE);
        Files.write(file, render(result).getBytes(StandardCharsets.UTF_8));
        return file;
    }

    static String formatDuration(long durationMs) {
        long seconds = Math.max(0L, durationMs) / 1000L;
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%.1fs", Math.max(0L, durationMs) / 1000.0);
        }
        return String.format(Locale.ROOT, "%dm%02ds", seconds / 60, seconds % 60);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_DETAIL_LENGTH ? value : value.substring(0, MAX_DETAIL_LENGTH - 3) + "...";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
