/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.util.player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;
import org.junit.Test;
import static org.junit.Assert.*;

public class TerminalLogoutPromptTest {
    @Test public void waitsThroughBankClosureAndBlockedLogoutBeforeOneNotification() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        List<String> messages = new ArrayList<>();
        prompt.begin("Ran out of supplies: box traps");
        for (Rs2Player.TerminalLogoutState state : new Rs2Player.TerminalLogoutState[]{
                Rs2Player.TerminalLogoutState.BANK_CLOSING, Rs2Player.TerminalLogoutState.INPUT_BLOCKED,
                Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED}) {
            assertFalse(prompt.tick(() -> state, messages::add));
            assertTrue(prompt.active());
            assertTrue(messages.isEmpty());
        }
        assertTrue(prompt.tick(() -> Rs2Player.TerminalLogoutState.LOGGED_OUT, messages::add));
        assertFalse(prompt.active());
        assertEquals(List.of("Ran out of supplies: box traps"), messages);
        assertFalse(prompt.tick(() -> { throw new AssertionError("completed stop retried"); }, messages::add));
    }

    @Test public void explicitRestartCancelsOldLogoutAndMessage() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        prompt.begin("Old stop");
        prompt.reset();
        assertFalse(prompt.tick(() -> { throw new AssertionError("old stop continued"); },
                text -> { throw new AssertionError("old popup appeared"); }));
    }

    private static final Runnable NO_HOLD = () -> { throw new AssertionError("held a stop that requested suppression"); };

    private static final class FakeSuppression {
        private final AtomicLong clock;
        private long deadline = Long.MIN_VALUE;
        private int requests;
        private int renewals;
        private int holds;

        FakeSuppression(AtomicLong clock) {
            this.clock = clock;
        }

        void request() {
            requests++;
            if (!pending()) deadline = clock.get() + AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS;
        }

        void renew() {
            renewals++;
            if (pending()) deadline = clock.get() + AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS;
        }

        void hold() {
            holds++;
        }

        boolean pending() {
            return clock.get() < deadline;
        }
    }

    private static boolean tick(TerminalLogoutPrompt prompt, FakeSuppression suppression, boolean stepRequests,
                                Rs2Player.TerminalLogoutState state, List<String> messages) {
        return prompt.throttledTick(request -> {
            if (stepRequests) request.run();
            return state;
        }, messages::add, suppression::request, suppression::renew, suppression::hold);
    }

    @Test public void suppressionIsRequestedOnceAcrossRetriesAndAgainForANewStop() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        int[] requests = {0};
        int[] renewals = {0};
        List<String> messages = new ArrayList<>();
        prompt.begin("First stop");
        for (int i = 0; i < 3; i++) {
            assertFalse(prompt.tick(request -> {
                request.run();
                return Rs2Player.TerminalLogoutState.BANK_CLOSING;
            }, messages::add, () -> requests[0]++, () -> renewals[0]++, NO_HOLD));
        }
        assertEquals(1, requests[0]);
        assertEquals(1, renewals[0]);
        assertTrue(prompt.tick(request -> {
            request.run();
            return Rs2Player.TerminalLogoutState.LOGGED_OUT;
        }, messages::add, () -> requests[0]++, () -> renewals[0]++, NO_HOLD));
        assertEquals(1, requests[0]);
        prompt.begin("Second stop");
        assertTrue(prompt.tick(request -> {
            request.run();
            return Rs2Player.TerminalLogoutState.LOGGED_OUT;
        }, messages::add, () -> requests[0]++, () -> renewals[0]++, NO_HOLD));
        assertEquals(2, requests[0]);
        assertEquals(2, renewals[0]);
        assertEquals(List.of("First stop", "Second stop"), messages);
    }

    @Test public void delayedLogoutKeepsRenewingSuppressionUntilTheLoginScreen() {
        AtomicLong clock = new AtomicLong();
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt(clock::get);
        FakeSuppression suppression = new FakeSuppression(clock);
        List<String> messages = new ArrayList<>();
        prompt.begin("Ran out of supplies");
        long logoutAt = AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS * 3;
        for (; clock.get() < logoutAt; clock.addAndGet(TerminalLogoutPrompt.RETRY_MILLIS)) {
            assertFalse(tick(prompt, suppression, true, Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED, messages));
            assertTrue(suppression.pending());
        }
        assertEquals(1, suppression.requests);
        int renewals = suppression.renewals;
        assertTrue(tick(prompt, suppression, false, Rs2Player.TerminalLogoutState.LOGGED_OUT, messages));
        assertTrue(suppression.pending());
        assertEquals(renewals, suppression.renewals);
        assertEquals(0, suppression.holds);
        assertEquals(List.of("Ran out of supplies"), messages);
    }

    @Test public void suppressionIsRenewedWhileATransientStateBlocksTheLogoutStep() {
        AtomicLong clock = new AtomicLong();
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt(clock::get);
        FakeSuppression suppression = new FakeSuppression(clock);
        List<String> messages = new ArrayList<>();
        prompt.begin("Ran out of supplies");
        assertFalse(tick(prompt, suppression, true, Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED, messages));
        long reconnectedAt = AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS * 3;
        for (clock.addAndGet(TerminalLogoutPrompt.RETRY_MILLIS); clock.get() < reconnectedAt;
             clock.addAndGet(TerminalLogoutPrompt.RETRY_MILLIS)) {
            assertFalse(tick(prompt, suppression, false, Rs2Player.TerminalLogoutState.INPUT_BLOCKED, messages));
            assertTrue("suppression lapsed at " + clock.get() + " ms", suppression.pending());
        }
        assertEquals(1, suppression.requests);
        assertTrue(tick(prompt, suppression, false, Rs2Player.TerminalLogoutState.LOGGED_OUT, messages));
        assertTrue(suppression.pending());
        assertEquals(0, suppression.holds);
        assertEquals(List.of("Ran out of supplies"), messages);
    }

    @Test public void firstSuppressionExtendsARequestAnotherSourceAlreadyMade() {
        AtomicLong clock = new AtomicLong();
        FakeSuppression suppression = new FakeSuppression(clock);
        suppression.request();
        clock.set(AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS - 4_000L);
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt(clock::get);
        List<String> messages = new ArrayList<>();
        prompt.begin("Ran out of supplies");
        long logoutAt = AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS * 3;
        for (; clock.get() < logoutAt; clock.addAndGet(TerminalLogoutPrompt.RETRY_MILLIS)) {
            assertFalse(tick(prompt, suppression, true, Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED, messages));
            assertTrue("suppression lapsed at " + clock.get() + " ms", suppression.pending());
        }
        assertTrue(tick(prompt, suppression, false, Rs2Player.TerminalLogoutState.LOGGED_OUT, messages));
        assertEquals(List.of("Ran out of supplies"), messages);
    }

    @Test public void stopFirstSeenAtTheLoginScreenHoldsAutoLoginBeforeNotifying() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        List<String> events = new ArrayList<>();
        prompt.begin("Ran out of supplies");
        assertTrue(prompt.tick(request -> Rs2Player.TerminalLogoutState.LOGGED_OUT, text -> events.add("notify"),
                () -> events.add("request"), () -> events.add("renew"), () -> events.add("hold")));
        assertEquals(List.of("hold", "notify"), events);
        assertFalse(prompt.tick(request -> Rs2Player.TerminalLogoutState.LOGGED_OUT, text -> events.add("notify"),
                () -> events.add("request"), () -> events.add("renew"), () -> events.add("hold")));
        assertEquals(List.of("hold", "notify"), events);
    }

    @Test public void stopWithAPendingRequestDoesNotHoldAtTheLoginScreen() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        List<String> events = new ArrayList<>();
        prompt.begin("Ran out of supplies");
        assertFalse(prompt.tick(request -> {
            request.run();
            return Rs2Player.TerminalLogoutState.INPUT_BLOCKED;
        }, text -> events.add("notify"), () -> events.add("request"), () -> events.add("renew"), NO_HOLD));
        assertTrue(prompt.tick(request -> Rs2Player.TerminalLogoutState.LOGGED_OUT, text -> events.add("notify"),
                () -> events.add("request"), () -> events.add("renew"), NO_HOLD));
        assertEquals(List.of("request", "renew", "notify"), events);
    }

    @Test public void supplierTickNeverSuppresses() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        List<String> messages = new ArrayList<>();
        prompt.begin("Stop");
        assertTrue(prompt.tick(() -> Rs2Player.TerminalLogoutState.LOGGED_OUT, messages::add));
        assertEquals(List.of("Stop"), messages);
    }

    @Test public void throttleUsesTheInjectedClockEvenWhenItStartsNegative() {
        AtomicLong clock = new AtomicLong(-1_000_000L);
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt(clock::get);
        int[] attempts = {0};
        prompt.begin("Stop");
        Runnable tick = () -> prompt.throttledTick(request -> {
            attempts[0]++;
            return Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED;
        }, text -> {}, () -> {}, () -> {}, () -> {});
        tick.run();
        assertEquals(1, attempts[0]);
        clock.addAndGet(TerminalLogoutPrompt.RETRY_MILLIS - 1);
        tick.run();
        assertEquals(1, attempts[0]);
        clock.incrementAndGet();
        tick.run();
        assertEquals(2, attempts[0]);
        prompt.reset();
        prompt.begin("Next stop");
        tick.run();
        assertEquals(3, attempts[0]);
    }

    @Test public void restartFromAnotherThreadDuringLogoutIsNotCompletedOrSuppressedByTheOldTick() {
        TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
        List<String> suppressions = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        prompt.begin("Old stop");
        assertFalse(prompt.tick(request -> {
            Thread restart = new Thread(() -> {
                prompt.reset();
                prompt.begin("New stop");
            });
            restart.start();
            try {
                restart.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertFalse(restart.isAlive());
            request.run();
            return Rs2Player.TerminalLogoutState.LOGGED_OUT;
        }, messages::add, () -> suppressions.add("request"), () -> suppressions.add("renew"),
                () -> suppressions.add("hold")));
        assertTrue(suppressions.isEmpty());
        assertTrue(messages.isEmpty());
        assertEquals("New stop", prompt.message());
        assertTrue(prompt.tick(request -> {
            request.run();
            return Rs2Player.TerminalLogoutState.LOGGED_OUT;
        }, messages::add, () -> suppressions.add("request"), () -> suppressions.add("renew"),
                () -> suppressions.add("hold")));
        assertEquals(List.of("request", "renew"), suppressions);
        assertEquals(List.of("New stop"), messages);
    }
}
