package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
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
    String SKIP_ACTIVITY = "skipActivity";
    String REMOVE_ACTIVITY_TIME = "removeActivityTime";
    String ADD_ACTIVITY_TIME = "addActivityTime";
    String FOCUS_FISHING = "focusFishing";
    String FOCUS_COOKING = "focusCooking";
    String FOCUS_FIREMAKING = "focusFiremaking";
    String FOCUS_WOODCUTTING = "focusWoodcutting";
    String FOCUS_MINING = "focusMining";
    String FOCUS_SMITHING = "focusSmithing";
    String FOCUS_CRAFTING = "focusCrafting";
    String FOCUS_ATTACK = "focusAttack";
    String FOCUS_STRENGTH = "focusStrength";
    String FOCUS_DEFENCE = "focusDefence";
    String FOCUS_PRAYER = "focusPrayer";
    String FOCUS_QUESTS = "focusQuests";

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
            name = "Activity Controls",
            description = "Manual activity controls that remain available while game input is disabled",
            position = 2
    )
    String activityControlsSection = "activityControlsSection";

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

    @ConfigItem(
            keyName = SKIP_ACTIVITY,
            name = "Skip activity",
            description = "Stop the current task and select a new activity",
            section = "activityControlsSection",
            position = 1
    )
    default ConfigButton skipActivity() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = REMOVE_ACTIVITY_TIME,
            name = "Remove 10 minutes",
            description = "Reduce the current activity time by 10 minutes",
            section = "activityControlsSection",
            position = 2
    )
    default ConfigButton removeActivityTime() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = ADD_ACTIVITY_TIME,
            name = "Add 10 minutes",
            description = "Extend the current activity time by 10 minutes",
            section = "activityControlsSection",
            position = 3
    )
    default ConfigButton addActivityTime() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_FISHING,
            name = "Focus Fishing",
            description = "Run the next available Fishing activity",
            section = "activityControlsSection",
            position = 4
    )
    default ConfigButton focusFishing() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_COOKING,
            name = "Focus Cooking",
            description = "Run the next available Cooking activity",
            section = "activityControlsSection",
            position = 5
    )
    default ConfigButton focusCooking() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_FIREMAKING,
            name = "Focus Firemaking",
            description = "Run the next available Firemaking activity",
            section = "activityControlsSection",
            position = 6
    )
    default ConfigButton focusFiremaking() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_WOODCUTTING,
            name = "Focus Woodcutting",
            description = "Run the next available Woodcutting activity",
            section = "activityControlsSection",
            position = 7
    )
    default ConfigButton focusWoodcutting() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_MINING,
            name = "Focus Mining",
            description = "Run the next available Mining activity",
            section = "activityControlsSection",
            position = 8
    )
    default ConfigButton focusMining() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_SMITHING,
            name = "Focus Smithing",
            description = "Run the next available Smithing activity",
            section = "activityControlsSection",
            position = 9
    )
    default ConfigButton focusSmithing() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_CRAFTING,
            name = "Focus Crafting",
            description = "Run the next available Crafting activity",
            section = "activityControlsSection",
            position = 10
    )
    default ConfigButton focusCrafting() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_ATTACK,
            name = "Focus Attack",
            description = "Run the next available Attack activity",
            section = "activityControlsSection",
            position = 11
    )
    default ConfigButton focusAttack() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_STRENGTH,
            name = "Focus Strength",
            description = "Run the next available Strength activity",
            section = "activityControlsSection",
            position = 12
    )
    default ConfigButton focusStrength() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_DEFENCE,
            name = "Focus Defence",
            description = "Run the next available Defence activity",
            section = "activityControlsSection",
            position = 13
    )
    default ConfigButton focusDefence() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_PRAYER,
            name = "Focus Prayer",
            description = "Run the next available Prayer activity",
            section = "activityControlsSection",
            position = 14
    )
    default ConfigButton focusPrayer() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = FOCUS_QUESTS,
            name = "Focus Quests",
            description = "Run the next available enabled quest activity",
            section = "activityControlsSection",
            position = 15
    )
    default ConfigButton focusQuests() {
        return new ConfigButton();
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
