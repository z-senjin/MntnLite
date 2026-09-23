package net.runelite.client.plugins.microbot.mntn.aio;

import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MntnAIOBuilderOverlay extends OverlayPanel {

    private static final Color TITLE_COLOR = new Color(247, 200, 87);
    private static final Color BACKGROUND_COLOR = new Color(25, 28, 31, 232);
    private static final Color LABEL_COLOR = new Color(175, 184, 192);
    private static final Color VALUE_COLOR = new Color(242, 244, 246);
    private static final Color GOOD_COLOR = new Color(105, 211, 150);
    private static final Color WARN_COLOR = new Color(244, 180, 75);
    private static final Color SKILL_COLOR = new Color(37, 100, 88);

    private static final int WIDTH = 480;
    private static final int COLUMNS = 7;

    private static final Skill[] DISPLAY_SKILLS = {
        Skill.ATTACK,
        Skill.DEFENCE,
        Skill.STRENGTH,
        Skill.HITPOINTS,
        Skill.RANGED,
        Skill.PRAYER,
        Skill.MAGIC,
        Skill.COOKING,
        Skill.WOODCUTTING,
        Skill.FISHING,
        Skill.FIREMAKING,
        Skill.CRAFTING,
        Skill.SMITHING,
        Skill.MINING
    };

    private final MntnAIOBuilderPlugin plugin;
    private final Client client;
    private final SkillIconManager skillIconManager;
    private final MntnAIOBuilderOverlayActionGrid skillGrid;

    @Inject
    MntnAIOBuilderOverlay(MntnAIOBuilderPlugin plugin, Client client, SkillIconManager skillIconManager) {
        super(plugin);
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        this.plugin = plugin;
        this.client = client;
        this.skillIconManager = skillIconManager;
        this.skillGrid = new MntnAIOBuilderOverlayActionGrid();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            return renderPanel(graphics);
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
            return renderPanel(graphics);
        }
    }

    private Dimension renderPanel(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
        panelComponent.setBackgroundColor(BACKGROUND_COLOR);

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mntn Builder")
                .color(TITLE_COLOR)
                .build());

        MntnAIOBuilderScript script = plugin.script;
        net.runelite.client.plugins.microbot.mntn.aio.core.AccountContext context =
                script != null ? script.getAccountContext() : null;

        List<MntnAIOBuilderOverlayActionGrid.Action> tileActions = new ArrayList<>(DISPLAY_SKILLS.length);
        for (Skill skill : DISPLAY_SKILLS) {
            int level = context != null ? context.getLevel(skill) : 0;
            BufferedImage icon = skillIconManager.getSkillImage(skill, true);
            tileActions.add(new MntnAIOBuilderOverlayActionGrid.Action(
                    icon,
                    String.valueOf(level),
                    SKILL_COLOR,
                    true,
                    null
            ));
        }
        skillGrid.setActions(tileActions, COLUMNS);
        panelComponent.getChildren().add(skillGrid);

        panelComponent.getChildren().add(LineComponent.builder().build());

        int questPoints = client != null ? client.getVarpValue(VarPlayerID.QP) : 0;
        panelComponent.getChildren().add(LineComponent.builder()
                .left("QP")
                .leftColor(LABEL_COLOR)
                .right(String.valueOf(questPoints))
                .rightColor(VALUE_COLOR)
                .build());

        String coinsStr = "—";
        if (context != null && context.getBankCache().isPopulated()) {
            coinsStr = String.valueOf(context.getBankCache().getCount(ItemID.COINS_995));
        }
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Coins")
                .leftColor(LABEL_COLOR)
                .right(coinsStr)
                .rightColor(VALUE_COLOR)
                .build());

        panelComponent.getChildren().add(LineComponent.builder().build());

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