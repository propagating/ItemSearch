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
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import net.runelite.client.ui.ColorScheme;

/** The bordered cards, section headings, and stat cells the detail pages share. */
final class Cards
{
	private Cards()
	{
	}

	// A full-width bordered card. The getMaximumSize override lets the parent Y_AXIS BoxLayout
	// stretch the card across the whole sidebar width — a BoxLayout panel otherwise reports only
	// its (narrower) children's max width, so BoxLayout leaves the card at preferred width and
	// its alignmentX floats it inward. Height stays capped to the natural height.
	static JPanel card()
	{
		JPanel box = fullWidth();
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return box;
	}

	// A full-width holder for one card, so the card can be replaced in place.
	static JPanel slot()
	{
		JPanel slot = fullWidth();
		slot.setOpaque(false);
		return slot;
	}

	private static JPanel fullWidth()
	{
		JPanel box = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		// In a Y_AXIS BoxLayout a child whose alignmentX differs from its siblings is offset and
		// shrunk rather than filled; keeping every body child at CENTER_ALIGNMENT lets the
		// MAX-width cards span the full sidebar like the rows.
		box.setAlignmentX(Component.CENTER_ALIGNMENT);
		return box;
	}

	static JLabel section(String title)
	{
		JLabel label = new JLabel(title.toUpperCase(Locale.ROOT));
		label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1f));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** A dim name on the left and its value on the right, for a two-column grid. */
	static JPanel statCell(String label, String value)
	{
		JPanel cell = new JPanel(new BorderLayout());
		cell.setOpaque(false);
		JLabel name = new JLabel(label);
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		cell.add(name, BorderLayout.WEST);
		cell.add(new JLabel(value), BorderLayout.EAST);
		return cell;
	}

	static void refill(JPanel slot, JPanel card)
	{
		slot.removeAll();
		slot.add(card);
		slot.revalidate();
		slot.repaint();
	}

	// A left-aligned, non-editable text area styled like a label. Unlike a JLabel it
	// re-wraps to whatever width it is given, so it fills the (viewport-bounded) box width
	// and wraps instead of being clipped — see ItemSearchPanel.ScrollableWrapper.
	static JTextArea wrapLabel(String text)
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
