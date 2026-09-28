package net.runelite.client.plugins.loottracker;

import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LootTrackerRecordResetTest
{
	private LootTrackerRecord record(int quantity, int kills)
	{
		return new LootTrackerRecord("Goblin", "", LootRecordType.NPC,
			new LootTrackerItem[]{new LootTrackerItem(995, "Coins", quantity, 3_000_000_000L, 1, false)}, kills);
	}

	@Test
	public void resettingOnlyKillEmptiesAggregate()
	{
		LootTrackerRecord aggregate = record(2, 1);
		aggregate.subtract(record(2, 1));
		assertEquals(0, aggregate.getKills());
		assertEquals(0, aggregate.getItems().length);
	}

	@Test
	public void resettingOneKillPreservesOtherAndHistoricalLoot()
	{
		LootTrackerRecord aggregate = record(20, 10);
		LootTrackerRecord first = record(2, 1);
		LootTrackerRecord second = record(3, 1);
		aggregate.merge(first);
		aggregate.merge(second);
		aggregate.subtract(first);
		assertEquals(11, aggregate.getKills());
		assertEquals(23, aggregate.getItems()[0].getQuantity());
		assertEquals(69_000_000_000L, aggregate.getItems()[0].getTotalGePrice());
		assertEquals(2, first.getItems()[0].getQuantity());
		assertEquals(3, second.getItems()[0].getQuantity());
	}
}
