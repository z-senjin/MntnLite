package net.runelite.client.plugins.microbot.mntn.builder.activities.questing;

import net.runelite.api.Quest;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.quests.cooksassistant.CooksAssistantStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.QUESTING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        if (request.type() != ActivityType.QUESTING) {
            return false;
        }

        if (request.payload() instanceof Quest) {
            Quest quest = (Quest) request.payload();
            return quest == Quest.COOKS_ASSISTANT;
        }

        return true;
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        if (request.payload() instanceof Quest) {
            Quest quest = (Quest) request.payload();
            if (quest == Quest.COOKS_ASSISTANT) {
                return Collections.singletonList(new CooksAssistantStrategy());
            }
        }

        List<Strategy> strategies = new ArrayList<>();
        strategies.add(new CooksAssistantStrategy());
        return strategies;
    }
}
