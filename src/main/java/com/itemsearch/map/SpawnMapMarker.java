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
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import com.itemsearch.model.SourceData;
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
	/**
	 * Ceiling on markers drawn for one source. The generator already caps spawns well below this;
	 * the limit exists so a stale or hand-edited data file cannot stall the client by asking for
	 * hundreds of world-map points at once.
	 */
	public static final int MAX_MARKERS = 50;

	/**
	 * How far to lift the entrance marker off the map's own dungeon icon, at full zoom.
	 *
	 * <p>Our marker is drawn at a fixed size whatever the zoom, but the icon underneath is part of
	 * the map and shrinks with it — so a fixed lift that clears the icon zoomed in leaves the
	 * marker floating well above it zoomed out. The lift scales with the zoom for that reason.
	 */
	private static final int ENTRANCE_LIFT = 20;

	/** The zoom {@link #ENTRANCE_LIFT} was measured at, i.e. fully zoomed in. */
	private static final double FULL_ZOOM = 8.0;

	// kept so the lift can follow the zoom while the map is open
	private WorldMapPoint entranceMarker;

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

	public void showSpawns(SourceData npc)
	{
		clear();
		List<SpawnPoint> spawns = npc.getSpawnPoints();
		if (spawns.isEmpty())
		{
			return;
		}
		if (markerIcon == null)
		{
			log.warn("itemsearch: marker icon missing, not marking map");
			return;
		}
		if (spawns.size() > MAX_MARKERS)
		{
			log.debug("itemsearch: {} has {} spawns, marking the first {}",
				npc.getName(), spawns.size(), MAX_MARKERS);
			spawns = spawns.subList(0, MAX_MARKERS);
		}
		for (SpawnPoint sp : spawns)
		{
			show(marker(worldPoint(sp), npc.getName(), tooltipFor(npc.getName(), sp.getPlane(), null)));
		}
		// A monster that lives underground has its markers drawn on tiles the surface world map
		// never shows, so from above ground they are invisible and from below they are redundant
		// with standing there. The useful marker is the way in.
		SpawnPoint entrance = npc.getEntrance();
		if (entrance != null)
		{
			entranceMarker = marker(worldPoint(entrance), npc.getName(), npc.getName() + " — entrance");
			liftEntranceMarker();
			show(entranceMarker);
		}

		// pan to the entrance when there is one: it is the part of the trip the player has to
		// make, and the spawn tiles are somewhere the map cannot show them yet
		panTo(worldPoint(entrance != null ? entrance : spawns.get(0)));
	}

	/** Marks a single shop's location and pans the world map to it. */
	public void showShop(ShopData shop)
	{
		clear();
		if (shop.getX() == null || shop.getY() == null)
		{
			return;
		}
		if (markerIcon == null)
		{
			log.warn("itemsearch: marker icon missing, not marking map");
			return;
		}
		WorldPoint target = new WorldPoint(shop.getX(), shop.getY(),
			shop.getPlane() == null ? 0 : shop.getPlane());
		show(marker(target, shop.getName(),
			tooltipFor(shop.getName(), shop.getPlane(), shop.getMapId())));
		panTo(target);
	}

	private static WorldPoint worldPoint(SpawnPoint sp)
	{
		return new WorldPoint(sp.getX(), sp.getY(), sp.getPlane());
	}

	// every marker this plugin draws is the same pin; only where it is and what it says differ
	private WorldMapPoint marker(WorldPoint where, String name, String tooltip)
	{
		return WorldMapPoint.builder()
			.worldPoint(where)
			.image(markerIcon)
			.snapToEdge(true)
			.jumpOnClick(true)
			.name(name)
			.tooltip(tooltip)
			.build();
	}

	private void show(WorldMapPoint point)
	{
		worldMapPointManager.add(point);
		shown.add(point);
	}

	// the map is the game's, so its target is set on the client thread
	private void panTo(WorldPoint target)
	{
		clientThread.invokeLater(() ->
		{
			WorldMap worldMap = client.getWorldMap();
			if (worldMap != null)
			{
				worldMap.setWorldMapPositionTarget(target);
			}
		});
	}

	/**
	 * Marker tooltip, saying so when the surface map cannot place the point exactly.
	 *
	 * <p>The in-game map draws the surface, so a point on an upper floor or a separate map layer
	 * lands at those coordinates on the surface rather than where it really is. Naming the floor
	 * is more use than being thrown out to an external map mid-session.
	 */
	private static String tooltipFor(String name, Integer plane, Integer mapId)
	{
		boolean layered = mapId != null && mapId > 0;
		boolean upstairs = plane != null && plane > 0;
		if (layered)
		{
			return name + " — separate map layer, surface position approximate";
		}
		if (upstairs)
		{
			return name + " — floor " + plane + ", surface position approximate";
		}
		return name;
	}

	/**
	 * Keeps the entrance marker clear of the map's own dungeon icon as the map is zoomed.
	 *
	 * <p>Called on the client thread each tick while a marker is shown. {@code imagePoint} is the
	 * pixel of the image pinned to the tile, so pushing it below centre lifts the marker.
	 */
	public void liftEntranceMarker()
	{
		WorldMapPoint marker = entranceMarker;
		BufferedImage icon = markerIcon;
		if (marker == null || icon == null)
		{
			return;
		}
		WorldMap worldMap = client.getWorldMap();
		double zoom = worldMap == null ? FULL_ZOOM : worldMap.getWorldMapZoom();
		int lift = (int) Math.round(ENTRANCE_LIFT * Math.min(zoom, FULL_ZOOM) / FULL_ZOOM);
		marker.setImagePoint(new Point(icon.getWidth() / 2, icon.getHeight() / 2 + lift));
	}

	public void clear()
	{
		for (WorldMapPoint point : shown)
		{
			worldMapPointManager.remove(point);
		}
		shown.clear();
		entranceMarker = null;
	}
}
