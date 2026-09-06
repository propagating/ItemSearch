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
import net.runelite.api.NPC;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import com.itemsearch.data.BundledDataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KillTrackerTest
{
	@Mock
	private TrackerStore store;

	@Mock
	private ItemManager itemManager;

	@Mock
	private DropConditions conditions;

	private KillTracker tracker;

	@Before
	public void setUp()
	{
		// canonicalize is identity by default; individual tests override for noted ids
		lenient().when(itemManager.canonicalize(anyInt()))
			.thenAnswer(inv -> inv.getArgument(0));
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		ds.load();
		tracker = new KillTracker(ds, store, itemManager, conditions);
	}

	/**
	 * A brimstone key is on the table only while Konar assigned the task, so an off-task kill was
	 * never a chance at one and must not count against its dry streak. Everything else the monster
	 * drops still counts — the gate is per drop, not per kill.
	 */
	@Test
	public void anOffTaskKillIsNotDryForAConditionalDrop()
	{
		when(conditions.isMet("KONAR_TASK")).thenReturn(false);

		tracker.onKill(2, new HashSet<>(Collections.singletonList(526)));

		verify(store).recordKill(2, new HashSet<>(Collections.singletonList(526)),
			new HashSet<>(Collections.singletonList(526)));
	}

	/** On task, the key is in the pool and a kill without it is a genuine dry kill. */
	@Test
	public void anOnTaskKillIsDryForAConditionalDrop()
	{
		when(conditions.isMet("KONAR_TASK")).thenReturn(true);

		tracker.onKill(2, new HashSet<>(Collections.singletonList(526)));

		verify(store).recordKill(2, new HashSet<>(Collections.singletonList(526)),
			new HashSet<>(Arrays.asList(526, 23083)));
	}

	/**
	 * The drop landing is better evidence than the varp it was judged by: if a key arrives while we
	 * believe the player is off task, the belief is what was wrong, and the drop still counts.
	 */
	@Test
	public void aConditionalDropThatLandsCountsWhateverWeBelieved()
	{
		// lenient: the point of the test is that a landed drop short-circuits before the condition
		// is ever consulted, so this stub is expected to go unused
		lenient().when(conditions.isMet("KONAR_TASK")).thenReturn(false);

		tracker.onKill(2, new HashSet<>(Arrays.asList(526, 23083)));

		verify(store).recordKill(2, new HashSet<>(Arrays.asList(526, 23083)),
			new HashSet<>(Arrays.asList(526, 23083)));
	}

	@Test
	public void killLooksUpPossibleDropsAndRecords()
	{
		// Goblin (3031) can drop 995 and 1205; this kill dropped only 1205
		tracker.onKill(3031, new HashSet<>(Collections.singletonList(1205)));
		verify(store).recordKill(3031,
			new HashSet<>(Collections.singletonList(1205)),
			new HashSet<>(Arrays.asList(995, 1205)));
		verify(store).save();
	}

	@Test
	public void killUnderVariantIdRecordsUnderCanonicalId()
	{
		// Test Beast has npcIds [7001, 7002]; a kill under the variant id 7002 must accumulate
		// under the canonical id 7001 so all variants share one dry counter
		BundledDataSource multi = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		multi.load();
		KillTracker t = new KillTracker(multi, store, itemManager, conditions);

		t.onKill(7002, new HashSet<>(Collections.singletonList(995)));

		verify(store).recordKill(7001,
			new HashSet<>(Collections.singletonList(995)),
			new HashSet<>(Collections.singletonList(995)));
	}

	@Test
	public void killResolvedByNameAndLevelWhenIdNotInData()
	{
		// Test Beast is id 7001 in data; a live kill reports an id our data never listed (88888)
		// but the same name + level, so it must still resolve and record under canonical 7001
		BundledDataSource multi = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		multi.load();
		KillTracker t = new KillTracker(multi, store, itemManager, conditions);

		NPC beast = org.mockito.Mockito.mock(NPC.class);
		when(beast.getId()).thenReturn(88888);
		when(beast.getName()).thenReturn("Test Beast");
		when(beast.getCombatLevel()).thenReturn(100);

		t.onNpcLootReceived(new NpcLootReceived(beast,
			Collections.singletonList(new ItemStack(995, 1))));

		verify(store).recordKill(7001,
			new HashSet<>(Collections.singletonList(995)),
			new HashSet<>(Collections.singletonList(995)));
	}

	@Test
	public void unknownNpcIsIgnored()
	{
		tracker.onKill(999999, Collections.emptySet());
		verify(store, org.mockito.Mockito.never()).recordKill(
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void notedDropIsCanonicalizedToBaseItemId()
	{
		// a noted Bronze dagger drops as id 1206; canonicalize maps it back to 1205
		// so the drop is detected instead of incrementing the dry counter
		when(itemManager.canonicalize(1206)).thenReturn(1205);
		NPC goblin = org.mockito.Mockito.mock(NPC.class);
		when(goblin.getId()).thenReturn(3031);

		tracker.onNpcLootReceived(new NpcLootReceived(goblin,
			Collections.singletonList(new ItemStack(1206, 1))));

		verify(store).recordKill(3031,
			new HashSet<>(Collections.singletonList(1205)),
			new HashSet<>(Arrays.asList(995, 1205)));
	}
}
