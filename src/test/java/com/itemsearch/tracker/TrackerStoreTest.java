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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TrackerStoreTest
{
	@Mock
	private ConfigManager configManager;

	private TrackerStore store;

	@Before
	public void setUp()
	{
		store = new TrackerStore(configManager, new Gson());
		store.load();
	}

	@Test
	public void killWithoutDropIncrementsDry()
	{
		store.recordKill(3031, Collections.emptySet(), Arrays.asList(995, 1205));
		assertEquals(1, store.getNpcKills(3031));
		assertEquals(1, store.getDry(3031, 995));
		assertEquals(1, store.getDry(3031, 1205));
	}

	@Test
	public void killWithDropResetsThatPairOnly()
	{
		store.recordKill(3031, Collections.emptySet(), Arrays.asList(995, 1205));
		store.recordKill(3031, new HashSet<>(Collections.singletonList(1205)), Arrays.asList(995, 1205));
		assertEquals(2, store.getNpcKills(3031));
		assertEquals(2, store.getDry(3031, 995));   // still dry
		assertEquals(0, store.getDry(3031, 1205));  // reset on drop
	}

	@Test
	public void resetPairZeroesDry()
	{
		store.recordKill(3031, Collections.emptySet(), Collections.singletonList(995));
		store.resetPair(3031, 995);
		assertEquals(0, store.getDry(3031, 995));
	}

	/**
	 * Two clients on one profile each hold their own counters. A kill counted here must be added
	 * to what the other client saved in the meantime, not written over it: the albatross count
	 * went 1, 2, then back to 1 when a second client saved its stale copy plus one.
	 */
	@Test
	public void aKillIsAddedToCountersAnotherClientSavedMeanwhile()
	{
		store.recordKill(3031, Collections.emptySet(), Collections.singletonList(995));
		store.save();
		assertEquals(1, store.getNpcKills(3031));

		// another client saved five kills of its own since
		when(configManager.getConfiguration(eq("itemsearch"), eq("data")))
			.thenReturn("{\"npcKills\":{\"3031\":5},\"dry\":{\"3031:995\":5},\"obtained\":{}}");

		store.recordKill(3031, Collections.emptySet(), Collections.singletonList(995));
		assertEquals(6, store.getNpcKills(3031));
		assertEquals(6, store.getDry(3031, 995));
	}

	/**
	 * Which cards the player opened was written on every toggle and never read back, so the
	 * choice lasted until the client closed — and until the next kill, wherever another client
	 * shared the profile, since absorbing its counters reloads everything.
	 */
	@Test
	public void expandedCardsSurviveAReload()
	{
		when(configManager.getConfiguration(eq("itemsearch"), eq("data")))
			.thenReturn("{\"npcKills\":{},\"dry\":{},\"obtained\":{},\"expanded\":[\"1205:3031\"]}");

		store.load();

		assertTrue(store.isExpanded(1205, 3031));
		assertFalse(store.isExpanded(995, 3031));
	}

	@Test
	public void thisStoresOwnSaveIsNotMistakenForAnotherClients()
	{
		store.recordKill(3031, Collections.emptySet(), Collections.singletonList(995));
		store.save();
		ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq("itemsearch"), eq("data"), saved.capture());
		when(configManager.getConfiguration(eq("itemsearch"), eq("data"))).thenReturn(saved.getValue());

		assertEquals(false, store.absorbExternalChanges());
		assertEquals(1, store.getNpcKills(3031));
	}

	/** The game's own total is trusted upwards only: it never takes counted attempts away. */
	@Test
	public void aReportedTotalRaisesTheCountButNeverLowersIt()
	{
		store.recordKill(-1001, Collections.emptySet(), Collections.singletonList(995));
		store.raiseCountTo(-1001, 40);
		assertEquals(40, store.getNpcKills(-1001));
		store.raiseCountTo(-1001, 12);
		assertEquals(40, store.getNpcKills(-1001));
		verify(configManager).setConfiguration(eq("itemsearch"), eq("data"), anyString());
	}

	@Test
	public void corruptBlobLoadsEmpty()
	{
		when(configManager.getConfiguration(eq("itemsearch"), eq("data"))).thenReturn("{not valid json");
		TrackerStore s = new TrackerStore(configManager, new Gson());
		s.load(); // must not throw
		assertEquals(0, s.getNpcKills(3031));
	}

	@Test
	public void clearZeroesAllCountersInMemoryAndPersists()
	{
		store.recordKill(3031, Collections.emptySet(), Arrays.asList(995, 1205));
		store.clear();
		assertEquals(0, store.getNpcKills(3031));
		assertEquals(0, store.getDry(3031, 995));
		assertEquals(0, store.getDry(3031, 1205));
		// persists the emptied store so a later save() can't resurrect stale counts
		verify(configManager).setConfiguration(eq("itemsearch"), eq("data"), anyString());
	}

	@Test
	public void killWithDropIncrementsObtained()
	{
		store.recordKill(3031, new HashSet<>(Collections.singletonList(1205)), Arrays.asList(995, 1205));
		assertEquals(1, store.getObtained(3031, 1205));
		assertEquals(0, store.getObtained(3031, 995)); // possible but not dropped
	}

	@Test
	public void killWithoutDropDoesNotIncrementObtained()
	{
		store.recordKill(3031, Collections.emptySet(), Arrays.asList(995, 1205));
		assertEquals(0, store.getObtained(3031, 995));
		assertEquals(0, store.getObtained(3031, 1205));
	}

	@Test
	public void clearZeroesObtained()
	{
		store.recordKill(3031, new HashSet<>(Collections.singletonList(1205)), Collections.singletonList(1205));
		store.clear();
		assertEquals(0, store.getObtained(3031, 1205));
	}

	@Test
	public void obtainedSurvivesSaveAndReload()
	{
		store.recordKill(3031, new HashSet<>(Collections.singletonList(1205)), Collections.singletonList(1205));

		// capture the JSON save() writes, then feed it back into a fresh store's load()
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		store.save();
		verify(configManager).setConfiguration(eq("itemsearch"), eq("data"), json.capture());

		when(configManager.getConfiguration(eq("itemsearch"), eq("data"))).thenReturn(json.getValue());
		TrackerStore reloaded = new TrackerStore(configManager, new Gson());
		reloaded.load();
		assertEquals(1, reloaded.getObtained(3031, 1205));
	}

	@Test
	public void resetNpcKillsZeroesOnlyThatNpc()
	{
		store.recordKill(3031, Collections.emptySet(), Collections.singletonList(995));
		store.recordKill(3031, Collections.emptySet(), Collections.singletonList(995));
		store.recordKill(2042, Collections.emptySet(), Collections.singletonList(995));

		store.resetNpcKills(3031);

		assertEquals(0, store.getNpcKills(3031));
		assertEquals(1, store.getNpcKills(2042));       // other NPC untouched
		assertEquals(2, store.getDry(3031, 995));       // dry counters untouched
	}

	@Test
	public void resetNpcDropsClearsOnlyThatNpcsPairs()
	{
		store.recordKill(3031, new HashSet<>(Collections.singletonList(995)), Arrays.asList(995, 1205));
		store.recordKill(2042, Collections.emptySet(), Collections.singletonList(995));

		store.resetNpcDrops(3031);

		assertEquals(0, store.getDry(3031, 995));
		assertEquals(0, store.getDry(3031, 1205));
		assertEquals(0, store.getObtained(3031, 995));
		assertEquals(1, store.getNpcKills(3031));       // kills untouched
		assertEquals(1, store.getDry(2042, 995));       // other NPC untouched
	}

	@Test
	public void resetNpcDropsDoesNotPrefixCollide()
	{
		// "3031:" must not match the different NPC id 30310
		store.recordKill(30310, Collections.emptySet(), Collections.singletonList(995));
		store.resetNpcDrops(3031);
		assertEquals(1, store.getDry(30310, 995));
	}
}
