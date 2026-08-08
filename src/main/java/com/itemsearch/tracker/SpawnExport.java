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
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.itemsearch.model.SpawnPoint;

/**
 * Writes what the player recorded in game to a file the curator can read back.
 *
 * <p>Recording a monster's tiles while standing in front of it is the easy part; getting them into
 * the shipped data is the other half. The two maps here are exactly the shapes of
 * {@code spawns-overlay.json} and {@code entrance-overlay.json}, so the curator can merge them
 * without anything being reshaped by hand in between.
 */
public final class SpawnExport
{
	/** Under {@code .runelite}, as a plugin's files must be. */
	public static final String FILE_NAME = "spawn-export.json";

	private SpawnExport()
	{
	}

	/**
	 * @return how many monsters were written, so the caller can say something useful about it
	 */
	public static int write(Gson gson, SpawnStore store, File out) throws IOException
	{
		Map<String, List<SpawnPoint>> spawns = new TreeMap<>(SpawnExport::byNumber);
		Map<String, SpawnPoint> entrances = new TreeMap<>(SpawnExport::byNumber);
		for (Integer npcId : store.editedNpcIds())
		{
			List<SpawnPoint> tiles = store.spawnsFor(npcId);
			if (!tiles.isEmpty())
			{
				spawns.put(String.valueOf(npcId), tiles);
			}
			SpawnPoint entrance = store.entranceFor(npcId);
			if (entrance != null)
			{
				entrances.put(String.valueOf(npcId), entrance);
			}
		}

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("spawns", spawns);
		root.put("entrances", entrances);

		File parent = out.getParentFile();
		if (parent != null && !parent.isDirectory())
		{
			Files.createDirectories(parent.toPath());
		}
		try (Writer writer = new OutputStreamWriter(
			Files.newOutputStream(out.toPath()), StandardCharsets.UTF_8))
		{
			gson.toJson(root, writer);
		}
		int touched = spawns.size();
		for (String id : entrances.keySet())
		{
			touched += spawns.containsKey(id) ? 0 : 1;
		}
		return touched;
	}

	// keys are npc ids, and reading a file sorted 1, 10, 100, 2 is needlessly unpleasant
	private static int byNumber(String a, String b)
	{
		try
		{
			return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
		}
		catch (NumberFormatException e)
		{
			return a.compareTo(b);
		}
	}
}
