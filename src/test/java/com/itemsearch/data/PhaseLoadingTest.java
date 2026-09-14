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
import java.io.StringReader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** A source's phases come through the bundled file as written, and are absent when not written. */
public class PhaseLoadingTest
{
	@Test
	public void phasesAreReadWhenPresentAndNullOtherwise()
	{
		String json = "{\"tables\":[],\"sources\":[{\"id\":2042,\"npcIds\":[2042,2043],"
			+ "\"name\":\"Zulrah\",\"combatLevel\":725,\"type\":\"KILL\",\"drops\":[],"
			+ "\"phases\":[{\"label\":\"Serpentine\",\"image\":\"https://x/s.png\","
			+ "\"combat\":{\"attackLevel\":1,\"strengthLevel\":1,\"defenceLevel\":300,"
			+ "\"magicLevel\":300,\"rangedLevel\":300,\"hitpoints\":500,\"attacks\":[]}},"
			+ "{\"label\":\"Magma\",\"image\":\"https://x/m.png\",\"combat\":{\"attackLevel\":1,"
			+ "\"strengthLevel\":7,\"defenceLevel\":300,\"magicLevel\":300,\"rangedLevel\":300,"
			+ "\"hitpoints\":500,\"attacks\":[]}}]},"
			+ "{\"id\":3031,\"npcIds\":[3031],\"name\":\"Goblin\",\"combatLevel\":2,"
			+ "\"type\":\"KILL\",\"drops\":[]}]}";
		BundledDataSource ds = new BundledDataSource(new Gson(), "/nowhere.json");
		ds.loadFrom(new StringReader(json));

		assertEquals(2, ds.sourceById(2042).getPhases().size());
		assertEquals("Magma", ds.sourceById(2042).getPhases().get(1).getLabel());
		assertEquals(7, ds.sourceById(2042).getPhases().get(1).getCombat().getStrengthLevel());
		assertEquals("https://x/m.png", ds.sourceById(2042).getPhases().get(1).getImage());
		assertNull(ds.sourceById(3031).getPhases());
	}
}
