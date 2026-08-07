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
package com.itemsearch.data;

import com.google.gson.Gson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import okhttp3.OkHttpClient;
import java.util.Scanner;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WikiRefreshServiceTest
{
	private String resource(String path)
	{
		try (InputStream in = getClass().getResourceAsStream(path))
		{
			return new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	@Test
	public void parsesOriginalImageUrl()
	{
		String json = resource("/com/itemsearch/wiki-pageimages-sample.json");
		String url = WikiRefreshService.parseOriginalImageUrl(new Gson(), json);
		assertTrue(url != null && url.endsWith(".png"));
	}

	@Test
	public void malformedJsonReturnsNull()
	{
		assertNull(WikiRefreshService.parseOriginalImageUrl(new Gson(), "{garbage"));
		assertNull(WikiRefreshService.parseOriginalImageUrl(new Gson(), "{\"query\":{\"pages\":{}}}"));
	}

	/**
	 * A screenful of rows is one request, so the response carries many pages and each has to find
	 * its way back to the row that asked for it.
	 */
	@Test
	public void batchedImageUrlsComeBackKeyedByTheRequestedTitle()
	{
		String json = "{\"query\":{\"pages\":{"
			+ "\"1\":{\"title\":\"Goblin\",\"original\":{\"source\":\"https://x/Goblin.png\"}},"
			+ "\"2\":{\"title\":\"Cow\",\"original\":{\"source\":\"https://x/Cow.png\"}},"
			+ "\"3\":{\"title\":\"Nothing\"}}}}";
		Map<String, String> urls = WikiRefreshService.parseImageUrls(new Gson(), json,
			Arrays.asList("Goblin", "Cow", "Nothing"));
		assertEquals("https://x/Goblin.png", urls.get("Goblin"));
		assertEquals("https://x/Cow.png", urls.get("Cow"));
		assertNull("a page with no image contributes nothing", urls.get("Nothing"));
	}

	/**
	 * Two rows can name pages that redirect to one another's — the Killerwatt versions both ask
	 * about "Killerwatt". Both rows must get the image, not just whichever the wiki names last.
	 */
	@Test
	public void severalRowsSharingOnePageEachGetTheImage()
	{
		String json = "{\"query\":{"
			+ "\"redirects\":[{\"from\":\"Killerwatt (Idle)\",\"to\":\"Killerwatt\"}],"
			+ "\"pages\":{\"1\":{\"title\":\"Killerwatt\","
			+ "\"original\":{\"source\":\"https://x/Killerwatt.png\"}}}}}";
		Map<String, String> urls = WikiRefreshService.parseImageUrls(new Gson(), json,
			Arrays.asList("Killerwatt", "Killerwatt (Idle)"));
		assertEquals("https://x/Killerwatt.png", urls.get("Killerwatt"));
		assertEquals("https://x/Killerwatt.png", urls.get("Killerwatt (Idle)"));
	}

	/**
	 * Scrolling fires this constantly, so a title already answered must never reach the network
	 * again — including one the wiki has no picture for, which would otherwise be re-asked forever.
	 */
	@Test
	public void titlesAlreadyAnsweredAreServedFromMemory()
	{
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		WikiRefreshService service = new WikiRefreshService(new OkHttpClient(), new Gson(),
			mock(BundledDataSource.class), executor);
		// the constructor schedules its own one-off read of the remembered urls; count from here
		clearInvocations(executor);

		List<Map<String, String>> handed = new ArrayList<>();
		service.imageUrlsFor(Arrays.asList("Goblin"), handed::add);
		// nothing is memoised yet, so it went to the executor and nothing came back inline
		verify(executor, times(1)).submit(any(Runnable.class));
		assertTrue(handed.isEmpty());

		service.remember("Goblin", "https://x/Goblin.png");
		service.remember("Faceless", "");

		service.imageUrlsFor(Arrays.asList("Goblin", "Faceless"), handed::add);
		verifyNoMoreInteractions(executor);
		assertEquals(1, handed.size());
		assertEquals("https://x/Goblin.png", handed.get(0).get("Goblin"));
		assertFalse("a page with no picture is not handed back as a blank url",
			handed.get(0).containsKey("Faceless"));
	}

	@Test
	public void aMalformedBatchIsEmptyRatherThanAnException()
	{
		assertTrue(WikiRefreshService.parseImageUrls(new Gson(), "{garbage",
			Arrays.asList("Goblin")).isEmpty());
	}
}
