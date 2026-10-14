package net.runelite.client.plugins.microbot.accountselector;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.security.LoginLockout;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class AutoLoginRetryStateTest {
    private static final int WORLD = 383;

    private final AtomicReference<GameState> gameState = new AtomicReference<>(GameState.LOGIN_SCREEN);
    private final AtomicInteger loginIndex = new AtomicInteger(2);
    private final AtomicInteger loginCalls = new AtomicInteger();
    private final MutableClock clock = new MutableClock();
    private Client client;
    private AutoLoginConfig config;
    private AutoLoginScript script;
    private MockedStatic<Microbot> microbot;
    private MockedStatic<LoginManager> loginManager;

    @Before public void setUp() {
        LoginLockout.getInstance().recordSuccessfulLogin();
        client = mock(Client.class);
        when(client.getGameState()).thenAnswer(call -> gameState.get());
        when(client.getLoginIndex()).thenAnswer(call -> loginIndex.get());

        config = mock(AutoLoginConfig.class);
        when(config.usePreferredWorld()).thenReturn(true);
        when(config.world()).thenReturn(WORLD);
        when(config.loginRetryDelay()).thenReturn(5);
        when(config.maxLoginRetries()).thenReturn(5);
        when(config.enableLoginWatchdog()).thenReturn(false);

        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::isLoggedIn).thenAnswer(call -> gameState.get() == GameState.LOGGED_IN);

        loginManager = mockStatic(LoginManager.class, CALLS_REAL_METHODS);
        loginManager.when(() -> LoginManager.login(anyInt())).thenAnswer(call -> {
            loginCalls.incrementAndGet();
            gameState.set(GameState.LOGGING_IN);
            return true;
        });

        script = new AutoLoginScript();
        script.setClock(clock);
    }

    @After public void tearDown() {
        loginManager.close();
        microbot.close();
        LoginLockout.getInstance().recordSuccessfulLogin();
    }

    @Test public void failedLoginWaitsForBackoffBeforeRetrying() {
        tick();
        assertEquals(1, loginCalls());
        assertEquals("ATTEMPTING_LOGIN", script.getLoginStateName());

        for (int i = 0; i < 5; i++) {
            advance(Duration.ofMillis(600));
            tick();
        }
        assertEquals("ATTEMPTING_LOGIN", script.getLoginStateName());
        assertEquals(1, script.getRetryCount());

        gameState.set(GameState.LOGIN_SCREEN);
        tick();
        advance(Duration.ofSeconds(1));
        tick();
        assertEquals(1, loginCalls());
        assertEquals(1, script.getRetryCount());

        advance(Duration.ofSeconds(15));
        tick();
        assertEquals(2, loginCalls());
        assertEquals(2, script.getRetryCount());

        gameState.set(GameState.LOGIN_SCREEN);
        advance(Duration.ofSeconds(9));
        tick();
        assertEquals(2, loginCalls());

        advance(Duration.ofSeconds(20));
        tick();
        assertEquals(3, loginCalls());
    }

    @Test public void maxRetriesStillEntersExtendedSleepWithWatchdog() {
        when(config.enableLoginWatchdog()).thenReturn(true);
        when(config.loginWatchdogTimeout()).thenReturn(60);
        when(config.maxLoginRetries()).thenReturn(2);

        tick();
        gameState.set(GameState.LOGIN_SCREEN);
        advance(Duration.ofSeconds(30));
        tick();
        assertEquals(2, loginCalls());

        gameState.set(GameState.LOGIN_SCREEN);
        advance(Duration.ofMinutes(5));
        tick();
        assertEquals(2, loginCalls());
        assertEquals("LOGIN_EXTENDED_SLEEP", script.getLoginStateName());
    }

    @Test public void loggingInThenLoggedInResetsRetryState() {
        tick();
        assertEquals(1, script.getRetryCount());

        gameState.set(GameState.LOADING);
        advance(Duration.ofMillis(600));
        tick();
        assertEquals("ATTEMPTING_LOGIN", script.getLoginStateName());
        assertEquals(1, script.getRetryCount());

        gameState.set(GameState.LOGGED_IN);
        advance(Duration.ofMillis(600));
        tick();
        assertEquals("WAITING_FOR_LOGIN_SCREEN", script.getLoginStateName());
        assertEquals(0, script.getRetryCount());

        gameState.set(GameState.LOGIN_SCREEN);
        advance(Duration.ofMillis(600));
        tick();
        assertEquals(2, loginCalls());
        assertEquals(1, script.getRetryCount());
    }

    @Test public void lockoutScreenUsesCooldownInsteadOfBackoff() {
        tick();
        gameState.set(GameState.LOGIN_SCREEN);
        loginIndex.set(LoginLockout.LOGIN_INDEX_TOO_MANY_ATTEMPTS);
        tick();
        assertTrue(LoginLockout.getInstance().isActive());

        loginIndex.set(2);
        for (int i = 0; i < 5; i++) {
            advance(Duration.ofMinutes(1));
            tick();
        }

        assertEquals(1, loginCalls());
        assertTrue(LoginLockout.getInstance().isActive());
        assertEquals("ATTEMPTING_LOGIN", script.getLoginStateName());
    }

    private void tick() {
        script.processAutoLoginStateMachine(config);
    }

    private void advance(Duration duration) {
        clock.now = clock.now.plus(duration);
    }

    private int loginCalls() {
        return loginCalls.get();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }
}
