package net.runelite.client.plugins.microbot.testing.release;

public class CheckSkippedException extends RuntimeException {
    public CheckSkippedException(String reason) {
        super(reason);
    }
}
