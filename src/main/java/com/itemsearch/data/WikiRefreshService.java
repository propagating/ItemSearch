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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.CacheControl;
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

	/** Titles per request. The API caps a titles= list at 50 for unprivileged callers. */
	private static final int TITLE_BATCH = 50;

	/**
	 * Image urls for many pages at once, handed back as page title -> url.
	 *
	 * <p>A browse list wants an icon per row, and one request per row would mean a hundred calls
	 * to a third-party server to draw one screen. The API takes fifty titles at a time, so a
	 * screenful costs one request. Titles the wiki redirects or normalises are mapped back to what
	 * was asked for, since that is what the caller has a row for.
	 */
	public void imageUrlsFor(Collection<String> titles, Consumer<Map<String, String>> onUrls)
	{
		Map<String, String> known = new HashMap<>();
		List<String> batch = new ArrayList<>();
		for (String title : new LinkedHashSet<>(titles))
		{
			String cached = urlByTitle.get(title);
			if (cached == null)
			{
				batch.add(title);
			}
			else if (!MISSING.equals(cached))
			{
				known.put(title, cached);
			}
			// else: already known to have no picture, so do not ask again
		}
		if (!known.isEmpty())
		{
			// answered without touching the network: scrolling back over rows already seen is free
			onUrls.accept(known);
		}
		for (int i = 0; i < batch.size(); i += TITLE_BATCH)
		{
			List<String> slice = batch.subList(i, Math.min(i + TITLE_BATCH, batch.size()));
			executor.submit(() -> requestImageUrls(slice, onUrls));
		}
	}

	// title -> image url for this session. MISSING marks a page the wiki has no picture for, so
	// it is asked about once rather than on every scroll.
	private static final String MISSING = "";
	private final Map<String, String> urlByTitle = new ConcurrentHashMap<>();

	/** Visible for testing: seed the session memo, as a completed batch does. */
	void remember(String title, String url)
	{
		urlByTitle.put(title, url == null ? MISSING : url);
	}

	private void requestImageUrls(List<String> titles, Consumer<Map<String, String>> onUrls)
	{
		try
		{
			String url = API + "?action=query&format=json&prop=pageimages&piprop=original"
				+ "&redirects=1&pilimit=" + TITLE_BATCH + "&titles="
				+ URLEncoder.encode(String.join("|", titles), StandardCharsets.UTF_8.name());
			// no-store: OkHttp's shared disk cache cannot always rename its temp file on Windows
			// and throws out of body.string() mid-read, losing the whole response. These answers
			// are memoised below and the images they point at are cached by WikiImageLoader, so
			// nothing is gained by letting OkHttp store them too.
			Request request = new Request.Builder().url(url)
				.cacheControl(new CacheControl.Builder().noStore().build())
				.build();
			httpClient.newCall(request).enqueue(new Callback()
			{
				@Override
				public void onFailure(Call call, IOException e)
				{
					log.debug("itemsearch: batch image fetch failed", e);
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
						Map<String, String> urls = parseImageUrls(gson, r.body().string(), titles);
						for (String title : titles)
						{
							// remember the misses too, so a picture-less page is asked about once
							urlByTitle.put(title, urls.getOrDefault(title, MISSING));
						}
						if (!urls.isEmpty())
						{
							onUrls.accept(urls);
						}
					}
					catch (Exception e)
					{
						log.debug("itemsearch: batch image parse failed", e);
					}
				}
			});
		}
		catch (Exception e)
		{
			log.debug("itemsearch: batch image request error", e);
		}
	}

	/** Visible for testing: page title -> original image url, keyed by the REQUESTED title. */
	public static Map<String, String> parseImageUrls(Gson gson, String json,
		Collection<String> requested)
	{
		Map<String, String> out = new HashMap<>();
		try
		{
			JsonObject query = gson.fromJson(json, JsonObject.class).getAsJsonObject("query");
			// the wiki may answer a title with its redirect target or a normalised spelling;
			// walk both back so the caller can look the result up by what it asked for
			Map<String, List<String>> toOriginal = new HashMap<>();
			for (String title : requested)
			{
				toOriginal.computeIfAbsent(title, k -> new ArrayList<>()).add(title);
			}
			remapTitles(query.getAsJsonArray("normalized"), toOriginal);
			remapTitles(query.getAsJsonArray("redirects"), toOriginal);

			JsonObject pages = query.getAsJsonObject("pages");
			if (pages == null)
			{
				return out;
			}
			for (Map.Entry<String, com.google.gson.JsonElement> e : pages.entrySet())
			{
				JsonObject page = e.getValue().getAsJsonObject();
				if (!page.has("original"))
				{
					continue;
				}
				String title = page.get("title").getAsString();
				String source = page.getAsJsonObject("original").get("source").getAsString();
				for (String original : toOriginal.getOrDefault(title,
					Collections.singletonList(title)))
				{
					out.put(original, source);
				}
			}
		}
		catch (Exception e)
		{
			log.debug("itemsearch: could not parse batch image response", e);
		}
		return out;
	}

	private static void remapTitles(com.google.gson.JsonArray mappings,
		Map<String, List<String>> toOriginal)
	{
		if (mappings == null)
		{
			return;
		}
		for (com.google.gson.JsonElement e : mappings)
		{
			JsonObject m = e.getAsJsonObject();
			List<String> originals = toOriginal.remove(m.get("from").getAsString());
			if (originals != null)
			{
				toOriginal.computeIfAbsent(m.get("to").getAsString(), k -> new ArrayList<>())
					.addAll(originals);
			}
		}
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
