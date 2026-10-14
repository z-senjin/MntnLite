package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.microbot.util.security.HopBudget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MicrobotConfigTest
{
	@Test
	public void inputYieldingIsDisabledByDefault()
	{
		MicrobotConfig config = new MicrobotConfig() { };

		assertTrue(config.disableInputYielding());
	}

	@Test
	public void hopLimitIsOnByDefaultWithTheEstimatedAllowance()
	{
		MicrobotConfig config = new MicrobotConfig() { };

		assertTrue(config.enableHopLimit());
		assertEquals(HopBudget.DEFAULT_CAPACITY, config.hopLimitCapacity());
		assertEquals(HopBudget.DEFAULT_REFILL_PER_HOUR, config.hopLimitRefillPerHour());
		assertEquals(HopBudget.DEFAULT_RESERVE, config.hopLimitReserve());
	}
}
