package net.runelite.client.plugins;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.AlwaysOnPlugins;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.task.Scheduler;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PluginManagerAlwaysOnTest
{
	@PluginDescriptor(name = "Always On Fixture", alwaysOn = true, enabledByDefault = false)
	static class AlwaysOnFixturePlugin extends Plugin
	{
	}

	@PluginDescriptor(name = "External Always On Fixture", alwaysOn = true, isExternal = true)
	static class ExternalAlwaysOnFixturePlugin extends Plugin
	{
	}

	@PluginDescriptor(name = "Regular Fixture")
	static class RegularFixturePlugin extends Plugin
	{
	}

	private ConfigManager configManager;
	private PluginManager pluginManager;

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		pluginManager = new PluginManager(false, mock(EventBus.class), mock(Scheduler.class), configManager, () -> null, null);
	}

	@Test
	public void disablingAlwaysOnPluginDoesNotPersistFalse()
	{
		pluginManager.setPluginEnabled(new AlwaysOnFixturePlugin(), false);

		verify(configManager, never()).setConfiguration(eq(RuneLiteConfig.GROUP_NAME), anyString(), anyString());
	}

	@Test
	public void enablingAlwaysOnPluginStillPersistsTrue()
	{
		pluginManager.setPluginEnabled(new AlwaysOnFixturePlugin(), true);

		verify(configManager).setConfiguration(RuneLiteConfig.GROUP_NAME, "alwaysonfixtureplugin", "true");
	}

	@Test
	public void disablingRegularPluginPersistsFalse()
	{
		pluginManager.setPluginEnabled(new RegularFixturePlugin(), false);

		verify(configManager).setConfiguration(RuneLiteConfig.GROUP_NAME, "regularfixtureplugin", "false");
	}

	@Test
	public void alwaysOnPluginStaysEnabledWithLegacyFalseValue()
	{
		when(configManager.getConfiguration(RuneLiteConfig.GROUP_NAME, "alwaysonfixtureplugin")).thenReturn("false");

		assertTrue(pluginManager.isPluginEnabled(new AlwaysOnFixturePlugin()));
	}

	@Test
	public void isPluginAlwaysOnReflectsDescriptor()
	{
		assertTrue(pluginManager.isPluginAlwaysOn(new AlwaysOnFixturePlugin()));
		assertFalse(pluginManager.isPluginAlwaysOn(new RegularFixturePlugin()));
	}

	@Test
	public void externalAlwaysOnPluginIsNotLocked()
	{
		ExternalAlwaysOnFixturePlugin plugin = new ExternalAlwaysOnFixturePlugin();

		assertFalse(pluginManager.isPluginAlwaysOn(plugin));
		assertFalse(AlwaysOnPlugins.isLocked(plugin.getClass().getAnnotation(PluginDescriptor.class)));

		pluginManager.setPluginEnabled(plugin, false);

		verify(configManager).setConfiguration(RuneLiteConfig.GROUP_NAME, "externalalwaysonfixtureplugin", "false");
	}

	@Test
	public void externalAlwaysOnPluginHonorsSavedDisabledState()
	{
		when(configManager.getConfiguration(RuneLiteConfig.GROUP_NAME, "externalalwaysonfixtureplugin")).thenReturn("false");

		assertFalse(pluginManager.isPluginEnabled(new ExternalAlwaysOnFixturePlugin()));
	}

	@Test
	public void walkerInventorySetupsAndMicrobotStayAlwaysOn()
	{
		assertTrue(ShortestPathPlugin.class.getAnnotation(PluginDescriptor.class).alwaysOn());
		assertTrue(MInventorySetupsPlugin.class.getAnnotation(PluginDescriptor.class).alwaysOn());
		PluginDescriptor microbot = MicrobotPlugin.class.getAnnotation(PluginDescriptor.class);
		assertTrue(microbot.alwaysOn());
		assertTrue(microbot.hidden());
	}
}
