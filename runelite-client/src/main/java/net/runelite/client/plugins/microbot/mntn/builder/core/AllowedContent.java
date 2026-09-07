package net.runelite.client.plugins.microbot.mntn.builder.core;

public enum AllowedContent {
    F2P_ONLY,
    ALL;

    public boolean allows(ContentAccess access) {
        return access == ContentAccess.FREE_TO_PLAY || this == ALL;
    }

    @Override
    public String toString() {
        switch (this) {
            case ALL:
                return "All content";
            case F2P_ONLY:
            default:
                return "F2P only";
        }
    }
}
