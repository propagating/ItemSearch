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
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.SourceData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class BundledDataSourceSearchTest
{
	private BundledDataSource ds;

	@Before
	public void setUp()
	{
		ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		ds.load();
	}

	@Test
	public void searchSourcesIsCaseInsensitiveSubstring()
	{
		List<SourceData> r = ds.searchSources("gob");
		assertEquals(1, r.size());
		assertEquals("Goblin", r.get(0).getName());
	}

	@Test
	public void searchItemsDedupesByItemId()
	{
		// "bronze dagger" exists on two NPCs but is one item
		List<ItemMatch> r = ds.searchItems("bronze dagger");
		assertEquals(1, r.size());
		assertEquals(1205, r.get(0).getItemId());
	}

	@Test
	public void blankQueryReturnsEmpty()
	{
		assertTrue(ds.searchSources("   ").isEmpty());
		assertTrue(ds.searchItems("").isEmpty());
	}

	@Test
	public void overlayImageReplacesUrlPreservingDrops()
	{
		ds.overlayImage(3031, "https://example/new.png");
		SourceData goblin = ds.sourceById(3031);
		assertEquals("https://example/new.png", goblin.getWikiImageUrl());
		assertEquals(2, goblin.getDrops().size());
		// spawn points must survive the overlay too, or the "Show on map"
		// button would silently disappear after any wiki image refresh
		assertEquals(2, goblin.getSpawnPoints().size());
	}

	@Test
	public void overlayImageIgnoresUnknownNpc()
	{
		ds.overlayImage(999999, "https://example/x.png"); // must not throw
	}
}
