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
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class WikiImageLoader
{
	// Wiki "original" images can be several hundred px wide; anything larger than this
	// would force the fixed-width sidebar header wider than the panel itself.
	private static final int MAX_DIMENSION = 96;

	private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
	private static final ConcurrentHashMap<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

	private WikiImageLoader()
	{
	}

	public static void loadInto(JLabel target, String url)
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
		POOL.submit(() ->
		{
			try
			{
				// finite timeouts so a hung fetch can't pin one of the two pool threads forever
				URLConnection connection = new URL(url).openConnection();
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(5000);
				BufferedImage img;
				try (InputStream in = connection.getInputStream())
				{
					img = ImageIO.read(in);
				}
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
