package net.runelite.client.plugins.microbot.testing.release;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CheckOutcome {
    public int order;
    public String id;
    public String description;
    public ReleaseTier tier;
    public CheckStatus status;
    public long durationMs;
    public long timeoutMs;
    public String startedAt;
    public String detail;
    public Map<String, Object> details = new LinkedHashMap<>();
    public List<String> cleanupErrors = new ArrayList<>();

    public static CheckOutcome notRun(int order, ReleaseCheck check, String reason) {
        CheckOutcome outcome = new CheckOutcome();
        outcome.order = order;
        outcome.id = check.getId();
        outcome.description = check.getDescription();
        outcome.tier = check.getTier();
        outcome.timeoutMs = check.getTimeoutMs();
        outcome.status = CheckStatus.NOT_RUN;
        outcome.detail = reason;
        return outcome;
    }
}
