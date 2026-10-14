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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class TrackerStore
{
	private static final String GROUP = "itemsearch";
	private static final String KEY = "data";

	private final ConfigManager configManager;
	private final Gson gson;
	// where a debounced save runs; null saves at once, which is what tests want
	private final ScheduledExecutorService executor;

	/**
	 * How long a kill waits before the counters are written. A save serialises every counter the
	 * player has ever rolled, and a barrage task lands a dozen kills in one tick, so each kill
	 * writing at once meant a dozen copies of the same blob. Short, so a crash costs a second.
	 */
	static final long SAVE_DELAY_MS = 1000;
	private ScheduledFuture<?> pendingSave;

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

	/** A store that writes every change at once. */
	public TrackerStore(ConfigManager configManager, Gson gson)
	{
		this(configManager, gson, null);
	}

	/** @param executor where kills' saves are coalesced; null writes every change at once */
	public TrackerStore(ConfigManager configManager, Gson gson, ScheduledExecutorService executor)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.executor = executor;
	}

	private static String pairKey(int npcId, int itemId)
	{
		return npcId + ":" + itemId;
	}

	// Mutating methods are synchronized so compound sequences (mutate maps + save) are
	// atomic with respect to each other: recordKill on the client thread cannot interleave
	// with clear()/resetPair() on the EDT and re-persist counters a reset just wiped.
	// Reads (getNpcKills/getDry) stay lock-free on the concurrent maps.
	// the blob as last read from or written to the config, so a change made elsewhere — a second
	// client on the same profile, or the profile synced down from the RuneLite account — can be
	// told apart from this store's own writes
	private String lastSeen;

	public synchronized void load()
	{
		String json = configManager.getConfiguration(GROUP, KEY);
		lastSeen = json;
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Persisted p = gson.fromJson(json, Persisted.class);
			forgetEverything();
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
				if (p.expanded != null)
				{
					expanded.addAll(p.expanded);
				}
			}
		}
		catch (Exception e)
		{
			log.warn("itemsearch: corrupt tracker data, starting fresh", e);
			forgetEverything();
		}
	}

	private void forgetEverything()
	{
		npcKills.clear();
		dry.clear();
		obtained.clear();
		expanded.clear();
	}

	/**
	 * Re-reads the counters if something other than this store has changed them, and says so.
	 *
	 * <p>Two clients on one profile each hold their own copy of the counters, and whichever saves
	 * last wins; a kill counted in one client vanished when the other saved its stale copy plus
	 * one. Every change made here therefore starts from what is actually saved, so a kill is
	 * added to the other client's count rather than in place of it.
	 */
	public synchronized boolean absorbExternalChanges()
	{
		String json = configManager.getConfiguration(GROUP, KEY);
		if (Objects.equals(json, lastSeen))
		{
			return false;
		}
		load();
		return true;
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
		absorbExternalChanges();
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

	/**
	 * Raises a source's count to a total the game itself reported, when that is higher than what
	 * has been counted here. The game's figure covers attempts made before this plugin was
	 * watching, so it is trusted upwards and never used to take counted attempts away.
	 *
	 * @return whether the count moved
	 */
	public synchronized boolean raiseCountTo(int npcId, int total)
	{
		absorbExternalChanges();
		if (total <= getNpcKills(npcId))
		{
			return false;
		}
		npcKills.put(npcId, total);
		save();
		return true;
	}

	public synchronized void resetPair(int npcId, int itemId)
	{
		absorbExternalChanges();
		dry.put(pairKey(npcId, itemId), 0);
	}

	public synchronized void resetNpcKills(int npcId)
	{
		absorbExternalChanges();
		npcKills.remove(npcId);
	}

	public synchronized void resetNpcDrops(int npcId)
	{
		absorbExternalChanges();
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

	/**
	 * Saves soon rather than now, so a burst of kills is written once. A reset or a card toggle
	 * still calls {@link #save()} itself: those are one action each, and the player expects them
	 * kept. A change from another client that arrives inside the delay is absorbed over whatever
	 * is waiting here, which loses at most the last second of kills — the trade accepted for not
	 * serialising the whole history a dozen times a tick.
	 */
	public synchronized void saveSoon()
	{
		if (executor == null)
		{
			save();
			return;
		}
		if (pendingSave == null || pendingSave.isDone())
		{
			pendingSave = executor.schedule(this::save, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	/** Writes anything still waiting. For shutdown, after which nothing would run the save. */
	public synchronized void flush()
	{
		ScheduledFuture<?> waiting = pendingSave;
		pendingSave = null;
		if (waiting != null && waiting.cancel(false))
		{
			save();
		}
	}

	public synchronized void save()
	{
		if (pendingSave != null)
		{
			pendingSave.cancel(false); // this write covers it
			pendingSave = null;
		}
		// Synchronized with the mutators above, so the two copies below form one
		// consistent point-in-time snapshot of both maps.
		Persisted p = new Persisted();
		p.npcKills = new HashMap<>(npcKills);
		p.dry = new HashMap<>(dry);
		p.obtained = new HashMap<>(obtained);
		p.expanded = new HashSet<>(expanded);
		String json = gson.toJson(p);
		lastSeen = json;
		configManager.setConfiguration(GROUP, KEY, json);
	}

	private static class Persisted
	{
		Map<Integer, Integer> npcKills;
		Map<String, Integer> dry;
		Map<String, Integer> obtained;
		Set<String> expanded;
	}
}
