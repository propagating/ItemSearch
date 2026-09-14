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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopItem;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/** One line of a shop's stock, drawn the same way wherever a shop is shown. */
final class ShopRows
{
	private ShopRows()
	{
	}

	static JPanel row(ShopData shop, ShopItem item, ItemManager itemManager,
		Consumer<ShopItem> onItemSelected)
	{
		// stock 0 lines stay visible (the shop's full catalog) but are dimmed: they
		// are not currently a way to purchase the item
		boolean stocked = item.getStock() == null || item.getStock() != 0;

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		if (item.getItemId() >= 0)
		{
			// the whole row is a click target (Swing events don't bubble from unlistened
			// children like the icon), with hover feedback to advertise it
			row.setCursor(new Cursor(Cursor.HAND_CURSOR));
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					onItemSelected.accept(item);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				}
			});
		}

		if (item.getItemId() >= 0)
		{
			JLabel icon = new JLabel();
			AsyncBufferedImage img = itemManager.getImage(item.getItemId());
			if (img != null)
			{
				img.addTo(icon);
			}
			row.add(icon, BorderLayout.WEST);
		}

		JLabel name = new JLabel(item.getItemName());
		if (stocked && item.getItemId() >= 0)
		{
			name.setForeground(ColorScheme.BRAND_ORANGE);
		}
		if (item.getItemId() >= 0)
		{
			name.setCursor(new Cursor(Cursor.HAND_CURSOR));
			name.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					onItemSelected.accept(item);
				}
			});
		}
		row.add(name, BorderLayout.CENTER);

		String value = Format.priceText(item.getSellPrice(), shop.getCurrency())
			+ "  ·  " + Format.stockText(item.getStock());
		if (!stocked)
		{
			value += "  ·  not stocked";
		}
		JLabel valueLabel = new JLabel(value);
		valueLabel.setForeground(stocked ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		if (!stocked)
		{
			name.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}
		row.add(valueLabel, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}
}
