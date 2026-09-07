package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

/**
 * Owns exactly one running Task at a time. Does not decide WHAT to run - that's
 * AccountPlanner's job. This class just ticks the current task and reports back whether
 * the planner needs to be consulted again.
 */
public class TaskManager {

    private Task currentTask;
    private TaskStatus lastStatus = TaskStatus.COMPLETE;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;

    public void setTask(Task task) {
        this.currentTask = task;
        this.lastStatus = task != null ? TaskStatus.RUNNING : TaskStatus.COMPLETE;
        this.lastStopReason = TaskStopReason.NONE;
    }

    public boolean hasTask() {
        return currentTask != null;
    }

    public Task getCurrentTask() {
        return currentTask;
    }

    public TaskStatus getLastStatus() {
        return lastStatus;
    }

    public TaskStopReason getLastStopReason() {
        return lastStopReason;
    }

    public TaskStatus tick(AccountContext context) {
        if (currentTask == null) {
            lastStatus = TaskStatus.COMPLETE;
            lastStopReason = TaskStopReason.NONE;
            return TaskStatus.COMPLETE;
        }

        if (currentTask.needsReplan(context)) {
            TaskStopReason reason = currentTask.getReplanStopReason(context);
            currentTask = null;
            lastStatus = TaskStatus.REPLAN;
            lastStopReason = reason != null ? reason : TaskStopReason.TASK_REQUESTED_REPLAN;
            return TaskStatus.REPLAN;
        }

        TaskStatus status = currentTask.tick(context);
        lastStatus = status;
        lastStopReason = status.clearsTask()
                ? currentTask.getLastStopReason()
                : TaskStopReason.NONE;
        if (status.isUnsuccessfulStop() && lastStopReason == TaskStopReason.NONE) {
            lastStopReason = TaskStopReason.UNKNOWN;
        }

        if (status.clearsTask()) {
            currentTask = null;
        }

        return status;
    }
}
