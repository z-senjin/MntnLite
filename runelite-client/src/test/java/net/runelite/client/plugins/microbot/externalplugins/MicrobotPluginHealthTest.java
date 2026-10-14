package net.runelite.client.plugins.microbot.externalplugins;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MicrobotPluginHealthTest {
    private static final Gson GSON = new Gson();

    private static final String OLD_MANIFEST = "[{\"internalName\":\"OldPlugin\",\"name\":\"Old\",\"version\":\"1.0.0\","
            + "\"minClientVersion\":\"2.0.0\",\"disable\":false,\"sha256\":\"abc\"}]";

    private static MicrobotPluginManifest parseOne(String json) {
        List<MicrobotPluginManifest> manifests = GSON.fromJson(json, new TypeToken<List<MicrobotPluginManifest>>() {}.getType());
        return manifests.get(0);
    }

    private static MicrobotPluginManifest withHealth(String version, String health) {
        return parseOne("[{\"internalName\":\"Demo\",\"name\":\"Demo\",\"version\":\"" + version + "\","
                + "\"minClientVersion\":\"2.0.0\",\"disable\":false,\"health\":" + health + "}]");
    }

    @Test
    public void oldManifestWithoutHealthIsUnknownAndNotBlocking() {
        MicrobotPluginManifest manifest = parseOne(OLD_MANIFEST);
        MicrobotPluginHealth health = MicrobotPluginHealth.evaluate(manifest, null, true);

        assertNull(manifest.getHealth());
        assertEquals(MicrobotPluginHealth.State.UNKNOWN, health.getState());
        assertFalse(health.isBlocking());
        assertFalse(health.isWarning());
        assertTrue(health.getDetails("2.6.28").contains("No health report has been published for this plugin."));
    }

    @Test
    public void healthyPluginVerifiedForCurrentVersion() {
        MicrobotPluginManifest manifest = withHealth("1.2.0",
                "{\"status\":\"ok\",\"lastVerifiedVersion\":\"1.2.0\",\"lastVerifiedClientVersion\":\"2.6.28\"}");
        MicrobotPluginHealth health = MicrobotPluginHealth.evaluate(manifest, "1.2.0", true);

        assertEquals(MicrobotPluginHealth.State.VERIFIED, health.getState());
        assertFalse(health.isWarning());
        assertTrue(health.getDetails(null).contains("Last verified: plugin 1.2.0 on client 2.6.28"));
    }

    @Test
    public void okStatusVerifiedForOtherVersionIsNotClaimedVerified() {
        MicrobotPluginManifest manifest = withHealth("1.3.0", "{\"status\":\"ok\",\"lastVerifiedVersion\":\"1.2.0\"}");

        assertEquals(MicrobotPluginHealth.State.UNKNOWN, MicrobotPluginHealth.evaluate(manifest, "1.3.0", true).getState());
        assertEquals(MicrobotPluginHealth.State.VERIFIED, MicrobotPluginHealth.evaluate(manifest, "1.2.0", true).getState());
    }

    @Test
    public void incompatibleClientBlocksLatestVersionOnly() {
        MicrobotPluginManifest manifest = parseOne(OLD_MANIFEST);
        MicrobotPluginHealth latest = MicrobotPluginHealth.evaluate(manifest, "1.0.0", false);
        MicrobotPluginHealth older = MicrobotPluginHealth.evaluate(manifest, "0.9.0", false);

        assertEquals(MicrobotPluginHealth.State.INCOMPATIBLE, latest.getState());
        assertTrue(latest.isBlocking());
        assertEquals("Requires client 2.0.0", latest.getLabel());
        assertTrue(latest.getDetails("1.9.0").contains("Current client: 1.9.0"));
        assertEquals(MicrobotPluginHealth.State.UNKNOWN, older.getState());
    }

    @Test
    public void disabledUpstreamBlocksEveryVersionAndKeepsReason() {
        MicrobotPluginManifest manifest = parseOne("[{\"internalName\":\"Gone\",\"name\":\"Gone\",\"version\":\"2.0.0\","
                + "\"disable\":true,\"health\":{\"status\":\"broken\",\"reason\":\"Deprecated\"}}]");

        MicrobotPluginHealth health = MicrobotPluginHealth.evaluate(manifest, "1.0.0", true);
        assertEquals(MicrobotPluginHealth.State.DISABLED, health.getState());
        assertTrue(health.isBlocking());
        assertTrue(health.getDetails(null).contains("Reason: Deprecated"));

        manifest.setHealth(null);
        assertTrue(MicrobotPluginHealth.evaluate(manifest, null, true).getDetails(null).contains("Reason: not provided"));
    }

    @Test
    public void confirmedBrokenBlocksOnlyAffectedVersions() {
        MicrobotPluginManifest manifest = withHealth("1.2.1", "{\"status\":\"broken\",\"reason\":\"GE widget moved\","
                + "\"affectedVersions\":[\"1.2.0\",\"1.2.1\"],\"trackingUrl\":\"https://github.com/chsami/Microbot-Hub/issues/1\","
                + "\"lastVerifiedVersion\":\"1.1.9\",\"lastVerifiedClientVersion\":\"2.6.27\",\"updatedAt\":\"2026-10-05\"}");

        MicrobotPluginHealth broken = MicrobotPluginHealth.evaluate(manifest, "1.2.1", true);
        assertEquals(MicrobotPluginHealth.State.BROKEN, broken.getState());
        assertTrue(broken.isBlocking());
        assertEquals("https://github.com/chsami/Microbot-Hub/issues/1", broken.getTrackingUrl());
        List<String> details = broken.getDetails(null);
        assertTrue(details.contains("Reason: GE widget moved"));
        assertTrue(details.contains("Affected versions: 1.2.0, 1.2.1"));
        assertTrue(details.contains("Last verified: plugin 1.1.9 on client 2.6.27"));
        assertTrue(details.contains("Updated: 2026-10-05"));

        MicrobotPluginHealth unaffected = MicrobotPluginHealth.evaluate(manifest, "1.1.9", true);
        assertEquals(MicrobotPluginHealth.State.VERIFIED, unaffected.getState());
        assertFalse(unaffected.isBlocking());

        assertEquals(MicrobotPluginHealth.State.UNKNOWN, MicrobotPluginHealth.evaluate(manifest, "1.0.0", true).getState());
    }

    @Test
    public void recoveredReleaseIsInstallableWhileOldReleaseStaysBlocked() {
        MicrobotPluginManifest manifest = withHealth("1.2.2", "{\"status\":\"broken\",\"affectedVersions\":[\"1.2.1\"]}");

        MicrobotPluginHealth latest = MicrobotPluginHealth.evaluate(manifest, null, true);
        assertEquals("1.2.2", latest.getVersion());
        assertFalse(latest.isBlocking());
        assertTrue(MicrobotPluginHealth.evaluate(manifest, "1.2.1", true).isBlocking());

        manifest.setHealth(withHealth("1.2.2", "{\"status\":\"ok\"}").getHealth());
        assertEquals(MicrobotPluginHealth.State.VERIFIED, MicrobotPluginHealth.evaluate(manifest, null, true).getState());
        assertFalse(MicrobotPluginHealth.evaluate(manifest, "1.2.1", true).isBlocking());
    }

    @Test
    public void wildcardAffectsAllVersions() {
        MicrobotPluginManifest manifest = withHealth("3.0.0", "{\"status\":\"BROKEN\",\"affectedVersions\":[\"*\"]}");

        assertTrue(MicrobotPluginHealth.evaluate(manifest, "1.0.0", true).isBlocking());
        assertTrue(MicrobotPluginHealth.evaluate(manifest, null, true).getDetails(null).contains("Affected versions: all versions"));
    }

    @Test
    public void unverifiedReportsAndUnscopedBreakageNeverBlock() {
        MicrobotPluginHealth unverified = MicrobotPluginHealth.evaluate(
                withHealth("1.0.0", "{\"status\":\"unverified\",\"reason\":\"Stalls after banking\"}"), null, true);
        assertEquals(MicrobotPluginHealth.State.UNVERIFIED, unverified.getState());
        assertTrue(unverified.isWarning());
        assertFalse(unverified.isBlocking());
        assertTrue(unverified.getDetails(null).contains("Last verified: plugin unknown on client unknown"));

        MicrobotPluginHealth unscoped = MicrobotPluginHealth.evaluate(withHealth("1.0.0", "{\"status\":\"broken\"}"), null, true);
        assertEquals(MicrobotPluginHealth.State.UNVERIFIED, unscoped.getState());
        assertFalse(unscoped.isBlocking());
        assertEquals("Reported broken, versions not specified", unscoped.getLabel());
        assertTrue(unscoped.getDetails(null).contains("Affected versions: not specified"));
    }

    @Test
    public void unknownStatusAndUnsafeTrackingUrlAreIgnored() {
        MicrobotPluginHealth health = MicrobotPluginHealth.evaluate(
                withHealth("1.0.0", "{\"status\":\"stale\",\"trackingUrl\":\"file:///etc/passwd\"}"), null, true);

        assertEquals(MicrobotPluginHealth.State.UNKNOWN, health.getState());
        assertNull(health.getTrackingUrl());
        assertTrue(health.getDetails(null).contains("Tracking: none"));
        assertNull(MicrobotPluginHealth.evaluate(
                withHealth("1.0.0", "{\"status\":\"unverified\",\"trackingUrl\":\"http://example.com/issue\"}"), null, true).getTrackingUrl());
    }

    @Test
    public void malformedHealthDoesNotBreakManifestParsing() {
        String json = "[{\"internalName\":\"A\",\"name\":\"A\",\"version\":\"1.0.0\",\"health\":\"broken\"},"
                + "{\"internalName\":\"B\",\"name\":\"B\",\"version\":\"1.0.0\",\"health\":{\"status\":\"broken\",\"affectedVersions\":{\"x\":1}}},"
                + "{\"internalName\":\"C\",\"name\":\"C\",\"version\":\"1.0.0\",\"health\":null},"
                + "{\"internalName\":\"D\",\"name\":\"D\",\"version\":\"1.0.0\",\"health\":{\"status\":\"broken\",\"affectedVersions\":[\"1.0.0\"]}}]";
        List<MicrobotPluginManifest> manifests = GSON.fromJson(json, new TypeToken<List<MicrobotPluginManifest>>() {}.getType());

        assertEquals(4, manifests.size());
        assertNull(manifests.get(0).getHealth());
        assertNull(manifests.get(1).getHealth());
        assertNull(manifests.get(2).getHealth());
        assertNotNull(manifests.get(3).getHealth());
        assertTrue(MicrobotPluginHealth.evaluate(manifests.get(3), null, true).isBlocking());
    }

    @Test
    public void newManifestRoundTripsHealthForOlderConsumers() {
        MicrobotPluginManifest manifest = withHealth("1.0.0", "{\"status\":\"broken\",\"affectedVersions\":[\"1.0.0\"]}");
        String json = GSON.toJson(manifest);

        assertTrue(json.contains("\"health\":{\"status\":\"broken\""));
        assertTrue(MicrobotPluginHealth.evaluate(GSON.fromJson(json, MicrobotPluginManifest.class), null, true).isBlocking());
    }
}
