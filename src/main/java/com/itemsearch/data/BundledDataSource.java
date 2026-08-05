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
package com.itemsearch.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.itemsearch.model.DropSource;
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.NpcData;
import com.itemsearch.model.SpawnPoint;

@Slf4j
public class BundledDataSource implements DropDataService
{
	private final Gson gson;
	private final String resourcePath;

	private volatile boolean loaded;

	// maps EVERY npc id (each entry's npcIds) to that entry, so a kill/lookup under any
	// variant id resolves to the one canonical monster
	private final Map<Integer, NpcData> byNpcId = new ConcurrentHashMap<>();
	// distinct entries (one per canonical monster) for search; byNpcId.values() would
	// repeat an entry once per variant id
	private final List<NpcData> allNpcs = new CopyOnWriteArrayList<>();
	// "name|combatLevel" (lower-cased) -> canonical entry, for resolving a live kill by what
	// the game reports when its id isn't in our data
	private final Map<String, NpcData> byNameLevel = new ConcurrentHashMap<>();
	// lower-cased name -> first canonical entry, a looser fallback when the level doesn't match
	private final Map<String, NpcData> byName = new ConcurrentHashMap<>();
	private final Map<Integer, List<DropSource>> byItemId = new ConcurrentHashMap<>();
	private final Map<Integer, String> itemNames = new ConcurrentHashMap<>();
	// itemId -> owning npc ids, index-aligned with byItemId's list for the same itemId
	private final Map<Integer, List<Integer>> itemOwners = new ConcurrentHashMap<>();

	public BundledDataSource(Gson gson, String resourcePath)
	{
		this.gson = gson;
		this.resourcePath = resourcePath;
	}

