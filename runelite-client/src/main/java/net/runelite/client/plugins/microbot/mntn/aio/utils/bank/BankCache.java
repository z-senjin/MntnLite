package net.runelite.client.plugins.microbot.mntn.aio.utils.bank;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public boolean hasAnyItem(int[] itemIds) {
        for (int itemId : itemIds) {
            if (hasItem(itemId)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasAnyItem(String[] itemNames) {
        for (String itemName : itemNames) {
            if (hasItem(itemName)) {
                return true;
            }
        }

        return false;
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