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

/** Examine text rides in the bundled file as a map by item id, and is absent when not written. */
public class ExamineLoadingTest
{
	@Test
	public void examineTextIsReadByItemIdAndNullOtherwise()
	{
		String json = "{\"tables\":[],\"sources\":[{\"id\":3031,\"npcIds\":[3031],\"name\":\"Goblin\","
			+ "\"combatLevel\":2,\"type\":\"KILL\",\"drops\":[]}],"
			+ "\"examines\":{\"1205\":\"A dagger made of bronze.\"}}";
		BundledDataSource ds = new BundledDataSource(new Gson(), "/nowhere.json");
		ds.loadFrom(new StringReader(json));
		assertEquals("A dagger made of bronze.", ds.examineFor(1205));
		assertNull(ds.examineFor(995));

		BundledDataSource bare = new BundledDataSource(new Gson(), "/nowhere.json");
		bare.loadFrom(new StringReader("{\"tables\":[],\"sources\":[]}"));
		assertNull("older data without the map still loads", bare.examineFor(1205));
	}
}
