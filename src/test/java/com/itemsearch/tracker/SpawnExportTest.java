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
package com.itemsearch.tracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.runelite.client.config.ConfigManager;
import com.itemsearch.model.SpawnPoint;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SpawnExportTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private SpawnStore store()
	{
		return new SpawnStore(mock(ConfigManager.class), new Gson());
	}

	private JsonObject read(File file) throws Exception
	{
		String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		return new Gson().fromJson(json, JsonObject.class);
	}

	/**
	 * The curator reads the two overlay files by npc id, so the export has to arrive in exactly
	 * those shapes — anything else means hand-editing before it can be used.
	 */
	@Test
	public void exportsSpawnsAndEntrancesKeyedByNpcId() throws Exception
	{
		SpawnStore store = store();
		store.addSpawn(13463, new SpawnPoint(3016, 3514, 1));
		store.addSpawn(13463, new SpawnPoint(3020, 3520, 1));
		store.setEntrance(13463, new SpawnPoint(2760, 3061, 0));

		File out = new File(folder.getRoot(), "spawn-export.json");
		assertEquals(1, SpawnExport.write(new Gson(), store, out));

		JsonObject root = read(out);
		JsonObject spawns = root.getAsJsonObject("spawns");
		assertEquals(2, spawns.getAsJsonArray("13463").size());
		assertEquals(3016, spawns.getAsJsonArray("13463").get(0)
			.getAsJsonObject().get("x").getAsInt());
		assertEquals(2760, root.getAsJsonObject("entrances").getAsJsonObject("13463")
			.get("x").getAsInt());
	}

	/** A monster with only an entrance recorded still belongs in the export. */
	@Test
	public void anEntranceAloneIsExported() throws Exception
	{
		SpawnStore store = store();
		store.setEntrance(999, new SpawnPoint(1, 2, 0));

		File out = new File(folder.getRoot(), "spawn-export.json");
		assertEquals(1, SpawnExport.write(new Gson(), store, out));

		JsonObject root = read(out);
		assertFalse(root.getAsJsonObject("spawns").has("999"));
		assertTrue(root.getAsJsonObject("entrances").has("999"));
	}

	@Test
	public void exportingNothingStillWritesAFileSayingSo() throws Exception
	{
		File out = new File(folder.getRoot(), "spawn-export.json");
		assertEquals(0, SpawnExport.write(new Gson(), store(), out));

		JsonObject root = read(out);
		assertEquals(0, root.getAsJsonObject("spawns").size());
		assertEquals(0, root.getAsJsonObject("entrances").size());
	}

	/** Exporting must not need the folder to already exist. */
	@Test
	public void aMissingDirectoryIsCreated() throws Exception
	{
		File out = new File(new File(folder.getRoot(), "itemsearch"), "spawn-export.json");
		SpawnStore store = store();
		store.addSpawn(1, new SpawnPoint(1, 1, 0));

		assertEquals(1, SpawnExport.write(new Gson(), store, out));
		assertTrue(out.isFile());
	}
}
