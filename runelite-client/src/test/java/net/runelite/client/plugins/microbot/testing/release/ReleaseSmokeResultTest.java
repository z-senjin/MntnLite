package net.runelite.client.plugins.microbot.testing.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.client.plugins.microbot.testing.TestResultWriter;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReleaseSmokeResultTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static CheckOutcome outcome(int order, String id, CheckStatus status, String detail) {
        CheckOutcome outcome = new CheckOutcome();
        outcome.order = order;
        outcome.id = id;
        outcome.description = id + " description";
        outcome.tier = ReleaseTier.QUICK;
        outcome.status = status;
        outcome.durationMs = 1500;
        outcome.detail = detail;
        return outcome;
    }

    @Test
    public void passedAndSkippedChecksExitZero() {
        ReleaseSmokeResult result = new ReleaseSmokeResult("Release Smoke Harness");
        result.record(outcome(1, "boot", CheckStatus.PASSED, null));
        result.record(outcome(2, "bank", CheckStatus.SKIPPED, "no junk item"));
        result.finish("completed");

        assertEquals(0, result.exitCode);
        assertEquals("PASS", result.verdict);
        assertEquals(1, result.passed);
        assertEquals(1, result.skipped);
    }

    @Test
    public void failedOrNotRunChecksExitNonZero() {
        ReleaseSmokeResult failed = new ReleaseSmokeResult("h");
        failed.record(outcome(1, "boot", CheckStatus.FAILED, "bad"));
        failed.finish("completed");
        assertEquals(1, failed.exitCode);
        assertEquals("FAIL", failed.verdict);

        ReleaseSmokeResult notRun = new ReleaseSmokeResult("h");
        notRun.record(outcome(1, "boot", CheckStatus.PASSED, null));
        notRun.record(outcome(2, "bank", CheckStatus.NOT_RUN, "Not run: total timeout"));
        notRun.finish("completed");
        assertEquals(1, notRun.exitCode);
        assertEquals(1, notRun.notRun);
    }

    @Test
    public void emptyRunsAndCrashesAreFailures() {
        ReleaseSmokeResult empty = new ReleaseSmokeResult("h");
        empty.finish("completed");
        assertEquals(1, empty.exitCode);

        ReleaseSmokeResult crash = new ReleaseSmokeResult("h");
        crash.finish("crash");
        assertEquals(3, crash.exitCode);
        assertEquals("FAIL", crash.verdict);
    }

    @Test
    public void resultJsonContainsTheStructuredOutcomes() throws Exception {
        ReleaseSmokeResult result = new ReleaseSmokeResult("Release Smoke Harness");
        result.tier = "QUICK";
        result.microbotVersion = "2.6.30";
        CheckOutcome bank = outcome(1, "bank", CheckStatus.FAILED, "Inventory not restored");
        bank.details.put("junkItem", "Feather#314");
        result.record(bank);
        result.finish("completed");

        File dir = folder.newFolder("release-e2e");
        TestResultWriter.write(result, dir.getAbsolutePath());

        JsonObject json = new JsonParser().parse(new String(Files.readAllBytes(dir.toPath().resolve("result.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("FAIL", json.get("verdict").getAsString());
        assertEquals(1, json.get("exitCode").getAsInt());
        JsonArray results = json.getAsJsonArray("results");
        assertEquals(1, results.size());
        JsonObject first = results.get(0).getAsJsonObject();
        assertEquals("bank", first.get("id").getAsString());
        assertEquals("FAILED", first.get("status").getAsString());
        assertEquals(1500, first.get("durationMs").getAsLong());
        assertEquals("Feather#314", first.getAsJsonObject("details").get("junkItem").getAsString());
        JsonArray legacyChecks = json.getAsJsonArray("checks");
        assertEquals("bank", legacyChecks.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    public void summaryIsAMarkdownTableWithEscapedDetails() throws Exception {
        ReleaseSmokeResult result = new ReleaseSmokeResult("h");
        result.tier = "FULL";
        result.microbotVersion = "2.6.30";
        result.record(outcome(1, "boot", CheckStatus.PASSED, null));
        result.record(outcome(2, "world-hop", CheckStatus.FAILED, "a | b\nc"));
        result.finish("completed");

        Path file = ReleaseSmokeReport.write(result, folder.newFolder("out").toPath());
        String summary = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(summary, summary.startsWith("## Microbot release E2E: FAIL (full tier)"));
        assertTrue(summary, summary.contains("| 1 | `boot` boot description | quick | PASSED | 1.5s |  |"));
        assertTrue(summary, summary.contains("| 2 | `world-hop` world-hop description | quick | FAILED | 1.5s | a \\| b c |"));
        assertTrue(summary, summary.contains("1 passed, 1 failed, 0 skipped, 0 not run"));
    }

    @Test
    public void durationsAreHumanReadable() {
        assertEquals("0.4s", ReleaseSmokeReport.formatDuration(400));
        assertEquals("59.9s", ReleaseSmokeReport.formatDuration(59_900));
        assertEquals("2m05s", ReleaseSmokeReport.formatDuration(125_000));
    }

    @Test
    public void hopTargetIsAPlainF2pWorldPreferringTheSameRegion() {
        List<World> worlds = List.of(
                world(383, EnumSet.noneOf(WorldType.class), "Castle Wars - Free", 7, 219),
                world(571, EnumSet.noneOf(WorldType.class), "PvP Arena (Legacy Duels)", 3, 58),
                world(302, EnumSet.of(WorldType.MEMBERS), "-", 7, 20),
                world(537, EnumSet.noneOf(WorldType.class), "-", 3, 90),
                world(552, EnumSet.noneOf(WorldType.class), "-", 7, 102),
                world(553, EnumSet.noneOf(WorldType.class), "-", 7, 152));
        assertEquals(552, ReleaseSmokeChecks.pickF2pWorld(worlds, 383));
        assertEquals(-1, ReleaseSmokeChecks.pickF2pWorld(List.of(worlds.get(0), worlds.get(1)), 383));
    }

    @Test
    public void checkIdsParseFromCommasOrWhitespace() {
        assertEquals(List.of("bank", "world-hop", "logout"), ReleaseSmokeHarnessPlugin.parseIds(" bank,world-hop  logout "));
        assertTrue(ReleaseSmokeHarnessPlugin.parseIds("").isEmpty());
    }

    private static World world(int id, EnumSet<WorldType> types, String activity, int location, int players) {
        return World.builder().id(id).types(types).activity(activity).location(location).players(players).address("x").build();
    }
}
