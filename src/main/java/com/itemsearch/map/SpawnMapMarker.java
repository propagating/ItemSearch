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
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import com.itemsearch.model.NpcData;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.SpawnPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Sole owner of this plugin's world-map state: shows one NPC's spawn markers at a
 * time and sets the map pan target so opening the world map centers on the spawn.
 * All methods are EDT-safe; the pan target is marshalled to the client thread.
 */
@Slf4j
public class SpawnMapMarker
{
	private final Client client;
	private final ClientThread clientThread;
	private final WorldMapPointManager worldMapPointManager;
	private final BufferedImage markerIcon;

	// points we added; clear() removes exactly these, never other plugins' pins
	private final List<WorldMapPoint> shown = new ArrayList<>();

	public SpawnMapMarker(Client client, ClientThread clientThread,
		WorldMapPointManager worldMapPointManager, BufferedImage markerIcon)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.worldMapPointManager = worldMapPointManager;
		this.markerIcon = markerIcon;
	}

	public void showSpawns(NpcData npc)
	{
		clear();
		if (markerIcon == null)
		{
			log.warn("itemsearch: marker icon missing, not marking map");
			return;
		}
		List<SpawnPoint> spawns = npc.getSpawnPoints();
		if (spawns.isEmpty())
		{
			return;
		}
		for (SpawnPoint sp : spawns)
		{
			WorldMapPoint point = WorldMapPoint.builder()
				.worldPoint(new WorldPoint(sp.getX(), sp.getY(), sp.getPlane()))
				.image(markerIcon)
				.snapToEdge(true)
				.jumpOnClick(true)
				.name(npc.getName())
				.tooltip(npc.getName())
				.build();
			worldMapPointManager.add(point);
			shown.add(point);
		}
		SpawnPoint first = spawns.get(0);
		WorldPoint target = new WorldPoint(first.getX(), first.getY(), first.getPlane());
		clientThread.invokeLater(() ->
		{
			WorldMap worldMap = client.getWorldMap();
			if (worldMap != null)
			{
				worldMap.setWorldMapPositionTarget(target);
			}
		});
	}

	/** Marks a single shop's location and pans the world map to it. */
	public void showShop(ShopData shop)
	{
		clear();
		if (markerIcon == null)
		{
			log.warn("itemsearch: marker icon missing, not marking map");
			return;
		}
		if (shop.getX() == null || shop.getY() == null)
		{
			return;
		}
		WorldPoint target = new WorldPoint(shop.getX(), shop.getY(),
			shop.getPlane() == null ? 0 : shop.getPlane());
		WorldMapPoint point = WorldMapPoint.builder()
			.worldPoint(target)
			.image(markerIcon)
			.snapToEdge(true)
			.jumpOnClick(true)
			.name(shop.getName())
			.tooltip(shop.getName())
			.build();
		worldMapPointManager.add(point);
		shown.add(point);
		clientThread.invokeLater(() ->
		{
			WorldMap worldMap = client.getWorldMap();
			if (worldMap != null)
			{
				worldMap.setWorldMapPositionTarget(target);
			}
		});
	}

	public void clear()
	{
		for (WorldMapPoint point : shown)
		{
			worldMapPointManager.remove(point);
		}
		shown.clear();
	}
}
