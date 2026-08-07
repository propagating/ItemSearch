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
package com.itemsearch.model;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SourceDataTest
{
	private static SourceData source(String name, String wikiPage)
	{
		return new SourceData(1, name, 55, null, Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList(1), null, SourceType.KILL, null, null, null, null, wikiPage, null);
	}

	/**
	 * Splitting a page into one source per version invents names for the player's benefit —
	 * "Killerwatt (Attacking)" is not a page, and asking the wiki for it returns nothing at all,
	 * which is why those sources showed no image.
	 */
	@Test
	public void aSplitSourceIsFetchedByItsPageNotItsDisplayName()
	{
		assertEquals("Killerwatt", source("Killerwatt (Attacking)", "Killerwatt").wikiTitle());
	}

	/**
	 * Only the suffix we appended is dropped. A monster whose real page title already carries a
	 * parenthetical keeps it — the page is "Zombie (Shayzien Crypts)", not "Zombie".
	 */
	@Test
	public void aPageTitleKeepsAParentheticalThatWasAlreadyItsOwn()
	{
		assertEquals("Zombie (Shayzien Crypts)",
			source("Zombie (Shayzien Crypts) (Melee)", "Zombie (Shayzien Crypts)").wikiTitle());
	}

	@Test
	public void anUnsplitSourceIsStillFetchedByName()
	{
		assertEquals("Steel dragon", source("Steel dragon", null).wikiTitle());
		assertEquals("Steel dragon", source("Steel dragon", "  ").wikiTitle());
	}

	@Test
	public void resolvingSharedTablesKeepsThePage()
	{
		SourceData resolved = source("Killerwatt (Attacking)", "Killerwatt")
			.withDrops(Collections.emptyList());
		assertEquals("Killerwatt", resolved.wikiTitle());
	}
}
