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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Whether an NPC has a pickpocket source, by the id the game reports or by its name. */
public class PickpocketableTest
{
	private static BundledDataSource loaded()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(), "/com/itemsearch/sources-test.json");
		ds.load();
		return ds;
	}

	@Test
	public void anNpcWithAPickpocketSourceIsPickpocketable()
	{
		BundledDataSource ds = loaded();
		assertTrue(ds.canBePickpocketed(3257, "Master Farmer"));
		assertTrue("by name when the game reports an id the data never listed",
			ds.canBePickpocketed(77777, "Master Farmer"));
		assertTrue(ds.canBePickpocketed(77777, "master farmer"));
	}

	@Test
	public void aMonsterWithNoPickpocketSourceIsNot()
	{
		BundledDataSource ds = loaded();
		assertFalse(ds.canBePickpocketed(3031, "Goblin"));
		assertFalse(ds.canBePickpocketed(77777, null));
	}
}
