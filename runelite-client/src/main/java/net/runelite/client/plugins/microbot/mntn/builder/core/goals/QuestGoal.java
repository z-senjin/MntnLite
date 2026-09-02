package net.runelite.client.plugins.microbot.mntn.builder.core.goals;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.QuestRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.Collections;
import java.util.List;

public class QuestGoal implements Goal {

    private final Quest quest;
    private final double priority;

    public QuestGoal(Quest quest, double priority) {
        this.quest = quest;
        this.priority = priority;
    }

    @Override
    public String name() {
        return quest.getName();
    }

    @Override
    public boolean isComplete(AccountContext context) {
        return context.getQuestState(quest) == QuestState.FINISHED;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        return Collections.singletonList(new QuestRequirement(quest, priority));
    }

    @Override
    public double priority(AccountContext context) {
        return priority;
    }

    public Quest getQuest() {
        return quest;
    }
}
