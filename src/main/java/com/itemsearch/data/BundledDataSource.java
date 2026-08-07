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
import com.itemsearch.model.SourceData;
import com.itemsearch.model.SourceType;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopEntry;
import com.itemsearch.model.ShopItem;
import com.itemsearch.model.SpawnPoint;

@Slf4j
public class BundledDataSource implements DropDataService
{
	private final Gson gson;
	private final String resourcePath;
	private final String shopsResourcePath;

	private volatile boolean loaded;

	// maps EVERY npc id (each entry's npcIds) to that entry, so a kill/lookup under any
	// variant id resolves to the one canonical monster
	private final Map<Integer, SourceData> byNpcId = new ConcurrentHashMap<>();
	// distinct entries (one per canonical monster) for search; byNpcId.values() would
	// repeat an entry once per variant id
	private final List<SourceData> allNpcs = new CopyOnWriteArrayList<>();
	// "name|combatLevel" (lower-cased) -> canonical entry, for resolving a live kill by what
	// the game reports when its id isn't in our data
	private final Map<String, SourceData> byNameLevel = new ConcurrentHashMap<>();
	// lower-cased name -> first canonical entry, a looser fallback when the level doesn't match
	private final Map<String, SourceData> byName = new ConcurrentHashMap<>();
	private final Map<Integer, List<DropSource>> byItemId = new ConcurrentHashMap<>();
	private final Map<Integer, String> itemNames = new ConcurrentHashMap<>();
	// itemId -> owning npc ids, index-aligned with byItemId's list for the same itemId
	private final Map<Integer, List<Integer>> itemOwners = new ConcurrentHashMap<>();
	private final Map<Integer, List<ShopEntry>> shopsByItemId = new ConcurrentHashMap<>();
	private final List<ShopData> allShops = new CopyOnWriteArrayList<>();

	public BundledDataSource(Gson gson, String resourcePath)
	{
		this(gson, resourcePath, null);
	}

