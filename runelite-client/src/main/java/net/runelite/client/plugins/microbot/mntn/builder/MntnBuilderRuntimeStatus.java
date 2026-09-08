package net.runelite.client.plugins.microbot.mntn.builder;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Publishes the Builder's immutable overlay snapshot for read-only runtime diagnostics.
 * The HTTP server reads this value without touching the script's mutable task state.
 */
public final class MntnBuilderRuntimeStatus {

    private static final AtomicReference<MntnBuilderOverlayState> LATEST = new AtomicReference<>();

    private MntnBuilderRuntimeStatus() {
    }

    static void publish(MntnBuilderOverlayState state) {
        LATEST.set(state);
    }

    static void clear() {
        LATEST.set(null);
    }

    public static MntnBuilderOverlayState getLatest() {
        return LATEST.get();
    }
}
