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
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopEntry;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BundledDataSourceShopTest
{
	private static final int BRONZE_DAGGER = 1205;
	private static final int BRONZE_ARROW = 882;
	private static final int STAFF_OF_AIR = 1381;

	private BundledDataSource loaded()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json",
			"/com/itemsearch/shops-test.json");
		ds.load();
		return ds;
	}

	@Test
	public void shopsForItemSortsByPriceAndIncludesInfiniteStock()
	{
		List<ShopEntry> entries = loaded().shopsForItem(BRONZE_DAGGER);
		assertEquals(2, entries.size());
		assertEquals("Zaff's Superior Staffs", entries.get(0).getShop().getName()); // 30 < 50
		assertEquals(Integer.valueOf(-1), entries.get(0).getItem().getStock());
		assertEquals("Aaron's Archery Appendages", entries.get(1).getShop().getName());
	}

	@Test
	public void zeroStockEntriesAreNotAWayToPurchase()
	{
		assertTrue(loaded().shopsForItem(BRONZE_ARROW).isEmpty());
	}

	@Test
	public void unknownPriceSortsLast()
	{
		List<ShopEntry> entries = loaded().shopsForItem(STAFF_OF_AIR);
		assertEquals(1, entries.size());
		assertNull(entries.get(0).getItem().getSellPrice());
	}

	@Test
	public void searchShopsMatchesNameAndLocation()
	{
		BundledDataSource ds = loaded();
		List<ShopData> byName = ds.searchShops("zaff");
		assertEquals(1, byName.size());
		assertEquals("Zaff's Superior Staffs", byName.get(0).getName());

		List<ShopData> byLocation = ds.searchShops("ranging guild");
		assertEquals(1, byLocation.size());
		assertEquals("Aaron's Archery Appendages", byLocation.get(0).getName());
	}

	@Test
	public void shopOnlyItemsBecomeSearchableItems()
	{
		// Staff of air is sold but never dropped; item search should still find it
		List<ItemMatch> matches = loaded().searchItems("staff of air");
		assertEquals(1, matches.size());
		assertEquals(STAFF_OF_AIR, matches.get(0).getItemId());
	}

	@Test
	public void missingShopsResourceLeavesShopFeaturesEmptyButDropsWork()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json",
			"/com/itemsearch/no-such-shops.json");
		ds.load();
		assertTrue(ds.shopsForItem(BRONZE_DAGGER).isEmpty());
		assertTrue(ds.searchShops("zaff").isEmpty());
		assertTrue(ds.searchSources("goblin").size() >= 1);
	}
}
