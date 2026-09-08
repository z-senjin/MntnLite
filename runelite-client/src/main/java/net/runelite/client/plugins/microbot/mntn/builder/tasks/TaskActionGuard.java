package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Non-blocking action verification for task state machines.
 *
 * A task checks the expected game-state change on every tick, retries only after a short
 * cooldown, and receives an exhausted result once the action has taken too long. The task
 * remains responsible for selecting the appropriate stop reason and recovery phase.
 */
public final class TaskActionGuard {

    private static final AtomicLong SUSPENDED_TIMEOUT_MS = new AtomicLong();

    public enum Result {
        CONFIRMED,
        READY,
        WAITING,
        EXHAUSTED
    }

    private final int maxAttempts;
    private final long timeoutMs;
    private final long retryDelayMs;
    private String actionKey;
    private long startedAtMs;
    private long lastAttemptAtMs;
    private long observedSuspendedTimeoutMs = SUSPENDED_TIMEOUT_MS.get();
    private int attempts;

    public TaskActionGuard(int maxAttempts, long timeoutMs, long retryDelayMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.timeoutMs = Math.max(1, timeoutMs);
        this.retryDelayMs = Math.max(0, retryDelayMs);
    }

    public Result evaluate(String nextActionKey, boolean confirmed) {
        return evaluate(nextActionKey, confirmed, System.currentTimeMillis());
    }

    Result evaluate(String nextActionKey, boolean confirmed, long nowMs) {
        accountForSuspendedTimeouts();

        if (confirmed) {
            reset();
            return Result.CONFIRMED;
        }

        if (actionKey == null || !actionKey.equals(nextActionKey)) {
            actionKey = nextActionKey;
            startedAtMs = nowMs;
            lastAttemptAtMs = 0;
            attempts = 0;
        }

        if (attempts >= maxAttempts || nowMs - startedAtMs >= timeoutMs) {
            return Result.EXHAUSTED;
        }
        if (lastAttemptAtMs == 0 || nowMs - lastAttemptAtMs >= retryDelayMs) {
            return Result.READY;
        }
        return Result.WAITING;
    }

    public void recordAttempt() {
        recordAttempt(System.currentTimeMillis());
    }

    void recordAttempt(long nowMs) {
        attempts++;
        lastAttemptAtMs = nowMs;
    }

    public int attempts() {
        return attempts;
    }

    /**
     * Freezes all active Builder action deadlines for a known external script pause.
     * New guards start at the current offset, so only work already in progress is adjusted.
     */
    public static void suspendTimeouts(Duration pauseDuration) {
        if (pauseDuration == null || pauseDuration.isNegative() || pauseDuration.isZero()) {
            return;
        }
        SUSPENDED_TIMEOUT_MS.addAndGet(pauseDuration.toMillis());
    }

    private void accountForSuspendedTimeouts() {
        long currentSuspendedMs = SUSPENDED_TIMEOUT_MS.get();
        long elapsedSuspendedMs = currentSuspendedMs - observedSuspendedTimeoutMs;
        if (elapsedSuspendedMs <= 0) {
            return;
        }
        if (startedAtMs != 0) {
            startedAtMs += elapsedSuspendedMs;
        }
        if (lastAttemptAtMs != 0) {
            lastAttemptAtMs += elapsedSuspendedMs;
        }
        observedSuspendedTimeoutMs = currentSuspendedMs;
    }

    public void reset() {
        actionKey = null;
        startedAtMs = 0;
        lastAttemptAtMs = 0;
        attempts = 0;
    }
}
