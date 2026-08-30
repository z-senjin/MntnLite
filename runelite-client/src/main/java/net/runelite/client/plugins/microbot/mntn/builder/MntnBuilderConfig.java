package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("MntnBuilder")
@ConfigInformation("Planner-driven F2P account builder. This vertical-slice build only knows " +
        "about a single Fishing goal - once that loop is validated end-to-end, add Cooking/" +
        "Combat/Woodcutting goals and activities the same way.")
public interface MntnBuilderConfig extends Config {

    @ConfigItem(
            keyName = "fishingTarget",
            name = "Fishing target level",
            description = "The planner will keep fishing until real Fishing level reaches this",
            position = 1
    )
    default int fishingTarget() {
        return 20;
    }

    @ConfigItem(
            keyName = "cookingTarget",
            name = "Cooking target level",
            description = "The planner will keep fishing until real Cooking level reaches this",
            position = 1
    )
    default int cookingTarget() {
        return 20;
    }
}
