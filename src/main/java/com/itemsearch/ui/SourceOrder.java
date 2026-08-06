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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import com.itemsearch.model.DropSource;

/**
 * Orders an item's drop sources for display: pairs each {@link DropSource} with its
 * owning NPC id (the two input lists are index-aligned, as produced by
 * BundledDataSource#sourcesForItem / #ownersOfItem) and sorts by owner name,
 * case-insensitively. Sources whose owner is unknown (no npc id, or no name for it)
 * sort last, after every named owner.
 */
final class SourceOrder
{
	/** Stands in for a source with no owner id at all; never resolves to a name. */
	static final int UNKNOWN_OWNER = Integer.MIN_VALUE;

	private SourceOrder()
	{
	}

	static final class Entry
	{
		final DropSource source;
		final int npcId;
		final String name; // owner display name, or null when the owner is unknown

		Entry(DropSource source, int npcId, String name)
		{
			this.source = source;
			this.npcId = npcId;
			this.name = name;
		}
	}

	/**
	 * @param sources drop sources for one item
	 * @param owners  npc ids index-aligned with {@code sources}; may be shorter, in
	 *                which case the missing tail is treated as unknown owners (npcId -1)
	 * @param nameOf  resolves an npc id to its display name, or null if unknown
	 */
	static List<Entry> order(List<DropSource> sources, List<Integer> owners, IntFunction<String> nameOf)
	{
		List<Entry> entries = new ArrayList<>(sources.size());
		for (int i = 0; i < sources.size(); i++)
		{
			int npcId = i < owners.size() ? owners.get(i) : UNKNOWN_OWNER;
			// resolve unconditionally: non-NPC sources carry synthetic NEGATIVE ids, so the
			// sign of the id says nothing about whether an owner exists — only the lookup does
			String name = nameOf.apply(npcId);
			entries.add(new Entry(sources.get(i), npcId, name));
		}
		entries.sort(Comparator
			.comparing((Entry e) -> e.name == null)          // known owners (false) before unknown (true)
			.thenComparing(e -> e.name == null ? "" : e.name, String.CASE_INSENSITIVE_ORDER));
		return entries;
	}
}
