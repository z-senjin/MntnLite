/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.util.player;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;

/** Keeps terminal logout separate from notification; the host pauses gameplay until completed. */
public final class TerminalLogoutPrompt {
    static final long RETRY_MILLIS = 600L;
    static final long RENEW_SUPPRESSION_MILLIS = AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS / 3;

    private final LongSupplier clock;
    private String message;
    private long stop;
    private boolean attempted;
    private long lastAttemptAt;
    private boolean suppressionRequested;
    private long suppressionRequestedAt;

    public TerminalLogoutPrompt() {
        this(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    TerminalLogoutPrompt(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized void begin(String text) {
        if (message == null) {
            message = text;
            startNewStop();
        }
    }

    public synchronized boolean active() { return message != null; }
    public synchronized String message() { return message; }

    public synchronized void reset() {
        message = null;
        startNewStop();
    }

    public boolean tick() {
        return throttledTick(Rs2Player::logoutWithoutAutoLoginStep, Microbot::showMessage,
                () -> Microbot.getEventBus().post(new AutoLoginSuppressionRequest()),
                () -> Microbot.getEventBus().post(AutoLoginSuppressionRequest.renewal()),
                () -> Microbot.getEventBus().post(AutoLoginSuppressionRequest.loggedOut()));
    }

    public boolean tick(Supplier<Rs2Player.TerminalLogoutState> logout, Consumer<String> notify) {
        return tick(ignored -> logout.get(), notify, () -> {}, () -> {}, () -> {});
    }

    boolean throttledTick(Function<Runnable, Rs2Player.TerminalLogoutState> logout, Consumer<String> notify,
                          Runnable requestSuppression, Runnable renewSuppression, Runnable holdLoggedOut) {
        synchronized (this) {
            if (message == null) return false;
            long now = clock.getAsLong();
            if (attempted && now - lastAttemptAt < RETRY_MILLIS) return false;
            attempted = true;
            lastAttemptAt = now;
        }
        return tick(logout, notify, requestSuppression, renewSuppression, holdLoggedOut);
    }

    boolean tick(Function<Runnable, Rs2Player.TerminalLogoutState> logout, Consumer<String> notify,
                 Runnable requestSuppression, Runnable renewSuppression, Runnable holdLoggedOut) {
        long tickStop;
        synchronized (this) {
            if (message == null) return false;
            tickStop = stop;
        }
        Rs2Player.TerminalLogoutState state = logout.apply(
                () -> requestSuppressionOnce(tickStop, requestSuppression, renewSuppression));
        if (state != Rs2Player.TerminalLogoutState.LOGGED_OUT) {
            renewSuppressionIfDue(tickStop, renewSuppression);
            return false;
        }
        boolean neverSuppressed;
        synchronized (this) {
            if (stop != tickStop || message == null) return false;
            neverSuppressed = !suppressionRequested;
        }
        if (neverSuppressed) holdLoggedOut.run();
        String completedMessage;
        synchronized (this) {
            if (stop != tickStop || message == null) return false;
            completedMessage = message;
            message = null;
            startNewStop();
        }
        notify.accept(completedMessage);
        return true;
    }

    private void requestSuppressionOnce(long tickStop, Runnable requestSuppression, Runnable renewSuppression) {
        synchronized (this) {
            if (stop != tickStop || suppressionRequested) return;
        }
        requestSuppression.run();
        renewSuppression.run();
        markSuppressed(tickStop);
    }

    private void renewSuppressionIfDue(long tickStop, Runnable renewSuppression) {
        synchronized (this) {
            if (stop != tickStop || !suppressionRequested
                    || clock.getAsLong() - suppressionRequestedAt < RENEW_SUPPRESSION_MILLIS) return;
        }
        renewSuppression.run();
        markSuppressed(tickStop);
    }

    private synchronized void markSuppressed(long tickStop) {
        if (stop != tickStop) return;
        suppressionRequested = true;
        suppressionRequestedAt = clock.getAsLong();
    }

    private void startNewStop() {
        stop++;
        attempted = false;
        suppressionRequested = false;
    }
}
