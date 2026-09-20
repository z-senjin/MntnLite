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
import java.util.concurrent.TimeUnit;

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
    private static final int HEIGHT = 120;

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
            return renderPanel(graphics);
        }
    }

    private Dimension renderPanel(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mntn Builder")
                .color(TITLE_COLOR)
                .build());

        panelComponent.getChildren().add(LineComponent.builder().build());

        MntnAIOBuilderScript script = plugin.script;
        net.runelite.client.plugins.microbot.mntn.aio.core.Plan plan =
                script != null ? script.getActivePlan() : null;

        if (plan != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Goal")
                    .leftColor(LABEL_COLOR)
                    .right(plan.getGoal().toString())
                    .rightColor(VALUE_COLOR)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Strategy")
                    .leftColor(LABEL_COLOR)
                    .right(plan.getStrategy().getName())
                    .rightColor(VALUE_COLOR)
                    .build());

            long remainingMs = script.getActivePlanRemainingMs();
            Color timeColor = remainingMs < TimeUnit.MINUTES.toMillis(5) ? WARN_COLOR : GOOD_COLOR;

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time left")
                    .leftColor(LABEL_COLOR)
                    .right(formatTime(remainingMs))
                    .rightColor(timeColor)
                    .build());
        } else {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status")
                    .leftColor(LABEL_COLOR)
                    .right(Microbot.status != null ? Microbot.status : "Idle")
                    .rightColor(VALUE_COLOR)
                    .build());
        }

        return super.render(graphics);
    }

    private static String formatTime(long ms) {
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }

        return seconds + "s";
    }


}
