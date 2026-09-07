package net.runelite.client.plugins.microbot.mntn.builder.activities.questing;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class QuestMetadata {

    private final Quest quest;
    private final WorldPoint preferredLocation;
    private final List<Requirement> requirements;
    private final Map<Skill, Integer> xpRewards;
    private final int questPoints;

    public QuestMetadata(
            Quest quest,
            WorldPoint preferredLocation,
            List<Requirement> requirements,
            Map<Skill, Integer> xpRewards,
            int questPoints
    ) {
        this.quest = quest;
        this.preferredLocation = preferredLocation;
        this.requirements = new ArrayList<>(requirements);
        this.xpRewards = new EnumMap<>(Skill.class);
        this.xpRewards.putAll(xpRewards);
        this.questPoints = questPoints;
    }

    public Quest getQuest() {
        return quest;
    }

    public WorldPoint getPreferredLocation() {
        return preferredLocation;
    }

    public List<Requirement> requirements() {
        return Collections.unmodifiableList(requirements);
    }

    public Map<Skill, Integer> xpRewards() {
        return Collections.unmodifiableMap(xpRewards);
    }

    public int getQuestPoints() {
        return questPoints;
    }

    public double unlockValue(AccountContext context) {
        double value = questPoints * 6.0;
        for (Map.Entry<Skill, Integer> reward : xpRewards.entrySet()) {
            int level = context.getRealLevel(reward.getKey());
            double earlyLevelMultiplier = level < 30 ? 1.0 : 0.4;
            value += (reward.getValue() / 500.0) * earlyLevelMultiplier;
        }
        return value;
    }
}
