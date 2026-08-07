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
package com.itemsearch;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ItemSearchConfig.GROUP)
public interface ItemSearchConfig extends Config
{
	String GROUP = "itemsearch";

	@ConfigItem(
		keyName = "resetAll",
		name = "Reset all counters",
		description = "Clear every tracked kill and dry counter"
	)
	default boolean resetAll()
	{
		return false;
	}

	@ConfigItem(
		keyName = "autoMarkSpawns",
		name = "Auto-mark spawns on map",
		description = "Automatically mark an NPC's spawn points on the world map when opening its details"
	)
	default boolean autoMarkSpawns()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBrowseImages",
		name = "Show images while browsing",
		description = "Fetch each monster's picture from the OSRS Wiki for the rows of the browse "
			+ "list. Only the rows on screen are fetched, and each image is cached on disk "
			+ "afterwards. Item rows always show their in-game icon, which needs no download.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or "
			+ "verified by RuneLite developers"
	)
	default boolean showBrowseImages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDropsMenuEntry",
		name = "Right-click \"Show drops\"",
		description = "Add a Show drops option to the right-click menu of NPCs this plugin "
			+ "has drop data for, opening their page in the sidebar"
	)
	default boolean showDropsMenuEntry()
	{
		return true;
	}
}
