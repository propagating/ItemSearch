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
import com.itemsearch.model.DropSource;
import com.itemsearch.model.SourceData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/** Shared drop tables must resolve back into ordinary per-source drop lists. */
public class TableSharingTest
{
	private static BundledDataSource ds;

	@BeforeClass
	public static void load()
	{
		ds = new BundledDataSource(new Gson(), "/com/itemsearch/sources.json",
			"/com/itemsearch/shops.json");
		ds.load();
	}

	private static SourceData named(String name)
	{
		for (SourceData s : ds.searchSources(name))
		{
			if (name.equalsIgnoreCase(s.getName()))
			{
				return s;
			}
		}
		return null;
	}

	@Test
	public void aSharedTableResolvesForEveryMonsterThatRollsIt()
	{
		SourceData spectre = named("Aberrant spectre");
		assertNotNull(spectre);
		long herbs = spectre.getDrops().stream()
			.filter(d -> "Herb drop table".equals(d.getDropTable())).count();
		assertTrue("the herb table must resolve, got " + spectre.getDrops().size() + " drops",
			herbs >= 10);
		// the table names itself once; every row it produced still knows which table it is
		assertTrue(spectre.getDrops().stream()
			.anyMatch(d -> d.getItemName().startsWith("Grimy") && d.getRarity() > 0));
	}

	@Test
	public void versionsOfOneMonsterShareTheirDropTable()
	{
		SourceData idle = named("Killerwatt (Idle)");
		SourceData attacking = named("Killerwatt (Attacking)");
		assertNotNull(idle);
		assertNotNull(attacking);
		assertEquals("both versions drop the page's table", idle.getDrops(), attacking.getDrops());
		assertTrue(idle.getDrops().size() > 0);
	}

	@Test
	public void everySourceResolvedSomeDrops()
	{
		int empty = 0;
		for (SourceData s : ds.searchSources("e"))
		{
			if (s.getDrops().isEmpty())
			{
				empty++;
			}
		}
		assertEquals("a dangling table reference would leave a source with no drops", 0, empty);
	}

	@Test
	public void itemLookupStillFindsSharedTableDrops()
	{
		// grimy guam leaf comes only from shared herb tables; it must still be item-searchable
		assertTrue("no source found for a shared-table item",
			ds.searchItems("Grimy guam leaf").stream()
				.anyMatch(i -> !ds.sourcesForItem(i.getItemId()).isEmpty()));
	}
}
