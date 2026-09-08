package net.runelite.client.plugins.microbot.mntn.builder.core;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventoryViewTest {

    @Test
    public void planningReadUsesTheFrozenInventoryState() {
        InventoryView inventory = new InventoryView();
        inventory.beginPlanningRead(
                Map.of("Coins", 31, "Bronze pickaxe", 1),
                Map.of(995, 31, 1265, 1),
                false,
                false
        );

        assertTrue(inventory.hasItem("Bronze pickaxe"));
        assertTrue(inventory.hasItem(995));
        assertEquals(31, inventory.getCount("Coins"));
        assertEquals(1, inventory.getCount(1265));
        assertFalse(inventory.isFull());
        assertFalse(inventory.isEmpty());
        assertFalse(inventory.hasFood());

        inventory.endPlanningRead();
    }
}
