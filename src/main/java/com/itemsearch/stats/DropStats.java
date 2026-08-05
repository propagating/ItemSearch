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

public final class DropStats
{
	private DropStats()
	{
	}

	public static double expectedKills(double p)
	{
		if (p <= 0)
		{
			return Double.POSITIVE_INFINITY;
		}
		if (p >= 1)
		{
			return 1;
		}
		return 1.0 / p;
	}

	public static int killsForConfidence(double p, double c)
	{
		if (p >= 1)
		{
			return 1;
		}
		if (p <= 0)
		{
			return -1;
		}
		// smallest k with 1-(1-p)^k >= c  ->  k >= ln(1-c)/ln(1-p)
		// the small epsilon keeps floating-point noise from bumping an exact-integer
		// quotient (e.g. p=0.75, c=0.9375 -> exactly 2) up to the next integer
		return (int) Math.ceil(Math.log(1 - c) / Math.log(1 - p) - 1e-9);
	}

	public static double dryStreakProbability(double p, int n)
	{
		if (p <= 0)
		{
			return 1.0;
		}
		if (p >= 1)
		{
			return n == 0 ? 1.0 : 0.0;
		}
		return Math.pow(1 - p, n);
	}
}
