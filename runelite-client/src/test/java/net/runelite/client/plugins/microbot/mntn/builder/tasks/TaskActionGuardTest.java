package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class TaskActionGuardTest {

    @Test
    public void waitsBetweenAttemptsAndExhaustsTheRetryBudget() {
        TaskActionGuard guard = new TaskActionGuard(2, 10_000, 500);

        assertEquals(TaskActionGuard.Result.READY, guard.evaluate("walk", false, 1_000));
        guard.recordAttempt(1_000);
        assertEquals(TaskActionGuard.Result.WAITING, guard.evaluate("walk", false, 1_300));
        assertEquals(TaskActionGuard.Result.READY, guard.evaluate("walk", false, 1_500));
        guard.recordAttempt(1_500);
        assertEquals(TaskActionGuard.Result.EXHAUSTED, guard.evaluate("walk", false, 2_000));
    }

    @Test
    public void confirmationResetsTheCurrentAction() {
        TaskActionGuard guard = new TaskActionGuard(1, 1_000, 100);

        assertEquals(TaskActionGuard.Result.READY, guard.evaluate("open bank", false, 1_000));
        guard.recordAttempt(1_000);
        assertEquals(TaskActionGuard.Result.CONFIRMED, guard.evaluate("open bank", true, 1_050));
        assertEquals(TaskActionGuard.Result.READY, guard.evaluate("open bank", false, 1_100));
    }

    @Test
    public void scriptGuardPauseDoesNotConsumeAnActiveDeadline() {
        TaskActionGuard guard = new TaskActionGuard(2, 1_000, 100);

        assertEquals(TaskActionGuard.Result.READY, guard.evaluate("attack", false, 1_000));
        guard.recordAttempt(1_000);
        TaskActionGuard.suspendTimeouts(Duration.ofMillis(2_000));

        assertEquals(TaskActionGuard.Result.READY, guard.evaluate("attack", false, 3_500));
    }
}
