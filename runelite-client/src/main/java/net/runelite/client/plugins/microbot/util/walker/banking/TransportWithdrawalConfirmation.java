package net.runelite.client.plugins.microbot.util.walker.banking;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntUnaryOperator;

public final class TransportWithdrawalConfirmation
{
	public static final int TIMEOUT_MS = 8_000;

	public enum State
	{
		CONFIRMED,
		PENDING,
		ABORTED
	}

	private final Set<Integer> itemIds;
	private final int targetQuantity;

	private TransportWithdrawalConfirmation(Set<Integer> itemIds, int targetQuantity)
	{
		this.itemIds = itemIds;
		this.targetQuantity = targetQuantity;
	}

	public static TransportWithdrawalConfirmation start(int requestedItemId, int withdrawnItemId,
		int amount, IntUnaryOperator inventoryQuantity)
	{
		LinkedHashSet<Integer> ids = new LinkedHashSet<>();
		ids.add(requestedItemId);
		if (withdrawnItemId > 0)
		{
			ids.add(withdrawnItemId);
		}
		Set<Integer> itemIds = Collections.unmodifiableSet(ids);
		long target = carried(itemIds, inventoryQuantity) + Math.max(0, amount);
		return new TransportWithdrawalConfirmation(itemIds, (int) Math.min(Integer.MAX_VALUE, target));
	}

	public State evaluate(IntUnaryOperator inventoryQuantity, boolean bankOpen)
	{
		if (carried(itemIds, inventoryQuantity) >= targetQuantity)
		{
			return State.CONFIRMED;
		}
		return bankOpen ? State.PENDING : State.ABORTED;
	}

	public Set<Integer> getItemIds()
	{
		return itemIds;
	}

	public int getTargetQuantity()
	{
		return targetQuantity;
	}

	public int carriedQuantity(IntUnaryOperator inventoryQuantity)
	{
		return (int) Math.min(Integer.MAX_VALUE, carried(itemIds, inventoryQuantity));
	}

	private static long carried(Set<Integer> itemIds, IntUnaryOperator inventoryQuantity)
	{
		long total = 0;
		for (int itemId : itemIds)
		{
			total += Math.max(0, inventoryQuantity.applyAsInt(itemId));
		}
		return total;
	}
}
