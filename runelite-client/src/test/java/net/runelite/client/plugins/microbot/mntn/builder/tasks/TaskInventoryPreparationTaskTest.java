package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskInventoryPreparationTaskTest {

    @Test
    public void skipsBankingWhenInventoryContainsOnlyTheTaskLoadout() {
        TestContext context = new TestContext(false, true);
        TaskInventoryPreparationTask task = new TaskInventoryPreparationTask(
                new TestTask(), new String[]{"Small fishing net"});

        assertFalse(task.needsInventoryPreparation(context));
        assertTrue(task.tick(context) == TaskStatus.RUNNING);
    }

    @Test
    public void preparesWhenInventoryContainsUnrelatedItemsOrIsFull() {
        TaskInventoryPreparationTask unrelatedItems = new TaskInventoryPreparationTask(
                new TestTask(), new String[]{"Bronze pickaxe"});
        TaskInventoryPreparationTask fullInventory = new TaskInventoryPreparationTask(
                new TestTask(), new String[]{"Bronze pickaxe"});

        assertTrue(unrelatedItems.needsInventoryPreparation(new TestContext(false, false)));
        assertTrue(fullInventory.needsInventoryPreparation(new TestContext(true, true)));
    }

    private static class TestContext extends AccountContext {
        private final InventoryView inventory;

        private TestContext(boolean full, boolean onlyTaskItems) {
            inventory = new TestInventoryView(full, onlyTaskItems);
        }

        @Override
        public InventoryView inventory() {
            return inventory;
        }
    }

    private static class TestInventoryView extends InventoryView {
        private final boolean full;
        private final boolean onlyTaskItems;

        private TestInventoryView(boolean full, boolean onlyTaskItems) {
            this.full = full;
            this.onlyTaskItems = onlyTaskItems;
        }

        @Override
        public boolean isFull() {
            return full;
        }

        @Override
        public boolean hasOnlyItems(Collection<String> itemNames) {
            return onlyTaskItems;
        }
    }

    private static class TestTask implements Task {
        @Override
        public TaskStatus tick(AccountContext context) {
            return TaskStatus.RUNNING;
        }

        @Override
        public boolean needsReplan(AccountContext context) {
            return false;
        }
    }
}
