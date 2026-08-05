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

import java.util.Locale;
import com.itemsearch.stats.DropStats;

/**
 * Display formatting for drop rates and kill counts. Rounding a non-reciprocal rate
 * like 0.4 to "1/3" misstates the odds by 20%; these helpers keep a fractional
 * denominator ("1/2.5") whenever the true reciprocal isn't a whole number.
 */
final class Format
{
	private Format()
	{
	}

	/**
	 * Formats a drop probability as "1/N", using one decimal place ("1/2.5")
	 * when the reciprocal is not (near) a whole number.
	 */
	static String oneIn(double p)
	{
		return "1/" + count(1.0 / p);
	}

	/**
	 * Formats a kill count, dropping the fraction only when it is (near) whole
	 * (e.g. 128 -> "128", 2.5 -> "2.5").
	 */
	static String count(double v)
	{
		double rounded = Math.round(v);
		if (Math.abs(v - rounded) < 0.05)
		{
			return String.valueOf((long) rounded);
		}
		return String.format(Locale.ROOT, "%.1f", v);
	}

	/**
	 * Formats a whole count with thousands separators (e.g. 1234 -> "1,234").
	 */
	static String tally(int n)
	{
		return String.format(Locale.US, "%,d", n);
	}

	/**
	 * Formats the probability that the player is still without a p-rate drop after
	 * {@code dry} kills, as a one-decimal percentage (e.g. "12.3%").
	 */
	static String dryPercent(double p, int dry)
	{
		if (p >= 1.0)
		{
			return "---";
		}
		return String.format(Locale.ROOT, "%.1f", DropStats.dryStreakProbability(p, dry) * 100) + "%";
	}

	/** Formats a shop price with its currency ("1,500 gp", "120 Tokkul"); null price -> "?". */
	static String priceText(Integer price, String currency)
	{
		if (price == null)
		{
			return "?";
		}
		if (currency == null || "Coins".equalsIgnoreCase(currency))
		{
			return tally(price) + " gp";
		}
		return tally(price) + " " + currency;
	}

	/** Formats shop stock: negative = infinite ("∞"), null = unknown ("?"). */
	static String stockText(Integer stock)
	{
		if (stock == null)
		{
			return "?";
		}
		if (stock < 0)
		{
			return "∞";
		}
		return tally(stock);
	}

	/** Formats a restock duration in seconds ("30s", "1m 40s"); null -> "?". */
	static String restockText(Integer seconds)
	{
		if (seconds == null)
		{
			return "?";
		}
		if (seconds < 60)
		{
			return seconds + "s";
		}
		return (seconds / 60) + "m " + (seconds % 60) + "s";
	}
}
