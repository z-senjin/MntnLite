package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.banking.Rs2WalkerBankingPlanner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WalkerBankedDirectWalkStateTest
{
	private static final WorldPoint PLAYER = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint TARGET = new WorldPoint(3400, 3400, 0);
	private static final String DIRECT_WALK_FAILED = "rs2walker:walkWithBankedTransports:direct-walk-failed";

	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2Player> player;
	private MockedStatic<Rs2Tile> tile;
	private MockedStatic<Rs2PathApi> pathApi;
	private MockedStatic<Rs2WalkerBankingPlanner> planner;
	private ShortestPathConfig originalConfig;
	private Client client;

	@Before
	public void setUp()
	{
		originalConfig = Rs2Walker.config;
		Rs2Walker.config = mock(ShortestPathConfig.class);
		Rs2Walker.routeStateForTesting().lastRouteClearReason = "";

		client = mock(Client.class);
		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);

		player = Mockito.mockStatic(Rs2Player.class);
		tile = Mockito.mockStatic(Rs2Tile.class);
		tile.when(() -> Rs2Tile.getReachableTilesFromTile(any(WorldPoint.class), anyInt())).thenReturn(new HashMap<>());

		Rs2ActiveRouteStatus status = mock(Rs2ActiveRouteStatus.class);
		pathApi = Mockito.mockStatic(Rs2PathApi.class);
		pathApi.when(Rs2PathApi::getActiveRouteStatus).thenReturn(status);

		TransportRouteAnalysis comparison = mock(TransportRouteAnalysis.class);
		when(comparison.getDirectDistance()).thenReturn(250);
		when(comparison.getTransportEdgesToBank()).thenReturn(List.of());
		planner = Mockito.mockStatic(Rs2WalkerBankingPlanner.class);
		planner.when(() -> Rs2WalkerBankingPlanner.compareRoutes(isNull(), eq(TARGET), isNull())).thenReturn(comparison);
		planner.when(() -> Rs2WalkerBankingPlanner.getRequiredTransportEdgesFromBank(any())).thenReturn(List.of());
		planner.when(() -> Rs2WalkerBankingPlanner.getMissingTransportEdges(anyList())).thenReturn(List.of());
		planner.when(() -> Rs2WalkerBankingPlanner.getMissingTransportEdgeLoadout(anyList(), anyList()))
			.thenReturn(Rs2TransportLoadout.empty());
	}

	@After
	public void tearDown()
	{
		planner.close();
		pathApi.close();
		tile.close();
		player.close();
		microbot.close();
		Rs2Walker.config = originalConfig;
	}

	@Test
	public void transientMovingFromDirectWalkKeepsTheRoute()
	{
		player.when(Rs2Player::getWorldLocation).thenReturn(PLAYER, (WorldPoint) null);

		WalkerState state = Rs2Walker.walkWithBankedTransportsAndState(TARGET, 5, false);

		assertEquals(WalkerState.MOVING, state);
		assertNotEquals(DIRECT_WALK_FAILED, Rs2Walker.getLastRouteClearReason());
		pathApi.verify(Rs2PathApi::cancelAndClearActiveRoute, Mockito.never());
	}

	@Test
	public void terminalExitFromDirectWalkClearsTheRoute()
	{
		player.when(Rs2Player::getWorldLocation).thenReturn(PLAYER);
		when(client.isClientThread()).thenReturn(false, true);

		WalkerState state = Rs2Walker.walkWithBankedTransportsAndState(TARGET, 5, false);

		assertEquals(WalkerState.EXIT, state);
		assertEquals(DIRECT_WALK_FAILED, Rs2Walker.getLastRouteClearReason());
		pathApi.verify(Rs2PathApi::cancelAndClearActiveRoute);
	}
}
