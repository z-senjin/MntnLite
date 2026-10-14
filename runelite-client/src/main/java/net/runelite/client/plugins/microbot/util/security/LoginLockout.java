package net.runelite.client.plugins.microbot.util.security;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public final class LoginLockout {
    public static final int LOGIN_INDEX_TOO_MANY_ATTEMPTS = 33;

    static final long[] COOLDOWN_STEPS_MILLIS = {
            TimeUnit.MINUTES.toMillis(5),
            TimeUnit.MINUTES.toMillis(10),
            TimeUnit.MINUTES.toMillis(20),
            TimeUnit.MINUTES.toMillis(30)
    };
    static final double MIN_JITTER = 0.85;
    static final double JITTER_RANGE = 0.3;
    static final long DISMISS_INTERVAL_MILLIS = 10_000L;

    private static final LoginLockout INSTANCE = new LoginLockout();

    private final LongSupplier clock;
    private final DoubleSupplier random;
    private int consecutiveLockouts;
    private boolean active;
    private long cooldownUntil;
    private boolean dismissedOnce;
    private boolean awaitingScreenClear;
    private long lastDismissAt;

    LoginLockout() {
        this(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    LoginLockout(LongSupplier clock, DoubleSupplier random) {
        this.clock = clock;
        this.random = random;
    }

    public static LoginLockout getInstance() {
        return INSTANCE;
    }

    public static boolean isLockoutLoginIndex(int loginIndex) {
        return loginIndex == LOGIN_INDEX_TOO_MANY_ATTEMPTS;
    }

    static long baseCooldownMillis(int lockoutNumber) {
        int idx = Math.min(Math.max(lockoutNumber - 1, 0), COOLDOWN_STEPS_MILLIS.length - 1);
        return COOLDOWN_STEPS_MILLIS[idx];
    }

    public synchronized boolean recordLockout() {
        if (isActive() || awaitingScreenClear) {
            return false;
        }
        consecutiveLockouts++;
        double jitter = MIN_JITTER + random.getAsDouble() * JITTER_RANGE;
        long duration = Math.round(baseCooldownMillis(consecutiveLockouts) * jitter);
        cooldownUntil = clock.getAsLong() + duration;
        active = true;
        dismissedOnce = false;
        awaitingScreenClear = true;
        return true;
    }

    public synchronized void recordScreenCleared() {
        awaitingScreenClear = false;
    }

    public synchronized boolean isActive() {
        if (active && clock.getAsLong() >= cooldownUntil) {
            active = false;
        }
        return active;
    }

    public synchronized long remainingMillis() {
        return isActive() ? cooldownUntil - clock.getAsLong() : 0L;
    }

    public synchronized int getConsecutiveLockouts() {
        return consecutiveLockouts;
    }

    public synchronized boolean tryClaimDismiss() {
        long now = clock.getAsLong();
        if (dismissedOnce && now - lastDismissAt < DISMISS_INTERVAL_MILLIS) {
            return false;
        }
        dismissedOnce = true;
        lastDismissAt = now;
        return true;
    }

    public synchronized void recordSuccessfulLogin() {
        consecutiveLockouts = 0;
        active = false;
        dismissedOnce = false;
        awaitingScreenClear = false;
    }
}
