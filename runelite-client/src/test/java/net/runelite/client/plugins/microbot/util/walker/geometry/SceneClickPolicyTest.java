package net.runelite.client.plugins.microbot.util.walker.geometry;

import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static net.runelite.client.plugins.microbot.util.walker.geometry.SceneClickPolicy.Outcome.CONFIRMED;
import static net.runelite.client.plugins.microbot.util.walker.geometry.SceneClickPolicy.Outcome.DIVERTED;
import static net.runelite.client.plugins.microbot.util.walker.geometry.SceneClickPolicy.Outcome.MISSED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SceneClickPolicyTest {
    private static LocalPoint tile(int x, int y) {
        return new LocalPoint(x * 128 + 64, y * 128 + 64, WorldView.TOPLEVEL);
    }

    @Test
    public void softwareRenderingUsesFixedDistanceEvenWhenGpuValueRemains() {
        assertEquals(25, SceneClickPolicy.renderedDrawDistance(false, 50));
        assertEquals(25, SceneClickPolicy.renderedDrawDistance(false, 90));
        assertEquals(25, SceneClickPolicy.renderedDrawDistance(true, 0));
        assertEquals(50, SceneClickPolicy.renderedDrawDistance(true, 50));
        assertEquals(12, SceneClickPolicy.renderedDrawDistance(true, 12));
    }

    @Test
    public void renderedAreaIsChebyshevFromCameraWithMargin() {
        LocalPoint camera = tile(50, 40);
        assertTrue(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), tile(50, 63), 25));
        assertTrue(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), tile(27, 17), 25));
        assertFalse(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), tile(50, 64), 25));
        assertFalse(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), tile(74, 40), 25));
        assertTrue(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), tile(50, 88), 50));
        assertFalse(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), tile(50, 89), 50));
        assertFalse(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), null, 50));
    }

    @Test
    public void cameraOffsetFromPlayerIsRespected() {
        LocalPoint camera = tile(50, 33);
        assertFalse(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), tile(50, 57), 25));
        assertTrue(SceneClickPolicy.isWithinRenderedArea(camera.getX(), camera.getY(), tile(50, 20), 25));
    }

    @Test
    public void classifiesDestinationOutcomes() {
        LocalPoint target = tile(10, 10);
        assertEquals(CONFIRMED, SceneClickPolicy.classify(null, target, target));
        assertEquals(CONFIRMED, SceneClickPolicy.classify(null, tile(12, 8), target));
        assertEquals(DIVERTED, SceneClickPolicy.classify(null, tile(13, 10), target));
        assertEquals(DIVERTED, SceneClickPolicy.classify(tile(3, 3), tile(10, 25), target));
        assertEquals(MISSED, SceneClickPolicy.classify(null, null, target));
        assertEquals(MISSED, SceneClickPolicy.classify(tile(3, 3), tile(3, 3), target));
        assertEquals(CONFIRMED, SceneClickPolicy.classify(tile(10, 11), tile(10, 11), target));
        assertEquals(MISSED, SceneClickPolicy.classify(null, target, null));
    }

    @Test
    public void reportsDestinationErrorInTiles() {
        assertEquals(0, SceneClickPolicy.destinationErrorTiles(tile(4, 4), tile(4, 4)));
        assertEquals(7, SceneClickPolicy.destinationErrorTiles(tile(4, 11), tile(4, 4)));
        assertEquals(-1, SceneClickPolicy.destinationErrorTiles(null, tile(4, 4)));
        assertEquals(-1, SceneClickPolicy.destinationErrorTiles(tile(4, 4), null));
    }

    @Test
    public void settlesOnlyWhenDestinationChangesOrAlreadyMatches() {
        LocalPoint target = tile(10, 10);
        assertFalse(SceneClickPolicy.isSettled(null, null, target));
        assertFalse(SceneClickPolicy.isSettled(tile(3, 3), tile(3, 3), target));
        assertTrue(SceneClickPolicy.isSettled(tile(3, 3), tile(10, 10), target));
        assertTrue(SceneClickPolicy.isSettled(null, tile(20, 20), target));
        assertTrue(SceneClickPolicy.isSettled(tile(10, 9), tile(10, 9), target));
    }

    @Test
    public void suppressesAfterConsecutiveFailuresForBoundedWindow() {
        SceneClickPolicy policy = SceneClickPolicy.create();
        assertFalse(policy.record(MISSED, 1_000));
        assertFalse(policy.isSuppressed(1_000));
        assertTrue(policy.record(DIVERTED, 1_100));
        assertTrue(policy.isSuppressed(1_100));
        assertTrue(policy.isSuppressed(1_100 + SceneClickPolicy.SUPPRESSION_MS - 1));
        assertFalse(policy.isSuppressed(1_100 + SceneClickPolicy.SUPPRESSION_MS));
        assertFalse(policy.record(MISSED, 5_000));
        assertFalse(policy.isSuppressed(5_000));
    }

    @Test
    public void confirmationResetsFailureStreak() {
        SceneClickPolicy policy = SceneClickPolicy.create();
        assertFalse(policy.record(MISSED, 0));
        assertFalse(policy.record(CONFIRMED, 10));
        assertFalse(policy.record(MISSED, 20));
        assertFalse(policy.isSuppressed(20));
        policy.record(MISSED, 30);
        assertTrue(policy.isSuppressed(30));
        policy.reset();
        assertFalse(policy.isSuppressed(30));
    }
}
