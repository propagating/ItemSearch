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

import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DropConditionsTest
{
	private static DropConditions with(int slayerCount, int slayerArea)
	{
		Client client = mock(Client.class);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(slayerCount);
		when(client.getVarpValue(VarPlayerID.SLAYER_AREA)).thenReturn(slayerArea);
		return new DropConditions(client);
	}

	/** Most drops carry no condition and must not be gated by one. */
	@Test
	public void anUnconditionalDropIsAlwaysOnTheTable()
	{
		assertTrue(with(0, 0).isMet(null));
		assertTrue(with(0, 0).isMet(""));
	}

	/** No task at all is the case that was quietly inflating dry streaks. */
	@Test
	public void noTaskMeetsNothing()
	{
		assertFalse(with(0, 0).isMet(DropConditions.SLAYER_TASK));
		assertFalse(with(0, 0).isMet(DropConditions.KONAR_TASK));
	}

	/**
	 * Konar is the only master who assigns a place with the monster, so the assigned area is what
	 * separates her task from another master's — a Duradel task has kills remaining but no area.
	 */
	@Test
	public void anotherMastersTaskIsNotAKonarTask()
	{
		DropConditions other = with(42, 0);
		assertTrue(other.isMet(DropConditions.SLAYER_TASK));
		assertFalse(other.isMet(DropConditions.KONAR_TASK));
	}

	@Test
	public void aKonarTaskMeetsBoth()
	{
		DropConditions konar = with(42, 5);
		assertTrue(konar.isMet(DropConditions.SLAYER_TASK));
		assertTrue(konar.isMet(DropConditions.KONAR_TASK));
	}

	/**
	 * Data can name a condition a build predates. Reading it as unmet only understates a dry
	 * streak; reading it as met would invent one on kills that never had the drop in the pool.
	 */
	@Test
	public void anUnknownConditionReadsAsUnmet()
	{
		assertFalse(with(42, 5).isMet("SOME_FUTURE_CONDITION"));
	}
}
