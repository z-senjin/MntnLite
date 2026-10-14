package net.runelite.client.plugins.microbot.testing.release;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientLogScannerTest {

    private static final String START_OLD = "2026-10-10 18:00:00 CEST [main] INFO  net.runelite.client.RuneLite - RuneLite 1.13.1 (launcher version 2.7.6) starting up, args: none";
    private static final String START_NEW = "2026-10-10 19:00:00 CEST [main] INFO  net.runelite.client.RuneLite - RuneLite 1.13.1 (launcher version 2.7.6) starting up, args: --disable-telemetry";
    private static final String OLD_ERROR = "2026-10-10 18:10:00 CEST [Client] ERROR n.r.c.p.m.Microbot - previous run";
    private static final String INFO = "2026-10-10 19:00:01 CEST [Client] INFO  n.r.c.p.m.Microbot - fine";
    private static final String ERROR = "2026-10-10 19:00:02 CEST [Client] ERROR n.r.c.p.m.Microbot - Failed to hop to world 552";
    private static final String STACK = "java.lang.IllegalStateException: boom";
    private static final String EXAMPLE_INTERRUPT = "2026-10-10 19:00:03 CEST [StateMachineExampleScript-5] ERROR n.r.c.p.m.s.StateMachineExampleScript - [StateMachineExample] Error in state SCAN_OBJECTS: Interrupted waiting for client thread";
    private static final String EXAMPLE_OTHER = "2026-10-10 19:00:04 CEST [StateMachineExampleScript-5] ERROR n.r.c.p.m.s.StateMachineExampleScript - [StateMachineExample] Error in state SCAN_OBJECTS: NullPointerException";

    @Test
    public void currentRunStartsAtTheLastStartupBanner() {
        List<String> run = ClientLogScanner.currentRun(List.of(START_OLD, OLD_ERROR, START_NEW, INFO));
        assertEquals(List.of(START_NEW, INFO), run);
    }

    @Test
    public void currentRunKeepsTheWholeFileWhenTheBannerRolledAway() {
        assertEquals(List.of(INFO, ERROR), ClientLogScanner.currentRun(List.of(INFO, ERROR)));
    }

    @Test
    public void onlyErrorLevelLinesCount() {
        assertTrue(ClientLogScanner.isErrorLine(ERROR));
        assertFalse(ClientLogScanner.isErrorLine(INFO));
        assertFalse(ClientLogScanner.isErrorLine("2026-10-10 19:00:02 CEST [Client] WARN  n.r.c.Foo - ERROR in message text"));
        assertFalse(ClientLogScanner.isErrorLine(STACK));
    }

    @Test
    public void errorsCarryTheirFirstContinuationLine() {
        List<String> errors = ClientLogScanner.findErrors(List.of(START_NEW, ERROR, STACK, INFO), List.of());
        assertEquals(List.of(ERROR + " | " + STACK), errors);
    }

    @Test
    public void allowlistedLinesAreIgnored() {
        List<ClientLogScanner.AllowRule> allow = List.of(new ClientLogScanner.AllowRule("Failed to hop", "test"));
        assertTrue(ClientLogScanner.findErrors(List.of(ERROR, INFO), allow).isEmpty());
    }

    @Test
    public void defaultAllowlistIsNarrowAndDocumented() {
        List<String> errors = ClientLogScanner.findErrors(List.of(EXAMPLE_INTERRUPT, EXAMPLE_OTHER, ERROR), ClientLogScanner.DEFAULT_ALLOWLIST);
        assertEquals(List.of(EXAMPLE_OTHER, ERROR), errors);
        for (ClientLogScanner.AllowRule rule : ClientLogScanner.DEFAULT_ALLOWLIST) {
            assertFalse(rule.getReason().isBlank());
        }
        assertTrue(ClientLogScanner.DEFAULT_ALLOWLIST.size() <= 5);
    }
}
