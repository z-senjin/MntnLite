package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionSnapshot.FLAG_EAST;
import static net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionSnapshot.FLAG_NORTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LiveCollisionPersistenceTest {
    private static final int REGION_ID = LiveCollisionView.regionId(3200, 3200);
    private static final LiveCollisionRegion NORTH_BLOCKED = LiveCollisionRegion.fromWords(
            1, new long[]{1L}, new long[0], new long[0], new long[0]);
    private static final LiveCollisionRegion EAST_OPEN = LiveCollisionRegion.fromWords(
            1, new long[0], new long[0], new long[]{2L}, new long[]{2L});

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void laterClientRetainsEdgesLearnedOnlyByEarlierClient() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final LiveCollisionPersistence first = new LiveCollisionPersistence(dir);
        first.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        first.shutdown();

        // This client's in-memory region does not include the first client's north edge.
        final LiveCollisionPersistence second = new LiveCollisionPersistence(dir);
        second.persist(Map.of(REGION_ID, EAST_OPEN));
        second.shutdown();

        assertBothEdges(load(dir));
    }

    @Test
    public void overlappingClientsKeepBothSetsOfEdges() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final LiveCollisionPersistence first = new LiveCollisionPersistence(dir);
        final LiveCollisionPersistence second = new LiveCollisionPersistence(dir);
        first.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        second.persist(Map.of(REGION_ID, EAST_OPEN));
        first.shutdown();
        second.shutdown();

        assertBothEdges(load(dir));
    }

    @Test
    public void abandonedPartialWriteDoesNotDiscardAnotherClientsEdges() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final LiveCollisionPersistence first = new LiveCollisionPersistence(dir);
        first.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        first.shutdown();

        // A client terminated before replacing the target, leaving only its unique temporary file.
        final Path orphan = dir.toPath().resolve(REGION_ID + ".lcr.orphan.tmp");
        Files.write(orphan, new byte[]{1, 2, 3});

        final LiveCollisionPersistence second = new LiveCollisionPersistence(dir);
        second.persist(Map.of(REGION_ID, EAST_OPEN));
        second.shutdown();

        assertBothEdges(load(dir));
    }

    @Test
    public void readableRegionLoadsWhenLockDirectoryIsUnavailable() throws Exception {
        final File source = temporaryFolder.newFolder("source");
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(source);
        writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        writer.shutdown();

        final File dir = temporaryFolder.newFolder("regions");
        Files.copy(source.toPath().resolve(REGION_ID + ".lcr"),
                dir.toPath().resolve(REGION_ID + ".lcr"));
        // A regular file at the lock-directory path makes createDirectories fail.
        Files.write(temporaryFolder.getRoot().toPath().resolve("regions-locks"), new byte[0]);

        final LiveCollisionView view = load(dir);
        assertNotNull(view);
        assertEquals(Boolean.FALSE, view.edge(3200, 3200, 0, FLAG_NORTH));
    }

    @Test
    public void failedLockIsRetriedWithoutAnotherCapture() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path blocker = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.write(blocker, new byte[0]);
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
        final Path target = dir.toPath().resolve(REGION_ID + ".lcr");
        assertFalse("the first write must have failed", Files.exists(target));

        Files.delete(blocker);
        awaitRegion(target);
        writer.shutdown();
        final LiveCollisionView view = load(dir);
        assertNotNull(view);
        assertEquals(Boolean.FALSE, view.edge(3200, 3200, 0, FLAG_NORTH));
    }

    @Test
    public void resetDiscardsFailedWritesBeforeLaterCaptures() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path blocker = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.write(blocker, new byte[0]);
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
        writer.deleteAllAsync();
        writer.persist(Map.of(REGION_ID, EAST_OPEN));
        writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);

        Files.delete(blocker);
        awaitRegion(dir.toPath().resolve(REGION_ID + ".lcr"));
        writer.shutdown();
        final LiveCollisionView view = load(dir);
        assertNotNull(view);
        assertNull(view.edge(3200, 3200, 0, FLAG_NORTH));
        assertEquals(Boolean.TRUE, view.edge(3201, 3200, 0, FLAG_EAST));
    }

    @Test
    public void repeatedShutdownIsSafe() throws Exception {
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(
                temporaryFolder.newFolder("regions"));
        writer.shutdown();
        writer.shutdown();
    }

    @Test
    public void synchronousResetDiscardsPendingWrites() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path blocker = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.write(blocker, new byte[0]);
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        try {
            writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
            writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            writer.deleteAllNow();
            Files.delete(blocker);
        } finally {
            writer.shutdown();
        }
        assertFalse("shutdown must not restore pre-reset data", dir.exists());
    }

    @Test
    public void shutdownWithPersistentFailureIsBounded() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path blocker = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.write(blocker, new byte[0]);
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
        final long started = System.nanoTime();
        writer.shutdown();
        assertTrue("shutdown should finish within its five-second budget",
                System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5));
        Files.delete(blocker);
        Thread.sleep(2_500);
        assertFalse("shutdown must cancel delayed retries",
                Files.exists(dir.toPath().resolve(REGION_ID + ".lcr")));
    }

    @Test
    public void automaticRetriesStopUntilAnotherCapture() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path blocker = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.write(blocker, new byte[0]);
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        final Path target = dir.toPath().resolve(REGION_ID + ".lcr");
        final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(LiveCollisionPersistence.class);
        final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
            writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            Thread.sleep(7_000);
            writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            Files.delete(blocker);
            Thread.sleep(3_000);
            writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            assertFalse("automatic retries must be bounded", Files.exists(target));
            assertTrue("persistent failures must not spam warnings", logs.list.stream()
                    .filter(event -> event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN))
                    .count() <= 2);
            writer.persist(Map.of(REGION_ID + 1, EAST_OPEN));
            writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            assertTrue("a new capture must retry retained data", Files.isRegularFile(target));
        } finally {
            writer.shutdown();
            logger.detachAppender(logs);
            logs.stop();
        }
        assertEquals(Boolean.FALSE, load(dir).edge(3200, 3200, 0, FLAG_NORTH));
    }

    @Test
    public void separateProcessesRetainDistinctEdgesAfterContention() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path locks = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.createDirectories(locks);
        final Path stripeLock = locks.resolve("regions-" + Math.floorMod(REGION_ID, 64) + ".lock");
        final Process north = startWriter(dir, "north");
        final Process east = startWriter(dir, "east");
        try {
            try (FileChannel channel = FileChannel.open(stripeLock,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                north.getOutputStream().write(1);
                north.getOutputStream().flush();
                east.getOutputStream().write(1);
                east.getOutputStream().flush();
                awaitRegion(dir.toPath().resolve("north-attempted"));
                awaitRegion(dir.toPath().resolve("east-attempted"));
                assertFalse(Files.exists(dir.toPath().resolve(REGION_ID + ".lcr")));
            }
            assertTrue("north process timed out", north.waitFor(15, TimeUnit.SECONDS));
            assertTrue("east process timed out", east.waitFor(15, TimeUnit.SECONDS));
            assertEquals(Files.readString(dir.getParentFile().toPath().resolve("north.log")), 0, north.exitValue());
            assertEquals(Files.readString(dir.getParentFile().toPath().resolve("east.log")), 0, east.exitValue());
            assertBothEdges(load(dir));
            try (Stream<Path> files = Files.list(dir.toPath())) {
                assertFalse(files.anyMatch(path -> path.toString().endsWith(".tmp")));
            }
        } finally {
            north.destroyForcibly();
            east.destroyForcibly();
            north.waitFor(5, TimeUnit.SECONDS);
            east.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void resetDiscardsAnotherProcessesPendingWrite() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path locks = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.createDirectories(locks);
        final Path stripeLock = locks.resolve("regions-" + Math.floorMod(REGION_ID, 64) + ".lock");
        final Process peer = startWriter(dir, "north-reset");
        try {
            try (FileChannel channel = FileChannel.open(stripeLock,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                peer.getOutputStream().write(1);
                peer.getOutputStream().flush();
                awaitRegion(dir.toPath().resolve("north-reset-attempted"));
                final LiveCollisionPersistence resetter = new LiveCollisionPersistence(dir);
                try {
                    resetter.deleteAllNow();
                } finally {
                    resetter.shutdown();
                }
            }
            peer.getOutputStream().write(1);
            peer.getOutputStream().flush();
            assertTrue(peer.waitFor(15, TimeUnit.SECONDS));
            assertEquals(Files.readString(dir.getParentFile().toPath().resolve("north-reset.log")),
                    0, peer.exitValue());
            assertFalse("a peer must not restore writes queued before the reset", dir.exists());
        } finally {
            peer.destroyForcibly();
            peer.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private Process startWriter(File dir, String edge) throws Exception {
        final Set<String> locations = new LinkedHashSet<>();
        for (Class<?> type : new Class<?>[]{LiveCollisionPersistenceTest.class,
                LiveCollisionPersistence.class, org.slf4j.LoggerFactory.class,
                ch.qos.logback.classic.Logger.class, ch.qos.logback.core.Context.class,
                org.junit.Assert.class, org.hamcrest.Matcher.class}) {
            locations.add(new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath());
        }
        return new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(),
                "-cp", String.join(File.pathSeparator, locations),
                LiveCollisionPersistenceTest.class.getName(), dir.getPath(), edge)
                .redirectErrorStream(true).redirectOutput(new File(dir.getParentFile(), edge + ".log")).start();
    }

    @Test
    public void peerCanPersistFreshCaptureAfterDiscardingPreResetRetries() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path blocker = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.write(blocker, new byte[0]);
        final LiveCollisionPersistence peer = new LiveCollisionPersistence(dir);
        final LiveCollisionPersistence resetter = new LiveCollisionPersistence(dir);
        try {
            peer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
            peer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            resetter.deleteAllNow();
            Files.delete(blocker);
            Thread.sleep(2_500);
            peer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            assertFalse("a scheduled peer retry must not undo reset", dir.exists());
            peer.persist(Map.of(REGION_ID, EAST_OPEN));
            peer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
        } finally {
            peer.shutdown();
            resetter.shutdown();
        }
        final LiveCollisionView view = load(dir);
        assertNotNull(view);
        assertNull(view.edge(3200, 3200, 0, FLAG_NORTH));
        assertEquals(Boolean.TRUE, view.edge(3201, 3200, 0, FLAG_EAST));
    }

    @Test
    public void busySharedResetLockFailsWithinBoundAndCanRecover() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path resetLock = temporaryFolder.getRoot().toPath().resolve("regions-reset.lock");
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        try {
            writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
            writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            try (FileChannel channel = FileChannel.open(resetLock,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                final long started = System.nanoTime();
                try {
                    writer.deleteAllNow();
                    fail("reset must report that another process owns the shared lock");
                } catch (IllegalStateException expected) {
                    assertTrue(expected.getCause().getMessage().contains("Timed out"));
                }
                assertTrue("reset lock contention must be bounded",
                        System.nanoTime() - started < TimeUnit.SECONDS.toNanos(8));
                assertTrue("a failed reset must not delete unlocked data",
                        Files.exists(dir.toPath().resolve(REGION_ID + ".lcr")));
            }
            writer.deleteAllNow();
            assertFalse(dir.exists());
            writer.persist(Map.of(REGION_ID, EAST_OPEN));
        } finally {
            writer.shutdown();
        }
        assertEquals(Boolean.TRUE, load(dir).edge(3201, 3200, 0, FLAG_EAST));
    }

    @Test
    public void startupLoadRetriesBusySharedResetLock() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        writer.shutdown();
        final LiveCollisionOverlay overlay = new LiveCollisionOverlay();
        overlay.setEnabled(true);
        final LiveCollisionPersistence reader = new LiveCollisionPersistence(dir);
        final Path resetLock = temporaryFolder.getRoot().toPath().resolve("regions-reset.lock");
        try {
            try (FileChannel channel = FileChannel.open(resetLock,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                reader.loadIntoAsync(overlay);
                reader.ioBarrierForTest().get(5, TimeUnit.SECONDS);
                assertNull(overlay.current());
            }
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (overlay.current() == null && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertNotNull("startup load should resume after contention", overlay.current());
            assertEquals(Boolean.FALSE, overlay.current().edge(3200, 3200, 0, FLAG_NORTH));
        } finally {
            reader.shutdown();
        }
    }

    @Test
    public void transientResetHandleDoesNotBlockCallerOnPeerLock() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        writer.shutdown();
        final LiveCollisionPersistence resetter = new LiveCollisionPersistence(dir);
        final Path resetLock = temporaryFolder.getRoot().toPath().resolve("regions-reset.lock");
        try {
            try (FileChannel channel = FileChannel.open(resetLock,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                final long started = System.nanoTime();
                resetter.deleteAllAsync();
                resetter.shutdownAsync();
                assertTrue("the client-thread reset entry point must not wait for a peer",
                        System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1));
                assertTrue(dir.exists());
            }
        } finally {
            resetter.shutdown();
        }
        assertFalse(dir.exists());
    }

    @Test
    public void readableRegionLoadsWhenSharedResetLockIsUnavailable() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
        writer.shutdown();
        final Path resetLock = temporaryFolder.getRoot().toPath().resolve("regions-reset.lock");
        Files.delete(resetLock);
        Files.createDirectory(resetLock);
        final LiveCollisionView view = load(dir);
        assertNotNull(view);
        assertEquals(Boolean.FALSE, view.edge(3200, 3200, 0, FLAG_NORTH));
    }

    public static void main(String[] args) throws Exception {
        final File dir = new File(args[0]);
        final boolean north = args[1].startsWith("north");
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);
        try {
            if (System.in.read() < 0) {
                throw new IllegalStateException("missing start signal");
            }
            writer.persist(Map.of(REGION_ID, north ? NORTH_BLOCKED : EAST_OPEN));
            writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            Files.write(dir.toPath().resolve(args[1] + "-attempted"), new byte[0]);
            if (args[1].equals("north-reset")) {
                if (System.in.read() < 0) {
                    throw new IllegalStateException("missing shutdown signal");
                }
                return;
            }
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            final LiveCollisionPersistenceTest checks = new LiveCollisionPersistenceTest();
            while (System.nanoTime() < deadline) {
                final LiveCollisionView view = checks.load(dir);
                if (view != null && view.edge(north ? 3200 : 3201, 3200, 0,
                        north ? FLAG_NORTH : FLAG_EAST) != null) {
                    return;
                }
                Thread.sleep(20);
            }
            throw new AssertionError("writer did not recover from cross-process contention");
        } finally {
            writer.shutdown();
        }
    }

    @Test
    public void busyRegionLockIsRetriedAfterAnotherOwnerReleasesIt() throws Exception {
        final File dir = temporaryFolder.newFolder("regions");
        final Path locks = temporaryFolder.getRoot().toPath().resolve("regions-locks");
        Files.createDirectories(locks);
        final Path stripeLock = locks.resolve("regions-" + Math.floorMod(REGION_ID, 64) + ".lock");
        final Path target = dir.toPath().resolve(REGION_ID + ".lcr");
        final LiveCollisionPersistence writer = new LiveCollisionPersistence(dir);

        try (FileChannel channel = FileChannel.open(stripeLock,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            writer.persist(Map.of(REGION_ID, NORTH_BLOCKED));
            writer.ioBarrierForTest().get(5, TimeUnit.SECONDS);
            assertFalse("another owner still holds the region lock", Files.exists(target));
        }

        awaitRegion(target);
        writer.shutdown();
        final LiveCollisionView view = load(dir);
        assertNotNull(view);
        assertEquals(Boolean.FALSE, view.edge(3200, 3200, 0, FLAG_NORTH));
    }

    private void awaitRegion(Path target) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.isRegularFile(target) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue("pending region should be retried after the lock path recovers",
                Files.isRegularFile(target));
    }

    private LiveCollisionView load(File dir) {
        final LiveCollisionOverlay overlay = new LiveCollisionOverlay();
        overlay.setEnabled(true);
        final LiveCollisionPersistence reader = new LiveCollisionPersistence(dir);
        reader.loadIntoAsync(overlay);
        reader.shutdown();
        return overlay.current();
    }

    private void assertBothEdges(LiveCollisionView view) {
        assertNotNull(view);
        assertEquals(Boolean.FALSE, view.edge(3200, 3200, 0, FLAG_NORTH));
        assertEquals(Boolean.TRUE, view.edge(3201, 3200, 0, FLAG_EAST));
    }
}
