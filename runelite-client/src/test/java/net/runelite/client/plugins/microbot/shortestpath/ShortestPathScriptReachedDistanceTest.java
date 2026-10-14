package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ShortestPathScriptReachedDistanceTest
{
	private static final WorldPoint TARGET = new WorldPoint(3210, 3424, 0);

	@Test
	public void bankedManualWalkUsesConfiguredFinishDistance() throws Exception
	{
		ShortestPathConfig config = mock(ShortestPathConfig.class);
		when(config.walkWithBankedTransports()).thenReturn(true);
		when(config.reachedDistance()).thenReturn(3);
		ShortestPathScript script = scriptWith(config);

		try (MockedStatic<Rs2Walker> walker = Mockito.mockStatic(Rs2Walker.class))
		{
			walker.when(() -> Rs2Walker.walkWithBankedTransportsAndState(eq(TARGET), anyInt(), anyBoolean()))
				.thenReturn(WalkerState.ARRIVED);

			assertEquals(WalkerState.ARRIVED, script.executeWalk(TARGET));

			walker.verify(() -> Rs2Walker.walkWithBankedTransportsAndState(TARGET, 3, false));
		}
	}

	@Test
	public void plainManualWalkStillUsesTheWalkerDefaultDistance() throws Exception
	{
		ShortestPathConfig config = mock(ShortestPathConfig.class);
		when(config.walkWithBankedTransports()).thenReturn(false);
		ShortestPathScript script = scriptWith(config);

		try (MockedStatic<Rs2Walker> walker = Mockito.mockStatic(Rs2Walker.class))
		{
			walker.when(() -> Rs2Walker.walkWithState(TARGET)).thenReturn(WalkerState.MOVING);

			assertEquals(WalkerState.MOVING, script.executeWalk(TARGET));

			walker.verify(() -> Rs2Walker.walkWithState(TARGET));
			walker.verify(() -> Rs2Walker.walkWithBankedTransportsAndState(Mockito.any(), anyInt(), anyBoolean()),
				Mockito.never());
		}
	}

	private static ShortestPathScript scriptWith(ShortestPathConfig config) throws Exception
	{
		ShortestPathScript script = new ShortestPathScript(ignored -> {});
		Field field = ShortestPathScript.class.getDeclaredField("config");
		field.setAccessible(true);
		field.set(script, config);
		return script;
	}
}
