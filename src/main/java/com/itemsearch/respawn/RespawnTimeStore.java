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
package com.itemsearch.respawn;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Persists learned respawn times (game ticks per NPC name) and the set of tracked
 * NPC names under the plugin's config group. Mutators are synchronized (same
 * discipline as TrackerStore); reads are lock-free on concurrent collections.
 */
@Slf4j
public class RespawnTimeStore
{
	private static final String GROUP = "itemsearch";
	private static final String KEY = "respawnTimes";

	private final ConfigManager configManager;
	private final Gson gson;

	// NPC name -> learned respawn ticks
	private final Map<String, Integer> respawnTicks = new ConcurrentHashMap<>();
	// NPC names the user is tracking (backed by a concurrent map)
	private final Set<String> tracked = ConcurrentHashMap.newKeySet();

	public RespawnTimeStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public synchronized void load()
	{
		String json = configManager.getConfiguration(GROUP, KEY);
		respawnTicks.clear();
		tracked.clear();
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
			if (p != null)
			{
				if (p.respawnTicks != null)
				{
					respawnTicks.putAll(p.respawnTicks);
				}
				if (p.tracked != null)
				{
					tracked.addAll(p.tracked);
				}
			}
		}
		catch (Exception e)
		{
			log.warn("itemsearch: corrupt respawn data, starting fresh", e);
			respawnTicks.clear();
			tracked.clear();
		}
	}

	public int getRespawnTicks(String npcName)
	{
		return respawnTicks.getOrDefault(npcName, -1);
	}

	public synchronized void learnRespawnTicks(String npcName, int ticks)
	{
		respawnTicks.put(npcName, ticks);
		save();
	}

	public boolean isTracked(String npcName)
	{
		return tracked.contains(npcName);
	}

	public synchronized void setTracked(String npcName, boolean isTracked)
	{
		if (isTracked)
		{
			tracked.add(npcName);
		}
		else
		{
			tracked.remove(npcName);
		}
		save();
	}

	public Set<String> trackedNames()
	{
		return new HashSet<>(tracked);
	}

	private synchronized void save()
	{
		Persisted p = new Persisted();
		p.respawnTicks = new HashMap<>(respawnTicks);
		p.tracked = new HashSet<>(tracked);
		configManager.setConfiguration(GROUP, KEY, gson.toJson(p));
	}

	private static class Persisted
	{
		Map<String, Integer> respawnTicks;
		Set<String> tracked;
	}
}
