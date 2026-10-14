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
package com.itemsearch.tracker;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import com.itemsearch.data.DropDataService;
import com.itemsearch.model.DropSource;
import com.itemsearch.model.SourceData;
import com.itemsearch.model.SourceType;

/**
 * Turns the client's loot events into kills against the monster that produced them.
 *
 * <p>Loot arrives two ways. Most monsters die and drop on the ground, which the client reports
 * as {@link NpcLootReceived}. Some never do: an albatross turns into a defeated bird that the
 * player harvests, and its loot goes straight to the ship's hold, so the only word of it is the
 * server's own loot report, {@link ServerNpcLoot}. That report also carries pickpocket loot,
 * which is not a kill, and nothing in it says which it was — so a monster that can also be
 * pickpocketed keeps only its ground drops, an accepted undercount.
 */
public class KillTracker
{
	// a kill the client reports both ways within this many milliseconds is one kill
	static final long SAME_KILL_WINDOW_MS = 3000;

	private final DropDataService dataService;
	private final TrackerStore store;
	private final ItemManager itemManager;
	private final DropConditions conditions;
	private final LongSupplier clock;
	// the last loot each source produced by each channel, so a kill reported by both counts once
	// while a burst of kills reported by one channel still counts every one
	private final Map<Integer, RecentLoot> lastGround = new HashMap<>();
	private final Map<Integer, RecentLoot> lastServer = new HashMap<>();

	public KillTracker(DropDataService dataService, TrackerStore store, ItemManager itemManager,
		DropConditions conditions)
	{
		this(dataService, store, itemManager, conditions, System::currentTimeMillis);
	}

	KillTracker(DropDataService dataService, TrackerStore store, ItemManager itemManager,
		DropConditions conditions, LongSupplier clock)
	{
		this.dataService = dataService;
		this.store = store;
		this.itemManager = itemManager;
		this.conditions = conditions;
		this.clock = clock;
	}

	// Every handler below answers whether it changed a counter, so the caller only redraws the
	// sidebar for the events that were about something it tracks.

	public boolean onKill(int npcId, Set<Integer> droppedItemIds)
	{
		return record(dataService.sourceById(npcId), droppedItemIds);
	}

