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
package com.itemsearch.stats;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DropStatsTest
{
	private static final double EPS = 1e-9;

	@Test
	public void expectedKillsIsInverse()
	{
		assertEquals(128.0, DropStats.expectedKills(1.0 / 128), EPS);
	}

	@Test
	public void expectedKillsHandlesDegenerate()
	{
		assertEquals(Double.POSITIVE_INFINITY, DropStats.expectedKills(0.0), 0.0);
		assertEquals(1.0, DropStats.expectedKills(1.0), EPS);
	}

	@Test
	public void killsForConfidenceMatchesGeometric()
	{
		// smallest k with 1-(1-p)^k >= c
		assertEquals(1, DropStats.killsForConfidence(0.5, 0.10));
		assertEquals(1, DropStats.killsForConfidence(0.5, 0.50));
		assertEquals(4, DropStats.killsForConfidence(0.5, 0.90));
		// 1/128 drop, 90% confidence -> ceil(ln(0.1)/ln(127/128)) = 294
		assertEquals(294, DropStats.killsForConfidence(1.0 / 128, 0.90));
	}

	@Test
	public void killsForConfidenceGuaranteedDrop()
	{
		assertEquals(1, DropStats.killsForConfidence(1.0, 0.90));
	}

	@Test
	public void dryStreakProbabilityShrinks()
	{
		assertEquals(1.0, DropStats.dryStreakProbability(1.0 / 128, 0), EPS);
		assertEquals(Math.pow(127.0 / 128, 300), DropStats.dryStreakProbability(1.0 / 128, 300), EPS);
	}

	@Test
	public void killsForConfidenceExactIntegerBoundary()
	{
		// ln(1-c)/ln(1-p) is exactly 2 in real arithmetic: 1-(1-0.75)^2 = 0.9375
		// floating-point noise must not bump the answer to 3
		assertEquals(2, DropStats.killsForConfidence(0.75, 0.9375));
	}

	@Test
	public void killsForConfidenceHandlesImpossibleDrop()
	{
		assertEquals(-1, DropStats.killsForConfidence(0.0, 0.5));
	}

	@Test
	public void dryStreakProbabilityHandlesDegenerate()
	{
		// p<=0: can never drop, so a dry streak of any length is certain
		assertEquals(1.0, DropStats.dryStreakProbability(0.0, 5), EPS);
		// p>=1: guaranteed drop -> only n==0 is possible
		assertEquals(1.0, DropStats.dryStreakProbability(1.0, 0), EPS);
		assertEquals(0.0, DropStats.dryStreakProbability(1.0, 5), EPS);
	}
}
