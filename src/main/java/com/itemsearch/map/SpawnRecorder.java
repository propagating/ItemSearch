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

import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import com.itemsearch.model.SpawnPoint;
import com.itemsearch.tracker.SpawnStore;

/**
 * Records where the player is standing as a spawn tile, or as the way into a dungeon.
 *
 * <p>The player's position can only be read on the client thread, and the result has to reach the
 * sidebar on the EDT, so every method here hops both ways and reports back through a callback.
 */
@Slf4j
public class SpawnRecorder
{
	/** Underground tiles are real world coordinates the surface map simply does not draw. */
	public static final int UNDERGROUND_Y = 6400;

	private final Client client;
	private final ClientThread clientThread;
	private final SpawnStore store;

	public SpawnRecorder(Client client, ClientThread clientThread, SpawnStore store)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.store = store;
	}

	/** Adds the player's tile to this monster's spawns. {@code onDone} runs on the EDT. */
	public void recordSpawn(int npcId, Consumer<String> onDone)
	{
		withCurrentTile(point ->
		{
			if (point == null)
			{
				report(onDone, "Not logged in");
				return;
			}
			store.addSpawn(npcId, point);
			report(onDone, "Added " + describe(point));
		});
	}

	/**
	 * Sets the player's tile as the way in. Refused underground: the entrance exists because the
	 * surface map does not draw those tiles, so one recorded down there marks a spot nobody
	 * above ground can see.
	 */
	public void recordEntrance(int npcId, Consumer<String> onDone)
	{
		withCurrentTile(point ->
		{
			if (point == null)
			{
				report(onDone, "Not logged in");
				return;
			}
			if (point.getY() >= UNDERGROUND_Y)
			{
				report(onDone, "Stand at the surface entrance, not inside");
				return;
			}
			store.setEntrance(npcId, point);
			report(onDone, "Entrance set to " + describe(point));
		});
	}

	private void withCurrentTile(Consumer<SpawnPoint> handler)
	{
		clientThread.invoke(() -> handler.accept(currentTile()));
	}

	/**
	 * Where the player is, in coordinates that mean the same thing next time.
	 *
	 * <p>Inside a raid or any other instance the game builds a throwaway copy of the map, and the
	 * player's reported position belongs to that copy — record it and you have written down a
	 * number that will point somewhere else on the next visit. The template coordinate the copy
	 * was made from is the stable one, so that is what is stored. Client thread only.
	 */
	SpawnPoint currentTile()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		WorldPoint where = player.getWorldLocation();
		if (client.isInInstancedRegion())
		{
			LocalPoint local = player.getLocalLocation();
			WorldPoint template = local == null ? null : WorldPoint.fromLocalInstance(client, local);
			if (template != null)
			{
				where = template;
			}
		}
		return where == null ? null : new SpawnPoint(where.getX(), where.getY(), where.getPlane());
	}

	private static String describe(SpawnPoint point)
	{
		return point.getX() + ", " + point.getY()
			+ (point.getPlane() > 0 ? ", floor " + point.getPlane() : "");
	}

	private static void report(Consumer<String> onDone, String message)
	{
		if (onDone != null)
		{
			SwingUtilities.invokeLater(() -> onDone.accept(message));
		}
	}
}
