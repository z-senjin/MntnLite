package net.runelite.client.plugins.microbot.util.walker.stall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The walker's stall detector, and specifically what it is allowed to call "movement".
 */
public class Rs2WalkerStallPolicyTest
{
	private static final long WINDOW = 2_500L;

	/**
	 * The bug this exists for. {@code Rs2Player.isMoving()} compares the pose animation against the
	 * idle pose, so it reads TRUE while the player merely turns on the spot. Crediting that as
	 * progress refreshed the stall clock, so a player wedged against a wall or a door who kept
	 * re-facing it could never be declared stuck — the exact state the detector exists to catch.
	 */
	@Test
	public void turningOnTheSpotIsNotProgress()
	{
		assertFalse("pose says moving, but no tile has changed in ten seconds",
			Rs2WalkerStallPolicy.poseCountsAsProgress(true, true, 10_000L, WINDOW));
	}

	/**
	 * And the reason it cannot simply require a tile change: a walking step is ~600ms while the check
	 * samples faster, so "same tile as the last sample" is the normal state of a healthy walk.
	 */
	@Test
	public void walkingBetweenTilesIsStillProgress()
	{
		assertTrue("mid-step, tile changed 400ms ago",
			Rs2WalkerStallPolicy.poseCountsAsProgress(true, true, 400L, WINDOW));
		assertTrue("just inside the window",
			Rs2WalkerStallPolicy.poseCountsAsProgress(true, true, WINDOW - 1, WINDOW));
		assertFalse("just outside it",
			Rs2WalkerStallPolicy.poseCountsAsProgress(true, true, WINDOW, WINDOW));
	}

	/** An unknown tile-change time must not manufacture a stall. */
	@Test
	public void unknownTileChangeTimeCreditsThePose()
	{
		assertTrue(Rs2WalkerStallPolicy.poseCountsAsProgress(true, true, -1L, WINDOW));
	}

	/** Both original conditions still gate it: off-path movement was never route progress. */
	@Test
	public void poseAndNearPathAreStillBothRequired()
	{
		assertFalse(Rs2WalkerStallPolicy.poseCountsAsProgress(false, true, 100L, WINDOW));
		assertFalse(Rs2WalkerStallPolicy.poseCountsAsProgress(true, false, 100L, WINDOW));
	}

	/** The threshold takes the largest applicable multiplier, not their product. */
	@Test
	public void thresholdUsesTheLargestMultiplierNotTheProduct()
	{
		assertEquals(24_000L, Rs2WalkerStallPolicy.computeThresholdMs(
			12_000L, 2.0, 1.5, 1.35, 1.75, 1.5,
			true, true, true, true, true));
		assertEquals(12_000L, Rs2WalkerStallPolicy.computeThresholdMs(
			12_000L, 2.0, 1.5, 1.35, 1.75, 1.5,
			false, false, false, false, false));
		assertEquals("an interim waypoint alone", 21_000L, Rs2WalkerStallPolicy.computeThresholdMs(
			12_000L, 2.0, 1.5, 1.35, 1.75, 1.5,
			false, false, false, true, false));
	}
}
