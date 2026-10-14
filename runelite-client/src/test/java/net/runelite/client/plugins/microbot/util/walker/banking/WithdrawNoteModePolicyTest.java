package net.runelite.client.plugins.microbot.util.walker.banking;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WithdrawNoteModePolicyTest
{
	private static final IntPredicate STACKABLE = itemId -> itemId == ItemID.AIRRUNE
		|| itemId == ItemID.LAWRUNE || itemId == ItemID.COINS || itemId == ItemID.POH_TABLET_VARROCKTELEPORT;

	@Test
	public void runesTabletsAndCoinsKeepTheUsersNoteMode()
	{
		boolean required = WithdrawNoteModePolicy.requiresItemMode(
			List.of(ItemID.AIRRUNE, ItemID.LAWRUNE, ItemID.COINS, ItemID.POH_TABLET_VARROCKTELEPORT), STACKABLE);

		assertFalse(required);
		assertFalse(WithdrawNoteModePolicy.shouldSwitchToItemMode(required, true));
	}

	@Test
	public void nonStackableProviderSwitchesOnlyWhenNoted()
	{
		boolean required = WithdrawNoteModePolicy.requiresItemMode(
			List.of(ItemID.LAWRUNE, ItemID.RING_OF_DUELING_8), STACKABLE);

		assertTrue(required);
		assertTrue(WithdrawNoteModePolicy.shouldSwitchToItemMode(required, true));
		assertFalse(WithdrawNoteModePolicy.shouldSwitchToItemMode(required, false));
	}

	@Test
	public void emptyOrMissingWithdrawalsNeverSwitch()
	{
		assertFalse(WithdrawNoteModePolicy.requiresItemMode(List.of(), STACKABLE));
		assertFalse(WithdrawNoteModePolicy.requiresItemMode(null, STACKABLE));
	}

	@Test
	public void providerIdsIncludeTheWithdrawnBankRow()
	{
		int savedId = 1001;
		int bankRowId = 1002;

		assertEquals(Set.of(savedId, bankRowId), WithdrawNoteModePolicy.providerItemIds(
			savedId, Map.of(savedId, Set.of(savedId, bankRowId))));
		assertEquals(Set.of(ItemID.DRAMEN_STAFF), WithdrawNoteModePolicy.providerItemIds(
			ItemID.DRAMEN_STAFF, Map.of()));
		assertEquals(Set.of(ItemID.DRAMEN_STAFF), WithdrawNoteModePolicy.providerItemIds(
			ItemID.DRAMEN_STAFF, null));
	}
}
