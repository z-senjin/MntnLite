package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ButtonComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

/**
 * This is the "debug UI" the doc calls out in section 15 as one of the most valuable things
 * you can build early - showing goal/requirement/activity/strategy/score live lets you see
 * exactly why the bot picked what it picked, instead of guessing from behavior alone.
 */
public class MntnBuilderOverlay extends OverlayPanel {

    private static final Color TITLE_COLOR = Color.decode("#a4ffff");
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 150);
    private static final Color LABEL_COLOR = Color.LIGHT_GRAY;
    private static final Color VALUE_COLOR = Color.WHITE;
    private static final Color GOOD_COLOR = new Color(120, 220, 120);
    private static final Color WARN_COLOR = new Color(255, 190, 90);
    private static final int COMPACT_WIDTH = 220;
    private static final int DETAILED_WIDTH = 320;

    private final MntnBuilderPlugin plugin;
    private final ButtonComponent skipButton;
    private long lastRenderErrorLogMs;

    @Inject
    MntnBuilderOverlay(MntnBuilderPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        this.plugin = plugin;

        this.skipButton = new ButtonComponent("Skip Activity");
        this.skipButton.setPreferredSize(new Dimension(COMPACT_WIDTH, 25));
        this.skipButton.setBackgroundColor(new Color(200, 60, 60));
        this.skipButton.setTextColor(Color.WHITE);
        this.skipButton.setFont(new Font("Arial", Font.BOLD, 11));
        this.skipButton.setOnClick(() -> {
            if (plugin != null && plugin.script != null) {
                plugin.script.forceReplan();
            }
        });
        this.skipButton.setParentOverlay(this);
        this.skipButton.hookMouseListener();
    }

    public void cleanup() {
        skipButton.unhookMouseListener();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            return renderPanel(graphics);
        } catch (Exception ex) {
            long now = System.currentTimeMillis();
            if (now - lastRenderErrorLogMs > 5000) {
                lastRenderErrorLogMs = now;
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
            return renderErrorPanel(graphics, ex);
        }
    }

    private Dimension renderPanel(Graphics2D graphics) {
        if (plugin == null || plugin.script == null) {
            return null;
        }

        MntnBuilderOverlayState state = plugin.script.getOverlayState();
        if (state == null || !state.isVisible()) {
            return null;
        }

        panelComponent.getChildren().clear();
        int width = state.isDetailed() ? DETAILED_WIDTH : COMPACT_WIDTH;
        panelComponent.setPreferredSize(new Dimension(width, state.isDetailed() ? 250 : 120));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);
        skipButton.setPreferredSize(new Dimension(width - 10, 25));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mntn Builder")
                .color(TITLE_COLOR)
                .build());

        addLine("State", state.getRunnerState(), stateColor(state));
        addLine("Activity", prettify(state.getActivity()), VALUE_COLOR);
        addLine("Strategy", trim(state.getStrategy(), 24), VALUE_COLOR);
        addLine("Time", formatDuration(computeRemaining(state)), timeColor(state));

        if (state.isDetailed()) {
            addLine("", "", VALUE_COLOR);
            addLine("Goal", trim(state.getGoal(), 26), VALUE_COLOR);
            addLine("Need", trim(state.getRequirement(), 48), VALUE_COLOR);
            addLine("Task", trim(state.getTask(), 42), VALUE_COLOR);
            addLine("Status", prettify(state.getTaskStatus()), statusColor(state.getTaskStatus()));
            addLine("Reason", prettify(state.getLastStopReason()), statusColor(state.getLastStopReason()));
            addLine("Score", String.format("%.1f", state.getScore()), GOOD_COLOR);
            addLine("Mode", prettify(state.getContentMode()), VALUE_COLOR);
            addLine("Flavor", prettify(state.getSessionFlavor()), VALUE_COLOR);
        }

        panelComponent.getChildren().add(skipButton);

        return super.render(graphics);
    }

    private Dimension renderErrorPanel(Graphics2D graphics, Exception ex) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(DETAILED_WIDTH, 80));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mntn Builder")
                .color(TITLE_COLOR)
                .build());
        addLine("Overlay", "Render error", Color.RED);
        addLine("Reason", trim(ex.getClass().getSimpleName(), 42), Color.RED);
        return super.render(graphics);
    }

    private void addLine(String left, String right, Color rightColor) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left == null || left.isEmpty() ? "" : left + ":")
                .leftColor(LABEL_COLOR)
                .right(right != null ? right : "-")
                .rightColor(rightColor)
                .build());
    }

    /**
     * Computes remaining time = commitmentDuration - elapsed since task started.
     */
    private Duration computeRemaining(MntnBuilderOverlayState state) {
        Duration total = state.getCommitmentDuration();
        Instant start = state.getTaskStartTime();
        if (total == null || start == null) {
            return null;
        }
        Duration elapsed = Duration.between(start, Instant.now());
        Duration remaining = total.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private Color stateColor(MntnBuilderOverlayState state) {
        if ("Startup".equals(state.getRunnerState()) || state.getRunnerState().startsWith("Paused")) {
            return WARN_COLOR;
        }
        return GOOD_COLOR;
    }

    private Color timeColor(MntnBuilderOverlayState state) {
        Duration remaining = computeRemaining(state);
        return remaining != null && !remaining.isZero() && !remaining.isNegative()
                ? GOOD_COLOR
                : WARN_COLOR;
    }

    private Color statusColor(String status) {
        if (status == null || "NONE".equals(status) || "COMPLETE".equals(status) || "RUNNING".equals(status)) {
            return GOOD_COLOR;
        }
        if ("REPLAN".equals(status) || "BLOCKED".equals(status)) {
            return WARN_COLOR;
        }
        return Color.RED;
    }

    private String prettify(String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) {
            return "-";
        }
        String lower = value.replace('_', ' ').toLowerCase();
        StringBuilder builder = new StringBuilder(lower.length());
        boolean capitalize = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isWhitespace(c)) {
                capitalize = true;
                builder.append(c);
            } else if (capitalize) {
                builder.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private String trim(String value, int maxLength) {
        String pretty = prettify(value);
        if (pretty.length() <= maxLength) {
            return pretty;
        }
        return pretty.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static String formatDuration(Duration duration, String header) {
        return String.format(header + " %s", formatDuration(duration));
    }

    /**
     * Formats a duration into HH:MM:SS format.
     */
    public static String formatDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "00:00:00";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
