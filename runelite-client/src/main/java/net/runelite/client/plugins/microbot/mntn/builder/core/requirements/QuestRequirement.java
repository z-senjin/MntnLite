package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.Collections;
import java.util.List;

public class QuestRequirement implements Requirement {

    private final Quest quest;
    private final double urgency;

    public QuestRequirement(Quest quest, double urgency) {
        this.quest = quest;
        this.urgency = urgency;
    }

    @Override
    public boolean isSatisfied(AccountContext context) {
        return context.getQuestState(quest) == QuestState.FINISHED;
    }

    @Override
    public List<ActivityRequest> getWaysToSatisfy(AccountContext context) {
        return Collections.singletonList(new ActivityRequest(ActivityType.QUESTING, quest));
    }

    @Override
    public double urgency(AccountContext context) {
        return urgency;
    }

    @Override
    public String description() {
        return "Complete quest: " + quest.getName();
    }

    public Quest getQuest() {
        return quest;
    }
}
