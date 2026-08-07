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

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.ui.ColorScheme;

public class ResultRow extends JPanel
{
	// wide enough for the longest badge ("PICKPOCKET" was; "STEAL" and "STORE" are shorter)
	private static final int BADGE_WIDTH = 46;
	private static final int ICON_HEIGHT = 32;

	// square reserved for an icon, so named rows still line up with iconless ones beside them
	private static final int ICON_WIDTH = 34;

	public ResultRow(String badge, String text, Runnable onClick)
	{
		this(badge, text, onClick, null);
	}

	/**
	 * @param icon drawn left of the badge, or null for a text-only row. An
	 *             {@link AsyncBufferedImage} paints itself in once it has loaded, so the row does
	 *             not wait on it.
	 */
	public ResultRow(String badge, String text, Runnable onClick, AsyncBufferedImage icon)
	{
		setLayout(new BorderLayout(6, 0));
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setCursor(new Cursor(Cursor.HAND_CURSOR));

		JLabel badgeLabel = new JLabel(badge);
		badgeLabel.setForeground(ColorScheme.BRAND_ORANGE);
		// One reserved column for every badge: sized to the longest, so a long badge cannot eat
		// the width of the name beside it and every row's name starts at the same x.
		Dimension badgeSize = new Dimension(BADGE_WIDTH, badgeLabel.getPreferredSize().height);
		badgeLabel.setPreferredSize(badgeSize);
		badgeLabel.setMinimumSize(badgeSize);
		if (icon == null)
		{
			add(badgeLabel, BorderLayout.WEST);
		}
		else
		{
			// icons only appear in lists where every row has one, so wrapping the icon in beside
			// the badge here cannot leave some rows' names starting further right than others
			JPanel left = new JPanel(new BorderLayout(4, 0));
			left.setOpaque(false);
			JLabel iconLabel = new JLabel();
			Dimension iconSize = new Dimension(ICON_WIDTH, ICON_HEIGHT);
			iconLabel.setPreferredSize(iconSize);
			iconLabel.setMinimumSize(iconSize);
			icon.addTo(iconLabel);
			left.add(iconLabel, BorderLayout.WEST);
			left.add(badgeLabel, BorderLayout.CENTER);
			add(left, BorderLayout.WEST);
		}

		JLabel nameLabel = new JLabel(text);
		// let the name shrink into the remaining space instead of forcing the row wider
		nameLabel.setMinimumSize(new Dimension(0, nameLabel.getPreferredSize().height));
		add(nameLabel, BorderLayout.CENTER);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
	}
}
