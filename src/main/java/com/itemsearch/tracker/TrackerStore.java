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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class TrackerStore
{
	private static final String GROUP = "itemsearch";
	private static final String KEY = "data";

	private final ConfigManager configManager;
	private final Gson gson;

	// npcId -> total kills. Mutated from both the client event-bus thread (recordKill) and the
	// Swing EDT (resetPair/clear), so the maps are concurrent and their references never reassigned.
	private final Map<Integer, Integer> npcKills = new ConcurrentHashMap<>();
	// "npcId:itemId" -> kills since last drop
	private final Map<String, Integer> dry = new ConcurrentHashMap<>();
	// "npcId:itemId" -> total times the item has dropped for the player
	private final Map<String, Integer> obtained = new ConcurrentHashMap<>();
	// "itemId:sourceId" for each item-view source card the player has expanded. A set of the
	// expanded ones (rather than a flag per card) keeps the persisted blob proportional to what
	// the player actually opened, not to the thousands of sources some items have.
	private final Set<String> expanded = ConcurrentHashMap.newKeySet();

	public TrackerStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	private static String pairKey(int npcId, int itemId)
	{
		return npcId + ":" + itemId;
	}

	// Mutating methods are synchronized so compound sequences (mutate maps + save) are
	// atomic with respect to each other: recordKill on the client thread cannot interleave
	// with clear()/resetPair() on the EDT and re-persist counters a reset just wiped.
	// Reads (getNpcKills/getDry) stay lock-free on the concurrent maps.
	public synchronized void load()
	{
		String json = configManager.getConfiguration(GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Type type = new TypeToken<Persisted>()
			{
			}.getType();
			Persisted p = gson.fromJson(json, type);
			npcKills.clear();
			dry.clear();
			obtained.clear();
			expanded.clear();
			if (p != null)
			{
				if (p.npcKills != null)
				{
					npcKills.putAll(p.npcKills);
				}
				if (p.dry != null)
				{
					dry.putAll(p.dry);
				}
				if (p.obtained != null)
				{
					obtained.putAll(p.obtained);
				}
			}
		}
		catch (Exception e)
		{
			log.warn("itemsearch: corrupt tracker data, starting fresh", e);
			npcKills.clear();
			dry.clear();
			obtained.clear();
			expanded.clear();
		}
	}

	public int getNpcKills(int npcId)
	{
		return npcKills.getOrDefault(npcId, 0);
	}

	public int getDry(int npcId, int itemId)
	{
		return dry.getOrDefault(pairKey(npcId, itemId), 0);
	}

	/** Whether the player has expanded this item's card for this source. */
	public boolean isExpanded(int itemId, int sourceId)
	{
		return expanded.contains(itemId + ":" + sourceId);
	}

	public void setExpanded(int itemId, int sourceId, boolean isExpanded)
	{
		String key = itemId + ":" + sourceId;
		if (isExpanded ? expanded.add(key) : expanded.remove(key))
		{
			save();
		}
	}

	public int getObtained(int npcId, int itemId)
	{
		return obtained.getOrDefault(pairKey(npcId, itemId), 0);
	}

	public synchronized void recordKill(int npcId, Set<Integer> droppedItemIds, Collection<Integer> possibleItemIds)
	{
		npcKills.merge(npcId, 1, Integer::sum);
		for (int itemId : possibleItemIds)
		{
			String k = pairKey(npcId, itemId);
			if (droppedItemIds.contains(itemId))
			{
				dry.put(k, 0);
				obtained.merge(k, 1, Integer::sum);
			}
			else
			{
				dry.merge(k, 1, Integer::sum);
			}
		}
	}

	public synchronized void resetPair(int npcId, int itemId)
	{
		dry.put(pairKey(npcId, itemId), 0);
	}

	public synchronized void resetNpcKills(int npcId)
	{
		npcKills.remove(npcId);
	}

	public synchronized void resetNpcDrops(int npcId)
	{
		// keys are "npcId:itemId"; the ':' delimiter prevents 3031 from matching 30310
		String prefix = npcId + ":";
		dry.keySet().removeIf(k -> k.startsWith(prefix));
		obtained.keySet().removeIf(k -> k.startsWith(prefix));
	}

	/**
	 * Clears every tracked counter in memory and persists the emptied store, so the reset
	 * takes effect in the running session and cannot be silently undone by a later save().
	 */
	public synchronized void clear()
	{
		npcKills.clear();
		dry.clear();
		obtained.clear();
		save();
	}

	public synchronized void save()
	{
		// Synchronized with the mutators above, so the two copies below form one
		// consistent point-in-time snapshot of both maps.
		Persisted p = new Persisted();
		p.npcKills = new HashMap<>(npcKills);
		p.dry = new HashMap<>(dry);
		p.obtained = new HashMap<>(obtained);
		p.expanded = new HashSet<>(expanded);
		configManager.setConfiguration(GROUP, KEY, gson.toJson(p));
	}

	private static class Persisted
	{
		Map<Integer, Integer> npcKills;
		Map<String, Integer> dry;
		Map<String, Integer> obtained;
		Set<String> expanded;
	}
}
