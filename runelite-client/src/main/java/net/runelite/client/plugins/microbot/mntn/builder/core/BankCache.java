package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

import java.util.Collections;
import java.util.HashMap;
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
     *
     * TODO: verify the exact Rs2Bank enumeration call in your Microbot version - the common
     * pattern across Microbot's Rs2XxxModel utilities (Rs2NpcModel, Rs2TileObjectModel) is a
     * method returning a list of item models with getId()/getName()/getQuantity(), e.g.
     * something like Rs2Bank.bankItems() or Rs2Bank.getAll(). Check autocomplete on Rs2Bank
     * in your IDE and drop the real call into the loop below - the two hasItem/getCount
     * confirmed methods (from your GemCrabKiller script) don't give you a way to enumerate
     * everything, only to check one specific item at a time.
     */
    public void refresh() {
        if (!Rs2Bank.isOpen()) {
            return;
        }

        quantitiesById.clear();
        quantitiesByName.clear();

        // TODO replace with the real enumeration, e.g.:
        // for (Rs2ItemModel item : Rs2Bank.bankItems()) {
        //     quantitiesById.merge(item.getId(), item.getQuantity(), Integer::sum);
        //     quantitiesByName.merge(item.getName(), item.getQuantity(), Integer::sum);
        // }

        populated = true;
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
