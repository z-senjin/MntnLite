package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.api.Skill;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

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
    private final Map<Skill, Integer> skillLevels;
    private final boolean questFocusAvailable;

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
        this(visible, detailed, runnerState, goal, requirement, activity, strategy, task, taskStatus,
                lastStopReason, contentMode, sessionFlavor, commitmentDuration, taskStartTime, score,
                Collections.emptyMap(), false);
    }

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
            double score,
            Map<Skill, Integer> skillLevels,
            boolean questFocusAvailable
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
        this.skillLevels = skillLevels == null || skillLevels.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new EnumMap<>(skillLevels));
        this.questFocusAvailable = questFocusAvailable;
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

    public Map<Skill, Integer> getSkillLevels() {
        return skillLevels;
    }

    public int getSkillLevel(Skill skill) {
        return skillLevels.getOrDefault(skill, 0);
    }

    public boolean isQuestFocusAvailable() {
        return questFocusAvailable;
    }
}
