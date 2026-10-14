package net.runelite.client.plugins.microbot.testing.release;

import java.util.Objects;

public final class ReleaseCheck {

    @FunctionalInterface
    public interface Body {
        void run(CheckContext context) throws Exception;
    }

    private final String id;
    private final String description;
    private final ReleaseTier tier;
    private final long timeoutMs;
    private final boolean alwaysRun;
    private final Body body;

    private ReleaseCheck(String id, String description, ReleaseTier tier, long timeoutMs, boolean alwaysRun, Body body) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Check id is required");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Check timeout must be positive for " + id);
        }
        this.id = id;
        this.description = Objects.requireNonNull(description, "description");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.timeoutMs = timeoutMs;
        this.alwaysRun = alwaysRun;
        this.body = Objects.requireNonNull(body, "body");
    }

    public static ReleaseCheck quick(String id, String description, long timeoutMs, Body body) {
        return new ReleaseCheck(id, description, ReleaseTier.QUICK, timeoutMs, false, body);
    }

    public static ReleaseCheck full(String id, String description, long timeoutMs, Body body) {
        return new ReleaseCheck(id, description, ReleaseTier.FULL, timeoutMs, false, body);
    }

    public ReleaseCheck alwaysRun() {
        return new ReleaseCheck(id, description, tier, timeoutMs, true, body);
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public ReleaseTier getTier() {
        return tier;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isAlwaysRun() {
        return alwaysRun;
    }

    public Body getBody() {
        return body;
    }
}
