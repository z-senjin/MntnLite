package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import net.runelite.client.plugins.microbot.util.walker.geometry.WalkerPathGeometry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression for the "walker deviates wide / traps itself near Varrock West Bank" bug.
 *
 * <p>The click layer had overshot onto {@code (3176,3428)} — a tile that was <em>not on the raw
 * route in the captured incident</em> — because route selection returned null on a stale anchor and
 * the caller fell through to clamping a far smoothed waypoint to a Euclidean radius. The game then
 * pathed to that off-route tile its own way, producing the wide deviation and backtracking.
 *
 * <p>The invariant this pins is therefore <strong>"the click target is on the raw route"</strong>,
 * not "the click target is in line of sight". A minimap click is resolved by the game's own
 * pathing, so line of sight is irrelevant to walking — a player clicks past a corner and lets the
 * server route them. An earlier revision clamped clicks to line of sight; that made the walker
 * advance corner-to-corner and was reverted. Do not reintroduce it: if a target sits on the planned
 * route, it does not matter which way the server walks there, because we arrive on the route.
 */
public class RouteClickTargetRegressionTest {

    private static SplitFlagMap collisionMap;

    private static final WorldPoint START = new WorldPoint(3183, 3435, 0);
    private static final WorldPoint GOAL = new WorldPoint(3173, 3399, 0);
    private static List<WorldPoint> sharedRawPath;

    @BeforeClass
    public static void load() {
        collisionMap = SplitFlagMap.fromResources();
        // Computed once: each Pathfinder.run() reloads all transports and, via
        // CollisionMap.getCachedRegionId, calls Rs2Player.getWorldLocation(), which has no client
        // thread under test and blocks for its full timeout.
        sharedRawPath = computeRawPathReachingGoal(START, GOAL);
    }

    /**
     * Computes the route, and refuses to report a starved run as a route regression.
     *
     * <p>{@code calculationCutoffMillis} is a NO-PROGRESS wall-clock guard. Under CPU contention —
     * a full-suite run, or a client running alongside the build — the search can be starved into
     * returning a best-effort PARTIAL path, and a partial path wanders through tiles the assertions
     * below require to be absent. That failure looks exactly like the regression this class exists
     * to catch, and it has already been misread as one: a red run here sent an investigation off
     * hunting a route-data change that did not exist.
     *
     * <p>So: a generous cutoff, one retry, and if the path still does not reach the goal, fail as
     * explicitly inconclusive rather than as a route change.
     */
    private static List<WorldPoint> computeRawPathReachingGoal(WorldPoint start, WorldPoint goal) {
        List<WorldPoint> path = computeRawPath(start, goal);
        if (reachesGoal(path, goal)) {
            return path;
        }
        path = computeRawPath(start, goal);
        if (reachesGoal(path, goal)) {
            return path;
        }
        throw new AssertionError("pathfinder starved — INCONCLUSIVE, not a route regression: the "
            + "search did not reach " + goal + " within its no-progress cutoff on two attempts "
            + "(got " + path.size() + " tiles, ending at "
            + (path.isEmpty() ? "nothing" : path.get(path.size() - 1)) + "). Re-run this test on an "
            + "idle machine before treating it as a routing change.");
    }

    /** The pathfinder returns a best-effort partial path when starved, so check the endpoint. */
    private static boolean reachesGoal(List<WorldPoint> path, WorldPoint goal) {
        return !path.isEmpty() && path.get(path.size() - 1).equals(goal);
    }

    private static List<WorldPoint> computeRawPath(WorldPoint start, WorldPoint goal) {
        HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
        PathfinderConfig config = new PathfinderConfig(collisionMap, transports,
                Collections.emptyList(), null, null);
        try {
            java.lang.reflect.Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
            f.setAccessible(true);
            // 30s of NO PROGRESS, not 30s of runtime: the guard resets on every heuristic
            // improvement, so this costs nothing on a healthy run and only buys headroom on a
            // contended one.
            f.setLong(config, 30_000);
            for (Map.Entry<WorldPoint, Set<Transport>> e : transports.entrySet()) {
                if (e.getKey() == null) continue;
                config.getTransports().put(e.getKey(), e.getValue());
                config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(e.getKey()), e.getValue());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Pathfinder pf = new Pathfinder(config, start, goal);
        pf.run();
        return pf.getPath();
    }

    @Test
    public void primaryClickSelectionStaysOnComputedRawRoute() {
        WorldPoint selected = WalkerPathGeometry.findFurthestRawPathPointMatching(
                sharedRawPath, START, 10, 0, point -> true,
                sharedRawPath.size(), () -> 0);

        assertNotNull("the real route should offer a primary minimap target", selected);
        assertTrue("primary click selection must return a tile on the current randomized raw route",
                sharedRawPath.contains(selected));
    }

    /**
     * Route selection must be able to reach well past a corner in a single click, the way a player
     * would. A line-of-sight clamp reduced the first click on this route to ~3 tiles (just the
     * corner); the raw route offers far more forward reach than that within minimap range.
     */
    @Test
    public void rawRouteOffersLongForwardReachWithinMinimapRange() {
        int minimapReach = 10;
        int reachable = 0;
        for (WorldPoint p : sharedRawPath) {
            int dx = p.getX() - START.getX();
            int dy = p.getY() - START.getY();
            if (dx * dx + dy * dy <= minimapReach * minimapReach) {
                reachable++;
            }
        }
        assertTrue("the route should offer several on-route candidates within minimap reach, "
                        + "so a click is not artificially shortened to the nearest corner; got " + reachable,
                reachable >= 6);
    }
}
