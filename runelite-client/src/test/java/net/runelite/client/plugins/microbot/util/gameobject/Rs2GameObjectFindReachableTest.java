package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2GameObjectFindReachableTest {

    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2GameObject> gameObjects;

    private final List<GameObject> scene = new ArrayList<>();
    private final Map<GameObject, String> names = new HashMap<>();
    private final Map<GameObject, String[]> actions = new HashMap<>();
    private final Set<GameObject> reachable = new HashSet<>();
    private final List<GameObject> reachabilityChecked = new ArrayList<>();
    private boolean onClientThread;

    @Before
    public void setUp() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            Callable<?> callable = inv.getArgument(0);
            onClientThread = true;
            try {
                return Optional.ofNullable(callable.call());
            } finally {
                onClientThread = false;
            }
        });

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        player = Mockito.mockStatic(Rs2Player.class);
        player.when(Rs2Player::getRs2WorldPoint).thenReturn(new Rs2WorldPoint(PLAYER));

        gameObjects = Mockito.mockStatic(Rs2GameObject.class, Mockito.CALLS_REAL_METHODS);
        gameObjects.when(() -> Rs2GameObject.getObjectIdsByName(anyString())).thenReturn(Collections.emptyList());
        gameObjects.when(() -> Rs2GameObject.getCompositionName(any(TileObject.class)))
                .thenAnswer(inv -> Optional.ofNullable(names.get(inv.<GameObject>getArgument(0))));
        gameObjects.when(() -> Rs2GameObject.convertToObjectComposition(any(TileObject.class)))
                .thenAnswer(inv -> {
                    ObjectComposition comp = mock(ObjectComposition.class);
                    String[] objActions = actions.getOrDefault(inv.<GameObject>getArgument(0), new String[0]);
                    when(comp.getActions()).thenReturn(objActions);
                    return comp;
                });
        gameObjects.when(() -> Rs2GameObject.getGameObjects(any(), any(WorldPoint.class), anyInt()))
                .thenAnswer(inv -> {
                    assertTrue("scene scan must run inside the client-thread call", onClientThread);
                    Predicate<GameObject> predicate = inv.getArgument(0);
                    return scene.stream().filter(predicate).collect(Collectors.toList());
                });
        gameObjects.when(() -> Rs2GameObject.isReachable(any(GameObject.class)))
                .thenAnswer(inv -> {
                    assertTrue("reachability must run inside the client-thread call", onClientThread);
                    GameObject o = inv.getArgument(0);
                    reachabilityChecked.add(o);
                    return reachable.contains(o);
                });
    }

    @After
    public void tearDown() {
        gameObjects.close();
        player.close();
        microbot.close();
    }

    private GameObject object(String name, int dx, int dy, boolean isReachable, String... objActions) {
        GameObject o = mock(GameObject.class);
        when(o.getWorldLocation()).thenReturn(PLAYER.dx(dx).dy(dy));
        names.put(o, name);
        actions.put(o, objActions);
        if (isReachable) {
            reachable.add(o);
        }
        scene.add(o);
        return o;
    }

    @Test
    public void reachabilityIsNeverCheckedForObjectsThatDoNotMatchTheName() {
        for (int i = 0; i < 50; i++) {
            object("Rocks", 1 + (i % 5), i / 5, true);
        }
        GameObject ladder = object("Ladder", 8, 8, true);

        GameObject found = Rs2GameObject.findReachableObject("Ladder", true, 20, PLAYER);

        assertSame(ladder, found);
        assertEquals(Collections.singletonList(ladder), reachabilityChecked);
    }

    @Test
    public void reachabilityIsNeverCheckedForObjectsWithoutTheRequestedAction() {
        object("Bank booth", 1, 0, true, "Inspect");
        object("Bank booth", 2, 0, true, "Inspect");
        GameObject usable = object("Bank booth", 3, 0, true, "Bank", "Collect");

        GameObject found = Rs2GameObject.findReachableObject("Bank booth", true, 20, PLAYER, true, "Bank");

        assertSame(usable, found);
        assertEquals(Collections.singletonList(usable), reachabilityChecked);
    }

    @Test
    public void checksNearestFirstAndStopsAtTheFirstReachableMatch() {
        GameObject far = object("Rocks", 9, 0, true);
        GameObject nearUnreachable = object("Rocks", 1, 0, false);
        GameObject middle = object("Rocks", 4, 0, true);
        GameObject farther = object("Rocks", 12, 0, true);

        GameObject found = Rs2GameObject.findReachableObject("Rocks", true, 20, PLAYER);

        assertSame(middle, found);
        assertEquals(Arrays.asList(nearUnreachable, middle), reachabilityChecked);
    }

    @Test
    public void returnsNullWithoutCheckingReachabilityWhenNothingMatches() {
        object("Rocks", 1, 0, true);
        object("Tree", 2, 0, true);

        assertNull(Rs2GameObject.findReachableObject("Ladder", true, 20, PLAYER));
        assertEquals(Collections.emptyList(), reachabilityChecked);
    }

    @Test
    public void returnsNullWhenNoMatchIsReachable() {
        object("Rocks", 1, 0, false);
        object("Rocks", 2, 0, false);

        assertNull(Rs2GameObject.findReachableObject("Rocks", true, 20, PLAYER));
        assertEquals(2, reachabilityChecked.size());
    }

    @Test
    public void tiesKeepTheSceneOrderLikeTheOldMinimumSearch() {
        GameObject first = object("Rocks", 2, 0, true);
        object("Rocks", 0, 2, true);
        object("Rocks", -2, -2, true);

        assertSame(first, Rs2GameObject.findReachableObject("Rocks", true, 20, PLAYER));
        assertEquals(Collections.singletonList(first), reachabilityChecked);
    }

    @Test
    public void partialNameMatchStillApplies() {
        object("Tree", 1, 0, true);
        GameObject oak = object("Oak tree", 3, 0, true);
        object("Oak tree", 5, 0, true);

        assertSame(oak, Rs2GameObject.findReachableObject("oak", false, 20, PLAYER));
    }

    @Test
    public void nearestReachableHelperUsesChebyshevDistanceFromTheGivenPoint() {
        WorldPoint near = PLAYER.dx(3).dy(3);
        WorldPoint far = PLAYER.dx(4);
        List<WorldPoint> candidates = Arrays.asList(far, near);
        List<WorldPoint> checked = new ArrayList<>();

        WorldPoint found = Rs2GameObject.findNearestReachable(candidates, p -> p, PLAYER, p -> {
            checked.add(p);
            return true;
        });

        assertSame(near, found);
        assertEquals(Collections.singletonList(near), checked);
    }
}
