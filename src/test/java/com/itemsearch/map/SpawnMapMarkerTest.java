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
package com.itemsearch.map;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import com.itemsearch.model.NpcData;
import com.itemsearch.model.SpawnPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SpawnMapMarkerTest
{
	@Mock
	private Client client;

	@Mock
	private ClientThread clientThread;

	@Mock
	private WorldMapPointManager worldMapPointManager;

	@Mock
	private WorldMap worldMap;

	private SpawnMapMarker marker;

	private static NpcData npc(int id, String name, List<SpawnPoint> spawns)
	{
		return new NpcData(id, name, 42, null, Collections.emptyList(), spawns,
			Collections.singletonList(id), null);
	}

	@Before
	public void setUp()
	{
		// run client-thread work immediately so the pan target is observable
		lenient().doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		lenient().when(client.getWorldMap()).thenReturn(worldMap);

		marker = new SpawnMapMarker(client, clientThread, worldMapPointManager,
			new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
	}

	@Test
	public void showSpawnsAddsOnePointPerSpawnAndPansToFirst()
	{
		marker.showSpawns(npc(1, "Goblin", Arrays.asList(
			new SpawnPoint(3246, 3235, 0), new SpawnPoint(3251, 3231, 0))));

		ArgumentCaptor<WorldMapPoint> captor = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(worldMapPointManager, times(2)).add(captor.capture());
		assertEquals(new WorldPoint(3246, 3235, 0), captor.getAllValues().get(0).getWorldPoint());
		assertEquals(new WorldPoint(3251, 3231, 0), captor.getAllValues().get(1).getWorldPoint());
		assertEquals("Goblin", captor.getAllValues().get(0).getTooltip());
		verify(worldMap).setWorldMapPositionTarget(new WorldPoint(3246, 3235, 0));
	}

	@Test
	public void showingAnotherNpcReplacesPreviousMarkers()
	{
		marker.showSpawns(npc(1, "Goblin", Collections.singletonList(new SpawnPoint(1, 2, 0))));
		marker.showSpawns(npc(2, "Cow", Collections.singletonList(new SpawnPoint(3, 4, 0))));

		ArgumentCaptor<WorldMapPoint> added = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(worldMapPointManager, times(2)).add(added.capture());
		// the first NPC's point was removed when the second was shown
		verify(worldMapPointManager).remove(added.getAllValues().get(0));
	}

	@Test
	public void clearRemovesExactlyOurPoints()
	{
		marker.showSpawns(npc(1, "Goblin", Arrays.asList(
			new SpawnPoint(1, 2, 0), new SpawnPoint(3, 4, 0))));
		marker.clear();

		ArgumentCaptor<WorldMapPoint> added = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(worldMapPointManager, times(2)).add(added.capture());
		for (WorldMapPoint p : added.getAllValues())
		{
			verify(worldMapPointManager).remove(p);
		}
		// idempotent: clearing again removes nothing further
		marker.clear();
		verify(worldMapPointManager, times(2)).remove(any(WorldMapPoint.class));
	}

	@Test
	public void emptySpawnListOnlyClears()
	{
		marker.showSpawns(npc(1, "Zulrah", Collections.emptyList()));
		verify(worldMapPointManager, never()).add(any());
		verify(worldMap, never()).setWorldMapPositionTarget(any());
	}
}
