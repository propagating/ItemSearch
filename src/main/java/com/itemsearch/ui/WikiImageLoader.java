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
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
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

	private WikiImageLoader()
	{
	}

	public static void loadInto(OkHttpClient httpClient, JLabel target, String url)
	{
		if (url == null || url.trim().isEmpty())
		{
			return;
		}
		BufferedImage cached = CACHE.get(url);
		if (cached != null)
		{
			// Marshal to the EDT unconditionally so this utility is safe to call from any thread.
			SwingUtilities.invokeLater(() -> target.setIcon(new ImageIcon(cached)));
			return;
		}
		HttpUrl parsed = HttpUrl.parse(url);
		if (parsed == null)
		{
			log.debug("itemsearch: unparseable image url {}", url);
			return;
		}
		Request request = new Request.Builder().url(parsed).build();
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
						BufferedImage scaled = scaleToFit(img);
						CACHE.put(url, scaled);
						SwingUtilities.invokeLater(() -> target.setIcon(new ImageIcon(scaled)));
					}
				}
				catch (Exception e)
				{
					log.debug("itemsearch: image load failed {}", url, e);
				}
			}
		});
	}

	private static BufferedImage scaleToFit(BufferedImage img)
	{
		int w = img.getWidth();
		int h = img.getHeight();
		if (w <= MAX_DIMENSION && h <= MAX_DIMENSION)
		{
			return img;
		}
		double scale = Math.min((double) MAX_DIMENSION / w, (double) MAX_DIMENSION / h);
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
