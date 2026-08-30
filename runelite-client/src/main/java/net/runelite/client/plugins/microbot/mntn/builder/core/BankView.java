package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/**
 * Read-only view over the bank, backed by BankCache so queries work even when the bank
 * widget is closed - see BankCache's class doc for why that matters and how staleness works.
 *
 * Deliberately query-only: actions like openBank()/depositAllExcept() stay in BankingTask,
 * not here. AccountContext is "what's true right now", Tasks are "make something happen" -
 * keeping that split clean is what lets Strategy.score() and Requirement.isSatisfied() call
 * into this safely without risking a bank interaction firing off mid-plan.
 */
public class BankView {

    private final BankCache cache = new BankCache();

    public boolean isOpen() {
        return Rs2Bank.isOpen();
    }

    /**
     * Refreshes the cache from the live widget. Only has any effect while the bank is
     * actually open. Call this explicitly from BankingTask right after opening and again
     * after every deposit/withdraw, so a banking trip that never happens to call hasItem()/
     * getCount() mid-trip still leaves the cache accurate for later queries.
     */
    public void refresh() {
        cache.refresh();
    }

    public boolean isCachePopulated() {
        return cache.isPopulated();
    }

    public boolean hasItem(String itemName) {
        if (isOpen()) {
            refresh(); // opportunistic - keeps the cache honest any time we happen to be looking
        }
        return cache.hasItem(itemName);
    }

    public boolean hasItem(int itemId) {
        if (isOpen()) {
            refresh();
        }
        return cache.hasItem(itemId);
    }

    public int getCount(String itemName) {
        if (isOpen()) {
            refresh();
        }
        return cache.getCount(itemName);
    }

    public int getCount(int itemId) {
        if (isOpen()) {
            refresh();
        }
        return cache.getCount(itemId);
    }
}
