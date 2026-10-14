package net.runelite.client.plugins.microbot.util.walker.banking;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import static org.junit.Assert.assertEquals;

public class TransportWithdrawalConfirmationTest
{
	private static IntUnaryOperator inventory(Map<Integer, Integer> quantities)
	{
		return itemId -> quantities.getOrDefault(itemId, 0);
	}

	@Test
	public void stackableRunesConfirmOnQuantityNotSlotCount()
	{
		Map<Integer, Integer> inv = new HashMap<>();
		TransportWithdrawalConfirmation confirmation = TransportWithdrawalConfirmation.start(
			ItemID.AIRRUNE, ItemID.AIRRUNE, 3, inventory(inv));

		assertEquals(3, confirmation.getTargetQuantity());
		assertEquals(TransportWithdrawalConfirmation.State.PENDING,
			confirmation.evaluate(inventory(inv), true));

		inv.put(ItemID.AIRRUNE, 3);
		assertEquals(TransportWithdrawalConfirmation.State.CONFIRMED,
			confirmation.evaluate(inventory(inv), true));
	}

	@Test
	public void existingStackRaisesTheTarget()
	{
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(ItemID.LAWRUNE, 50);
		TransportWithdrawalConfirmation confirmation = TransportWithdrawalConfirmation.start(
			ItemID.LAWRUNE, ItemID.LAWRUNE, 2, inventory(inv));

		assertEquals(52, confirmation.getTargetQuantity());
		inv.put(ItemID.LAWRUNE, 51);
		assertEquals(TransportWithdrawalConfirmation.State.PENDING,
			confirmation.evaluate(inventory(inv), true));
		inv.put(ItemID.LAWRUNE, 52);
		assertEquals(TransportWithdrawalConfirmation.State.CONFIRMED,
			confirmation.evaluate(inventory(inv), true));
	}

	@Test
	public void withdrawnBankRowIdCountsWhenSavedIdDrifted()
	{
		int savedId = 1001;
		int bankRowId = 1002;
		Map<Integer, Integer> inv = new HashMap<>();
		TransportWithdrawalConfirmation confirmation = TransportWithdrawalConfirmation.start(
			savedId, bankRowId, 1, inventory(inv));

		assertEquals(Set.of(savedId, bankRowId), confirmation.getItemIds());
		inv.put(bankRowId, 1);
		assertEquals(TransportWithdrawalConfirmation.State.CONFIRMED,
			confirmation.evaluate(inventory(inv), true));
	}

	@Test
	public void notedCopyDoesNotConfirmUsableItem()
	{
		Map<Integer, Integer> inv = new HashMap<>();
		TransportWithdrawalConfirmation confirmation = TransportWithdrawalConfirmation.start(
			ItemID.RING_OF_DUELING_8, ItemID.RING_OF_DUELING_8, 1, inventory(inv));

		inv.put(ItemID.Cert.RING_OF_DUELING_8, 1);
		assertEquals(TransportWithdrawalConfirmation.State.PENDING,
			confirmation.evaluate(inventory(inv), true));
	}

	@Test
	public void bankClosedBeforeConfirmationAborts()
	{
		Map<Integer, Integer> inv = new HashMap<>();
		TransportWithdrawalConfirmation confirmation = TransportWithdrawalConfirmation.start(
			ItemID.POH_TABLET_VARROCKTELEPORT, -1, 1, inventory(inv));

		assertEquals(Set.of(ItemID.POH_TABLET_VARROCKTELEPORT), confirmation.getItemIds());
		assertEquals(TransportWithdrawalConfirmation.State.ABORTED,
			confirmation.evaluate(inventory(inv), false));
		inv.put(ItemID.POH_TABLET_VARROCKTELEPORT, 1);
		assertEquals(TransportWithdrawalConfirmation.State.CONFIRMED,
			confirmation.evaluate(inventory(inv), false));
	}

	@Test
	public void targetSaturatesInsteadOfOverflowing()
	{
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(ItemID.COINS, Integer.MAX_VALUE - 1);
		TransportWithdrawalConfirmation confirmation = TransportWithdrawalConfirmation.start(
			ItemID.COINS, ItemID.COINS, 10, inventory(inv));

		assertEquals(Integer.MAX_VALUE, confirmation.getTargetQuantity());
		assertEquals(TransportWithdrawalConfirmation.State.PENDING,
			confirmation.evaluate(inventory(inv), true));
	}
}
