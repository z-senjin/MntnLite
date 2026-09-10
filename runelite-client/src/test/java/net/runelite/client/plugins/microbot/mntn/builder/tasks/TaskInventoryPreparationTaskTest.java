package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.EquipmentView;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskInventoryPreparationTaskTest {

    @Test
    public void skipsBankingWhenInventoryAndEquipmentAreEmpty() {
        TestContext context = new TestContext(true, true);
        TaskInventoryPreparationTask task = new TaskInventoryPreparationTask(new TestTask());

        assertFalse(task.needsPreparation(context));
        assertTrue(task.tick(context) == TaskStatus.RUNNING);
    }

    @Test
    public void preparesWhenEitherInventoryOrEquipmentIsNotEmpty() {
        TaskInventoryPreparationTask inventoryItems = new TaskInventoryPreparationTask(new TestTask());
        TaskInventoryPreparationTask wornGear = new TaskInventoryPreparationTask(new TestTask());

        assertTrue(inventoryItems.needsPreparation(new TestContext(false, true)));
        assertTrue(wornGear.needsPreparation(new TestContext(true, false)));
    }

    private static class TestContext extends AccountContext {
        private final InventoryView inventory;
        private final EquipmentView equipment;

        private TestContext(boolean inventoryEmpty, boolean equipmentEmpty) {
            inventory = new TestInventoryView(inventoryEmpty);
            equipment = new TestEquipmentView(equipmentEmpty);
        }

        @Override
        public InventoryView inventory() {
            return inventory;
        }

        @Override
        public EquipmentView equipment() {
            return equipment;
        }
    }

    private static class TestInventoryView extends InventoryView {
        private final boolean empty;

        private TestInventoryView(boolean empty) {
            this.empty = empty;
        }

        @Override
        public boolean isEmpty() {
            return empty;
        }
    }

    private static class TestEquipmentView extends EquipmentView {
        private final boolean empty;

        private TestEquipmentView(boolean empty) {
            this.empty = empty;
        }

        @Override
        public boolean isEmpty() {
            return empty;
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
