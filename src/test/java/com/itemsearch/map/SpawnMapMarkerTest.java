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
import com.itemsearch.model.SourceData;
import com.itemsearch.model.SourceType;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.SpawnPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
import static org.mockito.Mockito.atLeastOnce;
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

	private static SourceData npc(int id, String name, List<SpawnPoint> spawns)
	{
		return npc(id, name, spawns, null);
	}

	private static SourceData npc(int id, String name, List<SpawnPoint> spawns, SpawnPoint entrance)
	{
		return new SourceData(id, name, 42, null, Collections.emptyList(), spawns,
			Collections.singletonList(id), null, SourceType.KILL, null, null, null, null, null,
			entrance, null);
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

	private static ShopData shopAt(Integer x, Integer y, Integer plane, Integer mapId)
	{
		return new ShopData("Test Shop", "Somewhere", "Coins", x, y, plane, mapId,
			Collections.emptyList());
	}

	@Test
	public void surfaceShopPinsTheInGameMap()
	{
		marker.showShop(shopAt(3203, 3434, 0, null));
		verify(worldMapPointManager).add(any(WorldMapPoint.class));
	}

	// Being thrown out to a browser mid-session is worse than an approximate pin, so a layered
	// location is marked in game and the tooltip says the surface map cannot place it exactly.
	@Test
	public void layeredShopPinsInGameAndSaysSoInTheTooltip()
	{
		marker.showShop(shopAt(2857, 10189, 0, 10));
		ArgumentCaptor<WorldMapPoint> captor = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(worldMapPointManager).add(captor.capture());
		assertTrue("tooltip must flag the approximation: " + captor.getValue().getTooltip(),
			captor.getValue().getTooltip().contains("approximate"));
	}

	@Test
	public void upstairsSpawnsPinInGameWithTheFloorNoted()
	{
		marker.showSpawns(npc(1, "Upstairs guy", Arrays.asList(
			new SpawnPoint(2662, 3657, 1), new SpawnPoint(2660, 3655, 2))));
		ArgumentCaptor<WorldMapPoint> captor = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(worldMapPointManager, times(2)).add(captor.capture());
		assertTrue("tooltip must name the floor: " + captor.getAllValues().get(0).getTooltip(),
			captor.getAllValues().get(0).getTooltip().contains("floor 1"));
	}

	@Test
	public void groundFloorSpawnsGetAPlainTooltip()
	{
		marker.showSpawns(npc(1, "Goblin", Collections.singletonList(new SpawnPoint(3246, 3235, 0))));
		ArgumentCaptor<WorldMapPoint> captor = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(worldMapPointManager).add(captor.capture());
		assertEquals("Goblin", captor.getValue().getTooltip());
	}

	@Test
	public void mixedPlaneSpawnsStillPinInGame()
	{
		marker.showSpawns(npc(1, "Mixed", Arrays.asList(
			new SpawnPoint(2662, 3657, 1), new SpawnPoint(2660, 3655, 0))));
		verify(worldMapPointManager, times(2)).add(any(WorldMapPoint.class));
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

	// Defence in depth: the generator caps spawns per source, but a stale or hand-edited data
	// file must not be able to stall the client by flooding the world map with markers.
	@Test
	public void floodOfSpawnsIsTruncatedRatherThanDrawn()
	{
		List<SpawnPoint> flood = new java.util.ArrayList<>();
		for (int i = 0; i < 500; i++)
		{
			flood.add(new SpawnPoint(3000 + i, 3000, 0));
		}
		marker.showSpawns(npc(1, "Rat", flood));
		verify(worldMapPointManager, times(SpawnMapMarker.MAX_MARKERS))
			.add(any(WorldMapPoint.class));
	}

	@Test
	public void emptySpawnListOnlyClears()
	{
		marker.showSpawns(npc(1, "Zulrah", Collections.emptyList()));
		verify(worldMapPointManager, never()).add(any());
		verify(worldMap, never()).setWorldMapPositionTarget(any());
	}

	/**
	 * A steel dragon's tiles are at y 9435, which the surface world map never draws — so its
	 * markers are invisible from above ground and pointless from inside the dungeon. The marker
	 * worth having is the rope, and the map should open there.
	 */
	@Test
	public void anUndergroundMonsterIsMarkedAtItsEntrance()
	{
		marker.showSpawns(npc(274, "Steel dragon",
			Collections.singletonList(new SpawnPoint(2702, 9447, 0)),
			new SpawnPoint(2760, 3061, 0)));

		ArgumentCaptor<WorldMapPoint> points = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(worldMapPointManager, atLeastOnce()).add(points.capture());
		assertTrue("the entrance is marked",
			points.getAllValues().stream().anyMatch(
				p -> p.getWorldPoint().getX() == 2760 && p.getWorldPoint().getY() == 3061));
		assertTrue("and says what it is",
			points.getAllValues().stream().anyMatch(
				p -> p.getTooltip() != null && p.getTooltip().endsWith("entrance")));

		ArgumentCaptor<WorldPoint> target = ArgumentCaptor.forClass(WorldPoint.class);
		verify(worldMap).setWorldMapPositionTarget(target.capture());
		assertEquals("the map opens on the way in, not on tiles it cannot draw",
			3061, target.getValue().getY());
	}

	/** An above-ground monster has no entrance to mark; its own tiles are the answer. */
	@Test
	public void aSurfaceMonsterIsMarkedWhereItStands()
	{
		marker.showSpawns(npc(3031, "Goblin",
			Collections.singletonList(new SpawnPoint(3250, 3250, 0))));

		ArgumentCaptor<WorldMapPoint> points = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(worldMapPointManager, atLeastOnce()).add(points.capture());
		assertEquals(1, points.getAllValues().size());
		assertFalse(points.getAllValues().stream().anyMatch(
			p -> p.getTooltip() != null && p.getTooltip().endsWith("entrance")));
	}
}
