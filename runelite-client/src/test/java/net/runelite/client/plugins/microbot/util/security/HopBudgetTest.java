package net.runelite.client.plugins.microbot.util.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HopBudgetTest {
    private static final long ONE_TOKEN_MILLIS = (long) Math.ceil(TimeUnit.HOURS.toMillis(1) / 93.0);

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private HopBudget budget(int capacity, int reserve) {
        HopBudget budget = new HopBudget(clock::get, new HopBudget.MemoryStore());
        budget.configure(true, capacity, 93, reserve);
        return budget;
    }

    private static void hop(HopBudget budget, int times) {
        for (int i = 0; i < times; i++) {
            budget.recordHop();
        }
    }

    @Test public void freshBudgetStartsFullAndAllowsHops() {
        HopBudget budget = budget(390, 20);
        assertEquals(390, budget.available());
        assertEquals(0L, budget.millisUntilHopAllowed());
    }

    @Test public void hopsStopAtTheReserveAndResumeAfterOneTokenRefills() {
        HopBudget budget = budget(30, 5);
        hop(budget, 24);
        assertEquals(6, budget.available());
        assertEquals(0L, budget.millisUntilHopAllowed());
        hop(budget, 1);
        assertEquals(5, budget.available());
        long wait = budget.millisUntilHopAllowed();
        assertTrue(Math.abs(wait - ONE_TOKEN_MILLIS) <= 1);
        clock.addAndGet(wait - 2);
        assertTrue(budget.millisUntilHopAllowed() > 0);
        clock.addAndGet(2);
        assertEquals(0L, budget.millisUntilHopAllowed());
    }

    @Test public void refillNeverExceedsCapacity() {
        HopBudget budget = budget(30, 5);
        hop(budget, 10);
        clock.addAndGet(TimeUnit.HOURS.toMillis(5));
        assertEquals(30, budget.available());
    }

    @Test public void lockoutEmptiesTheBudget() {
        HopBudget budget = budget(390, 20);
        budget.recordLockout();
        assertEquals(0, budget.available());
        assertTrue(budget.millisUntilHopAllowed() >= 20 * ONE_TOKEN_MILLIS);
    }

    @Test public void disabledBudgetNeverWaitsButStillCounts() {
        HopBudget budget = budget(10, 2);
        budget.configure(false, 10, 93, 2);
        hop(budget, 10);
        assertEquals(0, budget.available());
        assertEquals(0L, budget.millisUntilHopAllowed());
    }

    @Test public void reserveIsClampedBelowCapacity() {
        HopBudget budget = budget(5, 50);
        assertEquals(0L, budget.millisUntilHopAllowed());
        hop(budget, 1);
        assertTrue(budget.millisUntilHopAllowed() > 0);
    }

    @Test public void clockGoingBackwardsDoesNotDrainOrRefill() {
        HopBudget budget = budget(30, 5);
        hop(budget, 10);
        clock.addAndGet(-TimeUnit.HOURS.toMillis(1));
        assertEquals(20, budget.available());
    }

    @Test public void fileStoreIsSharedBetweenClientsOnTheSameNetwork() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        HopBudget first = budget(30, 5);
        first.persistTo(file, "direct");
        HopBudget second = budget(30, 5);
        second.persistTo(file, "direct");
        HopBudget proxied = budget(30, 5);
        proxied.persistTo(file, "proxy.10.0.0.1");

        hop(first, 4);
        hop(second, 3);
        assertEquals(23, first.available());
        assertEquals(23, second.available());
        assertEquals(30, proxied.available());

        HopBudget restarted = budget(30, 5);
        restarted.persistTo(file, "direct");
        assertEquals(23, restarted.available());
    }

    @Test public void interruptedThreadStillUsesTheSharedFileAndKeepsItsInterrupt() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        HopBudget first = budget(30, 5);
        first.persistTo(file, "direct");
        HopBudget second = budget(30, 5);
        second.persistTo(file, "direct");

        Thread.currentThread().interrupt();
        try {
            first.recordHop();
            assertEquals(29, first.available());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(29, second.available());
    }

    @Test public void corruptFileIsTreatedAsAFullBudget() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        Files.write(file, "direct.tokens=abc\ndirect.updatedAt=\n".getBytes(StandardCharsets.UTF_8));
        HopBudget budget = budget(30, 5);
        budget.persistTo(file, "direct");
        assertEquals(30, budget.available());
        budget.recordHop();
        assertEquals(29, budget.available());
    }

    @Test public void nonFiniteOrNegativeTokensAreTreatedAsAFullBudget() throws Exception {
        for (String tokens : new String[]{"NaN", "Infinity", "-5"}) {
            Path file = folder.newFolder().toPath().resolve(HopBudget.FILE_NAME);
            Files.write(file, ("direct.tokens=" + tokens + "\ndirect.updatedAt=" + clock.get() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            HopBudget budget = budget(30, 5);
            budget.persistTo(file, "direct");
            assertEquals(tokens, 30, budget.available());
            assertEquals(tokens, 0L, budget.millisUntilHopAllowed());
        }
    }

    @Test public void writesLeaveNoTemporaryFilesBehind() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        HopBudget budget = budget(30, 5);
        budget.persistTo(file, "direct");
        hop(budget, 3);
        try (java.util.stream.Stream<Path> files = Files.list(folder.getRoot().toPath())) {
            assertEquals(java.util.Set.of(HopBudget.FILE_NAME, HopBudget.FILE_NAME + ".lock"),
                    files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
        assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("direct.tokens=27"));
    }

    @Test public void storedTimestampInTheFutureIsRepairedSoRefillResumes() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        HopBudget budget = budget(30, 5);
        budget.persistTo(file, "direct");
        hop(budget, 25);
        clock.addAndGet(-TimeUnit.HOURS.toMillis(1));
        assertTrue(budget.millisUntilHopAllowed() > 0);
        clock.addAndGet(ONE_TOKEN_MILLIS);
        assertEquals(0L, budget.millisUntilHopAllowed());
    }

    @Test public void persistToRepairsAFutureTimestampForTheCachedCheck() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        Files.write(file, ("direct.tokens=5.0\ndirect.updatedAt=" + (clock.get() + TimeUnit.HOURS.toMillis(1)) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        HopBudget budget = budget(30, 5);
        budget.persistTo(file, "direct");
        assertTrue(budget.cachedMillisUntilHopAllowed() > 0);
        clock.addAndGet(ONE_TOKEN_MILLIS);
        assertEquals(0L, budget.cachedMillisUntilHopAllowed());
    }

    @Test public void zeroRefillRateIsClampedSoWaitsStayFinite() {
        HopBudget budget = new HopBudget(clock::get, new HopBudget.MemoryStore());
        budget.configure(true, 30, 0, 5);
        hop(budget, 25);
        assertTrue(Math.abs(budget.millisUntilHopAllowed() - TimeUnit.HOURS.toMillis(1)) <= 1);
    }

    @Test public void cachedWaitUsesThisClientsLastKnownState() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        HopBudget first = budget(30, 5);
        first.persistTo(file, "direct");
        HopBudget second = budget(30, 5);
        second.persistTo(file, "direct");
        assertEquals(0L, first.cachedMillisUntilHopAllowed());
        hop(second, 25);
        assertEquals(0L, first.cachedMillisUntilHopAllowed());
        assertTrue(first.millisUntilHopAllowed() > 0);
        assertTrue(first.cachedMillisUntilHopAllowed() > 0);
        first.configure(false, 30, 93, 5);
        assertEquals(0L, first.cachedMillisUntilHopAllowed());
    }

    @Test public void unwritableFileFallsBackToThisClientOnly() throws Exception {
        Path directory = folder.newFolder("blocked").toPath();
        HopBudget budget = budget(30, 5);
        hop(budget, 2);
        budget.persistTo(directory, "direct");
        assertEquals(28, budget.available());
        budget.recordHop();
        assertEquals(27, budget.available());
    }

    @Test public void networkKeyFollowsTheSocksProxy() {
        String previousHost = System.getProperty("socksProxyHost");
        String previousPort = System.getProperty("socksProxyPort");
        try {
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
            assertEquals("direct", HopBudget.networkKey());
            System.setProperty("socksProxyHost", "proxy.example.com");
            System.setProperty("socksProxyPort", "10001");
            assertEquals("proxy.proxy.example.com_10001", HopBudget.networkKey());
        } finally {
            restore("socksProxyHost", previousHost);
            restore("socksProxyPort", previousPort);
        }
    }

    @Test public void networkKeySeparatesProxyPortsAndUsersWithoutStoringTheUsername() {
        assertEquals("direct", HopBudget.networkKey(null, "1080", "user"));
        assertEquals("direct", HopBudget.networkKey(" ", "1080", "user"));
        assertEquals("proxy.fe80__1_1080", HopBudget.networkKey("fe80::1", "1080", null));
        assertNotEquals(HopBudget.networkKey("gate.example.com", "10001", null),
                HopBudget.networkKey("gate.example.com", "10002", null));
        String first = HopBudget.networkKey("gate.example.com", "7000", "customer-session-a");
        String second = HopBudget.networkKey("gate.example.com", "7000", "customer-session-b");
        assertNotEquals(first, second);
        assertFalse(first.contains("customer"));
        assertTrue(first.matches("[A-Za-z0-9._-]+"));
    }

    @Test public void networkKeyUsernamesWithEqualHashCodesGetSeparateKeys() {
        assertEquals("Aa".hashCode(), "BB".hashCode());
        assertNotEquals(HopBudget.networkKey("gate.example.com", "7000", "Aa"),
                HopBudget.networkKey("gate.example.com", "7000", "BB"));
    }

    @Test public void freshClientReadsTheSharedFileBeforeAnsweringFromCache() throws Exception {
        Path file = folder.getRoot().toPath().resolve(HopBudget.FILE_NAME);
        HopBudget first = budget(30, 5);
        first.persistTo(file, "direct");
        hop(first, 25);
        HopBudget fresh = budget(30, 5);
        fresh.persistTo(file, "direct");
        assertTrue(fresh.cachedMillisUntilHopAllowed() > 0);
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}
