package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Read-only, live view over the account's inventory. This is what the doc calls
 * "InventorySnapshot" - name kept generic (View, not Snapshot) since it reads through to
 * Rs2Inventory live rather than freezing state. If you later add real per-tick snapshotting
 * to AccountContext, this is the only class whose internals need to change - callers using
 * context.inventory().hasItem(...) don't need to know or care.
 */
public class InventoryView {

    public boolean hasItem(String itemName) {
        return Rs2Inventory.hasItem(itemName);
    }

    public boolean hasItem(int itemId) {
        return Rs2Inventory.hasItem(itemId);
    }

    public boolean isFull() {
        return Rs2Inventory.isFull();
    }

    public boolean isEmpty() {
        return Rs2Inventory.isEmpty();
    }

    public boolean hasFood() {
        // Mirrors GemCrabKillerScript's `!Rs2Inventory.getInventoryFood().isEmpty()` check.
        return !Rs2Inventory.getInventoryFood().isEmpty();
    }

    /**
     * TODO: verify the exact method name/signature Rs2Inventory exposes for counting a
     * specific item in your Microbot version - candidates are usually named count(...) or
     * itemQuantity(...). Your reference script didn't call either, so check autocomplete on
     * Rs2Inventory in your IDE and wire the real one in here.
     */
    public int getCount(String itemName) {
        return Rs2Inventory.itemQuantity(itemName); // TODO verify this exists
    }
}
