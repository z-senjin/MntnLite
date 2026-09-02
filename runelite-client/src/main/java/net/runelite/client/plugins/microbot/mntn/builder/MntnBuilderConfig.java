package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

@ConfigGroup("MntnBuilder")
@ConfigInformation("Planner-driven F2P account builder. This vertical-slice build only knows " +
        "about a single Fishing goal - once that loop is validated end-to-end, add Cooking/" +
        "Combat/Woodcutting goals and activities the same way.")
public interface MntnBuilderConfig extends Config {
    @ConfigItem(
            keyName = "antibanIntensity",
            name = "Antiban intensity",
            description = "Activity intensity used by Mntn"
    )
    default ActivityIntensity antibanIntensity() {
        return ActivityIntensity.MODERATE;
    }

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
            description = "The planner will keep cooking until real Cooking level reaches this",
            position = 1
    )
    default int cookingTarget() {
        return 20;
    }

    @ConfigItem(
            keyName = "woodcuttingTarget",
            name = "Woodcutting target level",
            description = "The planner will keep woodcutting until real Woodcutting level reaches this",
            position = 3
    )
    default int woodcuttingTarget() {
        return 20;
    }

    @ConfigItem(
            keyName = "miningTarget",
            name = "Mining target level",
            description = "The planner will keep mining until real Mining level reaches this",
            position = 4
    )
    default int miningTarget() {
        return 20;
    }

    @ConfigItem(
            keyName = "smithingTarget",
            name = "Smithing target level",
            description = "The planner will keep smithing until real Smithing level reaches this",
            position = 5
    )
    default int smithingTarget() {
        return 20;
    }

    @ConfigSection(
            name = "Quests",
            description = "Quest goals for the builder to complete",
            position = 10
    )
    String questsSection = "questsSection";

    @ConfigItem(
            keyName = "enableCooksAssistant",
            name = "Cook's Assistant",
            description = "Complete Cook's Assistant",
            section = "questsSection",
            position = 1
    )
    default boolean enableCooksAssistant() {
        return true;
    }
}
