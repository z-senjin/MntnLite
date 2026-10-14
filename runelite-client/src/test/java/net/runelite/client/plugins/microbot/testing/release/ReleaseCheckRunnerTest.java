package net.runelite.client.plugins.microbot.testing.release;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReleaseCheckRunnerTest {

    private static final ReleaseCheck.Body PASS = context -> {
    };

    private static List<ReleaseCheck> catalog() {
        return List.of(
                ReleaseCheck.quick("boot", "boot", 1000, PASS),
                ReleaseCheck.full("world-hop", "hop", 1000, PASS),
                ReleaseCheck.quick("bank", "bank", 1000, PASS),
                ReleaseCheck.quick("logout", "logout", 1000, PASS).alwaysRun());
    }

    private static List<String> ids(List<ReleaseCheck> checks) {
        return checks.stream().map(ReleaseCheck::getId).collect(Collectors.toList());
    }

    private static ReleaseCheckRunner runner(boolean failFast, long totalMs) {
        return new ReleaseCheckRunner(new ReleaseCheckRunner.Options()
                .failFast(failFast)
                .totalTimeoutMs(totalMs)
                .cleanupTimeoutMs(2000)
                .terminationWaitMs(2000));
    }

    @Test
    public void quickTierSkipsFullChecksAndKeepsOrder() {
        assertEquals(List.of("boot", "bank", "logout"), ids(ReleaseCheckRunner.select(catalog(), ReleaseTier.QUICK, List.of())));
    }

    @Test
    public void fullTierIncludesEveryCheckInDeclaredOrder() {
        assertEquals(List.of("boot", "world-hop", "bank", "logout"), ids(ReleaseCheckRunner.select(catalog(), ReleaseTier.FULL, null)));
    }

    @Test
    public void explicitIdsIgnoreTierAndKeepDeclaredOrder() {
        assertEquals(List.of("world-hop", "bank"), ids(ReleaseCheckRunner.select(catalog(), ReleaseTier.QUICK, List.of("BANK", " world-hop "))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownIdIsRejected() {
        ReleaseCheckRunner.select(catalog(), ReleaseTier.QUICK, List.of("bank", "nope"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdsAreRejected() {
        ReleaseCheckRunner.select(List.of(ReleaseCheck.quick("a", "a", 10, PASS), ReleaseCheck.quick("A", "b", 10, PASS)),
                ReleaseTier.QUICK, List.of());
    }

    @Test
    public void tierParsingDefaultsToQuick() {
        assertEquals(ReleaseTier.QUICK, ReleaseTier.parse(null));
        assertEquals(ReleaseTier.FULL, ReleaseTier.parse(" Full "));
        try {
            ReleaseTier.parse("medium");
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("medium"));
        }
    }

    @Test
    public void classifiesPassFailSkipAndUnexpectedExceptions() {
        List<ReleaseCheck> checks = List.of(
                ReleaseCheck.quick("pass", "p", 1000, context -> context.detail("seen", 3)),
                ReleaseCheck.quick("fail", "f", 1000, context -> context.require(false, "expected failure")),
                ReleaseCheck.quick("skip", "s", 1000, context -> context.skip("no coins")),
                ReleaseCheck.quick("boom", "b", 1000, context -> {
                    throw new IllegalStateException("kaput");
                }));

        List<CheckOutcome> outcomes = runner(false, 10_000).run(checks, null);

        assertEquals(CheckStatus.PASSED, outcomes.get(0).status);
        assertEquals("3", outcomes.get(0).details.get("seen"));
        assertNull(outcomes.get(0).detail);
        assertEquals(CheckStatus.FAILED, outcomes.get(1).status);
        assertEquals("expected failure", outcomes.get(1).detail);
        assertEquals(CheckStatus.SKIPPED, outcomes.get(2).status);
        assertEquals("no coins", outcomes.get(2).detail);
        assertEquals(CheckStatus.FAILED, outcomes.get(3).status);
        assertEquals("IllegalStateException: kaput", outcomes.get(3).detail);
        assertEquals(List.of(1, 2, 3, 4), outcomes.stream().map(o -> o.order).collect(Collectors.toList()));
    }

    @Test
    public void continuesAfterFailureByDefault() {
        List<ReleaseCheck> checks = List.of(
                ReleaseCheck.quick("first", "f", 1000, context -> context.require(false, "nope")),
                ReleaseCheck.quick("second", "s", 1000, PASS));

        List<CheckOutcome> outcomes = runner(false, 10_000).run(checks, null);

        assertEquals(CheckStatus.FAILED, outcomes.get(0).status);
        assertEquals(CheckStatus.PASSED, outcomes.get(1).status);
    }

    @Test
    public void failFastMarksRemainingChecksNotRunButStillRunsAlwaysRunChecks() {
        AtomicBoolean secondRan = new AtomicBoolean();
        AtomicBoolean logoutRan = new AtomicBoolean();
        List<ReleaseCheck> checks = List.of(
                ReleaseCheck.quick("first", "f", 1000, context -> context.require(false, "nope")),
                ReleaseCheck.quick("second", "s", 1000, context -> secondRan.set(true)),
                ReleaseCheck.quick("logout", "l", 1000, context -> logoutRan.set(true)).alwaysRun());

        List<CheckOutcome> outcomes = runner(true, 10_000).run(checks, null);

        assertEquals(CheckStatus.NOT_RUN, outcomes.get(1).status);
        assertTrue(outcomes.get(1).detail.contains("fail-fast after first"));
        assertFalse(secondRan.get());
        assertEquals(CheckStatus.PASSED, outcomes.get(2).status);
        assertTrue(logoutRan.get());
    }

    @Test
    public void perCheckTimeoutInterruptsTheBodyAndRunsCleanup() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean cleaned = new AtomicBoolean();
        List<ReleaseCheck> checks = List.of(
                ReleaseCheck.quick("slow", "s", 200, context -> {
                    context.onCleanup("restore", () -> cleaned.set(true));
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                    }
                }),
                ReleaseCheck.quick("next", "n", 1000, PASS));

        List<CheckOutcome> outcomes = runner(false, 10_000).run(checks, null);

        assertEquals(CheckStatus.FAILED, outcomes.get(0).status);
        assertEquals("Timed out after 200ms", outcomes.get(0).detail);
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertTrue(cleaned.get());
        assertEquals(CheckStatus.PASSED, outcomes.get(1).status);
    }

    @Test
    public void totalTimeoutCapsTheCheckBudgetAndSkipsTheRest() {
        AtomicBoolean logoutRan = new AtomicBoolean();
        List<ReleaseCheck> checks = List.of(
                ReleaseCheck.quick("hog", "h", 5_000, context -> {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }),
                ReleaseCheck.quick("later", "l", 1000, PASS),
                ReleaseCheck.quick("logout", "o", 1000, context -> logoutRan.set(true)).alwaysRun());

        List<CheckOutcome> outcomes = runner(false, 300).run(checks, null);

        assertEquals(CheckStatus.FAILED, outcomes.get(0).status);
        assertTrue(outcomes.get(0).detail, outcomes.get(0).detail.contains("remaining total budget"));
        assertEquals(CheckStatus.NOT_RUN, outcomes.get(1).status);
        assertTrue(outcomes.get(1).detail.contains("total timeout of 300ms exceeded"));
        assertEquals(CheckStatus.PASSED, outcomes.get(2).status);
        assertTrue(logoutRan.get());
    }

    @Test
    public void cleanupsRunInReverseRegistrationOrder() {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        List<ReleaseCheck> checks = List.of(ReleaseCheck.quick("c", "c", 1000, context -> {
            context.onCleanup("first", () -> order.add("first"));
            context.onCleanup("second", () -> order.add("second"));
            context.skip("account state");
        }));

        CheckOutcome outcome = runner(false, 10_000).run(checks, null).get(0);

        assertEquals(CheckStatus.SKIPPED, outcome.status);
        assertEquals(List.of("second", "first"), order);
    }

    @Test
    public void failingCleanupTurnsAPassIntoAFailure() {
        List<ReleaseCheck> checks = List.of(ReleaseCheck.quick("c", "c", 1000, context -> {
            context.onCleanup("close bank", () -> {
                throw new IllegalStateException("bank still open");
            });
            context.onCleanup("other", () -> {
            });
        }));

        CheckOutcome outcome = runner(false, 10_000).run(checks, null).get(0);

        assertEquals(CheckStatus.FAILED, outcome.status);
        assertEquals(List.of("close bank: IllegalStateException: bank still open"), outcome.cleanupErrors);
        assertTrue(outcome.detail.startsWith("Cleanup failed: close bank"));
    }

    @Test
    public void listenerSeesEveryOutcomeInOrder() {
        List<String> seen = new ArrayList<>();
        runner(true, 10_000).run(ReleaseCheckRunner.select(catalog(), ReleaseTier.FULL, List.of()), outcome -> seen.add(outcome.id + ":" + outcome.status));
        assertEquals(List.of("boot:PASSED", "world-hop:PASSED", "bank:PASSED", "logout:PASSED"), seen);
    }
}