	public boolean onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		if (npc == null)
		{
			return false;
		}
		SourceData data = resolve(npc.getId(), npc.getName(), npc.getCombatLevel());
		if (data == null)
		{
			return false;
		}
		Set<Integer> dropped = canonical(event.getItems());
		if (isRepeat(lastServer, data.getId(), dropped))
		{
			return false;
		}
		lastGround.put(data.getId(), new RecentLoot(dropped, clock.getAsLong()));
		return record(data, dropped);
	}

	public boolean onServerNpcLoot(ServerNpcLoot event)
	{
		NPCComposition npc = event.getComposition();
		if (npc == null)
		{
			return false;
		}
		SourceData data = resolve(npc.getId(), npc.getName(), npc.getCombatLevel());
		// only a fought monster: the same report describes a pickpocket, and a pickpocketable
		// NPC's reports cannot be told apart, so they are left uncounted
		if (data == null || data.getType() != SourceType.KILL
			|| dataService.canBePickpocketed(npc.getId(), npc.getName()))
		{
			return false;
		}
		Set<Integer> dropped = canonical(event.getItems());
		if (isRepeat(lastGround, data.getId(), dropped))
		{
			return false;
		}
		lastServer.put(data.getId(), new RecentLoot(dropped, clock.getAsLong()));
		return record(data, dropped);
	}

	/**
	 * Loot RuneLite's own Loot Tracker attributed to something other than a monster: a chest
	 * opened, an activity's reward, a pocket picked. Each event is one attempt at the source it
	 * names, so a Barrows chest or a Golem crafting reward moves the same counters a kill does.
	 * Monster and player loot on this channel is left alone, since kills arrive by their own.
	 */
	public boolean onLootReceived(LootReceived event)
	{
		if (event.getType() == null || event.getName() == null || event.getItems() == null)
		{
			return false;
		}
		SourceData data;
		switch (event.getType())
		{
			case PICKPOCKET:
				data = dataService.sourceByNameAndLevel(event.getName() + SourceType.PICKPOCKET_SUFFIX, 0);
				if (data == null || data.getType() != SourceType.PICKPOCKET)
				{
					return false;
				}
				break;
			case EVENT:
				data = dataService.sourceByNameAndLevel(LootEventNames.sourceNameFor(event.getName()), 0);
				// a pocket is picked, not opened; that channel says so itself
				if (data == null || data.getType() == SourceType.PICKPOCKET)
				{
					return false;
				}
				break;
			default:
				return false;
		}
		return record(data, canonical(event.getItems()));
	}

	// the game's own running total, announced as a golem is finished
	private static final Pattern GOLEMS_CRAFTED =
		Pattern.compile("You have crafted ([\\d,]+) golems? on Wyrmscraig\\.");
	private static final String GOLEM_CRAFTING = "Golem crafting";

	/**
	 * Game messages that state a total this plugin only ever sees one at a time. Golem crafting
	 * says how many golems the player has ever finished, which covers the ones made before the
	 * plugin was watching, so the attempt count is raised to match.
	 */
	public boolean onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return false;
		}
		Matcher golems = GOLEMS_CRAFTED.matcher(event.getMessage());
		if (!golems.find())
		{
			return false;
		}
		SourceData golemCrafting = dataService.sourceByNameAndLevel(GOLEM_CRAFTING, 0);
		return golemCrafting != null
			&& store.raiseCountTo(golemCrafting.getId(), Integer.parseInt(golems.group(1).replace(",", "")));
	}

	// Match by id first; fall back to the name + level the game reports, because the bundled
	// data does not list every in-game NPC id (id-only matching missed most kills).
	private SourceData resolve(int npcId, String name, int combatLevel)
	{
		SourceData data = dataService.sourceById(npcId);
		return data != null ? data : dataService.sourceByNameAndLevel(name, combatLevel);
	}

	// canonicalize maps noted (and placeholder) ids to the base item id, so a noted drop still
	// matches the unnoted id our data records — without this, the very kill that produced the
	// drop would increment the dry counter
	private Set<Integer> canonical(Collection<ItemStack> items)
	{
		Set<Integer> dropped = new HashSet<>();
		for (ItemStack item : items)
		{
			dropped.add(itemManager.canonicalize(item.getId()));
		}
		return dropped;
	}

	private boolean isRepeat(Map<Integer, RecentLoot> byOtherChannel, int sourceId, Set<Integer> dropped)
	{
		RecentLoot other = byOtherChannel.get(sourceId);
		return other != null && other.items.equals(dropped)
			&& clock.getAsLong() - other.at <= SAME_KILL_WINDOW_MS;
	}

	// Record one kill against a resolved monster. The data maps every variant id of a monster
	// to one canonical entry, so kills recorded under the canonical id (npc.getId()) all
	// accumulate into a single shared dry counter regardless of which variant was killed.
	private boolean record(SourceData npc, Set<Integer> droppedItemIds)
	{
		if (npc == null)
		{
			return false;
		}
		Set<Integer> possible = new HashSet<>();
		for (DropSource d : npc.getDrops())
		{
			// a drop that was not on the table for this kill is not a dry kill for it. An item
			// that actually dropped is always counted whatever we believe about the condition:
			// the drop itself is better evidence than the varp we read it from.
			if (d.getCondition() != null
				&& !droppedItemIds.contains(d.getItemId())
				&& !conditions.isMet(d.getCondition()))
			{
				continue;
			}
			possible.add(d.getItemId());
		}
		store.recordKill(npc.getId(), droppedItemIds, possible);
		store.saveSoon();
		return true;
	}

	private static final class RecentLoot
	{
		private final Set<Integer> items;
		private final long at;

		private RecentLoot(Set<Integer> items, long at)
		{
			this.items = items;
			this.at = at;
		}
	}
}
