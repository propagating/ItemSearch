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
			"/com/itemsearch/drops-test.json",
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
			"/com/itemsearch/drops-test.json",
			"/com/itemsearch/no-such-shops.json");
		ds.load();
		assertTrue(ds.shopsForItem(BRONZE_DAGGER).isEmpty());
		assertTrue(ds.searchShops("zaff").isEmpty());
		assertTrue(ds.searchNpcs("goblin").size() >= 1);
	}
}
