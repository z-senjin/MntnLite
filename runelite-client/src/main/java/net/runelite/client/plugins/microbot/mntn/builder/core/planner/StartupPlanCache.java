package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.RuneLite;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Properties;

/** Persists one last-known planning direction for each local Microbot profile. */
public final class StartupPlanCache {

    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final String CACHE_VERSION = "1";
    private static Path cacheDirectory = RuneLite.RUNELITE_DIR.toPath()
            .resolve("microbot")
            .resolve("mntn-builder");

    private StartupPlanCache() {
    }

    public static synchronized void remember(String profileId, String configurationFingerprint, Plan plan) {
        if (profileId == null || configurationFingerprint == null || plan == null || plan.goal() == null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("version", CACHE_VERSION);
        properties.setProperty("configurationFingerprint", configurationFingerprint);
        properties.setProperty("goalName", plan.goal().name());
        properties.setProperty("updatedAtMs", String.valueOf(System.currentTimeMillis()));
        write(profileId, properties);
    }

    public static synchronized Direction takeIfUsable(String profileId, String configurationFingerprint) {
        if (profileId == null || configurationFingerprint == null) {
            return null;
        }

        Properties properties = read(profileId);
        if (properties == null
                || !CACHE_VERSION.equals(properties.getProperty("version"))
                || !configurationFingerprint.equals(properties.getProperty("configurationFingerprint"))) {
            return null;
        }

        String goalName = properties.getProperty("goalName");
        long updatedAtMs = parseLong(properties.getProperty("updatedAtMs"));
        if (goalName == null || goalName.isBlank()
                || updatedAtMs <= 0
                || System.currentTimeMillis() - updatedAtMs > MAX_AGE.toMillis()) {
            return null;
        }

        return new Direction(goalName);
    }

    static synchronized void setCacheDirectoryForTests(Path directory) {
        cacheDirectory = directory;
    }

    static synchronized void resetCacheDirectoryForTests() {
        cacheDirectory = RuneLite.RUNELITE_DIR.toPath().resolve("microbot").resolve("mntn-builder");
    }

    private static Properties read(String profileId) {
        Path path = cachePath(profileId);
        if (!Files.isRegularFile(path)) {
            return null;
        }

        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            return properties;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void write(String profileId, Properties properties) {
        try {
            Files.createDirectories(cacheDirectory);
            Path target = cachePath(profileId);
            Path temporary = Files.createTempFile(cacheDirectory, "startup-direction-", ".tmp");
            try (java.io.Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "Mntn Builder startup direction");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Startup selection falls back to the full planner if persistence is unavailable.
        }
    }

    private static Path cachePath(String profileId) {
        return cacheDirectory.resolve("startup-direction-" + profileId + ".properties");
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static final class Direction {
        private final String goalName;

        private Direction(String goalName) {
            this.goalName = goalName;
        }

        public String goalName() {
            return goalName;
        }
    }
}
