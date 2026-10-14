package net.runelite.client.plugins.microbot.testing.release;

import net.runelite.client.plugins.microbot.testing.TestResult;

import java.util.ArrayList;
import java.util.List;

public class ReleaseSmokeResult extends TestResult {
    public String tier;
    public boolean failFast;
    public long totalTimeoutMs;
    public List<String> requestedChecks = new ArrayList<>();
    public String microbotVersion;
    public String microbotCommit;
    public String runeliteVersion;
    public String expectedVersion;
    public int world;
    public long passed;
    public long failed;
    public long skipped;
    public long notRun;
    public String verdict;
    public List<CheckOutcome> results = new ArrayList<>();

    public ReleaseSmokeResult(String script) {
        super(script);
    }

    public void record(CheckOutcome outcome) {
        results.add(outcome);
        addCheck(outcome.id, outcome.status.isGreen(),
                outcome.status == CheckStatus.PASSED ? null : outcome.status + ": " + outcome.detail);
    }

    public void finish(String exitReason) {
        passed = count(CheckStatus.PASSED);
        failed = count(CheckStatus.FAILED);
        skipped = count(CheckStatus.SKIPPED);
        notRun = count(CheckStatus.NOT_RUN);
        complete(exitReason);
        if ("completed".equals(exitReason) && results.isEmpty()) {
            exitCode = 1;
        }
        verdict = exitCode == 0 ? "PASS" : "FAIL";
    }

    private long count(CheckStatus status) {
        return results.stream().filter(outcome -> outcome.status == status).count();
    }
}
