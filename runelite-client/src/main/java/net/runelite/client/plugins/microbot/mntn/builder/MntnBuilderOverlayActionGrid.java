package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed-size display grid used for the overlay's skill-level tiles. */
final class MntnBuilderOverlayActionGrid implements LayoutableRenderableEntity {

    private static final int GAP = 2;
    private static final int CELL_HEIGHT = 19;
    private static final Font FONT = new Font("Arial", Font.BOLD, 10);

    static final class Action {
        private final BufferedImage icon;
        private final String label;
        private final Color color;
        private final boolean enabled;

        Action(BufferedImage icon, String label, Color color, boolean enabled, Runnable ignoredOnClick) {
            this.icon = icon;
            this.label = label;
            this.color = color;
            this.enabled = enabled;
        }
    }

    private List<Action> actions = Collections.emptyList();
    private int columns = 1;
    private Point preferredLocation = new Point();
    private Dimension preferredSize = new Dimension(1, CELL_HEIGHT);
    private Rectangle bounds = new Rectangle();

    void setActions(List<Action> actions, int columns) {
        this.actions = actions == null ? Collections.emptyList() : new ArrayList<>(actions);
        this.columns = Math.max(1, columns);
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void setPreferredLocation(Point position) {
        preferredLocation = position != null ? position : new Point();
    }

    @Override
    public void setPreferredSize(Dimension dimension) {
        if (dimension != null) {
            preferredSize = dimension;
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        int rowCount = Math.max(1, (actions.size() + columns - 1) / columns);
        int width = Math.max(1, preferredSize.width);
        int cellWidth = Math.max(1, (width - ((columns - 1) * GAP)) / columns);
        int height = rowCount * CELL_HEIGHT + ((rowCount - 1) * GAP);
        graphics.setFont(FONT);
        FontMetrics metrics = graphics.getFontMetrics();

        for (int index = 0; index < actions.size(); index++) {
            int row = index / columns;
            int column = index % columns;
            int x = preferredLocation.x + column * (cellWidth + GAP);
            int y = preferredLocation.y + row * (CELL_HEIGHT + GAP);
            Action action = actions.get(index);
            Color fill = action.enabled ? action.color : new Color(55, 59, 63);
            graphics.setColor(fill);
            graphics.fillRect(x, y, cellWidth, CELL_HEIGHT);
            graphics.setColor(action.enabled ? new Color(12, 15, 18) : new Color(42, 45, 48));
            graphics.drawRect(x, y, cellWidth - 1, CELL_HEIGHT - 1);

            graphics.setColor(action.enabled ? Color.WHITE : new Color(165, 169, 173));
            int iconWidth = action.icon != null ? Math.min(16, action.icon.getWidth()) : 0;
            int iconHeight = action.icon != null ? Math.min(16, action.icon.getHeight()) : 0;
            int labelMaxWidth = cellWidth - 8 - (iconWidth > 0 ? iconWidth + 3 : 0);
            String label = fit(metrics, action.label, Math.max(1, labelMaxWidth));
            int contentWidth = metrics.stringWidth(label) + (iconWidth > 0 ? iconWidth + 3 : 0);
            int contentX = x + (cellWidth - contentWidth) / 2;
            if (action.icon != null) {
                int iconY = y + (CELL_HEIGHT - iconHeight) / 2;
                graphics.drawImage(action.icon, contentX, iconY, iconWidth, iconHeight, null);
            }
            int textX = contentX + (iconWidth > 0 ? iconWidth + 3 : 0);
            int textY = y + (CELL_HEIGHT - metrics.getHeight()) / 2 + metrics.getAscent();
            graphics.drawString(label, textX, textY);
        }

        bounds = new Rectangle(preferredLocation.x, preferredLocation.y, width, height);
        return new Dimension(width, height);
    }

    private String fit(FontMetrics metrics, String value, int maxWidth) {
        if (value == null || metrics.stringWidth(value) <= maxWidth) {
            return value != null ? value : "";
        }
        String shortened = value;
        while (shortened.length() > 1 && metrics.stringWidth(shortened + "...") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "...";
    }
}
