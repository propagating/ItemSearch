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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import com.itemsearch.model.SpawnPoint;

/**
 * Spawn tiles and dungeon entrances the player has added themselves.
 *
 * <p>The shipped data cannot place everything: its spawn dump stops at npc id 11151, and the wiki
 * has no map of a good many places. A player standing in front of the monster knows better than
 * either, so this is where what they record is kept — layered over the shipped tiles rather than
 * replacing them, and never leaving their machine.
 */
@Slf4j
public class SpawnStore
{
	private static final String GROUP = "itemsearch";
	private static final String KEY = "spawns";

	private final ConfigManager configManager;
	private final Gson gson;

	// npcId -> what the player recorded. Touched only from the EDT (the buttons) and read when a
	// view or marker is built, also on the EDT, so plain maps suffice.
	private final Map<Integer, Recorded> byNpc = new LinkedHashMap<>();

	public SpawnStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public synchronized void load()
	{
		byNpc.clear();
		String json = configManager.getConfiguration(GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Type type = new TypeToken<Map<Integer, Recorded>>()
			{
			}.getType();
			Map<Integer, Recorded> saved = gson.fromJson(json, type);
			if (saved != null)
			{
				saved.forEach((id, recorded) ->
				{
					if (id != null && recorded != null)
					{
						byNpc.put(id, recorded);
					}
				});
			}
		}
		catch (Exception e)
		{
			// a blob we cannot read is a player's additions lost, not a broken plugin
			log.warn("itemsearch: corrupt spawn data, starting fresh", e);
			byNpc.clear();
		}
	}

	public synchronized void save()
	{
		configManager.setConfiguration(GROUP, KEY, gson.toJson(byNpc));
	}

	/** Tiles the player recorded for this monster, oldest first. Never null. */
	public synchronized List<SpawnPoint> spawnsFor(int npcId)
	{
		Recorded recorded = byNpc.get(npcId);
		return recorded == null || recorded.spawns == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(recorded.spawns));
	}

	public synchronized SpawnPoint entranceFor(int npcId)
	{
		Recorded recorded = byNpc.get(npcId);
		return recorded == null ? null : recorded.entrance;
	}

	/** Every monster the player has recorded something for, for export. */
	public synchronized List<Integer> editedNpcIds()
	{
		return new ArrayList<>(byNpc.keySet());
	}

	public synchronized boolean hasAnything(int npcId)
	{
		return !spawnsFor(npcId).isEmpty() || entranceFor(npcId) != null;
	}

	public synchronized void addSpawn(int npcId, SpawnPoint point)
	{
		if (point == null)
		{
			return;
		}
		Recorded recorded = byNpc.computeIfAbsent(npcId, k -> new Recorded());
		if (recorded.spawns == null)
		{
			recorded.spawns = new ArrayList<>();
		}
		// standing still and clicking twice should not stack two markers on one tile
		for (SpawnPoint existing : recorded.spawns)
		{
			if (same(existing, point))
			{
				return;
			}
		}
		recorded.spawns.add(point);
		save();
	}

	public synchronized void removeLastSpawn(int npcId)
	{
		Recorded recorded = byNpc.get(npcId);
		if (recorded == null || recorded.spawns == null || recorded.spawns.isEmpty())
		{
			return;
		}
		recorded.spawns.remove(recorded.spawns.size() - 1);
		prune(npcId, recorded);
		save();
	}

	public synchronized void clearSpawns(int npcId)
	{
		Recorded recorded = byNpc.get(npcId);
		if (recorded == null)
		{
			return;
		}
		recorded.spawns = null;
		prune(npcId, recorded);
		save();
	}

	/** Sets the way in, or clears it with null so the shipped one applies again. */
	public synchronized void setEntrance(int npcId, SpawnPoint point)
	{
		Recorded recorded = byNpc.computeIfAbsent(npcId, k -> new Recorded());
		recorded.entrance = point;
		prune(npcId, recorded);
		save();
	}

	// an entry holding nothing is noise in the saved blob
	private void prune(int npcId, Recorded recorded)
	{
		if ((recorded.spawns == null || recorded.spawns.isEmpty()) && recorded.entrance == null)
		{
			byNpc.remove(npcId);
		}
	}

	private static boolean same(SpawnPoint a, SpawnPoint b)
	{
		return a.getX() == b.getX() && a.getY() == b.getY() && a.getPlane() == b.getPlane();
	}

	/** Persisted shape; Gson fills these directly. */
	private static class Recorded
	{
		List<SpawnPoint> spawns;
		SpawnPoint entrance;
	}
}
