package net.runelite.client.plugins.microbot.api.tileobject;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class Rs2TileObjectCacheTest
{
	private static final int WORLD_VIEW_ID = 4243;
	private static final long OWNED_HERE_HASH = 101L;
	private static final long OWNED_ELSEWHERE_HASH = 102L;
	private static final long WALL_HASH = 103L;

	private final AtomicBoolean onClientThread = new AtomicBoolean();
	private final AtomicInteger tick = new AtomicInteger(5);

	private Client client;
	private ClientThread clientThread;
	private Scene scene;
	private Rs2TileObjectCache cache;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
		{
			Callable<?> callable = invocation.getArgument(0);
			onClientThread.set(true);
			try
			{
				return Optional.ofNullable(callable.call());
			}
			finally
			{
				onClientThread.set(false);
			}
		});

		Point tileLocation = new Point(10, 20);
		GameObject ownedHere = mock(GameObject.class);
		when(ownedHere.getSceneMinLocation()).thenAnswer(invocation -> guarded(tileLocation));
		GameObject ownedElsewhere = mock(GameObject.class);
		when(ownedElsewhere.getSceneMinLocation()).thenAnswer(invocation -> guarded(new Point(9, 20)));
		WallObject wall = mock(WallObject.class);
		when(ownedHere.getHash()).thenReturn(OWNED_HERE_HASH);
		when(ownedElsewhere.getHash()).thenReturn(OWNED_ELSEWHERE_HASH);
		when(wall.getHash()).thenReturn(WALL_HASH);

		Tile tile = mock(Tile.class);
		when(tile.getSceneLocation()).thenAnswer(invocation -> guarded(tileLocation));
		when(tile.getGameObjects()).thenAnswer(invocation -> guarded(new GameObject[]{ownedHere, null, ownedElsewhere}));
		when(tile.getWallObject()).thenAnswer(invocation -> guarded(wall));

		scene = mock(Scene.class);
		Tile[][][] tiles = new Tile[1][1][2];
		tiles[0][0][0] = tile;
		when(scene.getTiles()).thenAnswer(invocation -> guarded(tiles));

		WorldView worldView = mock(WorldView.class);
		when(worldView.getId()).thenAnswer(invocation -> guarded(WORLD_VIEW_ID));
		when(worldView.getScene()).thenAnswer(invocation -> guarded(scene));
		when(worldView.getPlane()).thenAnswer(invocation -> guarded(0));

		Player player = mock(Player.class);
		when(client.getTickCount()).thenAnswer(invocation -> guarded(tick.get()));
		when(client.getLocalPlayer()).thenAnswer(invocation -> guarded(player));
		when(client.getWorldView(WORLD_VIEW_ID)).thenAnswer(invocation -> guarded(worldView));

		Microbot.getWorldViewIds().add(WORLD_VIEW_ID);
		cache = new Rs2TileObjectCache(client, clientThread);
	}

	@After
	public void tearDown()
	{
		Microbot.getWorldViewIds().remove(WORLD_VIEW_ID);
	}

	private <T> T guarded(T value)
	{
		assertTrue("client state must only be read inside the client-thread task", onClientThread.get());
		return value;
	}

	@Test
	public void offClientThreadRefreshReadsSceneOnlyInsideClientThreadTask()
	{
		List<Rs2TileObjectModel> objects = cache.getStream().collect(Collectors.toList());

		assertEquals(List.of(TileObjectType.GAME, TileObjectType.WALL), objects.stream()
			.map(Rs2TileObjectModel::getTileObjectType)
			.collect(Collectors.toList()));
		assertEquals(List.of(OWNED_HERE_HASH, WALL_HASH), objects.stream()
			.map(Rs2TileObjectModel::getHash)
			.collect(Collectors.toList()));
		verify(clientThread, times(1)).runOnClientThreadOptional(any());
	}

	@Test
	public void sameTickReusesSnapshotAndNextTickRescans()
	{
		assertEquals(2, cache.getStream().count());
		assertEquals(2, cache.getStream().count());
		verify(scene, times(1)).getTiles();

		tick.incrementAndGet();
		assertEquals(2, cache.getStream().count());
		verify(scene, times(2)).getTiles();
	}

	@Test
	public void clientThreadFailureReturnsEmptyStreamWithoutReadingClient()
	{
		doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any());

		assertEquals(0, cache.getStream().count());
		verifyNoInteractions(client);
	}
}
