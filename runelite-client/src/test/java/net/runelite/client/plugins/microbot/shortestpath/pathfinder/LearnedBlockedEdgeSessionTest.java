package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Learned blocked edges are SESSION-ONLY by policy (2026-08-07): the observing session blocks the
 * edge immediately — it watched the failure happen, and anything less loops the walker into the same
 * obstacle — but nothing is persisted and nothing is loaded. The hand-curated blocked_edges.tsv is
 * the sole cross-session authority. This replaces the two-strike persistent store, whose probation
 * machinery existed to manage its own poisonings and whose default file leaked developer state into
 * every test that built a config.
 */
public class LearnedBlockedEdgeSessionTest {

    private static final WorldPoint FROM = new WorldPoint(3012, 3204, 0);
    private static final WorldPoint TO = new WorldPoint(3011, 3204, 0);

    private static SplitFlagMap collisionMap;

    @BeforeClass
    public static void loadMap() {
        collisionMap = SplitFlagMap.fromResources();
    }

    private static PathfinderConfig newConfig() {
        return new PathfinderConfig(collisionMap, new HashMap<>(), Collections.emptyList(), null, null);
    }

    @Test
    public void firstObservationBlocksTheSession() {
        PathfinderConfig config = newConfig();
        assertTrue("first observation must block this session",
                config.learnBlockedEdge(FROM, TO, "wrong-traversal"));
        assertFalse("repeat in the same session is already blocked",
                config.learnBlockedEdge(FROM, TO, "wrong-traversal"));
    }

    /** Directionality: a one-way failure must not condemn the reverse crossing. */
    @Test
    public void onlyTheAttemptedDirectionIsBlocked() {
        PathfinderConfig config = newConfig();
        assertTrue(config.learnBlockedEdge(FROM, TO, "wrong-traversal"));
        assertTrue("the reverse direction is a separate observation",
                config.learnBlockedEdge(TO, FROM, "wrong-traversal"));
    }

    /** The whole policy: nothing learned in one session exists in the next. */
    @Test
    public void nothingSurvivesIntoAFreshConfig() {
        PathfinderConfig first = newConfig();
        assertTrue(first.learnBlockedEdge(FROM, TO, "wrong-traversal"));

        PathfinderConfig restarted = newConfig();
        assertTrue("a fresh session must not inherit the block",
                restarted.learnBlockedEdge(FROM, TO, "wrong-traversal"));
    }

    @Test
    public void nullEndpointsAreRejected() {
        PathfinderConfig config = newConfig();
        assertFalse(config.learnBlockedEdge(null, TO, "x"));
        assertFalse(config.learnBlockedEdge(FROM, null, "x"));
    }
}
