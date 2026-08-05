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
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class WikiRefreshService
{
	private static final String API = "https://oldschool.runescape.wiki/api.php";

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final BundledDataSource dataService;
	private final ScheduledExecutorService executor;

	public WikiRefreshService(OkHttpClient httpClient, Gson gson,
		BundledDataSource dataService, ScheduledExecutorService executor)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.dataService = dataService;
		this.executor = executor;
	}

	public void refreshNpcImage(int npcId, String npcName)
	{
		refreshNpcImage(npcId, npcName, null);
	}

	/**
	 * Fetches the NPC's current wiki image, overlays it on the data source, and — if
	 * {@code onImage} is non-null — hands the fresh URL to the caller (on an arbitrary
	 * thread) so an already-open view can update without being reopened.
	 */
	public void refreshNpcImage(int npcId, String npcName, java.util.function.Consumer<String> onImage)
	{
		executor.submit(() ->
		{
			try
			{
				String url = API + "?action=query&format=json&prop=pageimages&piprop=original&titles="
					+ URLEncoder.encode(npcName, StandardCharsets.UTF_8.name());
				Request request = new Request.Builder().url(url).build();
				httpClient.newCall(request).enqueue(new Callback()
				{
					@Override
					public void onFailure(Call call, IOException e)
					{
						log.debug("itemsearch: wiki image fetch failed for {}", npcName, e);
					}

					@Override
					public void onResponse(Call call, Response response)
					{
						try (Response r = response)
						{
							if (r.body() == null)
							{
								return;
							}
							String parsed = parseOriginalImageUrl(gson, r.body().string());
							if (parsed != null)
							{
								dataService.overlayImage(npcId, parsed);
								if (onImage != null)
								{
									onImage.accept(parsed);
								}
							}
						}
						catch (Exception e)
						{
							log.debug("itemsearch: wiki image parse failed for {}", npcName, e);
						}
					}
				});
			}
			catch (Exception e)
			{
				log.debug("itemsearch: wiki refresh error", e);
			}
		});
	}

	public static String parseOriginalImageUrl(Gson gson, String json)
	{
		try
		{
			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonObject pages = root.getAsJsonObject("query").getAsJsonObject("pages");
			for (Map.Entry<String, com.google.gson.JsonElement> e : pages.entrySet())
			{
				JsonObject page = e.getValue().getAsJsonObject();
				if (page.has("original"))
				{
					return page.getAsJsonObject("original").get("source").getAsString();
				}
			}
			return null;
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
