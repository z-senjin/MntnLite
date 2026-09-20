package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2BankMirrorCacheTest {
    @Test
    public void restoredSnapshotIsProfileScopedAndNotALiveBankEpoch() throws Exception {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
        when(config.getRSProfileKey()).thenReturn("profile-a", "profile-b");
        when(config.getRSProfileConfiguration("microbot", "bankitems"))
                .thenReturn("[995,5,0]", (String) null);
        when(config.getRSProfileConfiguration("microbot", "bankLastOpenedAt"))
                .thenReturn("123456", (String) null);

        Field clientField = Microbot.class.getDeclaredField("client");
        Field configField = Microbot.class.getDeclaredField("configManager");
        Field threadField = Microbot.class.getDeclaredField("clientThread");
        clientField.setAccessible(true);
        configField.setAccessible(true);
        threadField.setAccessible(true);
        Object previousClient = clientField.get(null);
        Object previousConfig = configField.get(null);
        Object previousThread = threadField.get(null);
        try {
            clientField.set(null, client);
            configField.set(null, config);
            // Cache reconstruction must work without a client-thread dispatcher.
            threadField.set(null, null);
            Rs2Bank.invalidateBankMirrorCache(null);
            Rs2Bank.restoreBankMirrorCache();
            assertTrue(Rs2Bank.hasBankMirrorSnapshot());
            assertEquals(123456L, Rs2Bank.getBankLastOpenedAt());
            assertEquals(0, Rs2Bank.getBankLiveEpoch());
            assertEquals(1, Rs2Bank.bankItems().size());
            assertEquals(995, Rs2Bank.bankItems().get(0).getId());
            assertEquals(5, Rs2Bank.bankItems().get(0).getQuantity());

            Rs2Bank.restoreBankMirrorCache();
            assertFalse(Rs2Bank.hasBankMirrorSnapshot());
            assertEquals(0L, Rs2Bank.getBankLastOpenedAt());
        } finally {
            Rs2Bank.invalidateBankMirrorCache(null);
            clientField.set(null, previousClient);
            configField.set(null, previousConfig);
            threadField.set(null, previousThread);
        }
    }
}
