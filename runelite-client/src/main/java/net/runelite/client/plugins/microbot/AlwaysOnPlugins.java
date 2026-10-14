package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.PluginDescriptor;

public final class AlwaysOnPlugins
{
	public static final String TOOLTIP = "Always on: required by Microbot";

	private AlwaysOnPlugins()
	{
	}

	public static boolean isLocked(PluginDescriptor descriptor)
	{
		return descriptor != null && descriptor.alwaysOn() && !descriptor.isExternal();
	}
}
