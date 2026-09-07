package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.Collections;
import java.util.List;

public class QuestRequirement implements Requirement {

    private final Quest quest;

    public QuestRequirement(Quest quest) {
        this.quest = quest;
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
        QuestState state = context.getQuestState(quest);
        if (state == QuestState.IN_PROGRESS) {
            return 30;
        }
        if (state == QuestState.NOT_STARTED) {
            return 10;
        }
        return 0;
    }

    @Override
    public String description() {
        return "Complete quest: " + quest.getName();
    }

    public Quest getQuest() {
        return quest;
    }
}
