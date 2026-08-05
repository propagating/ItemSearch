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

/**
 * Decides how a static location gets shown: the in-game world map only renders the
 * ground floor of the surface, so upper floors and special map layers (Keldagrim,
 * Prifddinas interiors, ...) instead deep-link to the map explorer the wiki's own
 * maps are built on, which can display any layer.
 */
public final class MapLinks
{
	private MapLinks()
	{
	}

	/**
	 * @return a browser URL showing the location on its proper layer, or null when the
	 * in-game world map can show it (surface ground floor) or there are no coordinates.
	 */
	public static String layeredMapUrl(Integer x, Integer y, Integer plane, Integer mapId)
	{
		if (x == null || y == null)
		{
			return null;
		}
		boolean groundFloor = plane == null || plane == 0;
		boolean surfaceMap = mapId == null || mapId <= 0;
		if (groundFloor && surfaceMap)
		{
			return null;
		}
		return "https://mejrs.github.io/osrs?m=" + (surfaceMap ? -1 : mapId)
			+ "&z=4&p=" + (groundFloor ? 0 : plane) + "&x=" + x + "&y=" + y;
	}
}
