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

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void reciprocalRatesStayWhole()
	{
		assertEquals("1/128", Format.oneIn(1.0 / 128));
		assertEquals("1/2", Format.oneIn(0.5));
		assertEquals("1/1", Format.oneIn(1.0));
	}

	@Test
	public void nonReciprocalRatesKeepAFractionalDenominator()
	{
		// 0.4 is 2-in-5; the old Math.round rendered it as a false "1/3"
		assertEquals("1/2.5", Format.oneIn(0.4));
		assertEquals("1/2.9", Format.oneIn(0.35));
	}

	@Test
	public void countsDropOnlyNearWholeFractions()
	{
		assertEquals("128", Format.count(128.0));
		assertEquals("2.5", Format.count(2.5));
		assertEquals("294", Format.count(294.0000001));
	}

	@Test
	public void tallyGroupsThousands()
	{
		assertEquals("0", Format.tally(0));
		assertEquals("999", Format.tally(999));
		assertEquals("1,234", Format.tally(1234));
		assertEquals("1,234,567", Format.tally(1234567));
	}

	@Test
	public void dryPercentFormatsAsOneDecimalPercent()
	{
		// a fresh streak (0 kills) is always 100% "still dry"
		assertEquals("100.0%", Format.dryPercent(0.5, 0));
		// one kill at 1/2: 50% chance you'd still be without it
		assertEquals("50.0%", Format.dryPercent(0.5, 1));
		// two kills at 1/10: 0.9^2 = 81%
		assertEquals("81.0%", Format.dryPercent(0.1, 2));
	}

	@Test
	public void dryPercentTreatsNonPositiveRateAsAlwaysDry()
	{
		// an unknown/zero rate can't run out; DropStats returns 1.0
		assertEquals("100.0%", Format.dryPercent(0.0, 5));
	}

	@Test
	public void dryPercentIsDashesForGuaranteedDrops()
	{
		// a 100% drop can never leave you dry -> "---", not a percentage
		assertEquals("---", Format.dryPercent(1.0, 0));
		assertEquals("---", Format.dryPercent(1.0, 3));
	}
}
