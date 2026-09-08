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

    private static final long OPEN_BANK_REFRESH_INTERVAL_MS = 250L;

    private final BankCache cache = new BankCache();
    private long lastOpenRefreshAtMs;
    private boolean bankWasOpen;
    private int planningReadDepth;

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
        lastOpenRefreshAtMs = System.currentTimeMillis();
        bankWasOpen = isOpen();
    }

    public boolean isCachePopulated() {
        return cache.isPopulated();
    }

    public boolean hasItem(String itemName) {
        refreshOpenBankIfNeeded();
        return cache.hasItem(itemName);
    }

    public boolean hasItem(int itemId) {
        refreshOpenBankIfNeeded();
        return cache.hasItem(itemId);
    }

    public int getCount(String itemName) {
        refreshOpenBankIfNeeded();
        return cache.getCount(itemName);
    }

    public int getCount(int itemId) {
        refreshOpenBankIfNeeded();
        return cache.getCount(itemId);
    }

    /**
     * Planner decisions use the bank snapshot warmed by BankingTask. Rechecking the live
     * bank widget for every candidate can block the script worker behind the client thread.
     */
    void beginPlanningRead() {
        planningReadDepth++;
    }

    void endPlanningRead() {
        planningReadDepth = Math.max(0, planningReadDepth - 1);
    }

    /**
     * A planner pass can ask hundreds of bank questions. Refresh once when the bank opens,
     * then share that fresh snapshot for a short interval; banking actions still call the
     * public refresh() method explicitly after every completed deposit or withdrawal.
     */
    private void refreshOpenBankIfNeeded() {
        if (planningReadDepth > 0) {
            return;
        }
        if (!isOpen()) {
            bankWasOpen = false;
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (!bankWasOpen || nowMs - lastOpenRefreshAtMs >= OPEN_BANK_REFRESH_INTERVAL_MS) {
            refresh();
        }
    }
}
