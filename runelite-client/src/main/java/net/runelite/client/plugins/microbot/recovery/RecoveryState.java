package net.runelite.client.plugins.microbot.recovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class RecoveryState
{
	boolean sessionOpen;
	long pid;
	Long processStart;
	long startedAt;
	String microbotVersion;
	boolean safeModeSession;
	String startingPlugin;
	String startupError;
	List<PluginRecord> activePlugins = new ArrayList<>();
	List<PluginFailure> failures = new ArrayList<>();
	Map<String, String> configKeys = new HashMap<>();

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PluginRecord
	{
		String plugin;
		String version;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PluginFailure
	{
		String plugin;
		String version;
		String phase;
		String reason;
	}
}
