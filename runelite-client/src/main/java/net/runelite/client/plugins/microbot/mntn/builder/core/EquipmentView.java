package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

/**
 * Read-only view over the account's equipped items.
 */
public class EquipmentView {

    public boolean hasItem(String itemName) {
        return Rs2Equipment.isWearing(itemName);
    }

    public boolean hasItem(int itemId) {
        return Rs2Equipment.isWearing(itemId);
    }
}
