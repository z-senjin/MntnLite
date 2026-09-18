package net.runelite.client.plugins.microbot.mntn.aio;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderTestOverride;
import net.runelite.client.plugins.microbot.mntn.builder.core.AllowedContent;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.SessionFlavor;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

@ConfigGroup(MntnAIOBuilderConfig.CONFIG_GROUP)
@ConfigInformation("Select goals, we will build it.")
public interface MntnAIOBuilderConfig extends Config {
    String CONFIG_GROUP = "MntnAIOBuilder";

    @ConfigSection(
            name = "Overlay",
            description = "In-game planner status display",
            position = 3
    )
    String overlaySection = "overlaySection";

    @ConfigSection(
            name = "Skill Targets",
            description = "Set a target to 0 to ignore that skill",
            position = 3
    )
    String skillTargetsSection = "skillTargetsSection";

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show overlay",
            description = "Show the Mntn planner status overlay",
            section = "overlaySection",
            position = 1
    )
    default boolean showOverlay() {
        return true;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "fishingTarget",
            name = "Fishing target level",
            description = "The planner will keep fishing until real Fishing level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 1
    )
    default int fishingTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "cookingTarget",
            name = "Cooking target level",
            description = "The planner will keep cooking until real Cooking level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 2
    )
    default int cookingTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "firemakingTarget",
            name = "Firemaking target level",
            description = "The planner will keep burning logs until real Firemaking level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 4
    )
    default int firemakingTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "woodcuttingTarget",
            name = "Woodcutting target level",
            description = "The planner will keep woodcutting until real Woodcutting level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 4
    )
    default int woodcuttingTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "miningTarget",
            name = "Mining target level",
            description = "The planner will keep mining until real Mining level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 6
    )
    default int miningTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "smithingTarget",
            name = "Smithing target level",
            description = "The planner will keep smithing until real Smithing level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 7
    )
    default int smithingTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "craftingTarget",
            name = "Crafting target level",
            description = "The planner will keep crafting until real Crafting level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 7
    )
    default int craftingTarget() {
        return 0;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "attackTarget",
            name = "Attack target level",
            description = "The planner will keep training Attack until real level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 8
    )
    default int attackTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "strengthTarget",
            name = "Strength target level",
            description = "The planner will keep training Strength until real level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 9
    )
    default int strengthTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "defenceTarget",
            name = "Defence target level",
            description = "The planner will keep training Defence until real level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 10
    )
    default int defenceTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "prayerTarget",
            name = "Prayer target level",
            description = "The planner will keep training Prayer and burying bones until real level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 11
    )
    default int prayerTarget() {
        return 20;
    }

    @ConfigSection(
            name = "Quests",
            description = "Quest goals for the builder to complete",
            position = 6
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

    @ConfigItem(
            keyName = "enableDoricsQuest",
            name = "Doric's Quest",
            description = "Complete Doric's Quest (requires Mining level 15)",
            section = "questsSection",
            position = 2
    )
    default boolean enableDoricsQuest() {
        return true;
    }

    @ConfigItem(
            keyName = "enableSheepShearer",
            name = "Sheep Shearer",
            description = "Complete Sheep Shearer when 20 balls of wool can be supplied",
            section = "questsSection",
            position = 3
    )
    default boolean enableSheepShearer() {
        return false;
    }
}
