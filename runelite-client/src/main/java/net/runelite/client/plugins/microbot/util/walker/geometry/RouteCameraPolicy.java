package net.runelite.client.plugins.microbot.util.walker.geometry;

public final class RouteCameraPolicy {
    public static final int MIN_TARGET_DISTANCE_TILES = 4;
    public static final long TURN_THROTTLE_NANOS = 1_200_000_000L;
    public static final int HEADING_TOLERANCE_DEGREES = 20;
    public static final long RECENT_SCENE_FAILURE_MS = 2_000L;

    public enum Decision {
        SKIP_NEAR,
        SKIP_THROTTLED,
        SKIP_VISIBLE,
        SKIP_ALIGNED,
        TURN
    }

    private RouteCameraPolicy() {
    }

    public static Decision decide(int targetDistanceTiles, long nanosSinceLastTurn, boolean targetSceneClickable,
                                  boolean recentSceneClickFailure, int yawErrorDegrees, boolean viewVariationDue) {
        if (targetDistanceTiles < MIN_TARGET_DISTANCE_TILES) {
            return Decision.SKIP_NEAR;
        }
        if (nanosSinceLastTurn >= 0 && nanosSinceLastTurn < TURN_THROTTLE_NANOS) {
            return Decision.SKIP_THROTTLED;
        }
        if (targetSceneClickable && !recentSceneClickFailure) {
            return Decision.SKIP_VISIBLE;
        }
        if (!viewVariationDue && Math.abs(yawErrorDegrees) < HEADING_TOLERANCE_DEGREES) {
            return Decision.SKIP_ALIGNED;
        }
        return Decision.TURN;
    }
}
