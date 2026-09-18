package net.runelite.client.plugins.microbot.mntn.aio;

import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Live Builder status display. Activity controls live in the plugin config panel. */
public class MntnAIOBuilderOverlay extends OverlayPanel {

    private static final Color TITLE_COLOR = new Color(247, 200, 87);
    private static final Color BACKGROUND_COLOR = new Color(25, 28, 31, 232);
    private static final Color LABEL_COLOR = new Color(175, 184, 192);
    private static final Color VALUE_COLOR = new Color(242, 244, 246);
    private static final Color GOOD_COLOR = new Color(105, 211, 150);
    private static final Color WARN_COLOR = new Color(244, 180, 75);
    private static final Color SKILL_COLOR = new Color(37, 100, 88);
    private static final int WIDTH = 230;
    private static final int HEIGHT = 225;

    private final MntnAIOBuilderPlugin plugin;
    private final SkillIconManager skillIconManager;
    private final MntnAIOBuilderOverlayActionGrid skillGrid;
    private long lastRenderErrorLogMs;

    @Inject
    MntnAIOBuilderOverlay(MntnAIOBuilderPlugin plugin, SkillIconManager skillIconManager) {
        super(plugin);
        setPosition(OverlayPosition.BOTTOM_LEFT);
        this.plugin = plugin;
        this.skillIconManager = skillIconManager;
        skillGrid = new MntnAIOBuilderOverlayActionGrid();
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

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mntn Builder")
                .color(TITLE_COLOR)
                .build());

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Skills")
                .color(TITLE_COLOR)
                .build());
        skillGrid.setPreferredSize(new Dimension(WIDTH - 10, 1));
        panelComponent.getChildren().add(skillGrid);

        return super.render(graphics);
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
