package net.runelite.client.plugins.microbot.util.walker.banking;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

public final class WithdrawNoteModePolicy
{
	private WithdrawNoteModePolicy()
	{
	}

	public static boolean requiresItemMode(Collection<Integer> withdrawalItemIds, IntPredicate stackable)
	{
		if (withdrawalItemIds == null)
		{
			return false;
		}
		for (Integer itemId : withdrawalItemIds)
		{
			if (itemId != null && !stackable.test(itemId))
			{
				return true;
			}
		}
		return false;
	}

	public static boolean shouldSwitchToItemMode(boolean requiresItemMode, boolean withdrawNoted)
	{
		return requiresItemMode && withdrawNoted;
	}

	public static Set<Integer> providerItemIds(int savedItemId, Map<Integer, Set<Integer>> withdrawnItemIds)
	{
		LinkedHashSet<Integer> ids = new LinkedHashSet<>();
		ids.add(savedItemId);
		if (withdrawnItemIds != null)
		{
			Set<Integer> withdrawn = withdrawnItemIds.get(savedItemId);
			if (withdrawn != null)
			{
				ids.addAll(withdrawn);
			}
		}
		return ids;
	}
}
