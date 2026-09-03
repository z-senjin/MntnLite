package net.runelite.client.plugins.microbot.mntn.builder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Mntn AIO Account Builder",
        description = "Planner-driven F2P account builder (vertical slice: Fishing only)",
        tags = {"mntn", "builder", "aio", "fishing"},
        enabledByDefault = false,
        isExternal = false
)
@Slf4j
public class MntnBuilderPlugin extends Plugin {

    @Inject
    MntnBuilderScript script;
    @Inject
    private MntnBuilderConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MntnBuilderOverlay overlay;

    @Provides
    MntnBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MntnBuilderConfig.class);
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

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (MntnBuilderConfig.CONFIG_GROUP.equals(event.getGroup())) {
            script.onConfigChanged(config);
        }
    }
}
