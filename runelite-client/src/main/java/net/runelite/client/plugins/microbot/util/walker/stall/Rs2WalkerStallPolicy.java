package net.runelite.client.plugins.microbot.util.walker.stall;

import net.runelite.api.widgets.ComponentID;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public final class Rs2WalkerStallPolicy {
    private Rs2WalkerStallPolicy() {
    }

    /**
     * Determines whether stall accounting should be bypassed for the current tick.
     * Bypasses when a leagues teleport is running, a leagues area teleport is still pending within the provided age window,
     * a dialogue is open, or the fairy-ring teleport widget is visible.
     *
     * @param leaguesPendingMaxAgeMs max age in milliseconds for treating a leagues teleport as still pending
     * @return true when stall accounting should be skipped
     */
    public static boolean shouldSkipStallAccounting(long leaguesPendingMaxAgeMs) {
        if (Rs2LeaguesTransport.isTeleportInProgress()) {
            return true;
        }
        if (Rs2LeaguesTransport.isLeaguesAreaTeleportPending(leaguesPendingMaxAgeMs)) {
            return true;
        }
        if (Rs2Dialogue.isInDialogue()) {
            return true;
        }
        return !Rs2Widget.isHidden(ComponentID.FAIRY_RING_TELEPORT_BUTTON);
    }

    /**
     * Whether the pose-based movement flag may be credited as route progress.
     *
     * <p>{@code Rs2Player.isMoving()} compares the pose animation against the idle pose, so it reads
     * TRUE while the player merely TURNS ON THE SPOT. Stall accounting credited that as progress and
     * refreshed the clock, so a player wedged against a wall or a door who kept re-facing it could
     * never be declared stuck — the one state the stall detector exists to catch.
     *
     * <p>Requiring a tile change outright would be worse: a walking step takes ~600ms and the check
     * samples faster than that, so "same tile as last sample" is the normal state of a healthy walk.
     * The question is not whether the tile changed since the last sample but whether it has changed
     * at all RECENTLY — walking changes tile continuously, spinning never does.
     *
     * @param sinceTileChangeMs ms since the player last actually changed tile; negative when unknown,
     *                          which is treated as "cannot disprove movement" and credits the pose
     */
    public static boolean poseCountsAsProgress(boolean poseMoving,
                                               boolean nearPath,
                                               long sinceTileChangeMs,
                                               long tileChangeWindowMs) {
        if (!poseMoving || !nearPath) {
            return false;
        }
        if (sinceTileChangeMs < 0L) {
            return true;
        }
        return sinceTileChangeMs < tileChangeWindowMs;
    }

    /**
     * Computes the stall threshold by multiplying {@code baseMs} by the maximum applicable multiplier.
     * Result uses {@link Math#round(double)}.
     *
     * @param baseMs base stall threshold in milliseconds
     * @param combatMultiplier multiplier applied when {@code inCombat} is true
     * @param animatingMultiplier multiplier applied when {@code animating} is true
     * @param movingMultiplier multiplier applied when {@code moving} is true
     * @param interimMultiplier multiplier applied when {@code hasInterimTarget} is true
     * @param interactingMultiplier multiplier applied when {@code interactingNearPath} is true
     * @param inCombat whether player is currently in combat
     * @param animating whether player is currently animating
     * @param moving whether player is currently moving
     * @param hasInterimTarget whether walker currently tracks an interim target
     * @param interactingNearPath whether interacting with an entity near path progression
     * @return rounded threshold in milliseconds
     */
    public static long computeThresholdMs(long baseMs,
                                          double combatMultiplier,
                                          double animatingMultiplier,
                                          double movingMultiplier,
                                          double interimMultiplier,
                                          double interactingMultiplier,
                                          boolean inCombat,
                                          boolean animating,
                                          boolean moving,
                                          boolean hasInterimTarget,
                                          boolean interactingNearPath) {
        double multiplier = 1.0;
        if (inCombat) {
            multiplier = Math.max(multiplier, combatMultiplier);
        }
        if (animating) {
            multiplier = Math.max(multiplier, animatingMultiplier);
        }
        if (moving) {
            multiplier = Math.max(multiplier, movingMultiplier);
        }
        if (hasInterimTarget) {
            multiplier = Math.max(multiplier, interimMultiplier);
        }
        if (interactingNearPath) {
            multiplier = Math.max(multiplier, interactingMultiplier);
        }
        return Math.round(baseMs * multiplier);
    }
}
