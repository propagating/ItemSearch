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
package com.itemsearch.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Maps an NPC attack-type token to how the player counters it. Melee sub-styles map to Protect from
 * Melee, ranged to Protect from Missiles, magic to Protect from Magic; (chromatic) dragonfire and icy
 * breath add a partial Protect from Magic plus a blocking shield; metallic dragonfire is typeless
 * (shield/potion only, no prayer); anything else (typeless/special/curse/unknown) has no counter.
 */
public final class AttackStyles
{
	private AttackStyles()
	{
	}

	/**
	 * @return the protection prayer for the given attack-type token, or {@code null} when the
	 *         attack cannot be prayed against (dragonfire, typeless, special, curse, unknown, null).
	 */
	@Nullable
	public static ProtectionPrayer prayerFor(String attackType)
	{
		if (attackType == null)
		{
			return null;
		}
		switch (attackType.toLowerCase(Locale.ROOT))
		{
			case "stab":
			case "slash":
			case "crush":
			case "melee":
				return ProtectionPrayer.MELEE;
			case "ranged":
				return ProtectionPrayer.MISSILES;
			case "magic":
				return ProtectionPrayer.MAGIC;
			default:
				return null;
		}
	}

	/**
	 * @return whether the token is a ground-targeted "bomb": a projectile aimed at a tile that
	 *         damages the area around where it lands. It has no prayer of its own — a bomb that a
	 *         prayer does block carries that in the attack's prayStyle.
	 */
	public static boolean isBomb(String attackType)
	{
		return attackType != null && "bomb".equals(attackType.toLowerCase(Locale.ROOT));
	}

	/**
	 * @return the item that counters this attack (shown as its icon) when no prayer applies —
	 *         an Anti-dragon shield for dragonfire, an Elemental shield for icy breath — else {@code null}.
	 */
	@Nullable
	public static ProtectionItem protectionItemFor(String attackType)
	{
		if (attackType == null)
		{
			return null;
		}
		switch (attackType.toLowerCase(Locale.ROOT))
		{
			case "dragonfire":
			case "metallic dragonfire":
				return ProtectionItem.ANTIDRAGON_SHIELD;
			case "icy breath":
				return ProtectionItem.ELEMENTAL_SHIELD;
			default:
				return null;
		}
	}

	/**
	 * Every way to defend against the given attack, in the order they should be shown. A prayable
	 * style yields its single full-protection prayer; dragonfire and icy breath yield a partial
	 * Protect from Magic plus the shield that blocks them (per the OSRS Wiki, Protect from Magic
	 * partially reduces both); anything else yields an empty list (nothing counters it).
	 */
	public static List<Protection> protectionsFor(String attackType)
	{
		ProtectionPrayer prayer = prayerFor(attackType);
		if (prayer != null)
		{
			return Collections.singletonList(Protection.prayer(prayer, false));
		}
		if (attackType == null)
		{
			return Collections.emptyList();
		}
		switch (attackType.toLowerCase(Locale.ROOT))
		{
			case "dragonfire":
				return Arrays.asList(
					Protection.prayer(ProtectionPrayer.MAGIC, true),
					Protection.item(ProtectionItem.ANTIDRAGON_SHIELD, "(or antifire potion)"));
			case "metallic dragonfire":
				// metallic dragons' dragonfire is typeless — Protect from Magic does nothing,
				// only a shield or antifire potion reduces it
				return Collections.singletonList(
					Protection.item(ProtectionItem.ANTIDRAGON_SHIELD, "(or antifire potion)"));
			case "icy breath":
				return Arrays.asList(
					Protection.prayer(ProtectionPrayer.MAGIC, true),
					Protection.item(ProtectionItem.ELEMENTAL_SHIELD, null));
			default:
				return Collections.emptyList();
		}
	}
}
