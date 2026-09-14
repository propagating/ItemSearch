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
package com.itemsearch.tracker;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The names RuneLite's Loot Tracker gives activities and containers, mapped to the wiki pages
 * this plugin's sources are named after, where the two disagree. Most agree already, apart from
 * case, so this only lists the exceptions.
 */
final class LootEventNames
{
	private static final Map<String, String> ALIASES = new HashMap<>();

	static
	{
		alias("Barrows", "Chest (Barrows)");
		alias("Tombs of Amascut", "Chest (Tombs of Amascut)");
		alias("Reward pool (Tempoross)", "Reward pool");
		alias("Casket (Tempoross)", "Casket (Reward pool)");
		alias("Bag full of gems (Percy)", "Bag full of gems");
		alias("Bag full of gems (Belona)", "Bag full of gems");
		alias("Bag full of gems (Dusuri)", "Bag full of gems");
	}

	private LootEventNames()
	{
	}

	private static void alias(String event, String source)
	{
		ALIASES.put(event.toLowerCase(Locale.ROOT), source);
	}

	/** The source name to look up for a Loot Tracker event name; the name itself when unmapped. */
	static String sourceNameFor(String eventName)
	{
		if (eventName == null)
		{
			return null;
		}
		return ALIASES.getOrDefault(eventName.trim().toLowerCase(Locale.ROOT), eventName.trim());
	}
}
