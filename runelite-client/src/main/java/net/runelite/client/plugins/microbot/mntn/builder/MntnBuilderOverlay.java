package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

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
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Score:")
                .right(String.format("%.1f", plugin.script.debugScore))
                .rightColor(Color.GREEN)
                .build());

        return super.render(graphics);
    }
}
