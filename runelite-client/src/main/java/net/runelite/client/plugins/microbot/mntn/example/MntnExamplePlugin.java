package net.runelite.client.plugins.microbot.mntn.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mntn + "Mntn's Example Plugin",
        description = "Performance test for GameObject composition retrieval",
        tags = {"performance", "microbot", "test", "gameobject"},
        enabledByDefault = false
)
@Slf4j
public class MntnExamplePlugin extends Plugin {
    @Inject
    MntnExampleScript mntnExampleScript;
    @Inject
    MntnExampleScriptOverlay mntnExampleScriptOverlay;
    @Inject
    OverlayManager overlayManager;


    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(mntnExampleScriptOverlay);
        mntnExampleScript.run();
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(mntnExampleScriptOverlay);
        mntnExampleScript.shutdown();
    }

    // on settings change
    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {
    }
}