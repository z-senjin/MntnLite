package net.runelite.client.plugins.microbot.util.walker.door;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerAwaitsTest {
    @Test
    public void shouldAcceptIdleDoorAwait_acceptsStationaryPastMinimum() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1200L, false));
        assertTrue(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1201L, true));
    }

    /**
     * The old implementation ended in {@code return edgeResolved}, which made this branch dead code:
     * the traversal wait returns the moment the edge resolves, so it only ever reached here with
     * edgeResolved == false. A stalled interaction burned the full 2200ms budget instead of releasing.
     */
    @Test
    public void shouldAcceptIdleDoorAwait_acceptsUnresolvedEdgeWhenStalled() {
        assertTrue(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1500L, false));
    }

    @Test
    public void shouldAcceptIdleDoorAwait_rejectsMovingOrAnimating() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(true, false, 5000L, true));
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, true, 5000L, true));
    }

    @Test
    public void shouldAcceptIdleDoorAwait_rejectsBeforeMinimumElapsed() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1200L, true));
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 800L, true));
    }

    // ---- door-open observation throttle -------------------------------------------------------------

    /**
     * An unlocked door opens within one game tick, so an observation before then can only report
     * "still shut". The observation is a scene scan, not a field read, which is why it is rationed at
     * all rather than run on every poll of the surrounding wait.
     */
    @Test
    public void shouldPollDoorOpen_notBeforeADoorCouldHaveOpened() {
        assertFalse(Rs2WalkerAwaits.shouldPollDoorOpen(0L, 10_000L));
        assertFalse(Rs2WalkerAwaits.shouldPollDoorOpen(100L, 10_000L));
    }

    @Test
    public void shouldPollDoorOpen_onceTheFirstTickHasPassed() {
        assertTrue(Rs2WalkerAwaits.shouldPollDoorOpen(250L, 10_000L));
        assertTrue(Rs2WalkerAwaits.shouldPollDoorOpen(600L, 10_000L));
    }

    /** Rationed: a fresh observation is not worth a scene scan on every poll of the wait. */
    @Test
    public void shouldPollDoorOpen_notMoreOftenThanTheInterval() {
        assertFalse(Rs2WalkerAwaits.shouldPollDoorOpen(1_000L, 0L));
        assertFalse(Rs2WalkerAwaits.shouldPollDoorOpen(1_000L, 100L));
        assertTrue(Rs2WalkerAwaits.shouldPollDoorOpen(1_000L, 250L));
    }

    // ---- traversal budget by click distance ---------------------------------------------------------

    /** Adjacent clicks keep the flat cap they were sized for — no behaviour change for the legacy band. */
    @Test
    public void traversalBudget_adjacentClicksKeepTheLegacyCap() {
        assertEquals(2_200L, Rs2WalkerAwaits.traversalBudgetMs(0));
        assertEquals(2_200L, Rs2WalkerAwaits.traversalBudgetMs(1));
        assertEquals(2_200L, Rs2WalkerAwaits.traversalBudgetMs(2));
    }

    /**
     * A ranged click spends its first seconds being WALKED to the door, at one tile per 0.6s. The
     * flat cap expired mid-approach — measured releasedBy=timeout at 11 tiles with the player still
     * walking — which handed the recovery machinery its window and cost a second interaction.
     */
    @Test
    public void traversalBudget_rangedClicksAreGivenTheApproachTime() {
        assertEquals(2_200L + 600L, Rs2WalkerAwaits.traversalBudgetMs(3));
        assertEquals(2_200L + 5 * 600L, Rs2WalkerAwaits.traversalBudgetMs(7));
        assertEquals(2_200L + 9 * 600L, Rs2WalkerAwaits.traversalBudgetMs(11));
    }

    /** The stall release bounds a wedged approach, but a hard ceiling still caps the worst case. */
    @Test
    public void traversalBudget_isCapped() {
        assertEquals(8_000L, Rs2WalkerAwaits.traversalBudgetMs(12));
        assertEquals(8_000L, Rs2WalkerAwaits.traversalBudgetMs(50));
    }
}
