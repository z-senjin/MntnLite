package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.door.model.DoorEdge;

import java.util.Map;

public final class Rs2DoorHandler {
    private Rs2DoorHandler() {
    }

    public static String doorAttemptKey(WorldPoint doorTile, WorldPoint fromWp, WorldPoint toWp) {
        if (fromWp != null && toWp != null) {
            return new DoorEdge(fromWp, toWp).normalizedKey();
        }
        return compactWorldPoint(doorTile) + "|" + compactWorldPoint(fromWp) + "->" + compactWorldPoint(toWp);
    }

    public static boolean shouldThrottleGlobalDoorInteraction(long nextDoorInteractionAllowedAtMs) {
        return System.currentTimeMillis() < nextDoorInteractionAllowedAtMs;
    }

    /**
     * Edge-scoped variant. The full window is anti-hammer for ONE door — re-clicking the same edge
     * before the world has caught up. A DIFFERENT door immediately after a successful open is not
     * hammering, it is chaining, and holding it for the full window serialised every pair of nearby
     * doors. A different edge owes only the cross-edge floor (one game tick): enough that two clicks
     * cannot land inside the same tick, no more.
     *
     * @param fullCooldownMs      the window {@code nextAllowedAtMs} was stamped with
     * @param crossEdgeCooldownMs the floor a different edge still owes
     */
    public static boolean shouldThrottleGlobalDoorInteraction(long nowMs, long nextAllowedAtMs,
                                                              boolean sameEdgeAsLastAttempt,
                                                              long fullCooldownMs, long crossEdgeCooldownMs) {
        if (sameEdgeAsLastAttempt) {
            return nowMs < nextAllowedAtMs;
        }
        return nowMs < nextAllowedAtMs - (fullCooldownMs - crossEdgeCooldownMs);
    }

    public static long markGlobalDoorInteractionCooldown(long cooldownMs) {
        return System.currentTimeMillis() + cooldownMs;
    }

    private static String compactWorldPoint(WorldPoint wp) {
        if (wp == null) {
            return "?";
        }
        return wp.getX() + "," + wp.getY() + ",p" + wp.getPlane();
    }
}
