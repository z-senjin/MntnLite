package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Read-only, live view over the account's inventory. This is what the doc calls
 * "InventorySnapshot" - name kept generic because tasks use it as a live view. Planner
 * passes temporarily install a frozen read so recursive strategy evaluation does not make
 * repeated client-thread calls through Rs2Inventory.
 */
public class InventoryView {

    private PlanningRead planningRead;

    public boolean hasItem(String itemName) {
        if (planningRead != null) {
            return planningRead.countByName(itemName) > 0;
        }
        return Rs2Inventory.hasItem(itemName);
    }

    public boolean hasItem(int itemId) {
        if (planningRead != null) {
            return planningRead.countById(itemId) > 0;
        }
        return Rs2Inventory.hasItem(itemId);
    }

    public boolean isFull() {
        if (planningRead != null) {
            return planningRead.full;
        }
        return Rs2Inventory.isFull();
    }

    public boolean isEmpty() {
        if (planningRead != null) {
            return planningRead.countsById.isEmpty();
        }
        return Rs2Inventory.isEmpty();
    }

    public boolean hasFood() {
        if (planningRead != null) {
            return planningRead.hasFood;
        }
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
        if (planningRead != null) {
            return planningRead.countByName(itemName);
        }
        return Rs2Inventory.itemQuantity(itemName); // TODO verify this exists
    }

    public int getCount(int itemId) {
        if (planningRead != null) {
            return planningRead.countById(itemId);
        }
        return Rs2Inventory.itemQuantity(itemId);
    }

    /**
     * Returns whether every inventory item belongs to the supplied task loadout.
     * Equipment is intentionally not considered: a task owns its required inventory items,
     * while equipped gear remains available to the account.
     */
    public boolean hasOnlyItems(Collection<String> itemNames) {
        return Rs2Inventory.items().allMatch(item -> itemNames.contains(item.getName()));
    }

    void beginPlanningRead(Map<String, Integer> countsByName, Map<Integer, Integer> countsById,
                           boolean full, boolean hasFood) {
        planningRead = new PlanningRead(countsByName, countsById, full, hasFood);
    }

    void endPlanningRead() {
        planningRead = null;
    }

    private static final class PlanningRead {
        private final Map<String, Integer> countsByName;
        private final Map<Integer, Integer> countsById;
        private final boolean full;
        private final boolean hasFood;

        private PlanningRead(Map<String, Integer> countsByName, Map<Integer, Integer> countsById,
                             boolean full, boolean hasFood) {
            this.countsByName = Collections.unmodifiableMap(new HashMap<>(countsByName));
            this.countsById = Collections.unmodifiableMap(new HashMap<>(countsById));
            this.full = full;
            this.hasFood = hasFood;
        }

        private int countByName(String itemName) {
            return countsByName.getOrDefault(itemName, 0);
        }

        private int countById(int itemId) {
            return countsById.getOrDefault(itemId, 0);
        }
    }
}
