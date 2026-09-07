package net.runelite.client.plugins.microbot.mntn.builder;

import java.time.Duration;
import java.time.Instant;

public class MntnBuilderOverlayState {

    private final boolean visible;
    private final boolean detailed;
    private final String runnerState;
    private final String goal;
    private final String requirement;
    private final String activity;
    private final String strategy;
    private final String task;
    private final String taskStatus;
    private final String lastStopReason;
    private final String contentMode;
    private final String sessionFlavor;
    private final Duration commitmentDuration;
    private final Instant taskStartTime;
    private final double score;

    public MntnBuilderOverlayState(
            boolean visible,
            boolean detailed,
            String runnerState,
            String goal,
            String requirement,
            String activity,
            String strategy,
            String task,
            String taskStatus,
            String lastStopReason,
            String contentMode,
            String sessionFlavor,
            Duration commitmentDuration,
            Instant taskStartTime,
            double score
    ) {
        this.visible = visible;
        this.detailed = detailed;
        this.runnerState = runnerState;
        this.goal = goal;
        this.requirement = requirement;
        this.activity = activity;
        this.strategy = strategy;
        this.task = task;
        this.taskStatus = taskStatus;
        this.lastStopReason = lastStopReason;
        this.contentMode = contentMode;
        this.sessionFlavor = sessionFlavor;
        this.commitmentDuration = commitmentDuration;
        this.taskStartTime = taskStartTime;
        this.score = score;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isDetailed() {
        return detailed;
    }

    public String getRunnerState() {
        return runnerState;
    }

    public String getGoal() {
        return goal;
    }

    public String getRequirement() {
        return requirement;
    }

    public String getActivity() {
        return activity;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getTask() {
        return task;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public String getLastStopReason() {
        return lastStopReason;
    }

    public String getContentMode() {
        return contentMode;
    }

    public String getSessionFlavor() {
        return sessionFlavor;
    }

    public Duration getCommitmentDuration() {
        return commitmentDuration;
    }

    public Instant getTaskStartTime() {
        return taskStartTime;
    }

    public double getScore() {
        return score;
    }
}
