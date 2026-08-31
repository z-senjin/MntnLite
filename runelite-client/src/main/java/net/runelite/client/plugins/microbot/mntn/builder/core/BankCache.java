package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bank contents snapshot that survives after the bank widget closes.
 *
 * Rs2Bank, like most Microbot widget-backed utilities, can usually only answer queries
 * accurately while the bank interface is actually open on screen - it's reading the live
 * widget, not a persistent model. That means calling a bank query from a Task phase where
 * the bank isn't open can silently return wrong/empty results even if the item IS in there.
 * This class captures a snapshot the moment the bank IS open (via refresh()), so the rest of
 * the codebase can keep asking "does the bank have X" from anywhere - fishing out in the
 * field, planning a strategy, wherever - using the last known truth instead of nothing.
 *
 * This is a snapshot, not a live feed - it goes stale the moment bank contents change
 * without a refresh() call (e.g. you log into a different session, or something deposits/
 * withdraws without going through BankView). Call refresh() at the end of every banking
 * interaction so it doesn't drift.
 */
public class BankCache {

    private final Map<Integer, Integer> quantitiesById = new HashMap<>();
    private final Map<String, Integer> quantitiesByName = new HashMap<>();
    private boolean populated = false;

    /**
     * Pulls a full snapshot from the live bank widget. Only works while the bank is actually
     * open - no-ops otherwise so a stray call from a closed-bank phase can't wipe out a
     * previously good cache with an empty one.
     */
    public void refresh() {
        if (!Rs2Bank.isOpen()) {
            return;
        }

        quantitiesById.clear();
        quantitiesByName.clear();

        for (Rs2ItemModel item : queryBankItems()) {
            if (item == null) {
                continue;
            }
            quantitiesById.merge(item.getId(), item.getQuantity(), Integer::sum);
            quantitiesByName.merge(item.getName(), item.getQuantity(), Integer::sum);
        }

        populated = true;
    }

    /**
     * NOT CONFIRMED against your exact Microbot version - none of the reference scripts you
     * shared ever enumerated the full bank, only checked single items (hasItem) or blindly
     * deposited (depositAllExcept). Rs2Bank.bankItems() is a best guess based on the naming
     * pattern your other Rs2XxxModel utilities already follow (Rs2NpcModel, Rs2TileObjectModel).
     *
     * If this doesn't compile: open Rs2Bank in your IDE, autocomplete "Rs2Bank." and look for
     * whatever method returns a List<Rs2ItemModel> (or similar) representing everything
     * currently in the bank, and swap the one line inside this method for that call. Nothing
     * else in BankCache needs to change - this is the only place that touches the real API.
     */
    private List<Rs2ItemModel> queryBankItems() {
        return Rs2Bank.bankItems(); // TODO verify this exact method exists in your version
    }

    public boolean isPopulated() {
        return populated;
    }

    public boolean hasItem(String itemName) {
        return quantitiesByName.getOrDefault(itemName, 0) > 0;
    }

    public boolean hasItem(int itemId) {
        return quantitiesById.getOrDefault(itemId, 0) > 0;
    }

    public int getCount(String itemName) {
        return quantitiesByName.getOrDefault(itemName, 0);
    }

    public int getCount(int itemId) {
        return quantitiesById.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> snapshotByName() {
        return Collections.unmodifiableMap(quantitiesByName);
    }
}