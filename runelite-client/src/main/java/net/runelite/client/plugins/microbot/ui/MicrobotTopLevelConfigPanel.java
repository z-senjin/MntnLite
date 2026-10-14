package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.RuneLiteProperties;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.config.TopLevelConfigPanel;
import net.runelite.client.plugins.microbot.diagnostics.DiagnosticReportCollector;
import net.runelite.client.plugins.microbot.recovery.StartupRecovery;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MicrobotTopLevelConfigPanel extends PluginPanel {
    private final MaterialTabGroup tabGroup;
    private final CardLayout layout;
    private final JPanel content;

    private final EventBus eventBus;
    private final DiagnosticReportCollector diagnosticReportCollector;
    private final MicrobotPluginListPanel pluginListPanel;
    private final MaterialTab pluginListPanelTab;
    private final MaterialTab profilePanelTab;

    private boolean active = false;
    private MicrobotPluginPanel current;
    private boolean removeOnTabChange;

    /**
     * Creates a simple text-based icon for tabs.
     * @param text {@link String} Text to display
     * @param textColor {@link Color} Color of the text
     * @param backgroundColor {@link Color} Background color (can be null for transparent)
     * @param width Width of the icon
     * @param height  Height of the icon
     * @return ImageIcon {@link ImageIcon} with rendered text
     */
    private ImageIcon createTextIcon(String text, Color textColor, Color backgroundColor, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Enable antialiasing for smoother text
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Set background (optional, can be transparent)
        if (backgroundColor != null) {
            g2d.setColor(backgroundColor);
            g2d.fillRect(0, 0, width, height);
        }

        // Set font and text color
        g2d.setFont(FontManager.getRunescapeBoldFont());
        g2d.setColor(textColor);

        // Calculate text position to center it
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();

        int x = (width - textWidth) / 2;
        int y = (height - textHeight) / 2 + textHeight;

        g2d.drawString(text, x, y);
        g2d.dispose();

        return new ImageIcon(image);
    }

    @Inject
    MicrobotTopLevelConfigPanel(
            EventBus eventBus,
            MicrobotPluginListPanel pluginListPanel,
            MicrobotProfilePanel profilePanel,
            Provider<MicrobotPluginHubPanel> microbotPluginHubPanelProvider,
            DiagnosticReportCollector diagnosticReportCollector
    ) {
        super(false);

        this.eventBus = eventBus;
        this.diagnosticReportCollector = diagnosticReportCollector;

        tabGroup = new MaterialTabGroup();
        tabGroup.setLayout(new GridLayout(1, 0, 7, 7));
        tabGroup.setBorder(new EmptyBorder(10, 10, 0, 10));

        content = new JPanel();
        layout = new CardLayout();
        content.setLayout(layout);

        setLayout(new BorderLayout());
        add(tabGroup, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(buildVersionFooter(), BorderLayout.SOUTH);

        this.pluginListPanel = pluginListPanel;

        // Create text-based icons instead of using image files for better clarity
        ImageIcon installedIcon = createTextIcon("Installed", Color.YELLOW, null, 80, 32);
        ImageIcon hubIcon = createTextIcon("Plugin Hub", Color.YELLOW, null, 80, 32);

        pluginListPanelTab = addTab(pluginListPanel.getMuxer(), installedIcon, "Installed Microbot Plugins");
        profilePanelTab = addTab(profilePanel, "profile_icon.png", "Profiles");
        addTab(microbotPluginHubPanelProvider, hubIcon, "Microbot Hub");

        tabGroup.select(pluginListPanelTab);
    }

    private JPanel buildVersionFooter() {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(new EmptyBorder(4, 0, 4, 0));

        String version = RuneLiteProperties.getMicrobotVersion();
        JLabel versionLabel = new JLabel(version == null || version.isBlank() ? "microbot" : "microbot v" + version, SwingConstants.CENTER);
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionLabel.setFont(FontManager.getRunescapeSmallFont());
        versionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        footer.add(versionLabel);
        if (StartupRecovery.mode().isSafeMode()) {
            footer.add(buildSafeModeNotice());
        }
        footer.add(buildDiagnosticsButton());
        footer.add(buildRecoveryButton());

        String proxy = ClientUI.proxyMessage;
        if (proxy != null && !proxy.isBlank()) {
            JLabel proxyLabel = new JLabel(proxy, SwingConstants.CENTER);
            proxyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            proxyLabel.setFont(FontManager.getRunescapeSmallFont());
            proxyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            footer.add(proxyLabel);
        }

        return footer;
    }

    private JLabel buildSafeModeNotice() {
        String text = StartupRecovery.mode().isTemporary()
                ? "<html><center>Safe mode: external plugins are off for this session. Your plugins and settings are kept. Restart to return to normal.</center></html>"
                : "<html><center>Safe mode (--safe-mode): external plugins are off. Remove the launch option to return to normal.</center></html>";
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
        label.setBorder(new EmptyBorder(2, 6, 2, 6));
        List<String> previous = StartupRecovery.previousSessionSummary();
        if (!previous.isEmpty()) {
            label.setToolTipText("<html>Previous session:<br>" + previous.stream()
                    .map(line -> line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    .collect(Collectors.joining("<br>")) + "</html>");
        }
        return label;
    }

    private JButton buildRecoveryButton() {
        JButton button = new JButton();
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusable(false);
        button.setToolTipText("Start Microbot once without external (Hub or sideloaded) plugins and the GPU plugin, to recover from a plugin that freezes or crashes the client. Plugin files and settings are kept.");
        updateRecoveryButton(button);
        button.addActionListener(e -> {
            boolean enable = !StartupRecovery.nextStartSafeMode();
            if (!StartupRecovery.requestNextStartSafeMode(enable)) {
                button.setText("Recovery unavailable");
                button.setEnabled(false);
                return;
            }
            updateRecoveryButton(button);
            if (enable) {
                JOptionPane.showMessageDialog(this,
                        "The next start will use safe mode: external plugins and the GPU plugin stay off for that session only.\n"
                                + "Close Microbot and start it again. Your plugins and settings are kept, and the start after that is normal.",
                        "Safe mode on next start", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        return button;
    }

    private static void updateRecoveryButton(JButton button) {
        button.setText(StartupRecovery.nextStartSafeMode() ? "Cancel safe mode next start" : "Safe mode next start");
    }

    private JButton buildDiagnosticsButton() {
        JButton button = new JButton("Copy diagnostics");
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusable(false);
        button.setToolTipText("Copy client, build, walker and plugin details for bug reports. Excludes account names, chat, tokens and file paths.");
        Timer reset = new Timer(2000, e -> button.setText("Copy diagnostics"));
        reset.setRepeats(false);
        button.addActionListener(e -> {
            try {
                String report = diagnosticReportCollector.collectReport();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
                button.setText("Copied");
            } catch (Exception ex) {
                log.warn("Could not copy diagnostics: {}", ex.getMessage());
                button.setText("Copy failed");
            }
            reset.restart();
        });
        return button;
    }

    private MaterialTab addTab(MicrobotPluginPanel panel, ImageIcon icon, String tooltip) {
        MaterialTab mt = new MaterialTab(icon, tabGroup, null);
        mt.setToolTipText(tooltip);
        tabGroup.addTab(mt);

        content.add(tooltip, panel.getWrappedPanel()); // Use tooltip as unique key instead of image name
        eventBus.register(panel);

        mt.setOnSelectEvent(() ->
        {
            switchTo(tooltip, panel, false);
            return true;
        });
        return mt;
    }

    private MaterialTab addTab(MicrobotPluginPanel panel, String image, String tooltip)
    {
        MaterialTab mt = new MaterialTab(
                new ImageIcon(ImageUtil.loadImageResource(TopLevelConfigPanel.class, image)),
                tabGroup, null);
        mt.setToolTipText(tooltip);
        tabGroup.addTab(mt);

        content.add(image, panel.getWrappedPanel());
        eventBus.register(panel);

        mt.setOnSelectEvent(() ->
        {
            switchTo(image, panel, false);
            return true;
        });
        return mt;
    }

    private MaterialTab addTab(Provider<? extends MicrobotPluginPanel> panelProvider, ImageIcon icon, String tooltip) {
        MaterialTab mt = new MaterialTab(icon, tabGroup, null);
        mt.setToolTipText(tooltip);
        tabGroup.addTab(mt);

        mt.setOnSelectEvent(() ->
        {
            MicrobotPluginPanel panel = panelProvider.get();
            content.add(tooltip, panel.getWrappedPanel());
            eventBus.register(panel);
            switchTo(tooltip, panel, true);
            return true;
        });
        return mt;
    }

    private void switchTo(String cardName, MicrobotPluginPanel panel, boolean removeOnTabChange) {
        boolean doRemove = this.removeOnTabChange;
        MicrobotPluginPanel prevPanel = current;
        if (active) {
            prevPanel.onDeactivate();
            panel.onActivate();
        }

        current = panel;
        this.removeOnTabChange = removeOnTabChange;

        layout.show(content, cardName);

        if (doRemove) {
            content.remove(prevPanel.getWrappedPanel());
            eventBus.unregister(prevPanel);
        }

        content.revalidate();
    }

    @Override
    public void onActivate() {
        active = true;
        current.onActivate();
    }

    @Override
    public void onDeactivate() {
        active = false;
        current.onDeactivate();
    }

    public void openConfigurationPanel(String name) {
        tabGroup.select(pluginListPanelTab);
        pluginListPanel.openConfigurationPanel(name);
    }

    public void openConfigurationPanel(Plugin plugin) {
        tabGroup.select(pluginListPanelTab);
        pluginListPanel.openConfigurationPanel(plugin);
    }

    public void openWithFilter(String filter) {
        tabGroup.select(pluginListPanelTab);
        pluginListPanel.openWithFilter(filter);
    }
}
