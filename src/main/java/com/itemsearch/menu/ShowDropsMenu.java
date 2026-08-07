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

import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuOpened;
import com.itemsearch.data.DropDataService;
import com.itemsearch.model.SourceData;

/**
 * Adds a "Show drops" option to the right-click menu of any NPC this plugin has data for,
 * opening that NPC's page in the sidebar.
 *
 * <p>The entry is {@link MenuAction#RUNELITE}: clicking it runs a callback in the client and
 * sends nothing to the server, which is what keeps it within the third-party client rules on
 * menu entries. Nothing is ever removed or reordered — the option is only added, and only when
 * it leads somewhere, since an entry that opens an empty page is worse than no entry.
 */
public class ShowDropsMenu
{
	private static final String OPTION = "Show drops";

	private final Client client;
	private final DropDataService data;
	private final Consumer<SourceData> onShow;

	private boolean enabled = true;

	public ShowDropsMenu(Client client, DropDataService data, Consumer<SourceData> onShow)
	{
		this.client = client;
		this.data = data;
		this.onShow = onShow;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	public void onMenuOpened(MenuOpened event)
	{
		if (!enabled)
		{
			return;
		}
		SourceData source = resolve(npcIn(event));
		if (source == null)
		{
			return;
		}
		client.createMenuEntry(-1)
			.setOption(OPTION)
			.setTarget(target(source))
			.setType(MenuAction.RUNELITE)
			.onClick(e -> onShow.accept(source));
	}

	private static NPC npcIn(MenuOpened event)
	{
		MenuEntry[] entries = event.getMenuEntries();
		if (entries == null)
		{
			return null;
		}
		for (MenuEntry entry : entries)
		{
			if (entry != null && isNpcEntry(entry.getType()) && entry.getNpc() != null)
			{
				return entry.getNpc();
			}
		}
		return null;
	}

	private static boolean isNpcEntry(MenuAction type)
	{
		if (type == null)
		{
			return false;
		}
		switch (type)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case EXAMINE_NPC:
			case WIDGET_TARGET_ON_NPC:
				return true;
			default:
				return false;
		}
	}

	/**
	 * The bundled data does not list every in-game npc id, so an id miss falls back to the name
	 * and combat level the game reports — the same resolution kill tracking uses.
	 */
	private SourceData resolve(NPC npc)
	{
		if (npc == null)
		{
			return null;
		}
		SourceData byId = data.sourceById(npc.getId());
		return byId != null ? byId : data.sourceByNameAndLevel(npc.getName(), npc.getCombatLevel());
	}

	// the game colours a menu target itself; this matches how other entries name their NPC
	private static String target(SourceData source)
	{
		return "<col=ffff00>" + source.getName() + "</col>";
	}
}
