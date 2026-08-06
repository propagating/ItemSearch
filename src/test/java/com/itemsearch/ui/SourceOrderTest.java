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
package com.itemsearch.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.itemsearch.model.DropSource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SourceOrderTest
{
	private static DropSource src(int itemId)
	{
		// DropSource(itemId, itemName, rarity, quantity, noted, rarityKnown, category)
		return new DropSource(itemId, "item" + itemId, 0.5, 1, false, true, null);
	}

	@Test
	public void sortsByOwnerNameCaseInsensitive()
	{
		List<DropSource> sources = Arrays.asList(src(1), src(2), src(3));
		List<Integer> owners = Arrays.asList(10, 20, 30);
		Map<Integer, String> names = new HashMap<>();
		names.put(10, "Zulrah");
		names.put(20, "aberrant spectre");
		names.put(30, "Goblin");

		List<SourceOrder.Entry> ordered = SourceOrder.order(sources, owners, names::get);

		assertEquals("aberrant spectre", ordered.get(0).name);
		assertEquals("Goblin", ordered.get(1).name);
		assertEquals("Zulrah", ordered.get(2).name);
	}

	@Test
	public void keepsSourcePairedWithItsOwner()
	{
		List<DropSource> sources = Arrays.asList(src(1), src(2));
		List<Integer> owners = Arrays.asList(10, 20);
		Map<Integer, String> names = new HashMap<>();
		names.put(10, "Zulrah");
		names.put(20, "Goblin");

		List<SourceOrder.Entry> ordered = SourceOrder.order(sources, owners, names::get);

		// Goblin sorts first and must still carry item 2 (its own source), not item 1
		assertEquals("Goblin", ordered.get(0).name);
		assertEquals(2, ordered.get(0).source.getItemId());
		assertEquals(20, ordered.get(0).npcId);
	}

	@Test
	public void unknownOwnersSortLast()
	{
		List<DropSource> sources = Arrays.asList(src(1), src(2));
		List<Integer> owners = Arrays.asList(-1, 40);
		Map<Integer, String> names = Collections.singletonMap(40, "Goblin");

		List<SourceOrder.Entry> ordered = SourceOrder.order(sources, owners, names::get);

		assertEquals("Goblin", ordered.get(0).name);
		assertNull(ordered.get(1).name);
		assertEquals(-1, ordered.get(1).npcId);
	}

	@Test
	public void ownersShorterThanSourcesTreatsTailAsUnknown()
	{
		List<DropSource> sources = Arrays.asList(src(1), src(2));
		List<Integer> owners = Collections.singletonList(50);
		Map<Integer, String> names = Collections.singletonMap(50, "Goblin");

		List<SourceOrder.Entry> ordered = SourceOrder.order(sources, owners, names::get);

		assertEquals("Goblin", ordered.get(0).name);
		assertNull(ordered.get(1).name); // index 1 had no owner id
	}
}