	public void load()
	{
		try (InputStream in = getClass().getResourceAsStream(resourcePath))
		{
			if (in == null)
			{
				log.warn("itemsearch: bundled data not found at {}", resourcePath);
				return;
			}
			Type listType = new TypeToken<List<NpcData>>()
			{
			}.getType();
			List<NpcData> npcs = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), listType);
			index(npcs == null ? Collections.emptyList() : npcs);
		}
		catch (Exception e)
		{
			log.warn("itemsearch: failed to load bundled data", e);
		}
		finally
		{
			loaded = true;
		}
	}

	public boolean isLoaded()
	{
		return loaded;
	}

	private void index(List<NpcData> npcs)
	{
		byNpcId.clear();
		allNpcs.clear();
		byNameLevel.clear();
		byName.clear();
		byItemId.clear();
		itemNames.clear();
		itemOwners.clear();
		for (NpcData npc : npcs)
		{
			// skip malformed entries instead of letting one bad record discard the whole dataset
			if (npc == null || npc.getName() == null)
			{
				log.warn("itemsearch: skipping malformed npc entry in bundled data");
				continue;
			}
			// normalize so views can iterate drops/spawn points without null checks
			List<DropSource> drops = npc.getDrops() == null
				? Collections.emptyList() : npc.getDrops();
			List<SpawnPoint> spawns = npc.getSpawnPoints() == null
				? Collections.emptyList()
				: npc.getSpawnPoints().stream().filter(Objects::nonNull).collect(Collectors.toList());
			// older/hand data may omit npcIds; fall back to the single canonical id
			List<Integer> npcIds = npc.getNpcIds() == null
				? Collections.emptyList()
				: npc.getNpcIds().stream().filter(Objects::nonNull).collect(Collectors.toList());
			if (npcIds.isEmpty())
			{
				npcIds = Collections.singletonList(npc.getId());
			}
			npc = new NpcData(npc.getId(), npc.getName(), npc.getCombatLevel(),
				npc.getWikiImageUrl(), drops, spawns, npcIds, npc.getCombat());
			allNpcs.add(npc);
			for (Integer id : npcIds)
			{
				byNpcId.put(id, npc);
			}
			// ensure the canonical id resolves even if it was absent from npcIds
			byNpcId.putIfAbsent(npc.getId(), npc);
			byNameLevel.putIfAbsent(nameLevelKey(npc.getName(), npc.getCombatLevel()), npc);
			byName.putIfAbsent(npc.getName().toLowerCase(Locale.ROOT), npc);
			for (DropSource d : npc.getDrops())
			{
				if (d == null || d.getItemName() == null)
				{
					log.warn("itemsearch: skipping malformed drop entry for {}", npc.getName());
					continue;
				}
				byItemId.computeIfAbsent(d.getItemId(), k -> new ArrayList<>()).add(d);
				itemNames.putIfAbsent(d.getItemId(), d.getItemName());
				itemOwners.computeIfAbsent(d.getItemId(), k -> new ArrayList<>()).add(npc.getId());
			}
		}
	}

	@Override
	public NpcData npcById(int npcId)
	{
		return byNpcId.get(npcId);
	}

	private static String nameLevelKey(String name, int combatLevel)
	{
		return name.toLowerCase(Locale.ROOT) + "|" + combatLevel;
	}

	@Override
	public NpcData npcByNameAndLevel(String name, int combatLevel)
	{
		if (name == null)
		{
			return null;
		}
		NpcData exact = byNameLevel.get(nameLevelKey(name, combatLevel));
		return exact != null ? exact : byName.get(name.toLowerCase(Locale.ROOT));
	}

	@Override
	public List<DropSource> sourcesForItem(int itemId)
	{
		return byItemId.getOrDefault(itemId, Collections.emptyList());
	}

	/**
	 * NPC ids that drop the given item, index-aligned with {@link #sourcesForItem(int)}
	 * for the same item id (both are populated in the same order within {@link #index(List)}).
	 */
	public List<Integer> ownersOfItem(int itemId)
	{
		return itemOwners.getOrDefault(itemId, Collections.emptyList());
	}

	@Override
	public List<NpcData> searchNpcs(String query)
	{
		String q = query.toLowerCase(Locale.ROOT).trim();
		List<NpcData> out = new ArrayList<>();
		if (q.isEmpty())
		{
			return out;
		}
		for (NpcData npc : allNpcs)
		{
			if (npc.getName().toLowerCase(Locale.ROOT).contains(q))
			{
				out.add(npc);
			}
		}
		out.sort(Comparator
			.comparing(NpcData::getName, String.CASE_INSENSITIVE_ORDER)
			.thenComparingInt(NpcData::getCombatLevel));
		return out;
	}

	@Override
	public List<ItemMatch> searchItems(String query)
	{
		String q = query.toLowerCase(Locale.ROOT).trim();
		Map<Integer, ItemMatch> out = new LinkedHashMap<>();
		if (q.isEmpty())
		{
			return new ArrayList<>();
		}
		for (Map.Entry<Integer, String> e : itemNames.entrySet())
		{
			if (e.getValue().toLowerCase(Locale.ROOT).contains(q))
			{
				out.putIfAbsent(e.getKey(), new ItemMatch(e.getKey(), e.getValue()));
			}
		}
		List<ItemMatch> list = new ArrayList<>(out.values());
		list.sort((a, b) -> a.getItemName().compareToIgnoreCase(b.getItemName()));
		return list;
	}

	public void overlayImage(int npcId, String wikiImageUrl)
	{
		if (wikiImageUrl == null || wikiImageUrl.trim().isEmpty())
		{
			return;
		}
		NpcData cur = byNpcId.get(npcId);
		if (cur == null)
		{
			return;
		}
		NpcData updated = new NpcData(cur.getId(), cur.getName(), cur.getCombatLevel(),
			wikiImageUrl, cur.getDrops(), cur.getSpawnPoints(), cur.getNpcIds(), cur.getCombat());
		// replace across every id that resolves to this entry, plus the search list, so a
		// later reopen (search reads allNpcs) sees the fresh image too. replace(k,old,new)
		// is a CAS: a racing overlay simply wins/loses a key without clobbering fields.
		for (Integer id : cur.getNpcIds())
		{
			if (id != null)
			{
				byNpcId.replace(id, cur, updated);
			}
		}
		byNpcId.replace(cur.getId(), cur, updated);
		int idx = allNpcs.indexOf(cur);
		if (idx >= 0)
		{
			allNpcs.set(idx, updated);
		}
	}
}
