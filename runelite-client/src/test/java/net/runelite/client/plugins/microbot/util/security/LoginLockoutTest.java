package net.runelite.client.plugins.microbot.util.security;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoginLockoutTest {
    private final AtomicLong clock = new AtomicLong(1_000L);

    private LoginLockout lockout(double random) {
        return new LoginLockout(clock::get, () -> random);
    }

    @Test public void recognisesOnlyTheTooManyAttemptsLoginIndex() {
        assertTrue(LoginLockout.isLockoutLoginIndex(33));
        for (int index : new int[] {0, 2, 3, 4, 9, 10, 14, 24, 32, 34}) {
            assertFalse(LoginLockout.isLockoutLoginIndex(index));
        }
    }

    @Test public void lockoutStartsCooldownThatExpiresWithTheClock() {
        LoginLockout lockout = lockout(0.5);
        assertFalse(lockout.isActive());
        assertTrue(lockout.recordLockout());
        long first = TimeUnit.MINUTES.toMillis(5);
        assertTrue(lockout.isActive());
        assertEquals(first, lockout.remainingMillis());
        clock.addAndGet(first - 1);
        assertTrue(lockout.isActive());
        clock.incrementAndGet();
        assertFalse(lockout.isActive());
        assertEquals(0L, lockout.remainingMillis());
    }

    @Test public void repeatedDetectionWhileActiveDoesNotExtendOrEscalate() {
        LoginLockout lockout = lockout(0.5);
        assertTrue(lockout.recordLockout());
        long remaining = lockout.remainingMillis();
        clock.addAndGet(1_000L);
        assertFalse(lockout.recordLockout());
        assertEquals(remaining - 1_000L, lockout.remainingMillis());
        assertEquals(1, lockout.getConsecutiveLockouts());
    }

    @Test public void consecutiveLockoutsEscalateAndCap() {
        LoginLockout lockout = lockout(0.5);
        long[] expectedMinutes = {5, 10, 20, 30, 30};
        for (long minutes : expectedMinutes) {
            assertTrue(lockout.recordLockout());
            long duration = TimeUnit.MINUTES.toMillis(minutes);
            assertEquals(duration, lockout.remainingMillis());
            lockout.recordScreenCleared();
            clock.addAndGet(duration);
        }
    }

    @Test public void lockoutScreenThatOutlivesItsCooldownIsNotCountedAgain() {
        LoginLockout lockout = lockout(0.5);
        assertTrue(lockout.recordLockout());
        clock.addAndGet(TimeUnit.MINUTES.toMillis(5));
        assertFalse(lockout.isActive());
        assertFalse(lockout.recordLockout());
        assertFalse(lockout.isActive());
        assertEquals(1, lockout.getConsecutiveLockouts());
        lockout.recordScreenCleared();
        assertTrue(lockout.recordLockout());
        assertEquals(2, lockout.getConsecutiveLockouts());
        assertEquals(TimeUnit.MINUTES.toMillis(10), lockout.remainingMillis());
    }

    @Test public void jitterStaysWithinBounds() {
        long base = TimeUnit.MINUTES.toMillis(5);
        LoginLockout low = lockout(0.0);
        low.recordLockout();
        assertEquals(Math.round(base * 0.85), low.remainingMillis());
        LoginLockout high = lockout(0.999999);
        high.recordLockout();
        assertTrue(high.remainingMillis() <= Math.round(base * 1.15));
        assertTrue(high.remainingMillis() > base);
    }

    @Test public void successfulLoginClearsCooldownAndEscalation() {
        LoginLockout lockout = lockout(0.5);
        lockout.recordLockout();
        lockout.recordScreenCleared();
        clock.addAndGet(TimeUnit.MINUTES.toMillis(5));
        lockout.recordLockout();
        lockout.recordSuccessfulLogin();
        assertFalse(lockout.isActive());
        assertEquals(0, lockout.getConsecutiveLockouts());
        assertTrue(lockout.recordLockout());
        assertEquals(TimeUnit.MINUTES.toMillis(5), lockout.remainingMillis());
    }

    @Test public void dismissIsRateLimited() {
        LoginLockout lockout = lockout(0.5);
        lockout.recordLockout();
        assertTrue(lockout.tryClaimDismiss());
        assertFalse(lockout.tryClaimDismiss());
        clock.addAndGet(LoginLockout.DISMISS_INTERVAL_MILLIS - 1);
        assertFalse(lockout.tryClaimDismiss());
        clock.incrementAndGet();
        assertTrue(lockout.tryClaimDismiss());
    }
}
