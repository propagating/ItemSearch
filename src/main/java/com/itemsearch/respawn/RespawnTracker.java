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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

/**
 * Tracks respawns of NPCs by name: watches live instances, pairs each death
 * (despawn with isDead) with the following spawn at the same NPC index, learns
 * per-name respawn times empirically, and exposes the pending respawns for the
 * overlay. Event processing is batched per game tick, mirroring npchighlight's
 * proven tick math (+1 compensation because events run before the tick counter
 * updates). All methods except setTracked/isTracked run on the client thread.
 */
@Slf4j
public class RespawnTracker
{
	static final int MAX_RESPAWN_TIME_TICKS = 500;
	static final int GRACE_TICKS = 50;

	private final Client client;
	private final RespawnTimeStore store;

	// npc index -> live bookkeeping for tracked NPCs
	private final Map<Integer, WatchedNpc> watched = new ConcurrentHashMap<>();
	// deaths/spawns observed this tick, processed together in onGameTick
	private final List<NPC> spawnedThisTick = new ArrayList<>();
	private final List<DeadNpc> diedThisTick = new ArrayList<>();
	// snapshot the overlay reads; replaced wholesale each tick
	private volatile List<RespawnEntry> active = new ArrayList<>();

	private boolean skipNextSpawnCheck = false;

	@Getter
	private volatile Instant lastTickUpdate = Instant.now();

	public RespawnTracker(Client client, RespawnTimeStore store)
	{
		this.client = client;
		this.store = store;
	}

	public boolean isTracked(String npcName)
	{
		return store.isTracked(npcName);
	}

	public void setTracked(String npcName, boolean tracked)
	{
		store.setTracked(npcName, tracked);
		if (!tracked)
		{
			watched.values().removeIf(w -> npcName.equals(w.getNpcName()));
			rebuildActive();
		}
	}

	public void onNpcSpawned(NPC npc)
	{
		String name = npc.getName();
		if (name == null || !store.isTracked(name) || client.isInInstancedRegion())
		{
			return;
		}
		spawnedThisTick.add(npc);
	}

	public void onNpcDespawned(NPC npc)
	{
		String name = npc.getName();
		if (name == null || !store.isTracked(name) || client.isInInstancedRegion())
		{
			return;
		}
		if (!npc.isDead())
		{
			// walked out of the scene, not a death: drop the bookkeeping so the watched
			// map can't slowly accumulate stale entries (the index will be reused, and a
			// pending death for it is left intact by only removing never-died entries)
			WatchedNpc w = watched.get(npc.getIndex());
			if (w != null && w.getDiedOnTick() == -1)
			{
				watched.remove(npc.getIndex());
			}
			return;
		}
		diedThisTick.add(new DeadNpc(npc.getIndex(), name, npc.getWorldLocation(), sizeOf(npc)));
	}

	public void onGameTick()
	{
		if (skipNextSpawnCheck)
		{
			skipNextSpawnCheck = false;
			spawnedThisTick.clear();
			diedThisTick.clear();
		}
		else
		{
			processDeaths();
			processSpawns();
		}
		expireOldEntries();
		rebuildActive();
		lastTickUpdate = Instant.now();
	}

	public void invalidateAll()
	{
		for (WatchedNpc w : watched.values())
		{
			w.setDiedOnTick(-1);
		}
		spawnedThisTick.clear();
		diedThisTick.clear();
		skipNextSpawnCheck = true;
		rebuildActive();
	}

	public List<RespawnEntry> activeRespawns()
	{
		return active;
	}

	private void processDeaths()
	{
		// events run before the tick counter updates, hence the +1 (npchighlight semantics)
		final int nowTick = client.getTickCount() + 1;
		for (DeadNpc dead : diedThisTick)
		{
			WatchedNpc w = watched.get(dead.index);
			if (w == null)
			{
				// never saw it spawn: fall back to the death tile, flagged approximate
				w = new WatchedNpc(dead.index, dead.name, dead.size);
				watched.put(dead.index, w);
			}
			w.setDiedOnTick(nowTick);
			w.setLastDeathLocation(dead.location);
		}
		diedThisTick.clear();
	}

