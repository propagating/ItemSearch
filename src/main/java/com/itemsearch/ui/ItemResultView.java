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
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import net.runelite.client.game.ItemManager;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.data.DropDataService;
import com.itemsearch.model.DropSource;
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.SourceData;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopEntry;
import com.itemsearch.model.ShopItem;
import com.itemsearch.stats.DropStats;
import com.itemsearch.tracker.TrackerStore;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

public class ItemResultView extends JPanel
{
	// live dry-streak values, updated in place when a kill is recorded (no rebuild/scroll reset)
	private final List<DryRow> dryRows = new ArrayList<>();

	public ItemResultView(ItemMatch item, DropDataService data, TrackerStore store,
		ItemManager itemManager, Runnable onBack, Consumer<SourceData> onNpcSelected,
		Consumer<ShopData> onShopSelected)
	{
		setLayout(new BorderLayout(0, 6));

		JButton back = new JButton("< Back");
		back.addActionListener(e -> onBack.run());
		add(back, BorderLayout.NORTH);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		JLabel icon = new JLabel();
		AsyncBufferedImage img = itemManager.getImage(item.getItemId());
		if (img != null)
		{
			img.addTo(icon);
		}
		header.add(icon, BorderLayout.WEST);
		header.add(new JLabel(item.getItemName()), BorderLayout.CENTER);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.add(header);

		List<DropSource> rawSources = data.sourcesForItem(item.getItemId());
		List<Integer> owners = data instanceof BundledDataSource
			? ((BundledDataSource) data).ownersOfItem(item.getItemId())
			: Collections.emptyList();

		// purchase options first: buying is usually the faster way to obtain the item,
		// and the drop list below can run to dozens of monsters
		List<ShopEntry> shopEntries = data.shopsForItem(item.getItemId());
		if (!shopEntries.isEmpty())
		{
			body.add(Box.createVerticalStrut(6));
			body.add(section("Buy from"));
			for (ShopEntry entry : shopEntries)
			{
				body.add(buildShopBox(entry, onShopSelected));
			}
			if (!rawSources.isEmpty())
			{
				body.add(Box.createVerticalStrut(6));
				body.add(section("Dropped by"));
			}
		}

		for (SourceOrder.Entry entry : SourceOrder.order(rawSources, owners,
			id ->
			{
				SourceData n = data.sourceById(id);
				return n != null ? n.getName() : null;
			}))
		{
			body.add(buildSourceBox(entry.source, data, store, item.getItemId(), entry.npcId, onNpcSelected));
		}

		add(body, BorderLayout.CENTER);
	}

	private JPanel buildShopBox(ShopEntry entry, Consumer<ShopData> onShopSelected)
	{
		ShopData shop = entry.getShop();
		ShopItem line = entry.getItem();

		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JTextArea name = wrapLabel(shop.getName());
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setForeground(ColorScheme.BRAND_ORANGE);
		name.setCursor(new Cursor(Cursor.HAND_CURSOR));
		name.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onShopSelected.accept(shop);
			}
		});
		box.add(name);

		if (shop.getLocation() != null)
		{
			box.add(subLabel(shop.getLocation()));
		}
		box.add(statRow("Price", Format.priceText(line.getSellPrice(), shop.getCurrency())));
		box.add(statRow("Stock", Format.stockText(line.getStock())));
		box.add(statRow("Restock", Format.restockText(line.getRestockSeconds())));
		clickable(box, () -> onShopSelected.accept(shop));
		return box;
	}

	// whole-card click target: Swing mouse events do not bubble, so the card needs its own
	// listener for presses that land outside the name label
	private static void clickable(JPanel box, Runnable action)
	{
		box.setCursor(new Cursor(Cursor.HAND_CURSOR));
		box.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				action.run();
			}
		});
	}

	private JPanel buildSourceBox(DropSource src, DropDataService data, TrackerStore store, int itemId, int npcId,
		Consumer<SourceData> onNpcSelected)
	{
		SourceData owner = npcId >= 0 ? data.sourceById(npcId) : null;

		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JTextArea name = wrapLabel(owner != null ? owner.getName() : "Unknown");
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		if (owner != null)
		{
			name.setForeground(ColorScheme.BRAND_ORANGE);
			name.setCursor(new Cursor(Cursor.HAND_CURSOR));
			name.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					onNpcSelected.accept(owner);
				}
			});
			clickable(box, () -> onNpcSelected.accept(owner));
		}
		box.add(name);

		boolean known = src.isRarityKnown() && src.getRarity() > 0;
		String rate = known ? Format.oneIn(src.getRarity()) : "rate unknown";
		box.add(subLabel((owner != null ? "Lvl " + owner.getCombatLevel() + "  ·  " : "") + rate));

		if (!known)
		{
			return box;
		}

		// compact on purpose: an item like uncut sapphire has dozens of sources, so each
		// card carries just the two scanning stats; the full breakdown (confidence bands,
		// obtained count, reset) lives in the NPC view behind the click-through
		double p = src.getRarity();
		int dry = npcId >= 0 ? store.getDry(npcId, itemId) : 0;
		box.add(statRow("Expected", expected(DropStats.expectedKills(p))));
		JLabel sinceValue = new JLabel(count(dry));
		box.add(statRow("Kills since drop", sinceValue));
		if (npcId >= 0)
		{
			dryRows.add(new DryRow(npcId, itemId, sinceValue));
		}
		return box;
	}

	/**
	 * Update the dry-streak values from the store in place. Called on the EDT after a kill is
	 * recorded so the counter moves without rebuilding the view (which would reset the scroll).
	 */
	public void refresh(TrackerStore store)
	{
		for (DryRow row : dryRows)
		{
			row.sinceValue.setText(count(store.getDry(row.npcId, row.itemId)));
		}
	}

	private static String count(int n)
	{
		return Format.tally(n);
	}

	// expected kills is 1/p: usually a whole number, but can be fractional for common drops
	private static String expected(double v)
	{
		if (Math.abs(v - Math.rint(v)) < 1e-9)
		{
			return count((int) Math.rint(v));
		}
		return String.format(Locale.US, "%,.1f", v);
	}

	// small section heading (e.g. "KILLS TO OBTAIN")
	private static JLabel section(String title)
	{
		JLabel label = new JLabel(title.toUpperCase(Locale.ROOT));
		label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1f));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	// dim secondary line under the monster name (level + drop rate)
	private static JLabel subLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	// one metric per line: dim label on the left, value aligned to the right edge
	private static JPanel statRow(String label, String value)
	{
		return statRow(label, new JLabel(value));
	}

	private static JPanel statRow(String label, JLabel val)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel(label);
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		row.add(name, BorderLayout.WEST);
		row.add(val, BorderLayout.EAST);
		// keep BoxLayout from stretching rows to fill leftover vertical space
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	// captures the kills-since-drop value label for one source so it can be refreshed in place
	private static final class DryRow
	{
		private final int npcId;
		private final int itemId;
		private final JLabel sinceValue;

		private DryRow(int npcId, int itemId, JLabel sinceValue)
		{
			this.npcId = npcId;
			this.itemId = itemId;
			this.sinceValue = sinceValue;
		}
	}

	// A left-aligned, non-editable text area styled like a label. Unlike a JLabel it
	// re-wraps to whatever width it is given, so it fills the (viewport-bounded) box width
	// and wraps instead of being clipped — see ItemSearchPanel.ScrollableWrapper.
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
