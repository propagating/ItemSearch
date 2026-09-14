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
package com.itemsearch.ui;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** What the client knows about an item, gathered in one read so the page can show it at once. */
final class ItemFacts
{
	final int value;
	final int lowAlch;
	final int highAlch;
	final boolean members;
	final boolean tradeable;
	final boolean stackable;
	final boolean geTradeable;
	final int gePrice;
	final int geLimit;
	final double weight;
	// null when the item cannot be worn
	final ItemEquipmentStats equipment;

	private ItemFacts(ItemComposition c, ItemStats s, int gePrice)
	{
		value = c.getPrice();
		// 40% of value, which is also what a general store pays for the first one sold to it
		lowAlch = value * 2 / 5;
		highAlch = c.getHaPrice();
		members = c.isMembers();
		tradeable = c.isTradeable();
		stackable = c.isStackable();
		geTradeable = c.isGeTradeable();
		this.gePrice = gePrice;
		geLimit = s == null ? 0 : s.getGeLimit();
		weight = s == null ? 0 : s.getWeight();
		equipment = s != null && s.isEquipable() ? s.getEquipment() : null;
	}

	/** Must run on the client thread: the composition comes from the game's own cache. */
	static ItemFacts read(ItemManager itemManager, int itemId)
	{
		ItemComposition c = itemManager.getItemComposition(itemId);
		if (c == null)
		{
			return null;
		}
		return new ItemFacts(c, itemManager.getItemStats(itemId), itemManager.getItemPrice(itemId));
	}
}
