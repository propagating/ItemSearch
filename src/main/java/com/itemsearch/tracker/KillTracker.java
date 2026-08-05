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

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.NPC;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import com.itemsearch.data.DropDataService;
import com.itemsearch.model.DropSource;
import com.itemsearch.model.NpcData;

// Loot arriving via ServerNpcLoot (script-provided loot) is deliberately NOT consumed:
// that channel also delivers pickpocket loot, which would count as false kills for
// pickpocketable seed NPCs (e.g. Guard). Kills whose loot only surfaces there are
// therefore missed — an accepted undercount until a safe filter exists.
public class KillTracker
{
	private final DropDataService dataService;
	private final TrackerStore store;
	private final ItemManager itemManager;

	public KillTracker(DropDataService dataService, TrackerStore store, ItemManager itemManager)
	{
		this.dataService = dataService;
		this.store = store;
		this.itemManager = itemManager;
	}

	public void onKill(int npcId, Set<Integer> droppedItemIds)
	{
		record(dataService.npcById(npcId), droppedItemIds);
	}

	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		if (npc == null)
		{
			return;
		}
		Set<Integer> dropped = new HashSet<>();
		for (ItemStack item : event.getItems())
		{
			// canonicalize maps noted (and placeholder) ids to the base item id, so a
			// noted drop still matches the unnoted id our data records — without this,
			// the very kill that produced the drop would increment the dry counter
			dropped.add(itemManager.canonicalize(item.getId()));
		}
		// Match by id first; fall back to the name + level the game reports, because the
		// bundled data does not list every in-game NPC id (id-only matching missed most kills).
		NpcData data = dataService.npcById(npc.getId());
		if (data == null)
		{
			data = dataService.npcByNameAndLevel(npc.getName(), npc.getCombatLevel());
		}
		record(data, dropped);
	}

	// Record one kill against a resolved monster. The data maps every variant id of a monster
	// to one canonical entry, so kills recorded under the canonical id (npc.getId()) all
	// accumulate into a single shared dry counter regardless of which variant was killed.
	private void record(NpcData npc, Set<Integer> droppedItemIds)
	{
		if (npc == null)
		{
			return;
		}
		Set<Integer> possible = new HashSet<>();
		for (DropSource d : npc.getDrops())
		{
			possible.add(d.getItemId());
		}
		store.recordKill(npc.getId(), droppedItemIds, possible);
		store.save();
	}
}
