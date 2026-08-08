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
import java.util.List;
import net.runelite.client.config.ConfigManager;
import com.itemsearch.model.SpawnPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpawnStoreTest
{
	private ConfigManager configManager;
	private final String[] saved = {null};

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		// a config manager that remembers, so a save can be read back by a fresh store
		doAnswer(inv ->
		{
			saved[0] = inv.getArgument(2);
			return null;
		}).when(configManager).setConfiguration(anyString(), eq("spawns"), anyString());
		when(configManager.getConfiguration("itemsearch", "spawns")).thenAnswer(inv -> saved[0]);
	}

	private SpawnStore store()
	{
		SpawnStore store = new SpawnStore(configManager, new Gson());
		store.load();
		return store;
	}

	@Test
	public void spawnsAreRememberedInTheOrderTheyWereAdded()
	{
		SpawnStore store = store();
		store.addSpawn(13463, new SpawnPoint(3016, 3514, 0));
		store.addSpawn(13463, new SpawnPoint(3020, 3520, 1));

		List<SpawnPoint> spawns = store.spawnsFor(13463);
		assertEquals(2, spawns.size());
		assertEquals(3016, spawns.get(0).getX());
		assertEquals(1, spawns.get(1).getPlane());
	}

	/** Standing still and clicking twice should not put two markers on one tile. */
	@Test
	public void theSameTileTwiceIsAddedOnce()
	{
		SpawnStore store = store();
		store.addSpawn(13463, new SpawnPoint(3016, 3514, 0));
		store.addSpawn(13463, new SpawnPoint(3016, 3514, 0));
		assertEquals(1, store.spawnsFor(13463).size());
	}

	@Test
	public void theMostRecentSpawnCanBeTakenBack()
	{
		SpawnStore store = store();
		store.addSpawn(13463, new SpawnPoint(1, 1, 0));
		store.addSpawn(13463, new SpawnPoint(2, 2, 0));
		store.removeLastSpawn(13463);

		assertEquals(1, store.spawnsFor(13463).size());
		assertEquals(1, store.spawnsFor(13463).get(0).getX());
	}

	@Test
	public void takingBackFromNothingIsHarmless()
	{
		SpawnStore store = store();
		store.removeLastSpawn(999);
		assertTrue(store.spawnsFor(999).isEmpty());
	}

	@Test
	public void allSpawnsCanBeCleared()
	{
		SpawnStore store = store();
		store.addSpawn(13463, new SpawnPoint(1, 1, 0));
		store.addSpawn(13463, new SpawnPoint(2, 2, 0));
		store.addSpawn(555, new SpawnPoint(3, 3, 0));
		store.clearSpawns(13463);

		assertTrue(store.spawnsFor(13463).isEmpty());
		assertEquals("another monster's spawns are its own", 1, store.spawnsFor(555).size());
	}

	@Test
	public void anEntranceIsOneTileAndReplacesTheLast()
	{
		SpawnStore store = store();
		assertNull(store.entranceFor(13463));
		store.setEntrance(13463, new SpawnPoint(3016, 3514, 0));
		store.setEntrance(13463, new SpawnPoint(2760, 3061, 0));
		assertEquals(2760, store.entranceFor(13463).getX());

		store.setEntrance(13463, null);
		assertNull("clearing hands it back to the shipped data", store.entranceFor(13463));
	}

	/** What is typed in one session has to be there in the next, or it is not worth typing. */
	@Test
	public void everythingSurvivesAReload()
	{
		SpawnStore store = store();
		store.addSpawn(13463, new SpawnPoint(3016, 3514, 1));
		store.setEntrance(13463, new SpawnPoint(2760, 3061, 0));

		SpawnStore reopened = store();
		assertEquals(1, reopened.spawnsFor(13463).size());
		assertEquals(3016, reopened.spawnsFor(13463).get(0).getX());
		assertEquals(2760, reopened.entranceFor(13463).getX());
	}

	@Test
	public void aCorruptBlobStartsEmptyRatherThanThrowing()
	{
		saved[0] = "{not json";
		SpawnStore store = store();
		assertTrue(store.spawnsFor(13463).isEmpty());
		assertNull(store.entranceFor(13463));
	}

	@Test
	public void nothingIsKnownAboutAMonsterNobodyHasEdited()
	{
		assertTrue(store().spawnsFor(4242).isEmpty());
	}
}
