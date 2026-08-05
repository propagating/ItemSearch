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
package com.itemsearch.respawn;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RespawnTimeStoreTest
{
	@Mock
	private ConfigManager configManager;

	private RespawnTimeStore store;

	@Before
	public void setUp()
	{
		store = new RespawnTimeStore(configManager, new Gson());
		store.load();
	}

	@Test
	public void unlearnedNameReturnsMinusOne()
	{
		assertEquals(-1, store.getRespawnTicks("Hill Giant"));
	}

	@Test
	public void learnedTicksRoundTripInMemoryAndPersist()
	{
		store.learnRespawnTicks("Hill Giant", 50);
		assertEquals(50, store.getRespawnTicks("Hill Giant"));
		verify(configManager, atLeastOnce())
			.setConfiguration(eq("itemsearch"), eq("respawnTimes"), ArgumentCaptor.forClass(String.class).capture());
	}

	@Test
	public void trackingToggleRoundTrips()
	{
		assertFalse(store.isTracked("Guard"));
		store.setTracked("Guard", true);
		assertTrue(store.isTracked("Guard"));
		assertTrue(store.trackedNames().contains("Guard"));
		store.setTracked("Guard", false);
		assertFalse(store.isTracked("Guard"));
	}

	@Test
	public void persistedBlobIsReloadable()
	{
		store.learnRespawnTicks("Cow", 25);
		store.setTracked("Cow", true);
		ArgumentCaptor<String> blob = ArgumentCaptor.forClass(String.class);
		verify(configManager, atLeastOnce()).setConfiguration(eq("itemsearch"), eq("respawnTimes"), blob.capture());

		when(configManager.getConfiguration(eq("itemsearch"), eq("respawnTimes"))).thenReturn(blob.getValue());
		RespawnTimeStore reloaded = new RespawnTimeStore(configManager, new Gson());
		reloaded.load();
		assertEquals(25, reloaded.getRespawnTicks("Cow"));
		assertTrue(reloaded.isTracked("Cow"));
	}

	@Test
	public void corruptBlobLoadsEmpty()
	{
		when(configManager.getConfiguration(eq("itemsearch"), eq("respawnTimes"))).thenReturn("{oops");
		RespawnTimeStore s = new RespawnTimeStore(configManager, new Gson());
		s.load(); // must not throw
		assertEquals(-1, s.getRespawnTicks("Cow"));
		assertFalse(s.isTracked("Cow"));
	}
}
