package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

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

    private final MntnBuilderPlugin plugin;

    @Inject
    MntnBuilderOverlay(MntnBuilderPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        this.plugin = plugin;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(230, 170));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Mntn AIO Builder")
                .leftColor(TITLE_COLOR)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Goal:")
                .right(plugin.script.debugGoal)
                .rightColor(Color.WHITE)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Requirement:")
                .right(plugin.script.debugRequirement)
                .rightColor(Color.WHITE)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Activity:")
                .right(plugin.script.debugActivity)
                .rightColor(Color.WHITE)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Strategy:")
                .right(plugin.script.debugStrategy)
                .rightColor(Color.WHITE)
                .build());

        Duration remaining = computeRemaining();
        Color timeColor = (remaining != null && !remaining.isNegative() && !remaining.isZero())
                ? Color.GREEN : Color.RED;

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Left:")
                .right(formatDuration(remaining))
                .rightColor(timeColor)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Score:")
                .right(String.format("%.1f", plugin.script.debugScore))
                .rightColor(Color.GREEN)
                .build());

        return super.render(graphics);
    }

    /**
     * Computes remaining time = commitmentDuration - elapsed since task started.
     */
    private Duration computeRemaining() {
        Duration total = plugin.script.debugTime;
        Instant start = plugin.script.debugTaskStartTime;
        if (total == null || start == null) {
            return null;
        }
        Duration elapsed = Duration.between(start, Instant.now());
        Duration remaining = total.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
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
