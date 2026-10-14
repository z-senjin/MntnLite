package net.runelite.client.plugins.microbot.util.player;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class Rs2PlayerRunEnergyTest {
    private final Map<String, Object> original = new HashMap<>();
    private Client client;
    private ClientThread clientThread;
    private Mouse mouse;
    private Widget orb;
    private boolean readingClientState;
    private int oldThreshold;

    @Before
    public void setUp() throws Exception {
        client = mock(Client.class);
        clientThread = mock(ClientThread.class);
        mouse = mock(Mouse.class);
        orb = mock(Widget.class);
        replace("client", client);
        replace("clientThread", clientThread);
        replace("mouse", mouse);
        oldThreshold = Microbot.runEnergyThreshold;
        Microbot.runEnergyThreshold = 1000;
        Field attempted = Rs2Player.class.getDeclaredField("runToggleAttempted");
        attempted.setAccessible(true);
        attempted.setBoolean(null, false);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
            boolean previous = readingClientState;
            readingClientState = true;
            try { return Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()); }
            finally { readingClientState = previous; }
        });
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getEnergy()).thenReturn(10000);
        when(client.getCanvasWidth()).thenReturn(765);
        when(client.getCanvasHeight()).thenReturn(503);
        when(client.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB.getId())).thenReturn(orb);
        when(orb.isHidden()).thenAnswer(i -> { assertTrue(readingClientState); return false; });
        when(orb.getBounds()).thenAnswer(i -> {
            assertTrue("Widget geometry must be read on the client thread", readingClientState);
            return new Rectangle(540, 100, 40, 40);
        });
    }

    private void replace(String name, Object value) throws Exception {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        original.put(name, field.get(null));
        field.set(null, value);
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Field field = Microbot.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(null, entry.getValue());
        }
        Microbot.runEnergyThreshold = oldThreshold;
    }

    @Test
    public void insufficientEnergyNeverClicksEvenWithRepeatedRequests() {
        for (int energy : new int[]{0, 1, 999, 1000}) {
            when(client.getEnergy()).thenReturn(energy);
            assertFalse(Rs2Player.toggleRunEnergy(true));
            assertFalse(Rs2Player.toggleRunEnergy(true));
        }
        verifyNoInteractions(mouse);
    }

    @Test
    public void sufficientEnergyUsesInteriorAndReportsObservedSuccess() {
        when(client.getEnergy()).thenReturn(1001);
        doAnswer(i -> { when(client.getVarpValue(173)).thenReturn(1); return mouse; })
                .when(mouse).click(any(Point.class));
        assertTrue(Rs2Player.toggleRunEnergy(true));
        verify(mouse).click(new Point(560, 120));
    }

    @Test
    public void missingAcknowledgmentIsNotSuccessAndDoesNotSpam() {
        assertFalse(Rs2Player.toggleRunEnergy(true));
        for (int i = 0; i < 20; i++) assertFalse(Rs2Player.toggleRunEnergy(true));
        verify(mouse, times(1)).click(any(Point.class));
        when(client.getVarpValue(173)).thenReturn(1);
        assertTrue(Rs2Player.toggleRunEnergy(true));
    }

    @Test
    public void alreadyRunningNeedsNoEnergyOrWidget() {
        when(client.getVarpValue(173)).thenReturn(1);
        when(client.getEnergy()).thenReturn(0);
        assertTrue(Rs2Player.toggleRunEnergy(true));
        verifyNoInteractions(mouse, orb);
    }

    @Test
    public void disableWorksAtZeroEnergy() {
        when(client.getVarpValue(173)).thenReturn(1);
        when(client.getEnergy()).thenReturn(0);
        doAnswer(i -> { when(client.getVarpValue(173)).thenReturn(0); return mouse; })
                .when(mouse).click(any(Point.class));
        assertTrue(Rs2Player.toggleRunEnergy(false));
        verify(mouse).click(new Point(560, 120));
    }

    @Test
    public void alreadyDisabledDoesNotClick() {
        assertTrue(Rs2Player.toggleRunEnergy(false));
        verifyNoInteractions(mouse, orb);
    }

    @Test
    public void absentAndHiddenOrbsDoNotClick() {
        when(client.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB.getId())).thenReturn(null);
        assertFalse(Rs2Player.toggleRunEnergy(true));
        when(client.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB.getId())).thenReturn(orb);
        doReturn(true).when(orb).isHidden();
        assertFalse(Rs2Player.toggleRunEnergy(true));
        verifyNoInteractions(mouse);
    }

    @Test
    public void invalidGeometryDoesNotClick() {
        for (Rectangle bounds : new Rectangle[]{new Rectangle(), new Rectangle(0, 0, 2, 2),
                new Rectangle(-1, -1, 50, 26), new Rectangle(-100, 0, 40, 40), new Rectangle(800, 100, 40, 40)}) {
            doReturn(bounds).when(orb).getBounds();
            assertFalse(Rs2Player.toggleRunEnergy(true));
        }
        verifyNoInteractions(mouse);
    }

    @Test
    public void resizedOrbUsesCurrentBounds() {
        when(client.getCanvasWidth()).thenReturn(1200);
        doReturn(new Rectangle(1100, 100, 40, 40)).when(orb).getBounds();
        Rs2Player.toggleRunEnergy(true);
        verify(mouse).click(new Point(1120, 120));
    }

    @Test
    public void configurableThresholdAppliesToDirectCalls() {
        Microbot.runEnergyThreshold = 3000;
        when(client.getEnergy()).thenReturn(3000);
        assertFalse(Rs2Player.toggleRunEnergy(true));
        verifyNoInteractions(mouse);
        when(client.getEnergy()).thenReturn(3001);
        Rs2Player.toggleRunEnergy(true);
        verify(mouse).click(any(Point.class));
    }

    @Test
    public void clientThreadNeverDispatchesBlockingGesture() {
        when(clientThread.isClientThread()).thenReturn(true);
        assertFalse(Rs2Player.toggleRunEnergy(true));
        verifyNoInteractions(mouse);
    }

    @Test
    public void loggedOutDoesNotSucceedOrClick() {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertFalse(Rs2Player.toggleRunEnergy(false));
        assertFalse(Rs2Player.toggleRunEnergy(true));
        verifyNoInteractions(mouse);
    }
    @Test
    public void missedClickCanRetryAfterCooldown() throws Exception {
        assertFalse(Rs2Player.toggleRunEnergy(true));
        Field last = Rs2Player.class.getDeclaredField("lastRunToggleAttempt");
        last.setAccessible(true);
        last.setLong(null, System.nanoTime() - java.util.concurrent.TimeUnit.SECONDS.toNanos(2));
        assertFalse(Rs2Player.toggleRunEnergy(true));
        verify(mouse, times(2)).click(any(Point.class));
    }

    @Test
    public void concurrentCallerDoesNotDispatchAnotherToggle() throws Exception {
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(i -> {
            entered.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return mouse;
        }).when(mouse).click(any(Point.class));
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<Boolean> first = executor.submit(() -> Rs2Player.toggleRunEnergy(true));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(Rs2Player.toggleRunEnergy(true));
            release.countDown();
            assertFalse(first.get(5, java.util.concurrent.TimeUnit.SECONDS));
            verify(mouse, times(1)).click(any(Point.class));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

}
