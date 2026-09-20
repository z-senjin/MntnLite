package net.runelite.client.plugins.microbot.util.walker.door;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The edge-scoped global door cooldown. The full window is anti-hammer for re-clicking ONE door; a
 * different door immediately after a successful open is chaining, not hammering, and holding it for
 * the full window serialised every pair of nearby doors at ~1.8s each.
 */
public class Rs2DoorHandlerTest {

    private static final long FULL = 1_800L;
    private static final long CROSS = 600L;
    private static final long CLICKED_AT = 100_000L;
    private static final long NEXT_ALLOWED = CLICKED_AT + FULL;

    @Test
    public void sameEdgeKeepsTheFullWindow() {
        assertTrue(Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(
                CLICKED_AT + 1_000L, NEXT_ALLOWED, true, FULL, CROSS));
        assertFalse(Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(
                CLICKED_AT + FULL, NEXT_ALLOWED, true, FULL, CROSS));
    }

    /** A different door owes one tick, no more — that is what a player chaining two doors looks like. */
    @Test
    public void differentEdgeOwesOnlyTheCrossEdgeFloor() {
        assertTrue(Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(
                CLICKED_AT + 200L, NEXT_ALLOWED, false, FULL, CROSS));
        assertFalse(Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(
                CLICKED_AT + CROSS, NEXT_ALLOWED, false, FULL, CROSS));
        assertFalse(Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(
                CLICKED_AT + 1_000L, NEXT_ALLOWED, false, FULL, CROSS));
    }

    /** No window stamped (or long expired): nothing throttles either way. */
    @Test
    public void expiredWindowThrottlesNothing() {
        assertFalse(Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(
                CLICKED_AT + 10_000L, NEXT_ALLOWED, true, FULL, CROSS));
        assertFalse(Rs2DoorHandler.shouldThrottleGlobalDoorInteraction(
                CLICKED_AT, 0L, false, FULL, CROSS));
    }

}
