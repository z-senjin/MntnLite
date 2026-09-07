package net.runelite.client.plugins.microbot.mntn.builder.activities.questing;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.quests.cooksassistant.CooksAssistantStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.quests.doricsquest.DoricQuestStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.SkillRequirement;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class QuestCatalog {

    private static final Map<Quest, QuestMetadata> QUESTS = build();

    private QuestCatalog() {
    }

    public static Optional<QuestMetadata> get(Quest quest) {
        return Optional.ofNullable(QUESTS.get(quest));
    }

    public static boolean isSupported(Quest quest) {
        return QUESTS.containsKey(quest);
    }

    private static Map<Quest, QuestMetadata> build() {
        Map<Quest, QuestMetadata> quests = new EnumMap<>(Quest.class);
        quests.put(Quest.COOKS_ASSISTANT, cooksAssistant());
        quests.put(Quest.DORICS_QUEST, doricsQuest());
        return Collections.unmodifiableMap(quests);
    }

    private static QuestMetadata cooksAssistant() {
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
        xp.put(Skill.COOKING, 300);
        return new QuestMetadata(
                Quest.COOKS_ASSISTANT,
                new WorldPoint(3208, 3213, 0),
                Arrays.asList(
                        new ItemRequirement(CooksAssistantStrategy.EGG, 1),
                        new ItemRequirement(CooksAssistantStrategy.BUCKET_OF_MILK, 1),
                        new ItemRequirement(CooksAssistantStrategy.POT_OF_FLOUR, 1)
                ),
                xp,
                1
        );
    }

    private static QuestMetadata doricsQuest() {
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
        xp.put(Skill.MINING, 1300);
        return new QuestMetadata(
                Quest.DORICS_QUEST,
                new WorldPoint(2952, 3451, 0),
                Arrays.asList(
                        new SkillRequirement(Skill.MINING, 15),
                        new ItemRequirement(DoricQuestStrategy.CLAY, DoricQuestStrategy.CLAY_NEEDED),
                        new ItemRequirement(DoricQuestStrategy.COPPER_ORE, DoricQuestStrategy.COPPER_NEEDED),
                        new ItemRequirement(DoricQuestStrategy.IRON_ORE, DoricQuestStrategy.IRON_NEEDED)
                ),
                xp,
                1
        );
    }
}
