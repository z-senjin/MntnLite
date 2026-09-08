package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.mntn.builder.core.AllowedContent;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.SessionFlavor;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

@ConfigGroup(MntnBuilderConfig.CONFIG_GROUP)
@ConfigInformation("Planner-driven account builder. Current method catalog is F2P-focused, " +
        "with the core model prepared for members content later.")
public interface MntnBuilderConfig extends Config {
    String CONFIG_GROUP = "MntnBuilder";

    @ConfigSection(
            name = "General",
            description = "Planner behavior and account mode",
            position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
            name = "Testing",
            description = "Run one supported task directly without normal planner selection",
            position = 1
    )
    String testingSection = "testingSection";

    @ConfigSection(
            name = "Overlay",
            description = "In-game planner status display",
            position = 2
    )
    String overlaySection = "overlaySection";

    @ConfigSection(
            name = "Skill Targets",
            description = "Set a target to 0 to ignore that skill",
            position = 3
    )
    String skillTargetsSection = "skillTargetsSection";

    @ConfigSection(
            name = "Skill Weights",
            description = "Higher weights make unfinished skill goals more likely to be selected",
            position = 4
    )
    String skillWeightsSection = "skillWeightsSection";

    @ConfigSection(
            name = "Supply Policy",
            description = "Controls how the builder may acquire supplies",
            position = 5
    )
    String supplyPolicySection = "supplyPolicySection";

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug logging",
            description = "Enable extensive debug logging for troubleshooting",
            section = "generalSection",
            position = 1
    )
    default boolean debugLogging() {
        return false;
    }

    @ConfigItem(
            keyName = "antibanIntensity",
            name = "Antiban intensity",
            description = "Activity intensity used by Mntn",
            section = "generalSection",
            position = 2
    )
    default ActivityIntensity antibanIntensity() {
        return ActivityIntensity.MODERATE;
    }

    @ConfigItem(
            keyName = "allowedContent",
            name = "Allowed content",
            description = "Limits planner choices to F2P methods for now, or allows members methods once implemented",
            section = "generalSection",
            position = 3
    )
    default AllowedContent allowedContent() {
        return AllowedContent.F2P_ONLY;
    }

    @ConfigItem(
            keyName = "sessionFlavor",
            name = "Session flavor",
            description = "Biases planner choices and commitment length without changing the account goals",
            section = "generalSection",
            position = 4
    )
    default SessionFlavor sessionFlavor() {
        return SessionFlavor.BALANCED;
    }

    @ConfigItem(
            keyName = "testOverride",
            name = "Test override",
            description = "Select a supported task to run directly. Choose Normal planner to return to account goals.",
            section = "testingSection",
            position = 1
    )
    default MntnBuilderTestOverride testOverride() {
        return MntnBuilderTestOverride.NORMAL_PLANNER;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "testCoinTarget",
            name = "Test coin target",
            description = "Money test modes stop after reaching this many coins in inventory.",
            section = "testingSection",
            position = 2
    )
    default int testCoinTarget() {
        return 1000;
    }

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

    @ConfigItem(
            keyName = "detailedOverlay",
            name = "Detailed overlay",
            description = "Show requirement, task, score, and planner mode details",
            section = "overlaySection",
            position = 2
    )
    default boolean detailedOverlay() {
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
            keyName = "woodcuttingTarget",
            name = "Woodcutting target level",
            description = "The planner will keep woodcutting until real Woodcutting level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 3
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
            position = 4
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
            position = 5
    )
    default int smithingTarget() {
        return 20;
    }

    @Range(max = 99)
    @ConfigItem(
            keyName = "attackTarget",
            name = "Attack target level",
            description = "The planner will keep training Attack until real level reaches this. Set to 0 to ignore.",
            section = "skillTargetsSection",
            position = 6
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
            position = 7
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
            position = 8
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
            position = 9
    )
    default int prayerTarget() {
        return 20;
    }

    @ConfigSection(
            name = "Money",
            description = "Coin goals for supplies and early account building",
            position = 6
    )
    String moneySection = "moneySection";

    @Range(min = 0)
    @ConfigItem(
            keyName = "moneyTarget",
            name = "Money target",
            description = "Optional total coins target across inventory and bank. Set to 0 to disable.",
            section = "moneySection",
            position = 1
    )
    default int moneyTarget() {
        return 0;
    }

    @ConfigSection(
            name = "Quests",
            description = "Quest goals for the builder to complete",
            position = 7
    )
    String questsSection = "questsSection";

    @ConfigItem(
            keyName = "allowGrandExchange",
            name = "Allow Grand Exchange",
            description = "Allow the supply planner to buy tradeable items from the Grand Exchange",
            section = "supplyPolicySection",
            position = 1
    )
    default boolean allowGrandExchange() {
        return true;
    }

    @ConfigItem(
            keyName = "allowShops",
            name = "Allow shops",
            description = "Allow the supply planner to buy supported items from NPC shops",
            section = "supplyPolicySection",
            position = 2
    )
    default boolean allowShops() {
        return true;
    }

    @ConfigItem(
            keyName = "allowGroundPickups",
            name = "Allow ground pickups",
            description = "Allow the supply planner to pick up supported ground-spawned items",
            section = "supplyPolicySection",
            position = 3
    )
    default boolean allowGroundPickups() {
        return true;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "fishingWeight",
            name = "Fishing weight",
            description = "Priority weight for Fishing goals",
            section = "skillWeightsSection",
            position = 1
    )
    default int fishingWeight() {
        return 5;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "cookingWeight",
            name = "Cooking weight",
            description = "Priority weight for Cooking goals",
            section = "skillWeightsSection",
            position = 2
    )
    default int cookingWeight() {
        return 5;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "woodcuttingWeight",
            name = "Woodcutting weight",
            description = "Priority weight for Woodcutting goals",
            section = "skillWeightsSection",
            position = 3
    )
    default int woodcuttingWeight() {
        return 5;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "miningWeight",
            name = "Mining weight",
            description = "Priority weight for Mining goals",
            section = "skillWeightsSection",
            position = 4
    )
    default int miningWeight() {
        return 5;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "smithingWeight",
            name = "Smithing weight",
            description = "Priority weight for Smithing goals",
            section = "skillWeightsSection",
            position = 5
    )
    default int smithingWeight() {
        return 5;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "attackWeight",
            name = "Attack weight",
            description = "Priority weight for Attack goals",
            section = "skillWeightsSection",
            position = 6
    )
    default int attackWeight() {
        return 5;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "strengthWeight",
            name = "Strength weight",
            description = "Priority weight for Strength goals",
            section = "skillWeightsSection",
            position = 7
    )
    default int strengthWeight() {
        return 5;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "defenceWeight",
            name = "Defence weight",
            description = "Priority weight for Defence goals",
            section = "skillWeightsSection",
            position = 8
    )
    default int defenceWeight() {
        return 5;
    }

    @Range(min = 1, max = 9)
    @ConfigItem(
            keyName = "prayerWeight",
            name = "Prayer weight",
            description = "Priority weight for Prayer goals",
            section = "skillWeightsSection",
            position = 9
    )
    default int prayerWeight() {
        return 5;
    }

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
