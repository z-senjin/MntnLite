package net.runelite.client.plugins.microbot.mntn.builder.activities.questing.quests.cooksassistant;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.questing.CooksAssistantTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

public class CooksAssistantStrategy implements Strategy {

    public static final String EGG = "Egg";
    public static final String BUCKET_OF_MILK = "Bucket of milk";
    public static final String POT_OF_FLOUR = "Pot of flour";

    @Override
    public String name() {
        return "COOKS_ASSISTANT";
    }

    @Override
    public boolean canExecute(AccountContext context) {
        return context.getQuestState(Quest.COOKS_ASSISTANT) != QuestState.FINISHED;
    }

    @Override
    public double score(AccountContext context) {
        if (context.getQuestState(Quest.COOKS_ASSISTANT) == QuestState.FINISHED) {
            return -1000;
        }

        double score = 50.0;

        // Convenience bonus for items already on-hand
        if (context.inventory().hasItem(EGG)) {
            score += 10;
        } else if (context.bank().hasItem(EGG)) {
            score += 5;
        }

        if (context.inventory().hasItem(BUCKET_OF_MILK)) {
            score += 10;
        } else if (context.bank().hasItem(BUCKET_OF_MILK)) {
            score += 5;
        }

        if (context.inventory().hasItem(POT_OF_FLOUR)) {
            score += 10;
        } else if (context.bank().hasItem(POT_OF_FLOUR)) {
            score += 5;
        }

        return score;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new CooksAssistantTask();
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(15, 30);
        return Duration.ofMinutes(minutes);
    }
}
