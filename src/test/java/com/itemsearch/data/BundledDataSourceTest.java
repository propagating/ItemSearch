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
import java.util.List;
import com.itemsearch.model.DropSource;
import com.itemsearch.model.NpcData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class BundledDataSourceTest
{
	private BundledDataSource ds;

	@Before
	public void setUp()
	{
		ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/drops-test.json");
		ds.load();
	}

	@Test
	public void loadsNpcById()
	{
		NpcData goblin = ds.npcById(3031);
		assertEquals("Goblin", goblin.getName());
		assertEquals(2, goblin.getDrops().size());
	}

	@Test
	public void unknownNpcIsNull()
	{
		assertNull(ds.npcById(999999));
	}

	@Test
	public void invertedIndexFindsAllSourcesOfItem()
	{
		// Bronze dagger (1205) drops from both Goblin and Zulrah
		List<DropSource> sources = ds.sourcesForItem(1205);
		assertEquals(2, sources.size());
	}

	@Test
	public void unknownItemHasNoSources()
	{
		assertTrue(ds.sourcesForItem(424242).isEmpty());
	}

	@Test
	public void spawnPointsAreParsedAndNullEntriesSkipped()
	{
		NpcData goblin = ds.npcById(3031);
		assertEquals(2, goblin.getSpawnPoints().size());
		assertEquals(3246, goblin.getSpawnPoints().get(0).getX());
		assertEquals(3235, goblin.getSpawnPoints().get(0).getY());
		assertEquals(0, goblin.getSpawnPoints().get(0).getPlane());
	}

	@Test
	public void absentSpawnPointsNormalizeToEmptyList()
	{
		NpcData zulrah = ds.npcById(2042);
		assertNotNull(zulrah.getSpawnPoints());
		assertTrue(zulrah.getSpawnPoints().isEmpty());
	}

	@Test
	public void variantIdsResolveToOneCanonicalEntry()
	{
		BundledDataSource multi = new BundledDataSource(new Gson(),
			"/com/itemsearch/drops-multiid-test.json");
		multi.load();

		NpcData viaCanonical = multi.npcById(7001);
		NpcData viaVariant = multi.npcById(7002);
		assertNotNull(viaVariant);
		assertEquals(7001, viaVariant.getId()); // variant id resolves to the canonical entry
		assertEquals("Test Beast", viaVariant.getName());
		assertEquals(viaCanonical.getId(), viaVariant.getId());

		// search lists the monster once, not once per underlying id
		assertEquals(1, multi.searchNpcs("beast").size());
	}

	@Test
	public void isLoadedReflectsLoadLifecycle()
	{
		BundledDataSource ds = new BundledDataSource(new com.google.gson.Gson(),
			"/com/itemsearch/drops-test.json");
		assertFalse(ds.isLoaded());
		ds.load();
		assertTrue(ds.isLoaded());
	}
}
