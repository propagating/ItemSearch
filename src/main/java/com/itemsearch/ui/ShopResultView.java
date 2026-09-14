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
import java.awt.Font;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import net.runelite.client.game.ItemManager;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopItem;
import net.runelite.client.ui.ColorScheme;

public class ShopResultView extends JPanel
{
	public ShopResultView(ShopData shop, ItemManager itemManager, Runnable onBack,
		Consumer<ShopItem> onItemSelected, Consumer<ShopData> onMarkMap, Runnable onClearMap)
	{
		setLayout(new BorderLayout(0, 6));

		JButton back = new JButton("< Back");
		back.addActionListener(e -> onBack.run());
		add(back, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JTextArea name = wrapLabel(shop.getName());
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		body.add(name);

		String location = shop.getLocation();
		String currency = shop.getCurrency() == null ? "Coins" : shop.getCurrency();
		JLabel sub = new JLabel((location != null ? location + "  ·  " : "") + currency);
		sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(sub);

		if (shop.getX() != null && shop.getY() != null)
		{
			// the same control a monster gets: a shop is somewhere to walk to in the same way
			JButton mark = MapToggle.create(() -> onMarkMap.accept(shop), () -> onClearMap.run());
			mark.setAlignmentX(Component.LEFT_ALIGNMENT);
			body.add(Box.createVerticalStrut(4));
			body.add(mark);
		}
		body.add(Box.createVerticalStrut(6));

		for (ShopItem item : shop.getItems())
		{
			body.add(ShopRows.row(shop, item, itemManager, onItemSelected));
		}

		add(body, BorderLayout.CENTER);
	}

	// same re-wrapping label used by the other detail views (see ItemResultView.wrapLabel)
	private static JTextArea wrapLabel(String text)
	{
		JTextArea area = new JTextArea(text);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setBorder(null);
		area.setFont(UIManager.getFont("Label.font"));
		area.setForeground(UIManager.getColor("Label.foreground"));
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		return area;
	}
}
