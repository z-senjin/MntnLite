package net.runelite.client.plugins.microbot.testing.release;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CheckContext {

    @FunctionalInterface
    public interface Cleanup {
        void run() throws Exception;
    }

    public static final class NamedCleanup {
        final String name;
        final Cleanup action;

        NamedCleanup(String name, Cleanup action) {
            this.name = name;
            this.action = action;
        }
    }

    private final String checkId;
    private final Map<String, Object> details = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<NamedCleanup> cleanups = Collections.synchronizedList(new ArrayList<>());

    public CheckContext(String checkId) {
        this.checkId = checkId;
    }

    public String getCheckId() {
        return checkId;
    }

    public void detail(String key, Object value) {
        details.put(key, value == null ? null : String.valueOf(value));
    }

    public void onCleanup(String name, Cleanup action) {
        cleanups.add(new NamedCleanup(name, action));
    }

    public void require(boolean condition, String message) {
        if (!condition) {
            throw new CheckFailedException(message);
        }
    }

    public void skip(String reason) {
        throw new CheckSkippedException(reason);
    }

    Map<String, Object> snapshotDetails() {
        synchronized (details) {
            return new LinkedHashMap<>(details);
        }
    }

    List<NamedCleanup> cleanupsInReverseOrder() {
        List<NamedCleanup> copy;
        synchronized (cleanups) {
            copy = new ArrayList<>(cleanups);
        }
        Collections.reverse(copy);
        return copy;
    }
}
