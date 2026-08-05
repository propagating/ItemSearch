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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;

/**
 * Live bookkeeping for one tracked NPC instance (keyed by NPC index — a respawned
 * NPC reuses its index, which is what lets us pair a death with its respawn).
 * Mirrors npchighlight's MemorizedNpc.
 */
@Getter
class WatchedNpc
{
	private final int npcIndex;
	private final String npcName;
	private int npcSize = 1;

	/** Tick the npc died on, or -1 when alive. */
	@Setter
	private int diedOnTick = -1;

	/** Death location, kept for the approximate fallback when no spawn tile is known. */
	@Setter
	private WorldPoint lastDeathLocation;

	@Setter
	private List<WorldPoint> possibleRespawnLocations = new ArrayList<>(2);

	WatchedNpc(NPC npc)
	{
		this.npcIndex = npc.getIndex();
		this.npcName = npc.getName();
		final NPCComposition composition = npc.getTransformedComposition();
		if (composition != null)
		{
			this.npcSize = composition.getSize();
		}
	}

	WatchedNpc(int npcIndex, String npcName, int npcSize)
	{
		this.npcIndex = npcIndex;
		this.npcName = npcName;
		this.npcSize = npcSize;
	}
}
