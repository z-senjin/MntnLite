package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Live Builder status display. Activity controls live in the plugin config panel. */
public class MntnBuilderOverlay extends OverlayPanel {

    private static final Color TITLE_COLOR = new Color(247, 200, 87);
    private static final Color BACKGROUND_COLOR = new Color(25, 28, 31, 232);
    private static final Color LABEL_COLOR = new Color(175, 184, 192);
    private static final Color VALUE_COLOR = new Color(242, 244, 246);
    private static final Color GOOD_COLOR = new Color(105, 211, 150);
    private static final Color WARN_COLOR = new Color(244, 180, 75);
    private static final Color SKILL_COLOR = new Color(37, 100, 88);
    private static final int WIDTH = 360;
    private static final int COMPACT_HEIGHT = 270;
    private static final int DETAILED_HEIGHT = 365;

    private final MntnBuilderPlugin plugin;
    private final SkillIconManager skillIconManager;
    private final MntnBuilderOverlayActionGrid skillGrid;
    private long lastRenderErrorLogMs;

    @Inject
    MntnBuilderOverlay(MntnBuilderPlugin plugin, SkillIconManager skillIconManager) {
        super(plugin);
        setPosition(OverlayPosition.BOTTOM_LEFT);
        this.plugin = plugin;
        this.skillIconManager = skillIconManager;
        skillGrid = new MntnBuilderOverlayActionGrid();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            return renderPanel(graphics);
        } catch (Exception ex) {
            long now = System.currentTimeMillis();
            if (now - lastRenderErrorLogMs > 5000) {
                lastRenderErrorLogMs = now;
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
            return renderErrorPanel(graphics, ex);
        }
    }

    private Dimension renderPanel(Graphics2D graphics) {
        MntnBuilderOverlayState state = MntnBuilderRuntimeStatus.getLatest();
        if (state == null && plugin != null && plugin.script != null) {
            state = plugin.script.getOverlayState();
        }
        if (state == null || !state.isVisible()) {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(WIDTH,
                state.isDetailed() ? DETAILED_HEIGHT : COMPACT_HEIGHT));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mntn Builder")
                .color(TITLE_COLOR)
                .build());
        addLine("State", state.getRunnerState(), stateColor(state));
        addLine("Goal", trim(state.getGoal(), 34), VALUE_COLOR);
        addLine("Activity", trim(prettify(state.getActivity()) + " - " + prettify(state.getStrategy()), 38),
                VALUE_COLOR);
        addLine("Time", formatDuration(computeRemaining(state)), timeColor(state));
        addLine("Status", prettify(state.getTaskStatus()), statusColor(state.getTaskStatus()));

        if (state.isDetailed()) {
            addLine("Need", trim(state.getRequirement(), 48), VALUE_COLOR);
            addLine("Task", trim(state.getTask(), 48), VALUE_COLOR);
            addLine("Reason", prettify(state.getLastStopReason()), statusColor(state.getLastStopReason()));
            addLine("Score", String.format("%.1f", state.getScore()), GOOD_COLOR);
            addLine("Mode", prettify(state.getContentMode()) + " / " + prettify(state.getSessionFlavor()),
                    VALUE_COLOR);
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Skills")
                .color(TITLE_COLOR)
                .build());
        configureSkills(state);
        skillGrid.setPreferredSize(new Dimension(WIDTH - 12, 1));
        panelComponent.getChildren().add(skillGrid);

        return super.render(graphics);
    }

    private void configureSkills(MntnBuilderOverlayState state) {
        List<MntnBuilderOverlayActionGrid.Action> actions = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            if ("Overall".equals(skill.getName())) {
                continue;
            }
            actions.add(new MntnBuilderOverlayActionGrid.Action(skillIconManager.getSkillImage(skill, true),
                    String.valueOf(state.getSkillLevel(skill)), SKILL_COLOR, true, null));
        }
        skillGrid.setActions(actions, 5);
    }

    private Dimension renderErrorPanel(Graphics2D graphics, Exception ex) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(WIDTH, 80));
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
                .left(left + ":")
                .leftColor(LABEL_COLOR)
                .right(right != null ? right : "-")
                .rightColor(rightColor)
                .build());
    }

    private Duration computeRemaining(MntnBuilderOverlayState state) {
        Duration total = state.getCommitmentDuration();
        Instant start = state.getTaskStartTime();
        if (total == null || start == null) {
            return null;
        }
        Duration remaining = total.minus(Duration.between(start, Instant.now()));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private Color stateColor(MntnBuilderOverlayState state) {
        String runnerState = state.getRunnerState();
        if (runnerState.startsWith("Paused") || runnerState.startsWith("Waiting")
                || "Startup".equals(runnerState)) {
            return WARN_COLOR;
        }
        return "Stopped".equals(runnerState) ? Color.RED : GOOD_COLOR;
    }

    private Color timeColor(MntnBuilderOverlayState state) {
        Duration remaining = computeRemaining(state);
        return remaining != null && !remaining.isZero() ? GOOD_COLOR : WARN_COLOR;
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
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if (Character.isWhitespace(character)) {
                capitalize = true;
                builder.append(character);
            } else if (capitalize) {
                builder.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value != null ? value : "-";
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static String formatDuration(Duration duration, String header) {
        return String.format(header + " %s", formatDuration(duration));
    }

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
