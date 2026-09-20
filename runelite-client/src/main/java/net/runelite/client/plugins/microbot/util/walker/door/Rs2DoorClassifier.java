package net.runelite.client.plugins.microbot.util.walker.door;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.runelite.api.ObjectComposition;

/**
 * Stateless classification of door/gate objects: whether a name looks door-like, which menu
 * action to use to walk through (never a close/shut), and whether a composition is in the
 * open state (only close/shut actions). Extracted from {@code Rs2Walker} — pure string/action
 * heuristics over names and {@link ObjectComposition}, no walker state.
 */
public final class Rs2DoorClassifier {

    private static final String[] DOOR_LIKE_NAME_FRAGMENTS = {
            "door", "gate", "barrier", "stile", "portcullis", "archway", "cattlegate", "fence", "curtain"
    };

    /** {@code fence} must be whole-word — substring matches {@code defence} ("fence" inside) otherwise. */
    private static final Pattern FENCE_AS_WORD = Pattern.compile("\\bfence\\b", Pattern.CASE_INSENSITIVE);

    /** Lower index = higher priority when multiple actions match (prefix, ASCII lower). */
    private static final List<String> DOOR_ACTION_PRIORITY = List.of(
            "pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass", "enter",
            "push", "climb-over", "climb-through", "squeeze-through", "cross", "force", "exit"
    );

    /**
     * Actions that carry the player ACROSS the obstacle rather than opening an edge in it.
     *
     * <p>The distinction decides who owns the crossing. The door cascade's completion contract is
     * "the blocked edge became passable" — it clicks, then waits for the edge to open. A stile never
     * opens: you climb over it and end up on the far side, so that wait can only ever time out.
     *
     * <p>Measured near Ardougne: a Stile at (2637,3350) with action Climb-over classified as a door
     * on its name, was taken by the door cascade, logged {@code door_edge_post_unresolved}, and cost
     * twenty seconds of refused clicks, a recovery wander and a replan before the transport handler
     * finally crossed it in one action. See {@code walker-transport-doors}: moves-you obstacles are
     * their own class and belong to the transport handler.
     */
    private static final List<String> MOVES_YOU_ACTIONS = List.of(
            "climb-over", "climb-through", "squeeze-through", "cross"
    );

    private Rs2DoorClassifier() {
    }

    /**
     * Whether {@code action} moves the player across the obstacle instead of opening it.
     *
     * @see #MOVES_YOU_ACTIONS
     */
    public static boolean isMovesYouAction(String action) {
        if (action == null) {
            return false;
        }
        String al = action.toLowerCase(Locale.ROOT).trim();
        for (String movesYou : MOVES_YOU_ACTIONS) {
            if (al.startsWith(movesYou)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNullOrPlaceholderObjectName(String name) {
        if (name == null) {
            return true;
        }
        String t = name.trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t);
    }

    public static int doorActionPriorityIndex(String action) {
        if (action == null) {
            return Integer.MAX_VALUE;
        }
        String al = action.toLowerCase(Locale.ROOT);
        for (int i = 0; i < DOOR_ACTION_PRIORITY.size(); i++) {
            if (al.startsWith(DOOR_ACTION_PRIORITY.get(i))) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Walker must never choose menu actions that close an open door/gate. */
    public static boolean isDoorCloseOrShutAction(String action) {
        if (action == null) {
            return false;
        }
        String al = action.toLowerCase(Locale.ROOT).trim();
        return al.startsWith("close") || al.startsWith("shut");
    }

    /** True when every non-null action is Close/Shut (typical open-door state). */
    public static boolean doorCompositionSpecifiesOnlyCloseOrShut(ObjectComposition comp) {
        if (comp == null || comp.getActions() == null) {
            return false;
        }
        boolean sawNonNull = false;
        for (String a : comp.getActions()) {
            if (a == null) {
                continue;
            }
            sawNonNull = true;
            if (!isDoorCloseOrShutAction(a)) {
                return false;
            }
        }
        return sawNonNull;
    }

    /**
     * Best door action for walking through, excluding close/shut. {@code null} if none
     * (empty defs or only close/shut).
     */
    public static String pickWalkDoorAction(ObjectComposition comp) {
        if (comp == null || comp.getActions() == null) {
            return null;
        }
        return Arrays.stream(comp.getActions())
                .filter(Objects::nonNull)
                .filter(a -> !isDoorCloseOrShutAction(a))
                .min(Comparator.comparingInt(Rs2DoorClassifier::doorActionPriorityIndex))
                .orElse(null);
    }

    public static boolean isDoorLikeGameObjectName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (String f : DOOR_LIKE_NAME_FRAGMENTS) {
            if ("fence".equals(f)) {
                if (FENCE_AS_WORD.matcher(n).find()) {
                    return true;
                }
            } else if (n.contains(f)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Actions whose VERB alone proves traversal — a chest never says Walk-through. Deliberately
     * excludes open/enter/push/force/exit, which scenery shares (Open on a chest, Enter on a cave).
     */
    private static final List<String> TRAVERSAL_PROOF_ACTIONS = List.of(
            "pay-toll", "pick-lock", "walk-through", "go-through", "pass"
    );

    public static boolean isTraversalProofAction(String action) {
        if (action == null) {
            return false;
        }
        String al = action.toLowerCase(Locale.ROOT).trim();
        for (String t : TRAVERSAL_PROOF_ACTIONS) {
            if (al.startsWith(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Route-door classification — the decide table's first column (D3 requirement #3).
     *
     * <p>WALL objects: any walk action proves doorhood — a wall that opens is a door, whatever its
     * name (unchanged semantics).
     *
     * <p>GAME objects: the NAME must prove doorhood, or the ACTION must be traversal-proof. Bare
     * Open/Enter/Push on a non-door name is scenery: the Gift of Peace chest (Stronghold,
     * 2026-08-13) was Open-clicked as a route door en route, costing 7-9s of failed traversal per
     * encounter — and any Open-actioned coffin, cupboard or sarcophagus on a route segment would do
     * the same. Large double gates ARE GameObjects, which is why the rule is name-or-verb rather
     * than a flat name filter.
     */
    public static boolean isRouteDoorObject(boolean wallObject, String name, String walkAction) {
        if (wallObject) {
            return isDoorLikeGameObjectName(name)
                    || (walkAction != null && doorActionPriorityIndex(walkAction) < Integer.MAX_VALUE);
        }
        return isDoorLikeGameObjectName(name) || isTraversalProofAction(walkAction);
    }

    /** Whether a (real, non-impostor) composition exposes one of {@code doorActions}. */
    public static boolean isDoorComposition(ObjectComposition comp, List<String> doorActions) {
        if (comp == null || comp.getImpostorIds() != null || isNullOrPlaceholderObjectName(comp.getName()) || comp.getActions() == null) {
            return false;
        }
        return getDoorAction(comp, doorActions) != null;
    }

    /** The highest-priority matching {@code doorActions} entry the composition exposes, or null. */
    public static String getDoorAction(ObjectComposition comp, List<String> doorActions) {
        if (comp == null || comp.getActions() == null) {
            return null;
        }
        return Arrays.stream(comp.getActions())
                .filter(Objects::nonNull)
                .filter(act -> doorActions.stream().anyMatch(dact -> act.toLowerCase().startsWith(dact.toLowerCase())))
                .min(Comparator.comparing(act -> doorActions.indexOf(doorActions.stream()
                        .filter(dact -> act.toLowerCase().startsWith(dact.toLowerCase()))
                        .findFirst()
                        .orElse(""))))
                .orElse(null);
    }
}
