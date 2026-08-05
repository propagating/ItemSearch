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

import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AttackStylesTest
{
	@Test
	public void meleeSubStylesMapToProtectFromMelee()
	{
		assertEquals(ProtectionPrayer.MELEE, AttackStyles.prayerFor("stab"));
		assertEquals(ProtectionPrayer.MELEE, AttackStyles.prayerFor("slash"));
		assertEquals(ProtectionPrayer.MELEE, AttackStyles.prayerFor("crush"));
		assertEquals(ProtectionPrayer.MELEE, AttackStyles.prayerFor("melee"));
	}

	@Test
	public void rangedAndMagicMap()
	{
		assertEquals(ProtectionPrayer.MISSILES, AttackStyles.prayerFor("ranged"));
		assertEquals(ProtectionPrayer.MAGIC, AttackStyles.prayerFor("magic"));
	}

	@Test
	public void matchingIsCaseInsensitive()
	{
		assertEquals(ProtectionPrayer.MELEE, AttackStyles.prayerFor("CRUSH"));
		assertEquals(ProtectionPrayer.MAGIC, AttackStyles.prayerFor("Magic"));
	}

	@Test
	public void unprotectableUnknownAndNullReturnNull()
	{
		assertNull(AttackStyles.prayerFor("dragonfire"));
		assertNull(AttackStyles.prayerFor("typeless"));
		assertNull(AttackStyles.prayerFor("special"));
		assertNull(AttackStyles.prayerFor("curse"));
		assertNull(AttackStyles.prayerFor("banana"));
		assertNull(AttackStyles.prayerFor(null));
	}

	@Test
	public void unprayableAttacksMapToProtectionItems()
	{
		assertEquals(ProtectionItem.DRAGONFIRE_SHIELD, AttackStyles.protectionItemFor("dragonfire"));
		assertEquals(ProtectionItem.ELEMENTAL_SHIELD, AttackStyles.protectionItemFor("icy breath"));
		assertEquals(ProtectionItem.DRAGONFIRE_SHIELD, AttackStyles.protectionItemFor("Dragonfire"));
	}

	@Test
	public void stylesWithoutAProtectionItemReturnNull()
	{
		assertNull(AttackStyles.protectionItemFor("typeless"));
		assertNull(AttackStyles.protectionItemFor("magic"));
		assertNull(AttackStyles.protectionItemFor("banana"));
		assertNull(AttackStyles.protectionItemFor(null));
	}

	@Test
	public void protectionsForPrayableStyleIsASingleFullPrayer()
	{
		List<Protection> p = AttackStyles.protectionsFor("crush");
		assertEquals(1, p.size());
		assertTrue(p.get(0).isPrayer());
		assertEquals(ProtectionPrayer.MELEE, p.get(0).getPrayer());
		assertEquals("Protect from Melee", p.get(0).getDisplayName());
	}

	@Test
	public void protectionsForDragonfireIsPartialMagicPlusShield()
	{
		List<Protection> p = AttackStyles.protectionsFor("dragonfire");
		assertEquals(2, p.size());
		assertEquals(ProtectionPrayer.MAGIC, p.get(0).getPrayer());
		assertEquals("Protect from Magic (partial)", p.get(0).getDisplayName());
		assertEquals(ProtectionItem.DRAGONFIRE_SHIELD, p.get(1).getItem());
		assertEquals("Protect with Dragonfire shield (or antifire potion)", p.get(1).getDisplayName());
	}

	@Test
	public void protectionsForMetallicDragonfireIsShieldOnlyNoPrayer()
	{
		List<Protection> p = AttackStyles.protectionsFor("metallic dragonfire");
		assertEquals(1, p.size());
		assertFalse(p.get(0).isPrayer());
		assertEquals(ProtectionItem.DRAGONFIRE_SHIELD, p.get(0).getItem());
		assertEquals("Protect with Dragonfire shield (or antifire potion)", p.get(0).getDisplayName());
	}

	@Test
	public void protectionsForIcyBreathIsPartialMagicPlusElementalShield()
	{
		List<Protection> p = AttackStyles.protectionsFor("icy breath");
		assertEquals(2, p.size());
		assertEquals(ProtectionPrayer.MAGIC, p.get(0).getPrayer());
		assertEquals("Protect from Magic (partial)", p.get(0).getDisplayName());
		assertEquals(ProtectionItem.ELEMENTAL_SHIELD, p.get(1).getItem());
		assertEquals("Protect with Elemental shield", p.get(1).getDisplayName());
	}

	@Test
	public void protectionsForUncounterableStyleIsEmpty()
	{
		assertTrue(AttackStyles.protectionsFor("typeless").isEmpty());
		assertTrue(AttackStyles.protectionsFor("banana").isEmpty());
		assertTrue(AttackStyles.protectionsFor(null).isEmpty());
	}
}
