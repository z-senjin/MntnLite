package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

@ConfigGroup(MntnBuilderConfig.CONFIG_GROUP)
@ConfigInformation("Planner-driven F2P account builder. This vertical-slice build only knows " +
        "about a single Fishing goal - once that loop is validated end-to-end, add Cooking/" +
        "Combat/Woodcutting goals and activities the same way.")
public interface MntnBuilderConfig extends Config {
    String CONFIG_GROUP = "MntnBuilder";

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

    @ConfigItem(
            keyName = "attackTarget",
            name = "Attack target level",
            description = "The planner will keep training Attack until real level reaches this",
            position = 6
    )
    default int attackTarget() {
        return 20;
    }

    @ConfigItem(
            keyName = "strengthTarget",
            name = "Strength target level",
            description = "The planner will keep training Strength until real level reaches this",
            position = 7
    )
    default int strengthTarget() {
        return 20;
    }

    @ConfigItem(
            keyName = "defenceTarget",
            name = "Defence target level",
            description = "The planner will keep training Defence until real level reaches this",
            position = 8
    )
    default int defenceTarget() {
        return 20;
    }

    @ConfigItem(
            keyName = "prayerTarget",
            name = "Prayer target level",
            description = "The planner will keep training Prayer and burying bones until real level reaches this",
            position = 9
    )
    default int prayerTarget() {
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
}
