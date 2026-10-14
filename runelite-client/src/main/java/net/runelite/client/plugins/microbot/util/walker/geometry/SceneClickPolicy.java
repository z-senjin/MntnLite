package net.runelite.client.plugins.microbot.util.walker.geometry;

import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

public final class SceneClickPolicy {
    public static final int SOFTWARE_DRAW_DISTANCE = 25;
    public static final int DRAW_DISTANCE_MARGIN_TILES = 2;
    public static final int DESTINATION_TOLERANCE_TILES = 2;
    public static final int FAILURES_BEFORE_SUPPRESSION = 2;
    public static final long SUPPRESSION_MS = 3_000L;

    public enum Outcome {
        CONFIRMED,
        DIVERTED,
        MISSED
    }

    private int consecutiveFailures;
    private long suppressedUntilMs;
    private long lastFailureAtMs = Long.MIN_VALUE;

    private SceneClickPolicy() {
    }

    public static SceneClickPolicy create() {
        return new SceneClickPolicy();
    }

    public static int renderedDrawDistance(boolean gpu, int sceneDrawDistance) {
        if (!gpu || sceneDrawDistance <= 0) {
            return SOFTWARE_DRAW_DISTANCE;
        }
        return sceneDrawDistance;
    }

    public static boolean isWithinRenderedArea(int cameraLocalX, int cameraLocalY, LocalPoint target, int drawDistance) {
        if (target == null) {
            return false;
        }
        int tiles = Math.max(Math.abs(target.getX() - cameraLocalX), Math.abs(target.getY() - cameraLocalY))
                / Perspective.LOCAL_TILE_SIZE;
        return tiles <= drawDistance - DRAW_DISTANCE_MARGIN_TILES;
    }

    public static Outcome classify(LocalPoint before, LocalPoint after, LocalPoint intended) {
        if (after == null || intended == null) {
            return Outcome.MISSED;
        }
        boolean nearIntended = tileDistance(after, intended) <= DESTINATION_TOLERANCE_TILES;
        if (nearIntended) {
            return Outcome.CONFIRMED;
        }
        return after.equals(before) ? Outcome.MISSED : Outcome.DIVERTED;
    }

    public static boolean isSettled(LocalPoint before, LocalPoint after, LocalPoint intended) {
        return after != null && (!after.equals(before) || classify(before, after, intended) == Outcome.CONFIRMED);
    }

    public static int destinationErrorTiles(LocalPoint after, LocalPoint intended) {
        return after == null || intended == null ? -1 : tileDistance(after, intended);
    }

    static int tileDistance(LocalPoint a, LocalPoint b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) / Perspective.LOCAL_TILE_SIZE;
    }

    public synchronized boolean isSuppressed(long nowMs) {
        return nowMs < suppressedUntilMs;
    }

    public synchronized boolean failedWithin(long nowMs, long windowMs) {
        return lastFailureAtMs != Long.MIN_VALUE && nowMs - lastFailureAtMs < windowMs;
    }

    public synchronized boolean record(Outcome outcome, long nowMs) {
        if (outcome == Outcome.CONFIRMED) {
            consecutiveFailures = 0;
            return false;
        }
        lastFailureAtMs = nowMs;
        consecutiveFailures++;
        if (consecutiveFailures < FAILURES_BEFORE_SUPPRESSION) {
            return false;
        }
        consecutiveFailures = 0;
        suppressedUntilMs = nowMs + SUPPRESSION_MS;
        return true;
    }

    public synchronized void reset() {
        consecutiveFailures = 0;
        suppressedUntilMs = 0L;
        lastFailureAtMs = Long.MIN_VALUE;
    }
}
