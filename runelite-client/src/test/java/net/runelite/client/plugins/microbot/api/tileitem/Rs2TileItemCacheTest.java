package net.runelite.client.plugins.microbot.api.tileitem;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class Rs2TileItemCacheTest
{
	private static final int WORLD_VIEW_ID = 4242;

	private final AtomicBoolean onClientThread = new AtomicBoolean();
	private final AtomicInteger tick = new AtomicInteger(5);

	private Client client;
	private ClientThread clientThread;
	private Scene scene;
	private TileItem coins;
	private TileItem bones;
	private Rs2TileItemCache cache;

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

		coins = mock(TileItem.class);
		bones = mock(TileItem.class);
		Tile tile = mock(Tile.class);
		when(tile.getGroundItems()).thenAnswer(invocation -> guarded(Arrays.asList(coins, null, bones)));

		scene = mock(Scene.class);
		Tile[][][] tiles = new Tile[1][1][2];
		tiles[0][0][0] = tile;
		when(scene.getTiles()).thenAnswer(invocation -> guarded(tiles));

		WorldView worldView = mock(WorldView.class);
		when(worldView.getScene()).thenAnswer(invocation -> guarded(scene));
		when(worldView.getPlane()).thenAnswer(invocation -> guarded(0));

		Player player = mock(Player.class);
		when(client.getTickCount()).thenAnswer(invocation -> guarded(tick.get()));
		when(client.getLocalPlayer()).thenAnswer(invocation -> guarded(player));
		when(client.getWorldView(WORLD_VIEW_ID)).thenAnswer(invocation -> guarded(worldView));

		Microbot.getWorldViewIds().add(WORLD_VIEW_ID);
		cache = new Rs2TileItemCache(client, clientThread);
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
		List<Rs2TileItemModel> items = cache.getStream().collect(Collectors.toList());

		assertEquals(2, items.size());
		List<TileItem> tileItems = items.stream()
			.map(Rs2TileItemModel::getTileItem)
			.collect(Collectors.toList());
		assertSame(coins, tileItems.get(0));
		assertSame(bones, tileItems.get(1));
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
