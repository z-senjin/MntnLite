package net.runelite.client.plugins.microbot.util.walker;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-pass stage stopwatch for the walk loop (task #25 slice 2).
 *
 * <p>The 16:35 Varlamore walk showed 5-12s passes with every single wait bounded at ≤1.2s — the
 * time is the SUM of per-tile handler work (door probes during interim, segment-handler
 * eligibility, click selection/issuance), invisible once the post-transport tmark window expires.
 * The handlers accumulate their elapsed time here from their own bodies — deliberately NOT from
 * call sites in {@code processWalk}, which sits at its architecture-guard line cap — and
 * {@code walkerHeartbeat} emits one {@code pass_slow} line at the start of the next pass whenever
 * the previous pass ran long. The residual (total minus stages) is itself a finding: it names how
 * much of a slow pass the instrumented stages do NOT explain.
 *
 * <p>Statics are safe here for the same reason they are everywhere else in the walker: one walk
 * loop runs at a time. Atomics are cheap insurance against a stray helper thread, not a
 * concurrency design.
 */
final class WalkPassStats {

    static final AtomicLong doorProbeMs = new AtomicLong();
    static final AtomicLong segDoorMs = new AtomicLong();
    static final AtomicLong segTransportMs = new AtomicLong();
    static final AtomicLong rawSceneScanMs = new AtomicLong();
    static final AtomicLong currentTileMs = new AtomicLong();
    static final AtomicLong clickSelectMs = new AtomicLong();
    static final AtomicLong clickIssueMs = new AtomicLong();

    private WalkPassStats() {
    }

    /** Formats the collected stages for the {@code pass_slow} line. Does not reset. */
    static String snapshot(long totalMs) {
        long doorProbe = doorProbeMs.get();
        long segDoor = segDoorMs.get();
        long segTransport = segTransportMs.get();
        long rawSceneScan = rawSceneScanMs.get();
        long currentTile = currentTileMs.get();
        long clickSelect = clickSelectMs.get();
        long clickIssue = clickIssueMs.get();
        long residual = totalMs - doorProbe - segDoor - segTransport - rawSceneScan
                - currentTile - clickSelect - clickIssue;
        return "doorProbe=" + doorProbe
                + "ms segDoor=" + segDoor
                + "ms segTransport=" + segTransport
                + "ms rawSceneScan=" + rawSceneScan
                + "ms currentTile=" + currentTile
                + "ms clickSelect=" + clickSelect
                + "ms clickIssue=" + clickIssue
                + "ms residual=" + residual + "ms";
    }

    /** Clears all stages; called at every pass start so each pass owns its numbers. */
    static void reset() {
        doorProbeMs.set(0);
        segDoorMs.set(0);
        segTransportMs.set(0);
        rawSceneScanMs.set(0);
        currentTileMs.set(0);
        clickSelectMs.set(0);
        clickIssueMs.set(0);
    }
}
