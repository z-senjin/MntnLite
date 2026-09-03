package net.runelite.client.plugins.microbot.mntn.builder.activities.questing.quests.doricsquest;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.questing.DoricQuestTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

public class DoricQuestStrategy implements Strategy {

    public static final String CLAY = "Clay";
    public static final String COPPER_ORE = "Copper ore";
    public static final String IRON_ORE = "Iron ore";

    public static final int CLAY_NEEDED = 6;
    public static final int COPPER_NEEDED = 4;
    public static final int IRON_NEEDED = 2;

    @Override
    public String name() {
        return "DORICS_QUEST";
    }

    @Override
    public boolean canExecute(AccountContext context) {
        if (context.getQuestState(Quest.DORICS_QUEST) == QuestState.FINISHED) {
            return false;
        }
        return context.getRealLevel(Skill.MINING) >= 15;
    }

    @Override
    public double score(AccountContext context) {
        if (context.getQuestState(Quest.DORICS_QUEST) == QuestState.FINISHED) {
            return -1000;
        }
        if (context.getRealLevel(Skill.MINING) < 15) {
            return -1000;
        }

        double score = 50.0;

        // Convenience bonus for items already on-hand
        int clayAvailable = Math.min(
                context.inventory().getCount(CLAY) + context.bank().getCount(CLAY),
                CLAY_NEEDED
        );
        score += clayAvailable * 2;

        int copperAvailable = Math.min(
                context.inventory().getCount(COPPER_ORE) + context.bank().getCount(COPPER_ORE),
                COPPER_NEEDED
        );
        score += copperAvailable * 2;

        int ironAvailable = Math.min(
                context.inventory().getCount(IRON_ORE) + context.bank().getCount(IRON_ORE),
                IRON_NEEDED
        );
        score += ironAvailable * 2;

        return score;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new DoricQuestTask();
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(15, 30);
        return Duration.ofMinutes(minutes);
    }
}