	private void processSpawns()
	{
		final int nowTick = client.getTickCount() + 1;
		for (NPC npc : spawnedThisTick)
		{
			WatchedNpc w = watched.computeIfAbsent(npc.getIndex(), i -> new WatchedNpc(npc));

			if (w.getDiedOnTick() != -1)
			{
				final int observed = nowTick - w.getDiedOnTick();
				final int stored = store.getRespawnTicks(w.getNpcName());
				// min-observation: walking away inflates the measurement, so only a
				// SHORTER observation may replace a stored value; cap at the ceiling
				if ((stored == -1 || observed < stored) && observed <= MAX_RESPAWN_TIME_TICKS)
				{
					store.learnRespawnTicks(w.getNpcName(), observed);
				}
				w.setDiedOnTick(-1);
			}

			// an NPC can move on its spawn tick, so the true spawn tile is either its
			// current tile or the tile behind it (npchighlight's intersection trick)
			final WorldPoint loc = npc.getWorldLocation();
			final WorldPoint behind = worldLocationBehind(npc);
			w.getPossibleRespawnLocations().removeIf(x -> !x.equals(loc) && !x.equals(behind));
			if (w.getPossibleRespawnLocations().isEmpty())
			{
				w.getPossibleRespawnLocations().add(loc);
				w.getPossibleRespawnLocations().add(behind);
			}
		}
		spawnedThisTick.clear();
	}

	private void expireOldEntries()
	{
		final int now = client.getTickCount();
		final Iterator<Map.Entry<Integer, WatchedNpc>> it = watched.entrySet().iterator();
		while (it.hasNext())
		{
			final WatchedNpc w = it.next().getValue();
			if (w.getDiedOnTick() == -1)
			{
				continue;
			}
			final int learned = store.getRespawnTicks(w.getNpcName());
			final int due = learned == -1 ? w.getDiedOnTick() + MAX_RESPAWN_TIME_TICKS : w.getDiedOnTick() + learned;
			if (due + GRACE_TICKS < now)
			{
				w.setDiedOnTick(-1);
			}
		}
	}

	private void rebuildActive()
	{
		final List<RespawnEntry> entries = new ArrayList<>();
		for (WatchedNpc w : watched.values())
		{
			if (w.getDiedOnTick() == -1)
			{
				continue;
			}
			final boolean approximate = w.getPossibleRespawnLocations().isEmpty();
			final WorldPoint tile = approximate ? w.getLastDeathLocation() : w.getPossibleRespawnLocations().get(0);
			if (tile == null)
			{
				continue;
			}
			final int learned = store.getRespawnTicks(w.getNpcName());
			final int respawnAt = learned == -1 ? -1 : w.getDiedOnTick() + learned;
			entries.add(new RespawnEntry(w.getNpcName(), tile, w.getNpcSize(), w.getDiedOnTick(), respawnAt, approximate));
		}
		active = entries;
	}

	private static int sizeOf(NPC npc)
	{
		return npc.getTransformedComposition() != null ? npc.getTransformedComposition().getSize() : 1;
	}

	private static WorldPoint worldLocationBehind(NPC npc)
	{
		final int orientation = npc.getOrientation() / 256;
		int dx = 0, dy = 0;
		switch (orientation)
		{
			case 0: dy = -1; break;          // South
			case 1: dx = -1; dy = -1; break; // Southwest
			case 2: dx = -1; break;          // West
			case 3: dx = -1; dy = 1; break;  // Northwest
			case 4: dy = 1; break;           // North
			case 5: dx = 1; dy = 1; break;   // Northeast
			case 6: dx = 1; break;           // East
			case 7: dx = 1; dy = -1; break;  // Southeast
		}
		final WorldPoint cur = npc.getWorldLocation();
		return new WorldPoint(cur.getX() - dx, cur.getY() - dy, cur.getPlane());
	}

	private static class DeadNpc
	{
		final int index;
		final String name;
		final WorldPoint location;
		final int size;

		DeadNpc(int index, String name, WorldPoint location, int size)
		{
			this.index = index;
			this.name = name;
			this.location = location;
			this.size = size;
		}
	}
}
