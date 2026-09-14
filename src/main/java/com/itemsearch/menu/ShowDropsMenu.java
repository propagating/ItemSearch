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

import java.util.List;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.game.ItemManager;
import com.itemsearch.data.DropDataService;
import com.itemsearch.model.DropSource;
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.ShopEntry;
import com.itemsearch.model.SourceData;

/**
 * Adds a "Show information" option to the right-click menu of any NPC or item this plugin has
 * data for, opening that NPC's or item's page in the sidebar.
 *
 * <p>The entry is {@link MenuAction#RUNELITE}: clicking it runs a callback in the client and
 * sends nothing to the server, which is what keeps it within the third-party client rules on
 * menu entries. Nothing is ever removed or reordered — the option is only added, and only when
 * it leads somewhere, since an entry that opens an empty page is worse than no entry.
 */
public class ShowDropsMenu
{
	private static final String OPTION = "Show information";

	private final Client client;
	private final DropDataService data;
	private final ItemManager itemManager;
	private final Consumer<SourceData> onShow;
	private final Consumer<ItemMatch> onShowItem;

	private boolean enabled = true;

	/** NPCs only. */
	public ShowDropsMenu(Client client, DropDataService data, Consumer<SourceData> onShow)
	{
		this(client, data, null, onShow, null);
	}

	/**
	 * @param itemManager  maps a noted or placeholder item to the item itself; null disables items
	 * @param onShowItem   opens an item's page; null disables items
	 */
	public ShowDropsMenu(Client client, DropDataService data, ItemManager itemManager,
		Consumer<SourceData> onShow, Consumer<ItemMatch> onShowItem)
	{
		this.client = client;
		this.data = data;
		this.itemManager = itemManager;
		this.onShow = onShow;
		this.onShowItem = onShowItem;
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
		if (source != null)
		{
			// index 0, not -1: the menu is drawn from the end of this array backwards, so the last
			// entry appears at the top and the first at the bottom. Ours belongs out of the way of
			// the options that actually do something in game.
			client.createMenuEntry(0)
				.setOption(OPTION)
				.setTarget("<col=ffff00>" + source.getName() + "</col>")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> onShow.accept(source));
			return;
		}
		ItemMatch item = resolveItem(itemIn(event));
		if (item != null)
		{
			client.createMenuEntry(0)
				.setOption(OPTION)
				.setTarget("<col=ff9040>" + item.getItemName() + "</col>")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> onShowItem.accept(item));
		}
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
	 * The item the menu is over, or -1. An item in the inventory, bank, or equipment comes with
	 * its id on the entry; one on the ground is named by the entry's identifier instead.
	 */
	private static int itemIn(MenuOpened event)
	{
		MenuEntry[] entries = event.getMenuEntries();
		if (entries == null)
		{
			return -1;
		}
		for (MenuEntry entry : entries)
		{
			if (entry == null)
			{
				continue;
			}
			if (entry.getItemId() > 0)
			{
				return entry.getItemId();
			}
			if (isGroundItemEntry(entry.getType()) && entry.getIdentifier() > 0)
			{
				return entry.getIdentifier();
			}
		}
		return -1;
	}

	private static boolean isGroundItemEntry(MenuAction type)
	{
		if (type == null)
		{
			return false;
		}
		switch (type)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case EXAMINE_ITEM_GROUND:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case ITEM_USE_ON_GROUND_ITEM:
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

	/**
	 * The item as this plugin knows it, or null when it knows nothing: an item nothing drops and
	 * no shop sells has no page worth opening. A noted item stands for the item itself.
	 */
	private ItemMatch resolveItem(int rawId)
	{
		if (rawId <= 0 || onShowItem == null)
		{
			return null;
		}
		int id = itemManager != null ? itemManager.canonicalize(rawId) : rawId;
		List<DropSource> drops = data.sourcesForItem(id);
		if (!drops.isEmpty())
		{
			return new ItemMatch(id, drops.get(0).getItemName());
		}
		List<ShopEntry> shops = data.shopsForItem(id);
		if (!shops.isEmpty())
		{
			return new ItemMatch(id, shops.get(0).getItem().getItemName());
		}
		return null;
	}
}
