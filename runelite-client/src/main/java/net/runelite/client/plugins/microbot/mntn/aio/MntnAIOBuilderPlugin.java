package net.runelite.client.plugins.microbot.mntn.aio;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mntn + " Mntn AIO Account Builder",
        description = "Builds the account for you.",
        tags = {"mntn", "builder", "aio", "account"},
        enabledByDefault = false,
        isExternal = false
)
@Slf4j
public class MntnAIOBuilderPlugin extends Plugin {

    @Inject
    MntnAIOBuilderScript script;
    @Inject
    private MntnAIOBuilderConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MntnAIOBuilderOverlay overlay;

    @Provides
    MntnAIOBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MntnAIOBuilderConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }


}
