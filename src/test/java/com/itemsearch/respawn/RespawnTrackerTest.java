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
package com.itemsearch.respawn;

import com.google.gson.Gson;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RespawnTrackerTest
{
	@Mock
	private Client client;

	@Mock
	private ConfigManager configManager;

	private RespawnTimeStore store;
	private RespawnTracker tracker;
	private int tick;

	@Before
	public void setUp()
	{
		lenient().when(client.isInInstancedRegion()).thenReturn(false);
		setTick(100);
		store = new RespawnTimeStore(configManager, new Gson());
		store.load();
		tracker = new RespawnTracker(client, store);
		tracker.setTracked("Hill Giant", true);
	}

	private void setTick(int t)
	{
		tick = t;
		lenient().when(client.getTickCount()).thenReturn(t);
	}

	private NPC npc(int index, String name, WorldPoint location, boolean dead)
	{
		NPC n = mock(NPC.class);
		lenient().when(n.getIndex()).thenReturn(index);
		lenient().when(n.getName()).thenReturn(name);
		lenient().when(n.getWorldLocation()).thenReturn(location);
		lenient().when(n.isDead()).thenReturn(dead);
		lenient().when(n.getOrientation()).thenReturn(0); // facing south
		NPCComposition comp = mock(NPCComposition.class);
		lenient().when(comp.getSize()).thenReturn(1);
		lenient().when(n.getTransformedComposition()).thenReturn(comp);
		return n;
	}

	private void spawnAndTick(NPC n)
	{
		tracker.onNpcSpawned(n);
		tracker.onGameTick();
	}

	@Test
	public void deathCreatesEntryAtRememberedSpawnTile()
	{
		WorldPoint spawn = new WorldPoint(3100, 9850, 0);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));

		// it wandered before dying elsewhere
		tracker.onNpcDespawned(npc(7, "Hill Giant", new WorldPoint(3105, 9855, 0), true));
		tracker.onGameTick();

		List<RespawnEntry> entries = tracker.activeRespawns();
		assertEquals(1, entries.size());
		assertEquals(spawn, entries.get(0).getTile());
		assertFalse(entries.get(0).isApproximate());
		assertEquals(-1, entries.get(0).getRespawnAtTick()); // not learned yet
	}

	@Test
	public void despawnWithoutDeathCreatesNothing()
	{
		spawnAndTick(npc(7, "Hill Giant", new WorldPoint(3100, 9850, 0), false));
		tracker.onNpcDespawned(npc(7, "Hill Giant", new WorldPoint(3100, 9850, 0), false));
		tracker.onGameTick();
		assertTrue(tracker.activeRespawns().isEmpty());
	}

	@Test
	public void untrackedNameIsIgnored()
	{
		spawnAndTick(npc(9, "Chicken", new WorldPoint(3230, 3290, 0), false));
		tracker.onNpcDespawned(npc(9, "Chicken", new WorldPoint(3230, 3290, 0), true));
		tracker.onGameTick();
		assertTrue(tracker.activeRespawns().isEmpty());
	}

	@Test
	public void respawnLearnsTickCountAndClearsEntry()
	{
		WorldPoint spawn = new WorldPoint(3100, 9850, 0);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));
		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick(); // died at tick 100 (+1 compensation -> 101)

		setTick(150);
		spawnAndTick(npc(7, "Hill Giant", spawn, false)); // respawn: observed 151-101 = 50

		assertEquals(50, store.getRespawnTicks("Hill Giant"));
		assertTrue(tracker.activeRespawns().isEmpty());
	}

	@Test
	public void secondDeathUsesLearnedTime()
	{
		WorldPoint spawn = new WorldPoint(3100, 9850, 0);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));
		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick();
		setTick(150);
		spawnAndTick(npc(7, "Hill Giant", spawn, false)); // learns 50

		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick(); // died at tick 151

		List<RespawnEntry> entries = tracker.activeRespawns();
		assertEquals(1, entries.size());
		assertEquals(151 + 50, entries.get(0).getRespawnAtTick());
	}

	@Test
	public void minObservationWinsAndCeilingApplies()
	{
		store.learnRespawnTicks("Hill Giant", 80);
		WorldPoint spawn = new WorldPoint(3100, 9850, 0);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));
		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick(); // died 101
		setTick(150);
		spawnAndTick(npc(7, "Hill Giant", spawn, false)); // observed 50 < 80 -> learn 50
		assertEquals(50, store.getRespawnTicks("Hill Giant"));

		// an absurdly long observation (walked away) must NOT overwrite
		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick(); // died 151
		setTick(800);
		spawnAndTick(npc(7, "Hill Giant", spawn, false)); // observed 650 > 500 ceiling
		assertEquals(50, store.getRespawnTicks("Hill Giant"));
	}

	@Test
	public void deathWithoutKnownSpawnTileFallsBackApproximate()
	{
		// never saw it spawn: despawn arrives for an unwatched index
		WorldPoint deathTile = new WorldPoint(3100, 9850, 0);
		tracker.onNpcDespawned(npc(7, "Hill Giant", deathTile, true));
		tracker.onGameTick();

		List<RespawnEntry> entries = tracker.activeRespawns();
		assertEquals(1, entries.size());
		assertTrue(entries.get(0).isApproximate());
		assertEquals(deathTile, entries.get(0).getTile());
	}

	@Test
	public void invalidateAllDropsPendingAndSkipsNextSpawnTick()
	{
		WorldPoint spawn = new WorldPoint(3100, 9850, 0);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));
		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick();
		assertEquals(1, tracker.activeRespawns().size());

		tracker.invalidateAll(); // teleport / world hop
		assertTrue(tracker.activeRespawns().isEmpty());

		// the flood of spawns right after a hop must not teach bogus times
		setTick(105);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));
		assertEquals(-1, store.getRespawnTicks("Hill Giant"));
	}

	@Test
	public void entryExpiresAfterGracePeriod()
	{
		store.learnRespawnTicks("Hill Giant", 50);
		WorldPoint spawn = new WorldPoint(3100, 9850, 0);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));
		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick(); // died 101, due 151

		setTick(151 + 50); // due + grace, boundary still visible
		tracker.onGameTick();
		assertEquals(1, tracker.activeRespawns().size());

		setTick(151 + 51); // past grace
		tracker.onGameTick();
		assertTrue(tracker.activeRespawns().isEmpty());
	}

	@Test
	public void untrackingDropsState()
	{
		WorldPoint spawn = new WorldPoint(3100, 9850, 0);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));
		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick();
		assertEquals(1, tracker.activeRespawns().size());

		tracker.setTracked("Hill Giant", false);
		assertTrue(tracker.activeRespawns().isEmpty());
		assertFalse(tracker.isTracked("Hill Giant"));
	}

	@Test
	public void instancedRegionIsNeverMemorized()
	{
		when(client.isInInstancedRegion()).thenReturn(true);
		WorldPoint spawn = new WorldPoint(3100, 9850, 0);
		spawnAndTick(npc(7, "Hill Giant", spawn, false));
		tracker.onNpcDespawned(npc(7, "Hill Giant", spawn, true));
		tracker.onGameTick();
		// no watched record was created, so death falls to the approximate path;
		// instanced deaths are suppressed entirely
		assertTrue(tracker.activeRespawns().isEmpty());
	}
}
