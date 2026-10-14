/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InventorySetupTerminalOwnerTest {
    @Test public void missingSupplyRetainsHostSchedulerForObservedLogout() throws Exception {
        ScheduledFuture<?> scheduler = mock(ScheduledFuture.class);
        Rs2InventorySetup loader = new Rs2InventorySetup(mock(InventorySetup.class), scheduler);
        List<String> missing = new ArrayList<>();
        loader.withMissingSupplyPolicy(Rs2InventorySetup.MissingSupplyPolicy.LOGOUT_AND_STOP)
                .withMissingSupplyHandler(missing::add);
        var method = Rs2InventorySetup.class.getDeclaredMethod("stopForMissingRequiredSupply", String.class);
        method.setAccessible(true);
        method.invoke(loader, "Super restore(4)");
        assertEquals(List.of("Super restore(4)"), missing);
        verifyNoInteractions(scheduler);
    }

    @Test public void handlerWithDefaultPolicyStopsEquipmentLoadAtFirstMissingGear() {
        boolean pausedBefore = Microbot.pauseAllScripts.getAndSet(false);
        InventorySetup setup = mock(InventorySetup.class);
        InventorySetupsItem missingGear = new InventorySetupsItem(100, "Missing helm", 1, false,
                InventorySetupsStackCompareID.None, false, -1);
        InventorySetupsItem bankedGear = new InventorySetupsItem(200, "Banked body", 1, false,
                InventorySetupsStackCompareID.None, false, -1);
        when(setup.getInventory()).thenReturn(List.of());
        when(setup.getEquipment()).thenReturn(List.of(missingGear, bankedGear));
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
        try (var microbot = mockStatic(Microbot.class);
             var bank = mockStatic(Rs2Bank.class);
             var inventory = mockStatic(Rs2Inventory.class);
             var equipment = mockStatic(Rs2Equipment.class);
             var player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.verifyBankMirrorAfterOpen(true, 0)).thenReturn(true);
            bank.when(() -> Rs2Bank.hasItem(200)).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.empty());
            inventory.when(() -> Rs2Inventory.items(any(Predicate.class))).thenAnswer(call -> Stream.empty());
            equipment.when(Rs2Equipment::all).thenAnswer(call -> Stream.empty());
            ScheduledFuture<?> scheduler = mock(ScheduledFuture.class);
            List<String> missing = new ArrayList<>();
            Rs2InventorySetup loader = new Rs2InventorySetup(setup, scheduler).withMissingSupplyHandler(missing::add);
            assertFalse(loader.loadEquipment(false));
            assertEquals(List.of("Missing helm"), missing);
            bank.verify(() -> Rs2Bank.withdrawAndEquip(anyInt()), never());
            player.verify(Rs2Player::logoutWithoutAutoLogin, never());
            verify(scheduler, never()).cancel(anyBoolean());
            assertFalse(Microbot.pauseAllScripts.get());
        } finally {
            Microbot.pauseAllScripts.set(pausedBefore);
        }
    }
}
