package net.runelite.client.plugins.microbot.mntn.builder.activities.questing;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuestCatalogTest {

    @Test
    public void exposesSupportedQuestMetadata() {
        QuestMetadata cooksAssistant = QuestCatalog.get(Quest.COOKS_ASSISTANT).orElseThrow(AssertionError::new);
        QuestMetadata doricsQuest = QuestCatalog.get(Quest.DORICS_QUEST).orElseThrow(AssertionError::new);

        assertEquals(Quest.COOKS_ASSISTANT, cooksAssistant.getQuest());
        assertEquals(1, cooksAssistant.getQuestPoints());
        assertEquals(Integer.valueOf(300), cooksAssistant.xpRewards().get(Skill.COOKING));
        assertTrue(cooksAssistant.requirements().size() >= 3);

        assertEquals(Quest.DORICS_QUEST, doricsQuest.getQuest());
        assertEquals(1, doricsQuest.getQuestPoints());
        assertEquals(Integer.valueOf(1300), doricsQuest.xpRewards().get(Skill.MINING));
        assertTrue(doricsQuest.requirements().size() >= 4);
    }
}
