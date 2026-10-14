package net.runelite.client.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ConfigProfileTest
{
	@Test
	public void toStringExcludesCredentials()
	{
		ConfigProfile profile = new ConfigProfile(42L);
		profile.setName("login-name-marker");
		profile.setPassword("password-marker");
		profile.setBankPin("9182");
		profile.setDiscordWebhookUrl("https://discord.test/api/webhooks/webhook-marker");

		String text = profile.toString();

		assertTrue(text.contains("id=42"));
		assertFalse(text.contains("login-name-marker"));
		assertFalse(text.contains("password-marker"));
		assertFalse(text.contains("9182"));
		assertFalse(text.contains("webhook-marker"));
		assertFalse(text.contains("password="));
		assertFalse(text.contains("bankPin="));
		assertFalse(text.contains("discordWebhookUrl="));
	}
}
