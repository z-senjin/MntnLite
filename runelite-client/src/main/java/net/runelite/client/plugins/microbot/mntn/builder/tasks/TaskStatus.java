package net.runelite.client.plugins.microbot.mntn.builder.tasks;

public enum TaskStatus {
    RUNNING,
    COMPLETE,
    BLOCKED,
    FAILED,
    REPLAN;

    public boolean clearsTask() {
        return this == COMPLETE
                || this == BLOCKED
                || this == FAILED
                || this == REPLAN;
    }

    public boolean needsPlannerDecision() {
        return this == COMPLETE
                || this == BLOCKED
                || this == FAILED
                || this == REPLAN;
    }

    public boolean isUnsuccessfulStop() {
        return this == BLOCKED
                || this == FAILED
                || this == REPLAN;
    }
}
