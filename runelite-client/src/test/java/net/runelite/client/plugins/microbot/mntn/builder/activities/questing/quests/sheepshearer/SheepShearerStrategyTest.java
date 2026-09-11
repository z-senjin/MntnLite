package net.runelite.client.plugins.microbot.mntn.builder.activities.questing.quests.sheepshearer;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.BankView;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SheepShearerStrategyTest {

    @Test
    public void finishedQuestCannotCreateASheepShearerStrategy() {
        QuestContext context = new QuestContext(QuestState.FINISHED);

        assertFalse(new SheepShearerStrategy().canExecute(context));
    }

    @Test
    public void inProgressQuestIsPrioritizedForCompletion() {
        SheepShearerStrategy strategy = new SheepShearerStrategy();

        assertTrue(strategy.score(new QuestContext(QuestState.IN_PROGRESS))
                > strategy.score(new QuestContext(QuestState.NOT_STARTED)));
    }

    @Test
    public void partialHandInOnlyRequiresTheOutstandingBallsOfWool() {
        QuestContext context = new QuestContext(QuestState.IN_PROGRESS, 20);

        assertEquals(1, SheepShearerStrategy.remainingBallsRequired(context));
    }

    private static final class QuestContext extends AccountContext {
        private final QuestState state;
        private final int sheepVarp;
        private final InventoryView inventory = new InventoryView() {
            @Override
            public int getCount(String itemName) {
                return 0;
            }
        };
        private final BankView bank = new BankView() {
            @Override
            public int getCount(String itemName) {
                return 0;
            }
        };

        private QuestContext(QuestState state) {
            this(state, 0);
        }

        private QuestContext(QuestState state, int sheepVarp) {
            this.state = state;
            this.sheepVarp = sheepVarp;
        }

        @Override
        public QuestState getQuestState(Quest quest) {
            return quest == Quest.SHEEP_SHEARER ? state : QuestState.NOT_STARTED;
        }

        @Override
        public int getVarpValue(int varpId) {
            return varpId == VarPlayerID.SHEEP ? sheepVarp : 0;
        }

        @Override
        public InventoryView inventory() {
            return inventory;
        }

        @Override
        public BankView bank() {
            return bank;
        }
    }
}
