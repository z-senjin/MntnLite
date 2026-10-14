package net.runelite.client.plugins.microbot.testing.release;

public enum CheckStatus {
    PASSED,
    FAILED,
    SKIPPED,
    NOT_RUN;

    public boolean isGreen() {
        return this == PASSED || this == SKIPPED;
    }
}
