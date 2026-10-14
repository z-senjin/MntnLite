package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Disk backing for the accumulating live-collision store, so what the bot learns while walking survives a
 * restart and keeps overriding the static map's reconstruction permanently (until a game update).
 * <p>
 * One binary file per region under
 * {@code ~/.runelite/microbot/live-collision/<cacheRevision>-c<captureVersion>/<regionId>.lcr}.
 * The cache revision and {@link LiveCollisionCapture#CAPTURE_VERSION capture-semantics version} form the
 * invalidation key: after either changes, previous regions are never read rather than being trusted
 * against changed geometry or capture rules. Old revision stores are retained while other clients may
 * still be using them; an explicit reset removes the entire store.
 * <p>
 * All I/O runs on a single daemon thread; loads and stores never touch the client or pathfinder threads.
 * {@link LiveCollisionOverlay#putRegion} and {@link LiveCollisionOverlay#drainDirty} are the only
 * synchronisation points, and both are already thread-safe.
 */
@Slf4j
public final class LiveCollisionPersistence {
    private static final int MAGIC = 0x4C435231; // "LCR1"
    private static final int VERSION = 1;
    // Part of the shared lock-file naming scheme; keep stable across client builds using this store.
    private static final int LOCK_STRIPES = 64;
    private static final long RETRY_DELAY_MS = 2_000;
    private static final int MAX_AUTOMATIC_RETRIES = 3;
    private static final Object[] JVM_LOCKS = new Object[LOCK_STRIPES];

    static {
        for (int i = 0; i < JVM_LOCKS.length; i++) {
            JVM_LOCKS[i] = new Object();
        }
    }

    private final File dir;
    /** Root removed by {@link #deleteAllNow()} — the whole {@code live-collision} tree (all revisions). */
    private final File deleteRoot;
    /** Kept outside deleteRoot so a reset cannot unlink a lock that another client holds. */
    private final File lockDir;
    private final Path resetLock;
    private final Path generationFile;
    private final AtomicBoolean loadLockWarningLogged = new AtomicBoolean();
    private final AtomicBoolean writeLockWarningLogged = new AtomicBoolean();
    private final AtomicBoolean writeFailureLogged = new AtomicBoolean();
    private final AtomicBoolean shutdownWarningLogged = new AtomicBoolean();
    private final AtomicBoolean retryLimitWarningLogged = new AtomicBoolean();
    /** Only the I/O worker touches these; failed writes remain here until a confirmed replacement. */
    private final Map<Integer, LiveCollisionRegion> pending = new HashMap<>();
    private ScheduledFuture<?> retryTask;
    private int automaticRetries;
    private String generation;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "live-collision-io");
        t.setDaemon(true);
        return t;
    });

    public LiveCollisionPersistence(int cacheRevision) {
        final File liveCollisionBase = new File(new File(RuneLite.RUNELITE_DIR, "microbot"), "live-collision");
        this.dir = new File(liveCollisionBase,
                cacheRevision + "-c" + LiveCollisionCapture.CAPTURE_VERSION);
        this.deleteRoot = liveCollisionBase;
        this.lockDir = new File(liveCollisionBase.getParentFile(), "live-collision-locks");
        this.resetLock = new File(liveCollisionBase.getParentFile(), "live-collision-reset.lock").toPath();
        this.generationFile = new File(liveCollisionBase.getParentFile(), "live-collision-generation").toPath();
        io.execute(this::initializeGeneration);
    }

    /** Backs the store with an explicit directory instead of the shared user dir (tests, tooling). */
    public LiveCollisionPersistence(File dir) {
        this.dir = dir;
        this.deleteRoot = dir;
        this.lockDir = new File(dir.getAbsoluteFile().getParentFile(), dir.getName() + "-locks");
        this.resetLock = new File(dir.getAbsoluteFile().getParentFile(), dir.getName() + "-reset.lock").toPath();
        this.generationFile = new File(dir.getAbsoluteFile().getParentFile(), dir.getName() + "-generation").toPath();
        io.execute(this::initializeGeneration);
    }

    private String readGeneration() throws IOException {
        return Files.exists(generationFile) ? Files.readString(generationFile) : "";
    }

    private void initializeGeneration() {
        try {
            generation = readGeneration();
        } catch (IOException ex) {
            if (writeFailureLogged.compareAndSet(false, true)) {
                log.warn("[LiveCollision] could not read shared store generation: {}", ex.toString());
            }
        }
    }

    private boolean withResetLock(boolean exclusive, StoreOperation operation) throws IOException {
        Files.createDirectories(resetLock.getParent());
        try (FileChannel channel = FileChannel.open(resetLock,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            final FileLock lock = channel.tryLock(0, Long.MAX_VALUE, !exclusive);
            if (lock == null) {
                return false;
            }
            try (FileLock ignored = lock) {
                operation.run();
                return true;
            }
        } catch (OverlappingFileLockException ex) {
            return false;
        }
    }

    @FunctionalInterface
    private interface StoreOperation {
        void run() throws IOException;
    }

    /**
     * Asynchronously reads every persisted region for this cache revision and merges it into {@code overlay}.
     * Safe to call once at enable-time; a corrupt or partial file is skipped, not fatal.
     */
    public void loadIntoAsync(LiveCollisionOverlay overlay) {
        io.execute(() -> loadInto(overlay, 0));
    }

    private void loadInto(LiveCollisionOverlay overlay, int attempt) {
        try {
            if (withResetLock(false, () -> loadRegions(overlay))) {
                return;
            }
            if (attempt < MAX_AUTOMATIC_RETRIES && !io.isShutdown()) {
                try {
                    io.schedule(() -> loadInto(overlay, attempt + 1), RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                    return;
                } catch (RejectedExecutionException ex) {
                    log.debug("[LiveCollision] shutdown cancelled a pending shared store load");
                }
            }
            if (loadLockWarningLogged.compareAndSet(false, true)) {
                log.warn("[LiveCollision] shared store remained busy; persisted regions were not loaded");
            }
        } catch (IOException ex) {
            if (loadLockWarningLogged.compareAndSet(false, true)) {
                log.warn("[LiveCollision] loading without shared reset lock: {}", ex.toString());
            }
            loadRegions(overlay);
        }
    }

    private void loadRegions(LiveCollisionOverlay overlay) {
        final File[] files = dir.listFiles((d, name) -> name.endsWith(".lcr"));
        if (files == null) {
            return;
        }
        int loaded = 0;
        for (File regionFile : files) {
            final Integer regionId = parseRegionId(regionFile.getName());
            if (regionId == null) {
                continue;
            }
            final LiveCollisionRegion region = readRegionWithLock(regionId, regionFile);
            if (region != null) {
                overlay.putRegion(regionId, region);
                loaded++;
            }
        }
        if (loaded > 0) {
            log.debug("[LiveCollision] loaded {} persisted regions from {}", loaded, dir);
        }
    }

    /** Asynchronously writes the given (region id -> region) entries, creating the directory as needed. */
    public void persist(Map<Integer, LiveCollisionRegion> dirtyRegions) {
        if (dirtyRegions == null || dirtyRegions.isEmpty()) {
            return;
        }
        // Copy the map reference set; the regions themselves are immutable so no defensive copy is needed.
        final Map<Integer, LiveCollisionRegion> batch = Map.copyOf(dirtyRegions);
        io.execute(() -> {
            pending.putAll(batch);
            automaticRetries = 0;
            flushPending(true);
        });
    }

    private void flushPending(boolean allowRetry) {
        if (pending.isEmpty()) {
            cancelRetry();
            return;
        }
        try {
            withResetLock(false, this::flushPendingLocked);
        } catch (IOException ex) {
            if (writeFailureLogged.compareAndSet(false, true)) {
                log.warn("[LiveCollision] could not prepare shared store for writes: {}", ex.toString());
            }
        }
        if (pending.isEmpty()) {
            cancelRetry();
        } else if (allowRetry && retryTask == null && !io.isShutdown()
                && automaticRetries < MAX_AUTOMATIC_RETRIES) {
            try {
                automaticRetries++;
                retryTask = io.schedule(() -> {
                    retryTask = null;
                    flushPending(true);
                }, RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                // Shutdown queued a final flush while this I/O task was finishing.
            }
        } else if (allowRetry && retryTask == null && automaticRetries >= MAX_AUTOMATIC_RETRIES
                && retryLimitWarningLogged.compareAndSet(false, true)) {
            log.warn("[LiveCollision] automatic write retries exhausted; retaining {} regions until next capture or shutdown",
                    pending.size());
        }
    }

    private void flushPendingLocked() throws IOException {
        final String currentGeneration = readGeneration();
        if (!Objects.equals(generation, currentGeneration)) {
            log.debug("[LiveCollision] discarding {} queued regions after shared store reset", pending.size());
            pending.clear();
            generation = currentGeneration;
            return;
        }
        Files.createDirectories(dir.toPath());
        Files.createDirectories(lockDir.toPath());
        final Iterator<Map.Entry<Integer, LiveCollisionRegion>> entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            final Map.Entry<Integer, LiveCollisionRegion> entry = entries.next();
            if (writeRegion(entry.getKey(), entry.getValue())) {
                entries.remove();
            }
        }
    }

    private void cancelRetry() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }

    /** A queue barrier for persistence tests; never called from the client thread. */
    Future<?> ioBarrierForTest() {
        return io.submit(() -> { });
    }

    /**
     * Asynchronously deletes this store's entire on-disk tree (all cache revisions). Discards failed
     * writes queued before the reset; a fresh capture queued afterwards can persist normally.
     */
    public void deleteAllAsync() {
        io.execute(() -> {
            try {
                resetStore();
            } catch (IllegalStateException ex) {
                log.warn("[LiveCollision] shared store reset failed: {}", ex.toString());
            }
        });
    }

    /** Synchronously resets pending writes and the on-disk tree; never call from the I/O worker. */
    public void deleteAllNow() {
        try {
            io.submit(this::resetStore).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resetting learned collision", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Failed to reset learned collision", ex.getCause());
        }
    }

    private void resetStore() {
        pending.clear();
        cancelRetry();
        automaticRetries = 0;
        try {
            for (int attempt = 0; attempt < 50; attempt++) {
                if (withResetLock(true, this::resetStoreLocked)) {
                    return;
                }
                if (attempt < 49) {
                    TimeUnit.MILLISECONDS.sleep(100);
                }
            }
            throw new IllegalStateException("Timed out waiting to reset shared collision store");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to reset shared collision store", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resetting shared collision store", ex);
        }
    }

    private void resetStoreLocked() throws IOException {
        final String nextGeneration = UUID.randomUUID().toString();
        final Path temporaryGeneration = Files.createTempFile(generationFile.getParent(),
                generationFile.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporaryGeneration, nextGeneration);
            Files.move(temporaryGeneration, generationFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            generation = nextGeneration;
            deleteRecursively(deleteRoot);
            if (deleteRoot.exists()) {
                throw new IOException("Could not remove the shared collision store");
            }
        } finally {
            Files.deleteIfExists(temporaryGeneration);
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        final File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!f.delete()) {
            log.warn("[LiveCollision] could not delete {}", f);
        }
    }

    private void flushBeforeShutdown() {
        cancelRetry();
        // A peer may hold a stripe briefly just as this client exits. Give that ordinary contention
        // a bounded chance to clear before reporting the region as unsaved.
        for (int attempt = 0; attempt < 3 && !pending.isEmpty(); attempt++) {
            flushPending(false);
            if (!pending.isEmpty() && attempt < 2) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Queues the final flush and stops accepting work without waiting on the calling thread. */
    public synchronized void shutdownAsync() {
        if (!io.isShutdown()) {
            io.execute(this::flushBeforeShutdown);
            io.shutdown();
        }
    }

    /** Flushes pending writes and stops the I/O thread. */
    public synchronized void shutdown() {
        shutdownAsync();
        try {
            final boolean finished = io.awaitTermination(5, TimeUnit.SECONDS);
            if (!finished && shutdownWarningLogged.compareAndSet(false, true)) {
                log.warn("[LiveCollision] I/O flush did not finish within 5 seconds");
            } else if (finished && !pending.isEmpty()
                    && shutdownWarningLogged.compareAndSet(false, true)) {
                log.warn("[LiveCollision] {} regions remain unsaved after I/O failure", pending.size());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean writeRegion(int regionId, LiveCollisionRegion region) {
        // File locks coordinate separate clients; the JVM guard also prevents OverlappingFileLockException
        // when two persistence instances in this process write a region at the same time.
        final int stripe = Math.floorMod(regionId, LOCK_STRIPES);
        synchronized (JVM_LOCKS[stripe]) {
            final Path lockPath = new File(lockDir, dir.getName() + "-" + stripe + ".lock").toPath();
            try {
                try (FileChannel channel = FileChannel.open(lockPath,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    final FileLock lock = channel.tryLock();
                    if (lock == null) {
                        return false;
                    }
                    try (FileLock ignored = lock) {
                        return writeRegionLocked(regionId, region);
                    }
                }
            } catch (IOException ex) {
                if (writeLockWarningLogged.compareAndSet(false, true)) {
                    log.warn("[LiveCollision] failed locking shared store for writes: {}", ex.toString());
                }
                return false;
            } catch (OverlappingFileLockException ex) {
                return false;
            }
        }
    }

    private LiveCollisionRegion readRegionWithLock(int regionId, File target) {
        // Coordinate startup reads with replacement on Windows, where moving over an open target can fail.
        final int stripe = Math.floorMod(regionId, LOCK_STRIPES);
        synchronized (JVM_LOCKS[stripe]) {
            final Path lockPath = new File(lockDir, dir.getName() + "-" + stripe + ".lock").toPath();
            try {
                Files.createDirectories(lockDir.toPath());
                try (FileChannel channel = FileChannel.open(lockPath,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    final FileLock lock = channel.tryLock();
                    if (lock == null) {
                        return readRegion(target);
                    }
                    try (FileLock ignored = lock) {
                        return readRegion(target);
                    }
                }
            } catch (IOException ex) {
                if (loadLockWarningLogged.compareAndSet(false, true)) {
                    log.warn("[LiveCollision] loading without shared store lock: {}", ex.toString());
                }
                // An already readable store must remain usable when a lock directory cannot be created.
                // Writers using this protocol also need that directory, so this preserves the old
                // best-effort read behaviour without silently dropping the region on startup.
                return readRegion(target);
            } catch (OverlappingFileLockException ex) {
                return readRegion(target);
            }
        }
    }

    private boolean writeRegionLocked(int regionId, LiveCollisionRegion region) {
        final File target = new File(dir, regionId + ".lcr");
        // The incoming snapshot is only this client's knowledge. Retain edges another client learned
        // since our last load, while preferring this snapshot for edges both clients know.
        final LiveCollisionRegion merged = LiveCollisionRegions.merge(readRegion(target), region);
        Path tmp = null;
        try {
            // Several clients can share this store; a fixed per-region temp path lets their writers
            // collide before either reaches the atomic replacement (especially on Windows).
            tmp = Files.createTempFile(dir.toPath(), regionId + ".lcr.", ".tmp");
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tmp.toFile())))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(LiveCollisionCapture.CAPTURE_VERSION);
                out.writeInt(merged.getPlaneCount());
                writeWords(out, merged.northKnownWords());
                writeWords(out, merged.northValueWords());
                writeWords(out, merged.eastKnownWords());
                writeWords(out, merged.eastValueWords());
            }
            try {
                Files.move(tmp, target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            if (writeFailureLogged.compareAndSet(false, true)) {
                log.warn("[LiveCollision] failed writing a shared region: {}", ex.toString());
            }
            return false;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ex) {
                    log.debug("[LiveCollision] could not remove temporary region file {}: {}", tmp, ex.toString());
                }
            }
        }
    }

    private static void writeWords(DataOutputStream out, long[] words) throws IOException {
        out.writeInt(words.length);
        for (long w : words) {
            out.writeLong(w);
        }
    }

    private static LiveCollisionRegion readRegion(File f) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                return null;
            }
            // Reject data produced by an older capture semantics version; a fresh capture would now record
            // these regions differently, so trusting the old bytes reintroduces exactly the stale-data bug
            // that previously required a manual "Reset learned collision".
            if (in.readInt() != LiveCollisionCapture.CAPTURE_VERSION) {
                return null;
            }
            final int planeCount = in.readInt();
            if (planeCount < 0 || planeCount > 4) {
                return null;
            }
            final long[] nK = readWords(in);
            final long[] nV = readWords(in);
            final long[] eK = readWords(in);
            final long[] eV = readWords(in);
            return LiveCollisionRegion.fromWords(planeCount, nK, nV, eK, eV);
        } catch (IOException | NegativeArraySizeException ex) {
            return null;
        }
    }

    private static long[] readWords(DataInputStream in) throws IOException {
        final int len = in.readInt();
        // A single region is at most 4 planes * 64 * 64 bits = 256 longs; reject anything absurd.
        if (len < 0 || len > 1024) {
            throw new IOException("implausible word length " + len);
        }
        final long[] words = new long[len];
        for (int i = 0; i < len; i++) {
            words[i] = in.readLong();
        }
        return words;
    }

    private static Integer parseRegionId(String fileName) {
        final int dot = fileName.indexOf(".lcr");
        if (dot <= 0) {
            return null;
        }
        try {
            return Integer.parseInt(fileName.substring(0, dot));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
