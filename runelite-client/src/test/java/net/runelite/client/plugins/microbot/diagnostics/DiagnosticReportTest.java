package net.runelite.client.plugins.microbot.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DiagnosticReportTest
{
	private static DiagnosticSnapshot.DiagnosticSnapshotBuilder official()
	{
		return DiagnosticSnapshot.builder()
			.microbotVersion("2.6.25")
			.runeliteVersion("1.13.0")
			.microbotCommit("3032e97")
			.sourceCommit("3032e97")
			.sourceDirty(false)
			.buildChannel("stable")
			.buildRepository("chsami/Microbot")
			.launcherVersion("2.2.1")
			.javaVersion("17.0.12")
			.javaVendor("Eclipse Adoptium")
			.osName("Windows 11")
			.osArch("amd64")
			.safeMode(false)
			.gameRevision(235)
			.walker("Rs2Walker (built into client)")
			.plannerMode("LOCAL")
			.mouse("VirtualMouse")
			.naturalMouse(true)
			.gpuPluginEnabled(true)
			.gpuRendererActive(true)
			.walkerSetting("autoRun", "on")
			.walkerSetting("bankedTransports", "off");
	}

	@Test
	public void officialBuildReportsProvenanceSettingsAndPlugins()
	{
		String report = DiagnosticReport.render(official()
			.plugin(DiagnosticSnapshot.Plugin.builder().name("<html><font color=green>AIO Fighter</font></html>")
				.internalName("AIOFighterPlugin").descriptorVersion("2.0.4").installedVersion("2.0.4").latestVersion("2.0.5")
				.hubInstalled(true).active(true).build())
			.plugin(DiagnosticSnapshot.Plugin.builder().name("My Script").internalName("MyScriptPlugin")
				.descriptorVersion("1.0.0").hubInstalled(false).active(false).build())
			.build());

		assertTrue(report, report.contains("Microbot: 2.6.25\n"));
		assertTrue(report, report.contains("RuneLite: 1.13.0\n"));
		assertTrue(report, report.contains("Commit: 3032e97 (build metadata)\n"));
		assertTrue(report, report.contains("Build origin: official stable (chsami/Microbot workflow)\n"));
		assertTrue(report, report.contains("GPU: plugin enabled, renderer active\n"));
		assertTrue(report, report.contains("Walker: Rs2Walker (built into client), planner LOCAL\n"));
		assertTrue(report, report.contains("Walker settings: autoRun=on, bankedTransports=off\n"));
		assertTrue(report, report.contains("External plugins (2):\n"));
		assertTrue(report, report.contains("- AIO Fighter (AIOFighterPlugin) 2.0.4 [hub, installed 2.0.4, hub latest 2.0.5, active]\n"));
		assertTrue(report, report.contains("- My Script (MyScriptPlugin) 1.0.0 [sideloaded or unknown source, inactive]\n"));
		assertFalse(report, report.contains("<"));
	}

	@Test
	public void missingMetadataIsLabelledUnknown()
	{
		String report = DiagnosticReport.render(DiagnosticSnapshot.builder().build());

		assertTrue(report, report.contains("Microbot: unknown\n"));
		assertTrue(report, report.contains("Commit: unknown (not embedded)\n"));
		assertTrue(report, report.contains("Build origin: unknown (no build-origin metadata"));
		assertTrue(report, report.contains("Launcher: unknown\n"));
		assertTrue(report, report.contains("Game revision: unknown\n"));
		assertTrue(report, report.contains("GPU: plugin unknown, renderer unknown\n"));
		assertTrue(report, report.contains("Walker settings: unknown\n"));
		assertTrue(report, report.contains("External plugins (0): none loaded\n"));
		assertFalse(report, report.toLowerCase().contains("official"));
	}

	@Test
	public void customBuildIsNotReportedAsOfficial()
	{
		String report = DiagnosticReport.render(official().buildRepository("someone/Microbot-WalkerV2").build());
		assertTrue(report, report.contains("Build origin: custom stable build from someone/Microbot-WalkerV2\n"));
		assertFalse(report, report.contains("official"));
	}

	@Test
	public void sanitizerRemovesPathsSecretsAndMarkup()
	{
		assertEquals("Plugin [path]", DiagnosticReport.clean("Plugin /home/alice/.runelite/microbot-plugins/x.jar"));
		assertEquals("Plugin [path]", DiagnosticReport.clean("Plugin C:\\Users\\Alice\\.runelite\\x.jar"));
		assertEquals("[path]", DiagnosticReport.clean("~/.runelite"));
		assertEquals("jar [path]", DiagnosticReport.clean("jar /data/microbot/plugins/x.jar"));
		assertEquals("[path] and [path]", DiagnosticReport.clean("/srv/bot and /run/user/1000/bus"));
		assertEquals("see https://example.com/a/b", DiagnosticReport.clean("see https://example.com/a/b"));
		assertEquals("token [redacted]", DiagnosticReport.clean("token 6f1c2a9b8e7d6c5b4a39281706f5e4d3c2b1a0ff"));
		assertEquals("mail [redacted]", DiagnosticReport.clean("mail alice@example.com"));
		assertEquals("socks5://[redacted]@proxy:1080", DiagnosticReport.clean("socks5://user:pass@proxy:1080"));
		assertEquals("Fishing/Cooking AIO", DiagnosticReport.clean("<b>Fishing/Cooking</b>\nAIO"));
		assertEquals(DiagnosticReport.MAX_VALUE_LENGTH, DiagnosticReport.clean("x ".repeat(100)).length());
	}

	@Test
	public void pluginFieldsAreSanitized()
	{
		String report = DiagnosticReport.render(official()
			.plugin(DiagnosticSnapshot.Plugin.builder().name("Evil /home/bob/secret").internalName("EvilPlugin")
				.descriptorVersion("1.0\nInjected: line").hubInstalled(false).active(true).build())
			.build());
		assertFalse(report, report.contains("/home/bob"));
		assertFalse(report, report.contains("\nInjected"));
		assertTrue(report, report.contains("- Evil [path] (EvilPlugin) 1.0 Injected: line [sideloaded or unknown source, active]\n"));
	}
}
