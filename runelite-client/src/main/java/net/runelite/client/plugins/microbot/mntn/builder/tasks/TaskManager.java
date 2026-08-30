package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

/**
 * Owns exactly one running Task at a time. Does not decide WHAT to run - that's
 * AccountPlanner's job. This class just ticks the current task and reports back whether
 * the planner needs to be consulted again.
 */
public class TaskManager {

    private Task currentTask;

    public void setTask(Task task) {
        this.currentTask = task;
    }

    public boolean hasTask() {
        return currentTask != null;
    }

    public Task getCurrentTask() {
        return currentTask;
    }

    public TaskStatus tick(AccountContext context) {
        if (currentTask == null) {
            return TaskStatus.COMPLETE;
        }

        if (currentTask.needsReplan(context)) {
            currentTask = null;
            return TaskStatus.REPLAN;
        }

        TaskStatus status = currentTask.tick(context);

        if (status == TaskStatus.COMPLETE || status == TaskStatus.FAILED || status == TaskStatus.REPLAN) {
            currentTask = null;
        }

        return status;
    }
}
