package net.runelite.client.plugins.microbot.testing.release;

import java.util.Locale;

public enum ReleaseTier {
    QUICK,
    FULL;

    public boolean includes(ReleaseTier checkTier) {
        return this == FULL || checkTier == QUICK;
    }

    public static ReleaseTier parse(String value) {
        if (value == null || value.isBlank()) {
            return QUICK;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown release E2E tier '" + value + "', expected quick or full", e);
        }
    }
}
