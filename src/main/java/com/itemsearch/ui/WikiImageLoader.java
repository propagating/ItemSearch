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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public final class WikiImageLoader
{
	// Wiki "original" images can be several hundred px wide; anything larger than this
	// would force the fixed-width sidebar header wider than the panel itself.
	private static final int MAX_DIMENSION = 96;

	private static final ConcurrentHashMap<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

	// label -> the image it was last asked to show. Loads finish in whatever order the disk and
	// the wiki please, and a label asked for two pictures in a row (a phase switch) must end up
	// showing the second — so a load that is no longer the latest ask is dropped on arrival.
	// Weak, so a label that has left the screen is not kept alive by its entry.
	private static final Map<JLabel, String> LATEST = Collections.synchronizedMap(new WeakHashMap<>());

	private WikiImageLoader()
	{
	}

	public static void loadInto(OkHttpClient httpClient, JLabel target, String url)
	{
		loadInto(httpClient, target, url, MAX_DIMENSION);
	}

	/**
	 * @param maxDimension longest edge to scale to. A list row wants a small icon, and scaling
	 *                     before caching keeps both the disk cache and the heap small — a full-size
	 *                     wiki render is ~54 KB on disk and rather more once decoded.
	 */
	public static void loadInto(OkHttpClient httpClient, JLabel target, String url, int maxDimension)
	{
		if (url == null || url.trim().isEmpty())
		{
			return;
		}
		String key = maxDimension + "|" + url;
		LATEST.put(target, key);
		BufferedImage cached = CACHE.get(key);
		if (cached != null)
		{
			showIfLatest(target, key, cached);
			return;
		}
		// Disk next: an image the player has seen once should never be asked of the wiki again.
		// Off the calling thread, which is usually the EDT — a screenful of rows scrolled into
		// view is a screenful of file reads and PNG decodes, and the sidebar stutters for them.
		// OkHttp's pool is where the network fallback would run anyway.
		httpClient.dispatcher().executorService().execute(() ->
		{
			BufferedImage onDisk = readFromDisk(key);
			if (onDisk != null)
			{
				CACHE.put(key, onDisk);
				showIfLatest(target, key, onDisk);
				return;
			}
			fetch(httpClient, target, url, key, maxDimension);
		});
	}

	private static void fetch(OkHttpClient httpClient, JLabel target, String url, String key,
		int maxDimension)
	{
		HttpUrl parsed = HttpUrl.parse(url);
		if (parsed == null)
		{
			log.debug("itemsearch: unparseable image url {}", url);
			return;
		}
		// no-store: this class keeps its own disk cache of the scaled image, and OkHttp's shared
		// cache throws mid-read on Windows when it cannot rename its temp file — which loses the
		// response entirely rather than merely failing to cache it
		Request request = new Request.Builder().url(parsed)
			.cacheControl(new CacheControl.Builder().noStore().build())
			.build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("itemsearch: image load failed {}", url, e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				// decode + scale on the okhttp thread; only the icon swap touches the EDT
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("itemsearch: image load failed {} (http {})", url, response.code());
						return;
					}
					BufferedImage img = ImageIO.read(body.byteStream());
					if (img != null)
					{
						BufferedImage scaled = scaleToFit(img, maxDimension);
						CACHE.put(key, scaled);
						writeToDisk(key, scaled);
						showIfLatest(target, key, scaled);
					}
				}
				catch (Exception e)
				{
					log.debug("itemsearch: image load failed {}", url, e);
				}
			}
		});
	}

	// Marshalled to the EDT unconditionally, so the loader is safe to call from any thread; the
	// check happens there too, so it sees whatever the label was most recently asked for.
	private static void showIfLatest(JLabel target, String key, BufferedImage image)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (key.equals(LATEST.get(target)))
			{
				target.setIcon(new ImageIcon(image));
			}
		});
	}

	/** Where fetched images live between sessions. Inside .runelite, as plugins must be. */
	private static File cacheDir()
	{
		return new File(RuneLite.RUNELITE_DIR, "itemsearch" + File.separator + "wiki-images");
	}

	// one file per cached image, named by a hash so no wiki title can escape the directory
	private static File cacheFile(String key)
	{
		return new File(cacheDir(), Integer.toHexString(key.hashCode()) + "-"
			+ Integer.toHexString(key.length()) + ".png");
	}

	private static BufferedImage readFromDisk(String key)
	{
		File file = cacheFile(key);
		if (!file.isFile())
		{
			return null;
		}
		try
		{
			return ImageIO.read(file);
		}
		catch (Exception e)
		{
			log.debug("itemsearch: unreadable cached image {}", file, e);
			return null;
		}
	}

	private static void writeToDisk(String key, BufferedImage image)
	{
		try
		{
			File dir = cacheDir();
			if (!dir.isDirectory() && !dir.mkdirs())
			{
				return;
			}
			ImageIO.write(image, "png", cacheFile(key));
		}
		catch (Exception e)
		{
			// a cache that cannot be written is a slower plugin, not a broken one
			log.debug("itemsearch: could not cache image for {}", key, e);
		}
	}

	private static BufferedImage scaleToFit(BufferedImage img, int maxDimension)
	{
		int w = img.getWidth();
		int h = img.getHeight();
		if (w <= maxDimension && h <= maxDimension)
		{
			return img;
		}
		double scale = Math.min((double) maxDimension / w, (double) maxDimension / h);
		int sw = Math.max(1, (int) Math.round(w * scale));
		int sh = Math.max(1, (int) Math.round(h * scale));
		BufferedImage out = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(img, 0, 0, sw, sh, null);
		g.dispose();
		return out;
	}
}
