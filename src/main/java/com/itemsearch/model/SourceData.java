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
package com.itemsearch.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
@AllArgsConstructor
public class SourceData
{
	int id;
	String name;
	int combatLevel;
	@With
	String wikiImageUrl;
	// resolved from the shared tables at load time, which is what the copy is for
	@With
	List<DropSource> drops;
	@With
	List<SpawnPoint> spawnPoints;
	// every game NPC id that maps to this monster (same name + combat level); includes id.
	// A kill under any of these counts as this monster. Empty for non-NPC sources
	// (chests, gathering nodes), which nothing can be killed under.
	List<Integer> npcIds;
	// combat stats + attack styles; null for non-combat NPCs or pre-combat bundled data
	CombatStats combat;
	// how the item is obtained here; never null after loading (older data defaults to KILL)
	SourceType type;
	// wiki {{Map}} location for chest/node sources; null for NPC-backed sources, which
	// locate themselves through spawnPoints instead
	Integer x;
	Integer y;
	Integer plane;
	// indexes into the bundled file's shared drop tables; resolved into drops at load time
	java.util.List<Integer> tables;
	// the wiki page behind this source, when the display name is no longer that page's title.
	// A version split calls itself "Killerwatt (Attacking)", which is not a page anyone can
	// fetch. Null whenever the name is the title, which is most sources.
	String wikiPage;
	// the surface tile you go in by, when this source's own tiles are somewhere the world map
	// does not draw (underground). Null for anything reachable on foot above ground.
	@With
	SpawnPoint entrance;
	// where this source is found, in words, when the wiki gives no coordinates the map can use —
	// the Black Knight Catacombs have no wiki map, so their tiles are drawing coordinates
	String locationName;
	// the wiki versions this source stands for, when it stands for several that look or fight
	// differently. Null for the rest, which is most sources.
	List<Phase> phases;
	// for a pickpocket source: the Thieving level it takes and the experience a success gives.
	// Null when the wiki's table does not list the NPC, and for everything that is not robbed.
	Integer thievingLevel;
	Double thievingXp;

	/** A source with no thieving requirement recorded. */
	public SourceData(int id, String name, int combatLevel, String wikiImageUrl,
		List<DropSource> drops, List<SpawnPoint> spawnPoints, List<Integer> npcIds,
		CombatStats combat, SourceType type, Integer x, Integer y, Integer plane,
		java.util.List<Integer> tables, String wikiPage, SpawnPoint entrance, String locationName,
		List<Phase> phases)
	{
		this(id, name, combatLevel, wikiImageUrl, drops, spawnPoints, npcIds, combat, type, x, y,
			plane, tables, wikiPage, entrance, locationName, phases, null, null);
	}

	/**
	 * The title to ask the wiki about. The display name is not always one: version splits and
	 * pickpocket variants are named for the player's benefit, not the wiki's.
	 */
	public String wikiTitle()
	{
		return wikiPage != null && !wikiPage.trim().isEmpty() ? wikiPage : name;
	}
}
