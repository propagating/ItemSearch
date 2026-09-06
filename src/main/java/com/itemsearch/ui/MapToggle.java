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

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import net.runelite.client.ui.ColorScheme;

/**
 * The "Map" button, shared by everything that can be pointed at on the world map.
 *
 * <p>A shop and a pickpocketable NPC are places to go in exactly the way a monster is, so they get
 * the same control rather than one of their own: same word, same on/off border, same tooltips. The
 * marker layer holds one source at a time, so each button tracks its own state — pressing Map on a
 * shop after a monster leaves the monster's button looking active when its pins are already gone,
 * which is a smaller lie than a button that does not say whether it is on.
 */
public final class MapToggle
{
	private MapToggle()
	{
	}

	/**
	 * A Map button that shows markers on the first press and clears them on the second.
	 *
	 * @param onShow draws this source's markers, replacing whatever was there
	 * @param onHide removes them
	 */
	public static JButton create(Runnable onShow, Runnable onHide)
	{
		JButton map = new JButton("Map");
		map.setFocusPainted(false);
		boolean[] shown = {false};
		applyToggle(map, false);
		map.addActionListener(e ->
		{
			boolean now = !shown[0];
			shown[0] = now;
			applyToggle(map, now);
			if (now)
			{
				onShow.run();
			}
			else
			{
				onHide.run();
			}
		});
		return map;
	}

	/** On/off state, shown as a green (on) or orange (off) border plus the matching tooltip. */
	public static void applyToggle(JButton button, boolean on)
	{
		setStateBorder(button, on ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
		button.setToolTipText(on ? "Hide map markers" : "Show this location on the map");
	}

	public static void setStateBorder(JButton button, Color color)
	{
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(color),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)));
	}
}
