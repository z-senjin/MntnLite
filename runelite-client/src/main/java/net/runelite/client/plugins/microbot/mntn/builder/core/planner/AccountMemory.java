package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Session-local planner memory.
 *
 * This is intentionally not persisted yet. It gives the scorer just enough
 * recent context to avoid immediately repeating failed or over-selected methods.
 */
public class AccountMemory {

    private static final int MAX_HISTORY_SIZE = 100;
    private static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(5);
    private static final Duration GE_SLOT_COOLDOWN = Duration.ofMinutes(1);

    private final Deque<PlanHistoryEntry> history = new ArrayDeque<>();
    private final Map<String, StopRecord> failedStrategies = new HashMap<>();
    private Instant grandExchangeUnavailableAt;

    public void recordSelected(Plan plan) {
        prune(Duration.ofHours(2));
        if (plan == null) {
            return;
        }

        history.addLast(new PlanHistoryEntry(plan.strategy().name(), Instant.now()));
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    public void recordOutcome(Plan plan, TaskStatus status) {
        recordOutcome(plan, status, TaskStopReason.UNKNOWN);
    }

    public void recordOutcome(Plan plan, TaskStatus status, TaskStopReason reason) {
        prune(Duration.ofHours(2));
        if (plan == null || status == null) {
            return;
        }

        if (status.isUnsuccessfulStop()
                && reason != TaskStopReason.REQUIREMENT_SATISFIED
                && reason != TaskStopReason.GOAL_COMPLETE) {
            failedStrategies.put(plan.strategy().name(), new StopRecord(Instant.now(), reason));
        }
        if (reason == TaskStopReason.GE_NO_OPEN_SLOT) {
            grandExchangeUnavailableAt = Instant.now();
        }
    }

    public int recentSelectionCount(String strategyName, Duration window) {
        prune(window != null ? window : Duration.ofHours(2));
        if (strategyName == null || window == null) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(window);
        int count = 0;
        for (PlanHistoryEntry entry : history) {
            if (entry.selectedAt.isAfter(cutoff) && strategyName.equals(entry.strategyName)) {
                count++;
            }
        }
        return count;
    }

    public boolean isCoolingDown(String strategyName) {
        prune(Duration.ofHours(2));
        StopRecord record = failedStrategies.get(strategyName);
        if (record == null) {
            return false;
        }

        return record.stoppedAt.plus(FAILURE_COOLDOWN).isAfter(Instant.now());
    }

    public TaskStopReason getRecentStopReason(String strategyName) {
        prune(Duration.ofHours(2));
        StopRecord record = failedStrategies.get(strategyName);
        if (record == null || !record.stoppedAt.plus(FAILURE_COOLDOWN).isAfter(Instant.now())) {
            return TaskStopReason.NONE;
        }
        return record.reason;
    }

    /**
     * A full exchange is account-wide, not a fault in one strategy. Keep this
     * short so a player who clears an offer can resume GE routes promptly.
     */
    public boolean isGrandExchangeUnavailable() {
        if (grandExchangeUnavailableAt == null) {
            return false;
        }
        if (!grandExchangeUnavailableAt.plus(GE_SLOT_COOLDOWN).isAfter(Instant.now())) {
            grandExchangeUnavailableAt = null;
            return false;
        }
        return true;
    }

    public void clear() {
        history.clear();
        failedStrategies.clear();
        grandExchangeUnavailableAt = null;
    }

    public void prune(Duration historyWindow) {
        if (historyWindow == null) {
            return;
        }

        Instant historyCutoff = Instant.now().minus(historyWindow);
        for (Iterator<PlanHistoryEntry> iterator = history.iterator(); iterator.hasNext(); ) {
            if (iterator.next().selectedAt.isBefore(historyCutoff)) {
                iterator.remove();
            }
        }

        Instant failureCutoff = Instant.now().minus(FAILURE_COOLDOWN);
        failedStrategies.entrySet().removeIf(entry -> entry.getValue().stoppedAt.isBefore(failureCutoff));
    }

    private static class PlanHistoryEntry {
        private final String strategyName;
        private final Instant selectedAt;

        private PlanHistoryEntry(String strategyName, Instant selectedAt) {
            this.strategyName = strategyName;
            this.selectedAt = selectedAt;
        }
    }

    private static class StopRecord {
        private final Instant stoppedAt;
        private final TaskStopReason reason;

        private StopRecord(Instant stoppedAt, TaskStopReason reason) {
            this.stoppedAt = stoppedAt;
            this.reason = reason != null ? reason : TaskStopReason.UNKNOWN;
        }
    }
}
