/*
 * Copyright (c) 2021, Alexsuperfly <alexsuperfly@users.noreply.github.com>
 * Copyright (c) 2021, Jordan Atwood <nightfirecat@protonmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.interacthighlight;

import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.ui.overlay.OverlayManager;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InteractHighlightPluginTest
{
	@Inject
	private InteractHighlightPlugin plugin;

	@Mock
	@Bind
	private Client client;

	@Mock
	@Bind
	private OverlayManager overlayManager;

	@Mock
	@Bind
	private InteractHighlightOverlay overlay;

	@Mock
	private Player player;

	@Mock
	private NPC npc;

	@Mock
	private MenuEntry entry;

	@Mock
	private MenuOptionClicked click;

	@Before
	public void clickNpc()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
		when(client.getTickCount()).thenReturn(10);
		when(click.getMenuAction()).thenReturn(MenuAction.NPC_FIRST_OPTION);
		when(click.getMenuEntry()).thenReturn(entry);
		when(entry.getNpc()).thenReturn(npc);
		plugin.onMenuOptionClicked(click);
		assertSame(npc, plugin.getInteractedActor());
	}

	@Test
	public void clearsFailedInteractionWithoutAnotherInteractionEvent()
	{
		when(client.getLocalPlayer()).thenReturn(player);
		plugin.onInteractingChanged(new InteractingChanged(player, null));
		assertSame(npc, plugin.getInteractedActor());

		when(client.getTickCount()).thenReturn(11);
		plugin.onGameTick(new GameTick());
		assertNull(plugin.getInteractedActor());
	}

	@Test
	public void preservesActiveInteractionAfterArrival()
	{
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getInteracting()).thenReturn(npc);
		when(client.getTickCount()).thenReturn(11);
		plugin.onGameTick(new GameTick());
		assertSame(npc, plugin.getInteractedActor());
	}

	@Test
	public void preservesClickedActorWhileWalking()
	{
		when(client.getTickCount()).thenReturn(11);
		when(client.getLocalDestinationLocation()).thenReturn(new LocalPoint(128, 128));
		plugin.onGameTick(new GameTick());
		assertSame(npc, plugin.getInteractedActor());
	}

	@Test
	public void preservesClickedActorDuringClickTick()
	{
		plugin.onGameTick(new GameTick());
		assertSame(npc, plugin.getInteractedActor());
	}

	@Test
	public void clearsClickedActorWhenLocalPlayerDisappears()
	{
		when(client.getTickCount()).thenReturn(11);
		plugin.onGameTick(new GameTick());
		assertNull(plugin.getInteractedActor());
	}
}
