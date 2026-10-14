/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.util.player;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TerminalLogoutStepTest {
    private void check(GameState state, boolean onClientThread, Rs2Player.TerminalLogoutState expected,
                       int expectedRequests) throws Exception {
        Client client=mock(Client.class);
        ClientThread thread=mock(ClientThread.class);
        when(client.getGameState()).thenReturn(state);
        when(thread.isClientThread()).thenReturn(onClientThread);
        when(thread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(invocation ->
                Optional.ofNullable(((Callable<?>)invocation.getArgument(0)).call()));
        Field cf=Microbot.class.getDeclaredField("client"),tf=Microbot.class.getDeclaredField("clientThread");
        cf.setAccessible(true);tf.setAccessible(true);
        Object oldClient=cf.get(null),oldThread=tf.get(null);
        try {
            cf.set(null,client);tf.set(null,thread);
            int[] requests={0};
            assertEquals(expected,Rs2Player.logoutWithoutAutoLoginStep(() -> requests[0]++));
            assertEquals(expectedRequests,requests[0]);
        } finally {
            cf.set(null,oldClient);tf.set(null,oldThread);
        }
    }

    @Test public void worldAndLoginTransitionsAreNotConfirmedLogout() throws Exception {
        for(GameState state:new GameState[]{GameState.HOPPING,GameState.LOADING,GameState.CONNECTION_LOST,
                GameState.LOGGING_IN,GameState.LOGIN_SCREEN_AUTHENTICATOR,GameState.UNKNOWN}) {
            check(state,false,Rs2Player.TerminalLogoutState.INPUT_BLOCKED,0);
        }
    }

    @Test public void loginScreenConfirmsLogoutWithoutAnUncompletableSuppression() throws Exception {
        check(GameState.LOGIN_SCREEN,false,Rs2Player.TerminalLogoutState.LOGGED_OUT,0);
    }

    @Test public void clientThreadCannotDispatchOrRequestSuppression() throws Exception {
        check(GameState.LOGGED_IN,true,Rs2Player.TerminalLogoutState.INPUT_BLOCKED,0);
    }
}
