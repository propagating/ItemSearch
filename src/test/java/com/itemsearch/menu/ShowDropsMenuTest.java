/*
 * Copyright (c) 2026, propagating <propagating@protonmail.com>
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
package com.itemsearch.menu;

import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuOpened;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.model.SourceData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ShowDropsMenuTest
{
	private static final int GOBLIN = 3031;

	private Client client;
	private BundledDataSource data;
	private AtomicReference<SourceData> shown;
	private ShowDropsMenu menu;
	private MenuEntry created;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		created = mock(MenuEntry.class, org.mockito.Mockito.RETURNS_SELF);
		org.mockito.Mockito.lenient().when(client.createMenuEntry(anyInt())).thenReturn(created);

		data = new BundledDataSource(new Gson(), "/com/itemsearch/sources-test.json");
		data.load();
		shown = new AtomicReference<>();
		menu = new ShowDropsMenu(client, data, shown::set);
	}

	private MenuOpened npcMenu(int npcId, String name, int level)
	{
		NPC npc = mock(NPC.class);
		org.mockito.Mockito.lenient().when(npc.getId()).thenReturn(npcId);
		org.mockito.Mockito.lenient().when(npc.getName()).thenReturn(name);
		org.mockito.Mockito.lenient().when(npc.getCombatLevel()).thenReturn(level);

		MenuEntry attack = mock(MenuEntry.class);
		when(attack.getType()).thenReturn(MenuAction.NPC_SECOND_OPTION);
		org.mockito.Mockito.lenient().when(attack.getNpc()).thenReturn(npc);

		MenuOpened event = mock(MenuOpened.class);
		when(event.getMenuEntries()).thenReturn(new MenuEntry[]{attack});
		return event;
	}

	@Test
	public void addsShowDropsForAnNpcWeHaveDataFor()
	{
		menu.onMenuOpened(npcMenu(GOBLIN, "Goblin", 2));

		verify(client).createMenuEntry(anyInt());
		verify(created).setOption("Show information");
		// a RuneLite-type entry is handled entirely in the client; nothing reaches the server
		verify(created).setType(MenuAction.RUNELITE);
	}

	@Test
	public void clickingItOpensThatNpc()
	{
		AtomicReference<java.util.function.Consumer<MenuEntry>> handler = new AtomicReference<>();
		when(created.onClick(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
		{
			handler.set(inv.getArgument(0));
			return created;
		});

		menu.onMenuOpened(npcMenu(GOBLIN, "Goblin", 2));
		assertNotNull("the entry must carry a click handler", handler.get());

		handler.get().accept(created);
		assertNotNull("clicking must open a source", shown.get());
		assertEquals(GOBLIN, shown.get().getId());
	}

	// an entry that leads nowhere is worse than no entry
	@Test
	public void addsNothingForAnNpcWeHaveNoDataFor()
	{
		menu.onMenuOpened(npcMenu(999999, "Unknown thing", 1));
		verify(client, never()).createMenuEntry(anyInt());
	}

	@Test
	public void addsNothingWhenNoNpcIsInTheMenu()
	{
		MenuEntry walk = mock(MenuEntry.class);
		when(walk.getType()).thenReturn(MenuAction.WALK);
		MenuOpened event = mock(MenuOpened.class);
		when(event.getMenuEntries()).thenReturn(new MenuEntry[]{walk});

		menu.onMenuOpened(event);
		verify(client, never()).createMenuEntry(anyInt());
	}

	/**
	 * The bundled data does not carry every in-game npc id, so an id miss falls back to the
	 * name and level the game reports — the same resolution kill tracking uses.
	 */
	@Test
	public void resolvesByNameAndLevelWhenTheIdIsUnknown()
	{
		AtomicReference<java.util.function.Consumer<MenuEntry>> handler = new AtomicReference<>();
		when(created.onClick(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
		{
			handler.set(inv.getArgument(0));
			return created;
		});

		menu.onMenuOpened(npcMenu(888888, "Goblin", 2));
		assertNotNull(handler.get());
		handler.get().accept(created);
		assertEquals(GOBLIN, shown.get().getId());
	}

	private static final int BRONZE_DAGGER = 1205;

	private ShowDropsMenu withItems(AtomicReference<com.itemsearch.model.ItemMatch> shownItem)
	{
		net.runelite.client.game.ItemManager itemManager = mock(net.runelite.client.game.ItemManager.class);
		org.mockito.Mockito.lenient().when(itemManager.canonicalize(anyInt()))
			.thenAnswer(inv -> inv.getArgument(0));
		org.mockito.Mockito.lenient().when(itemManager.canonicalize(1206)).thenReturn(BRONZE_DAGGER);
		return new ShowDropsMenu(client, data, itemManager, shown::set, shownItem::set);
	}

	private static MenuOpened itemMenu(int itemId)
	{
		MenuEntry wield = mock(MenuEntry.class);
		org.mockito.Mockito.lenient().when(wield.getType()).thenReturn(MenuAction.CC_OP);
		when(wield.getItemId()).thenReturn(itemId);
		MenuOpened event = mock(MenuOpened.class);
		when(event.getMenuEntries()).thenReturn(new MenuEntry[]{wield});
		return event;
	}

	/** An item in the inventory carries its id on the menu entry; its page is one click away. */
	@Test
	public void addsShowInformationForAnItemWeHaveDataFor()
	{
		AtomicReference<com.itemsearch.model.ItemMatch> shownItem = new AtomicReference<>();
		AtomicReference<java.util.function.Consumer<MenuEntry>> handler = new AtomicReference<>();
		when(created.onClick(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
		{
			handler.set(inv.getArgument(0));
			return created;
		});

		withItems(shownItem).onMenuOpened(itemMenu(BRONZE_DAGGER));

		verify(created).setOption("Show information");
		verify(created).setTarget("<col=ff9040>Bronze dagger</col>");
		verify(created).setType(MenuAction.RUNELITE);
		handler.get().accept(created);
		assertEquals(BRONZE_DAGGER, shownItem.get().getItemId());
		assertNull("an item is not a source", shown.get());
	}

	/** A noted dagger opens the dagger's page. */
	@Test
	public void aNotedItemOpensTheItemItself()
	{
		AtomicReference<com.itemsearch.model.ItemMatch> shownItem = new AtomicReference<>();
		AtomicReference<java.util.function.Consumer<MenuEntry>> handler = new AtomicReference<>();
		when(created.onClick(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
		{
			handler.set(inv.getArgument(0));
			return created;
		});
		withItems(shownItem).onMenuOpened(itemMenu(1206));
		handler.get().accept(created);
		assertEquals(BRONZE_DAGGER, shownItem.get().getItemId());
	}

	/** An item on the ground names itself through the entry's identifier instead. */
	@Test
	public void aGroundItemIsFoundByItsIdentifier()
	{
		MenuEntry take = mock(MenuEntry.class);
		when(take.getType()).thenReturn(MenuAction.GROUND_ITEM_THIRD_OPTION);
		when(take.getItemId()).thenReturn(-1);
		when(take.getIdentifier()).thenReturn(BRONZE_DAGGER);
		MenuOpened event = mock(MenuOpened.class);
		when(event.getMenuEntries()).thenReturn(new MenuEntry[]{take});

		withItems(new AtomicReference<>()).onMenuOpened(event);
		verify(created).setTarget("<col=ff9040>Bronze dagger</col>");
	}

	/** An item nothing drops and no shop sells has no page worth opening. */
	@Test
	public void addsNothingForAnItemWeHaveNoDataFor()
	{
		withItems(new AtomicReference<>()).onMenuOpened(itemMenu(4151));
		verify(client, never()).createMenuEntry(anyInt());
	}

	/** Without an item callback, items are left alone. */
	@Test
	public void theNpcOnlyMenuIgnoresItems()
	{
		menu.onMenuOpened(itemMenu(BRONZE_DAGGER));
		verify(client, never()).createMenuEntry(anyInt());
	}

	@Test
	public void aDisabledMenuAddsNothing()
	{
		ShowDropsMenu off = new ShowDropsMenu(client, data, shown::set);
		off.setEnabled(false);
		off.onMenuOpened(npcMenu(GOBLIN, "Goblin", 2));
		verify(client, never()).createMenuEntry(anyInt());
		assertNull(shown.get());
	}
}
