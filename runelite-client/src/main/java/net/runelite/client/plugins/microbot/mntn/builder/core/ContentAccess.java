package net.runelite.client.plugins.microbot.mntn.builder.core;

public enum ContentAccess {
    FREE_TO_PLAY,
    MEMBERS;

    public boolean isCurrentlyReachable(AccountSnapshot snapshot) {
        if (this == FREE_TO_PLAY) {
            return true;
        }
        return snapshot != null && snapshot.isMembersWorld();
    }
}
