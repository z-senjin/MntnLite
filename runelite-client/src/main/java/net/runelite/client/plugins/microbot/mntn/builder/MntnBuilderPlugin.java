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
        description = "Planner-driven account builder with an F2P method catalog",
        tags = {"mntn", "builder", "aio", "account"},
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
        if (!MntnBuilderConfig.CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

        if (handleActivityControl(event.getKey())) {
            return;
        }

        // The script queues normal config updates and applies them from its own worker loop.
        script.onConfigChanged(config);
    }

    private boolean handleActivityControl(String key) {
        if (MntnBuilderConfig.SKIP_ACTIVITY.equals(key)) {
            script.forceReplan();
            return true;
        }
        if (MntnBuilderConfig.REMOVE_ACTIVITY_TIME.equals(key)) {
            script.adjustActivityTime(-10);
            return true;
        }
        if (MntnBuilderConfig.ADD_ACTIVITY_TIME.equals(key)) {
            script.adjustActivityTime(10);
            return true;
        }

        MntnBuilderOverlayFocus focus = focusForKey(key);
        if (focus == null) {
            return false;
        }
        script.focusActivity(focus);
        return true;
    }

    private MntnBuilderOverlayFocus focusForKey(String key) {
        if (MntnBuilderConfig.FOCUS_FISHING.equals(key)) {
            return MntnBuilderOverlayFocus.FISHING;
        }
        if (MntnBuilderConfig.FOCUS_COOKING.equals(key)) {
            return MntnBuilderOverlayFocus.COOKING;
        }
        if (MntnBuilderConfig.FOCUS_FIREMAKING.equals(key)) {
            return MntnBuilderOverlayFocus.FIREMAKING;
        }
        if (MntnBuilderConfig.FOCUS_WOODCUTTING.equals(key)) {
            return MntnBuilderOverlayFocus.WOODCUTTING;
        }
        if (MntnBuilderConfig.FOCUS_MINING.equals(key)) {
            return MntnBuilderOverlayFocus.MINING;
        }
        if (MntnBuilderConfig.FOCUS_SMITHING.equals(key)) {
            return MntnBuilderOverlayFocus.SMITHING;
        }
        if (MntnBuilderConfig.FOCUS_CRAFTING.equals(key)) {
            return MntnBuilderOverlayFocus.CRAFTING;
        }
        if (MntnBuilderConfig.FOCUS_ATTACK.equals(key)) {
            return MntnBuilderOverlayFocus.ATTACK;
        }
        if (MntnBuilderConfig.FOCUS_STRENGTH.equals(key)) {
            return MntnBuilderOverlayFocus.STRENGTH;
        }
        if (MntnBuilderConfig.FOCUS_DEFENCE.equals(key)) {
            return MntnBuilderOverlayFocus.DEFENCE;
        }
        if (MntnBuilderConfig.FOCUS_PRAYER.equals(key)) {
            return MntnBuilderOverlayFocus.PRAYER;
        }
        if (MntnBuilderConfig.FOCUS_QUESTS.equals(key)) {
            return MntnBuilderOverlayFocus.QUESTS;
        }
        return null;
    }
}
