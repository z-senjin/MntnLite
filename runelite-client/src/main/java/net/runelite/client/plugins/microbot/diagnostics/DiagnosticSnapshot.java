package net.runelite.client.plugins.microbot.diagnostics;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class DiagnosticSnapshot
{
	String microbotVersion;
	String runeliteVersion;
	String microbotCommit;
	String sourceCommit;
	Boolean sourceDirty;
	String buildChannel;
	String buildRepository;
	String launcherVersion;
	String javaVersion;
	String javaVendor;
	String osName;
	String osArch;
	Boolean safeMode;
	Integer gameRevision;
	String walker;
	String plannerMode;
	String mouse;
	Boolean naturalMouse;
	Boolean gpuPluginEnabled;
	Boolean gpuRendererActive;
	@Singular
	Map<String, String> walkerSettings;
	@Singular
	List<Plugin> plugins;

	@Value
	@Builder
	public static class Plugin
	{
		String name;
		String internalName;
		String descriptorVersion;
		String installedVersion;
		String latestVersion;
		boolean hubInstalled;
		boolean active;
	}
}
