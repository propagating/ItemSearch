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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import com.itemsearch.ItemSearchConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws each pending respawn as a tile polygon with a smooth countdown.
 * Unknown-time entries draw the tile only; approximate (death-tile fallback)
 * entries draw dimmer. One bad entry never kills the frame.
 */
@Slf4j
public class RespawnOverlay extends Overlay
{
	private static final Color OUTLINE = new Color(0, 200, 255);
	private static final Color OUTLINE_APPROX = new Color(0, 200, 255, 100);
	private static final Color FILL = new Color(0, 200, 255, 30);
	private static final Color FILL_APPROX = new Color(0, 200, 255, 12);
	private static final Color TEXT_COLOR = Color.WHITE;
	private static final BasicStroke OUTLINE_STROKE = new BasicStroke(2);
	private static final NumberFormat TIME_FORMAT = DecimalFormat.getInstance(Locale.US);

	static
	{
		((DecimalFormat) TIME_FORMAT).applyPattern("#0.0");
	}

	private final Client client;
	private final RespawnTracker tracker;
	private final ItemSearchConfig config;

	public RespawnOverlay(Client client, RespawnTracker tracker, ItemSearchConfig config)
	{
		this.client = client;
		this.tracker = tracker;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showRespawnTiles())
		{
			return null;
		}
		for (RespawnEntry entry : tracker.activeRespawns())
		{
			try
			{
				renderEntry(graphics, entry);
			}
			catch (Exception e)
			{
				log.debug("itemsearch: failed to render respawn entry", e);
			}
		}
		return null;
	}

	private void renderEntry(Graphics2D graphics, RespawnEntry entry)
	{
		final WorldPoint tile = entry.getTile();
		final LocalPoint lp = LocalPoint.fromWorld(client, tile);
		if (lp == null)
		{
			return; // outside the loaded scene
		}

		final int size = Math.max(1, entry.getNpcSize());
		final LocalPoint centerLp = new LocalPoint(
			lp.getX() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2,
			lp.getY() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2);

		final Polygon poly = Perspective.getCanvasTileAreaPoly(client, centerLp, size);
		if (poly != null)
		{
			graphics.setColor(entry.isApproximate() ? OUTLINE_APPROX : OUTLINE);
			graphics.setStroke(OUTLINE_STROKE);
			graphics.drawPolygon(poly);
			graphics.setColor(entry.isApproximate() ? FILL_APPROX : FILL);
			graphics.fillPolygon(poly);
		}

		if (entry.getRespawnAtTick() == -1)
		{
			return; // still learning: tile only, no countdown
		}

		// smooth sub-tick countdown (npchighlight's recipe)
		final double baseSeconds = (entry.getRespawnAtTick() - client.getTickCount())
			* (Constants.GAME_TICK_LENGTH / 1000.0);
		final double sinceLastTick =
			(Instant.now().toEpochMilli() - tracker.getLastTickUpdate().toEpochMilli()) / 1000.0;
		final double timeLeft = Math.max(0.0, baseSeconds - sinceLastTick);
		final String text = TIME_FORMAT.format(timeLeft);

		final Point canvasPoint = Perspective.localToCanvas(client, centerLp, tile.getPlane());
		if (canvasPoint != null)
		{
			final int textWidth = graphics.getFontMetrics().stringWidth(text);
			final int textHeight = graphics.getFontMetrics().getAscent();
			graphics.setColor(TEXT_COLOR);
			graphics.drawString(text, canvasPoint.getX() - textWidth / 2, canvasPoint.getY() + textHeight / 2);
		}
	}
}