	public BundledDataSource(Gson gson, String resourcePath, String shopsResourcePath)
	{
		this.gson = gson;
		this.resourcePath = resourcePath;
		this.shopsResourcePath = shopsResourcePath;
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
			BundledData data = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), BundledData.class);
			index(materialise(data));
		}
		catch (Exception e)
		{
			log.warn("itemsearch: failed to load bundled data", e);
		}
		finally
		{
			loaded = true;
		}
		loadShops();
	}

	private void loadShops()
	{
		shopsByItemId.clear();
		allShops.clear();
		if (shopsResourcePath == null)
		{
			return;
		}
		try (InputStream in = getClass().getResourceAsStream(shopsResourcePath))
		{
			if (in == null)
			{
				log.warn("itemsearch: bundled shop data not found at {}", shopsResourcePath);
				return;
			}
			Type listType = new TypeToken<List<ShopData>>()
			{
			}.getType();
			List<ShopData> shops = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), listType);
			indexShops(shops == null ? Collections.emptyList() : shops);
		}
		catch (Exception e)
		{
			log.warn("itemsearch: failed to load bundled shop data", e);
		}
	}

	private void indexShops(List<ShopData> shops)
	{
		for (ShopData shop : shops)
		{
			if (shop == null || shop.getName() == null)
			{
				log.warn("itemsearch: skipping malformed shop entry in bundled data");
				continue;
			}
			List<ShopItem> items = shop.getItems() == null
				? Collections.emptyList()
				: shop.getItems().stream()
					.filter(i -> i != null && i.getItemName() != null)
					.collect(Collectors.toList());
			ShopData normalized = new ShopData(shop.getName(), shop.getLocation(),
				shop.getCurrency(), shop.getX(), shop.getY(), shop.getPlane(), shop.getMapId(), items);
			allShops.add(normalized);
			for (ShopItem item : items)
			{
				if (item.getItemId() < 0)
				{
					continue; // unresolvable name: shown in the catalog, never joined to items
				}
				shopsByItemId.computeIfAbsent(item.getItemId(), k -> new ArrayList<>())
					.add(new ShopEntry(normalized, item));
				// shop-only items should still be findable by item search
				itemNames.putIfAbsent(item.getItemId(), item.getItemName());
			}
		}
	}

	/**
	 * Sources with their drop lists resolved from the shared tables.
	 *
	 * <p>Most drop tables are shared — 47 monsters roll the same herb table, and every version of
	 * a monster repeats its page's — so the file stores each once and sources reference them.
	 * Resolving here keeps that off every view: a SourceData still just has a list of drops.
	 * The lists are shared rather than copied, so the sharing saves heap as well as file size.
	 */
	private List<SourceData> materialise(BundledData data)
	{
		if (data == null || data.sources == null)
		{
			return Collections.emptyList();
		}
		List<List<DropSource>> tables = new ArrayList<>();
		for (DropTable table : data.tables == null ? Collections.<DropTable>emptyList() : data.tables)
		{
			List<DropSource> rows = new ArrayList<>();
			for (DropSource d : table.drops == null ? Collections.<DropSource>emptyList() : table.drops)
			{
				// the table names itself once instead of every row repeating it
				rows.add(table.name == null ? d : new DropSource(d.getItemId(), d.getItemName(),
					d.getRarity(), d.getQuantity(), d.isNoted(), d.isRarityKnown(),
					d.getCategory(), table.name));
			}
			tables.add(Collections.unmodifiableList(rows));
		}

		List<SourceData> out = new ArrayList<>(data.sources.size());
		for (SourceData source : data.sources)
		{
			List<DropSource> drops = new ArrayList<>();
			for (Integer ref : source.getTables() == null
				? Collections.<Integer>emptyList() : source.getTables())
			{
				if (ref != null && ref >= 0 && ref < tables.size())
				{
					drops.addAll(tables.get(ref));
				}
				else
				{
					log.warn("itemsearch: {} references drop table {}, which does not exist",
						source.getName(), ref);
				}
			}
			out.add(source.withDrops(drops));
		}
		return out;
	}

	/** The shape of the bundled file: tables stored once, sources referencing them by index. */
	private static class BundledData
	{
		List<DropTable> tables;
		List<SourceData> sources;
	}

	private static class DropTable
	{
		String name;
		List<DropSource> drops;
	}

	public boolean isLoaded()
	{
		return loaded;
	}

	private void index(List<SourceData> npcs)
	{
		byNpcId.clear();
		allNpcs.clear();
		byNameLevel.clear();
		byName.clear();
		byItemId.clear();
		itemNames.clear();
		itemOwners.clear();
		for (SourceData npc : npcs)
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
			// older/hand data may omit npcIds; fall back to the single canonical id. Non-NPC
			// sources (chests, nodes) declare an empty list and must keep it — a synthetic
			// negative id is not a killable npc id and must never enter byNpcId.
			List<Integer> npcIds = npc.getNpcIds() == null
				? Collections.emptyList()
				: npc.getNpcIds().stream().filter(Objects::nonNull).collect(Collectors.toList());
			if (npcIds.isEmpty() && npc.getId() >= 0)
			{
				npcIds = Collections.singletonList(npc.getId());
			}
			// data written before source types existed is all monster drops
			SourceType type = npc.getType() == null ? SourceType.KILL : npc.getType();
			npc = new SourceData(npc.getId(), npc.getName(), npc.getCombatLevel(),
				npc.getWikiImageUrl(), drops, spawns, npcIds, npc.getCombat(),
				type, npc.getX(), npc.getY(), npc.getPlane(), npc.getTables());
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
	public SourceData sourceById(int npcId)
	{
		return byNpcId.get(npcId);
	}

	private static String nameLevelKey(String name, int combatLevel)
	{
		return name.toLowerCase(Locale.ROOT) + "|" + combatLevel;
	}

	@Override
	public SourceData sourceByNameAndLevel(String name, int combatLevel)
	{
		if (name == null)
		{
			return null;
		}
		SourceData exact = byNameLevel.get(nameLevelKey(name, combatLevel));
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
	public List<SourceData> searchSources(String query)
	{
		String q = query.toLowerCase(Locale.ROOT).trim();
		List<SourceData> out = new ArrayList<>();
		if (q.isEmpty())
		{
			return out;
		}
		for (SourceData npc : allNpcs)
		{
			if (npc.getName().toLowerCase(Locale.ROOT).contains(q))
			{
				out.add(npc);
			}
		}
		out.sort(Comparator
			.comparing(SourceData::getName, String.CASE_INSENSITIVE_ORDER)
			.thenComparingInt(SourceData::getCombatLevel));
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

	@Override
	public List<ShopEntry> shopsForItem(int itemId)
	{
		List<ShopEntry> purchasable = new ArrayList<>();
		for (ShopEntry e : shopsByItemId.getOrDefault(itemId, Collections.emptyList()))
		{
			Integer stock = e.getItem().getStock();
			if (stock != null && stock == 0)
			{
				continue; // 0 base stock is not a way to purchase the item (for now)
			}
			purchasable.add(e);
		}
		purchasable.sort(Comparator
			.comparing((ShopEntry e) -> e.getItem().getSellPrice() == null)
			.thenComparingInt(e -> e.getItem().getSellPrice() == null
				? 0 : e.getItem().getSellPrice())
			.thenComparing(e -> e.getShop().getName(), String.CASE_INSENSITIVE_ORDER));
		return purchasable;
	}

	@Override
	public List<ShopData> searchShops(String query)
	{
		String q = query.toLowerCase(Locale.ROOT).trim();
		List<ShopData> out = new ArrayList<>();
		if (q.isEmpty())
		{
			return out;
		}
		for (ShopData shop : allShops)
		{
			if (shop.getName().toLowerCase(Locale.ROOT).contains(q)
				|| (shop.getLocation() != null
					&& shop.getLocation().toLowerCase(Locale.ROOT).contains(q)))
			{
				out.add(shop);
			}
		}
		out.sort(Comparator.comparing(ShopData::getName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	public void overlayImage(int npcId, String wikiImageUrl)
	{
		if (wikiImageUrl == null || wikiImageUrl.trim().isEmpty())
		{
			return;
		}
		SourceData cur = byNpcId.get(npcId);
		if (cur == null)
		{
			return;
		}
		SourceData updated = new SourceData(cur.getId(), cur.getName(), cur.getCombatLevel(),
			wikiImageUrl, cur.getDrops(), cur.getSpawnPoints(), cur.getNpcIds(), cur.getCombat(),
			cur.getType(), cur.getX(), cur.getY(), cur.getPlane(), cur.getTables());
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
