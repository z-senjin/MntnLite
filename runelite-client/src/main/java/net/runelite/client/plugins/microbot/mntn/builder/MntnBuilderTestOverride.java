package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.cooking.CookingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.crafting.CraftingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.firemaking.FiremakingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmeltingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.combat.CombatTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.CookingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.CraftingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FiremakingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FishingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.MiningTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.SmeltingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.WoodcuttingTask;

/** Direct production-task entry points for focused runtime testing. */
public enum MntnBuilderTestOverride {
    NORMAL_PLANNER("Normal planner", null, null),
    FISHING_SHRIMP("Fishing: net shrimp", ActivityType.FISHING, null),
    COOKING_SHRIMP("Cooking: shrimp", ActivityType.COOKING, null),
    FIREMAKING_LOGS("Firemaking: logs", ActivityType.FIREMAKING, null),
    FIREMAKING_WILLOW_LOGS("Firemaking: willow logs", ActivityType.FIREMAKING, null),
    WOODCUTTING_NORMAL_TREES("Woodcutting: normal trees", ActivityType.WOODCUTTING, null),
    MINING_COPPER("Mining: copper ore", ActivityType.MINING, null),
    MINING_IRON("Mining: iron ore", ActivityType.MINING, null),
    SMELTING_BRONZE("Smithing: bronze bars", ActivityType.SMITHING, null),
    SMELTING_IRON("Smithing: iron bars", ActivityType.SMITHING, null),
    CRAFTING_OPAL("Crafting: cut opals", ActivityType.CRAFTING, null),
    CRAFTING_GOLD_RING("Crafting: gold rings", ActivityType.CRAFTING, null),
    COMBAT_CHICKENS_ATTACK("Combat: chickens (Attack)", ActivityType.COMBAT, Skill.ATTACK),
    COMBAT_CHICKENS_STRENGTH("Combat: chickens (Strength)", ActivityType.COMBAT, Skill.STRENGTH),
    COMBAT_CHICKENS_DEFENCE("Combat: chickens (Defence)", ActivityType.COMBAT, Skill.DEFENCE),
    COMBAT_CHICKENS_PRAYER("Combat: chickens (Prayer)", ActivityType.COMBAT, Skill.PRAYER);

    private final String displayName;
    private final ActivityType activityType;
    private final Skill combatSkill;

    MntnBuilderTestOverride(String displayName, ActivityType activityType, Skill combatSkill) {
        this.displayName = displayName;
        this.activityType = activityType;
        this.combatSkill = combatSkill;
    }

    public boolean isActive() {
        return this != NORMAL_PLANNER;
    }

    public String displayName() {
        return displayName;
    }

    public ActivityType activityType() {
        return activityType;
    }

    public Task createTask(AccountContext context) {
        switch (this) {
            case FISHING_SHRIMP:
                return new FishingTask(FishingStrategy.Method.NET_SHRIMP);
            case COOKING_SHRIMP:
                return new CookingTask(CookingStrategy.Method.COOK_SHRIMP);
            case FIREMAKING_LOGS:
                return new FiremakingTask(FiremakingStrategy.Method.LOGS);
            case FIREMAKING_WILLOW_LOGS:
                return new FiremakingTask(FiremakingStrategy.Method.WILLOW_LOGS);
            case WOODCUTTING_NORMAL_TREES:
                return new WoodcuttingTask(WoodcuttingStrategy.Method.NORMAL_TREE);
            case MINING_COPPER:
                return new MiningTask(MiningStrategy.Method.COPPER_ORE);
            case MINING_IRON:
                return new MiningTask(MiningStrategy.Method.IRON_ORE);
            case SMELTING_BRONZE:
                return new SmeltingTask(SmeltingStrategy.Bar.BRONZE_BAR);
            case SMELTING_IRON:
                return new SmeltingTask(SmeltingStrategy.Bar.IRON_BAR);
            case CRAFTING_OPAL:
                return new CraftingTask(CraftingStrategy.Method.CUT_OPAL);
            case CRAFTING_GOLD_RING:
                return new CraftingTask(CraftingStrategy.Method.GOLD_RING);
            case COMBAT_CHICKENS_ATTACK:
            case COMBAT_CHICKENS_STRENGTH:
            case COMBAT_CHICKENS_DEFENCE:
            case COMBAT_CHICKENS_PRAYER:
                return createCombatTask(context);
            case NORMAL_PLANNER:
            default:
                return null;
        }
    }

    private Task createCombatTask(AccountContext context) {
        int currentLevel = context.getRealLevel(combatSkill);
        if (currentLevel >= 99) {
            return null;
        }
        int targetLevel = currentLevel + 1;
        int prayerTarget = combatSkill == Skill.PRAYER ? targetLevel : 0;
        return new CombatTask(CombatStrategy.Monster.CHICKENS, combatSkill, targetLevel, prayerTarget);
    }
}
