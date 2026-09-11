package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CookingTaskTest {

    @Test
    public void completedBankingAdvancesCookingInsteadOfCompletingTheParentTask() {
        assertEquals(TaskStatus.RUNNING, CookingTask.statusAfterBanking(TaskStatus.COMPLETE));
        assertEquals(TaskStatus.RUNNING, CookingTask.statusAfterBanking(TaskStatus.RUNNING));
        assertEquals(TaskStatus.REPLAN, CookingTask.statusAfterBanking(TaskStatus.REPLAN));
    }
}
