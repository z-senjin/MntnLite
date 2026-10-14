package net.runelite.client.plugins.microbot.accountselector;

/**
 * Scripts post this event before a terminal logout to prevent automatic re-login.
 * AutoLogin resumes when another script starts, after a manual login, or on AutoLogin restart.
 * Ordinary logouts must not post this event.
 */
public final class AutoLoginSuppressionRequest {
    public static final long PENDING_LOGOUT_MILLIS = 15_000L;

    private enum Kind { REQUEST, RENEWAL, LOGGED_OUT }

    private final Kind kind;

    public AutoLoginSuppressionRequest() {
        this(Kind.REQUEST);
    }

    private AutoLoginSuppressionRequest(Kind kind) {
        this.kind = kind;
    }

    public static AutoLoginSuppressionRequest renewal() {
        return new AutoLoginSuppressionRequest(Kind.RENEWAL);
    }

    public static AutoLoginSuppressionRequest loggedOut() {
        return new AutoLoginSuppressionRequest(Kind.LOGGED_OUT);
    }

    public boolean isRenewal() {
        return kind == Kind.RENEWAL;
    }

    public boolean isLoggedOut() {
        return kind == Kind.LOGGED_OUT;
    }
}
