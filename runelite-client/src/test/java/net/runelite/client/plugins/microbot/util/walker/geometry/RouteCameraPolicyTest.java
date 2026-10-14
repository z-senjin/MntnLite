package net.runelite.client.plugins.microbot.util.walker.geometry;

import org.junit.Test;

import static net.runelite.client.plugins.microbot.util.walker.geometry.RouteCameraPolicy.Decision.SKIP_ALIGNED;
import static net.runelite.client.plugins.microbot.util.walker.geometry.RouteCameraPolicy.Decision.SKIP_NEAR;
import static net.runelite.client.plugins.microbot.util.walker.geometry.RouteCameraPolicy.Decision.SKIP_THROTTLED;
import static net.runelite.client.plugins.microbot.util.walker.geometry.RouteCameraPolicy.Decision.SKIP_VISIBLE;
import static net.runelite.client.plugins.microbot.util.walker.geometry.RouteCameraPolicy.Decision.TURN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteCameraPolicyTest {
    private static final long IDLE = 5_000_000_000L;

    @Test
    public void visibleTargetNeverTurnsEvenWhenVariationIsDueOrHeadingIsOff() {
        assertEquals(SKIP_VISIBLE, RouteCameraPolicy.decide(12, IDLE, true, false, 90, true));
        assertEquals(SKIP_VISIBLE, RouteCameraPolicy.decide(12, -1, true, false, 170, false));
    }

    @Test
    public void hiddenTargetTurnsWhenHeadingIsOff() {
        assertEquals(TURN, RouteCameraPolicy.decide(12, IDLE, false, false, 45, false));
        assertEquals(TURN, RouteCameraPolicy.decide(12, -1, false, false, -20, false));
    }

    @Test
    public void hiddenTargetAlreadyFacedOnlyTurnsForDueVariation() {
        assertEquals(SKIP_ALIGNED, RouteCameraPolicy.decide(12, IDLE, false, false, 19, false));
        assertEquals(SKIP_ALIGNED, RouteCameraPolicy.decide(12, IDLE, false, false, -19, false));
        assertEquals(TURN, RouteCameraPolicy.decide(12, IDLE, false, false, 5, true));
    }

    @Test
    public void recentSceneFailureTreatsProjectedTargetAsObscured() {
        assertEquals(TURN, RouteCameraPolicy.decide(12, IDLE, true, true, 60, false));
        assertEquals(SKIP_ALIGNED, RouteCameraPolicy.decide(12, IDLE, true, true, 10, false));
    }

    @Test
    public void nearTargetsAndRepeatedRequestsAreBounded() {
        assertEquals(SKIP_NEAR, RouteCameraPolicy.decide(3, IDLE, false, true, 170, true));
        assertEquals(SKIP_THROTTLED, RouteCameraPolicy.decide(12, 0, false, false, 170, true));
        assertEquals(SKIP_THROTTLED, RouteCameraPolicy.decide(12, RouteCameraPolicy.TURN_THROTTLE_NANOS - 1,
                false, false, 170, true));
        assertEquals(TURN, RouteCameraPolicy.decide(12, RouteCameraPolicy.TURN_THROTTLE_NANOS,
                false, false, 170, true));
    }

    @Test
    public void sceneClickFailuresAreRecentOnlyWithinWindow() {
        SceneClickPolicy clicks = SceneClickPolicy.create();
        assertFalse(clicks.failedWithin(10_000L, 2_000L));
        clicks.record(SceneClickPolicy.Outcome.CONFIRMED, 10_000L);
        assertFalse(clicks.failedWithin(10_000L, 2_000L));
        clicks.record(SceneClickPolicy.Outcome.DIVERTED, 10_000L);
        assertTrue(clicks.failedWithin(11_999L, 2_000L));
        assertFalse(clicks.failedWithin(12_000L, 2_000L));
        clicks.record(SceneClickPolicy.Outcome.CONFIRMED, 11_000L);
        assertTrue(clicks.failedWithin(11_500L, 2_000L));
        clicks.reset();
        assertFalse(clicks.failedWithin(11_500L, 2_000L));
    }
}
