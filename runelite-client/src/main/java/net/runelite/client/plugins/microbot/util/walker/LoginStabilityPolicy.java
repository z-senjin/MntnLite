package net.runelite.client.plugins.microbot.util.walker;

/** Pure decision used after the walker gives a transient logged-out sample a short grace window. */
final class LoginStabilityPolicy {
    private LoginStabilityPolicy() {
    }

    static boolean shouldExit(boolean initiallyLoggedIn,
                              boolean loggedInAfterGrace,
                              boolean walkCancelled) {
        return !walkCancelled && !initiallyLoggedIn && !loggedInAfterGrace;
    }
}
