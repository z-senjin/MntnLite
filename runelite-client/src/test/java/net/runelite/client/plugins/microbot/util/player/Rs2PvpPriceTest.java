package net.runelite.client.plugins.microbot.util.player;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2PvpPriceTest {
    @Test
    public void preservesRiskAboveIntegerRange() {
        Client client = mock(Client.class);
        ItemManager itemManager = mock(ItemManager.class);
        ItemContainer equipment = mock(ItemContainer.class);
        ItemContainer inventory = mock(ItemContainer.class);
        ItemComposition composition = mock(ItemComposition.class);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        when(equipment.getItems()).thenReturn(new Item[]{new Item(4151, 2)});
        when(inventory.getItems()).thenReturn(new Item[0]);
        when(itemManager.getItemComposition(4151)).thenReturn(composition);
        when(composition.isTradeable()).thenReturn(true);
        when(itemManager.getItemPrice(4151)).thenReturn((long) Integer.MAX_VALUE + 1);

        assertEquals(4_294_967_296L, Rs2Pvp.calculateRisk(client, itemManager));
    }
}
