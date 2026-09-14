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
import java.awt.GridLayout;
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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemEquipmentStats;
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
	private static final String MORE = "▸ more";
	private static final String LESS = "▾ less";

	// live dry-streak values, updated in place when a kill is recorded (no rebuild/scroll reset)
	private final List<DryRow> dryRows = new ArrayList<>();

	public ItemResultView(ItemMatch item, DropDataService data, TrackerStore store,
		ItemManager itemManager, Runnable onBack, Consumer<SourceData> onNpcSelected,
		Consumer<ShopData> onShopSelected)
	{
		this(item, data, store, itemManager, null, onBack, onNpcSelected, onShopSelected);
	}

	/**
	 * @param clientThread where the item's composition is read; null shows the page without its
	 *                     Facts and Equipment cards
	 */
	public ItemResultView(ItemMatch item, DropDataService data, TrackerStore store,
		ItemManager itemManager, ClientThread clientThread, Runnable onBack,
		Consumer<SourceData> onNpcSelected, Consumer<ShopData> onShopSelected)
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

		// every child of the body is a full-width, centre-aligned row: a Y_AXIS BoxLayout offsets
		// and shrinks any child whose alignment differs from its siblings, and the cards below
		// are full width, so everything else has to be too
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.add(fullWidth(header));

		String examine = data.examineFor(item.getItemId());
		if (examine != null)
		{
			JTextArea line = wrapLabel(examine);
			line.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			line.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
			body.add(fullWidth(line));
		}

		// what the item is worth and what it does when worn, read off the client thread and
		// filled in when it arrives, so opening an item never waits on the game
		body.add(factsSlot);
		body.add(equipmentSlot);
		if (clientThread != null)
		{
			int id = item.getItemId();
			clientThread.invoke(() ->
			{
				ItemFacts facts = ItemFacts.read(itemManager, id);
				if (facts != null)
				{
					SwingUtilities.invokeLater(() -> showFacts(facts));
				}
			});
		}

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
			body.add(fullWidth(section("Buy from")));
			for (ShopEntry entry : shopEntries)
			{
				body.add(buildShopBox(entry, onShopSelected));
			}
			if (!rawSources.isEmpty())
			{
				body.add(Box.createVerticalStrut(6));
				body.add(fullWidth(section("Obtained from")));
			}
		}

		List<SourceOrder.Entry> ordered = SourceOrder.order(rawSources, owners,
			id ->
			{
				SourceData n = data.sourceById(id);
				return n != null ? n.getName() : null;
			});

		// a page at a time. Coins has thousands of sources, and the cost is not only building
		// them: when the player opens one, removing the page beside the game canvas makes AWT
		// recompute clip shapes for every sibling of every removed component, quadratic in the
		// sibling count, and that froze the sidebar for seconds
		JPanel sources = Cards.slot();
		body.add(sources);
		JButton more = new JButton();
		more.setFocusPainted(false);
		more.setAlignmentX(Component.CENTER_ALIGNMENT);
		Runnable[] showNext = new Runnable[1];
		int[] shown = {0};
		showNext[0] = () ->
		{
			int end = Math.min(shown[0] + PAGE, ordered.size());
			for (SourceOrder.Entry entry : ordered.subList(shown[0], end))
			{
				sources.add(buildSourceBox(entry.source, data, store, item.getItemId(), entry.npcId,
					onNpcSelected));
			}
			shown[0] = end;
			int left = ordered.size() - shown[0];
			if (left > 0)
			{
				more.setText("Show " + Math.min(PAGE, left) + " more");
			}
			else
			{
				body.remove(more);
			}
			body.revalidate();
			body.repaint();
		};
		more.addActionListener(e -> showNext[0].run());
		if (ordered.size() > PAGE)
		{
			body.add(more);
		}
		showNext[0].run();

		add(body, BorderLayout.CENTER);
	}

	// source cards shown before the player asks for more
	static final int PAGE = 40;

	// a body row that spans the sidebar, so it lines up with the cards beside it
	private static JPanel fullWidth(JComponent content)
	{
		JPanel row = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setOpaque(false);
		row.setAlignmentX(Component.CENTER_ALIGNMENT);
		row.add(content, BorderLayout.CENTER);
		return row;
	}

	private final JPanel factsSlot = Cards.slot();
	private final JPanel equipmentSlot = Cards.slot();

	private void showFacts(ItemFacts f)
	{
		JPanel facts = Cards.card();
		facts.add(Cards.section("Facts"));
		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 0));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.add(Cards.statCell("Value", Format.tally(f.value)));
		grid.add(Cards.statCell("Weight", weightText(f.weight)));
		grid.add(Cards.statCell("Low alch", Format.tally(f.lowAlch)));
		grid.add(Cards.statCell("High alch", Format.tally(f.highAlch)));
		JPanel store = Cards.statCell("Vendor", Format.tally(f.lowAlch));
		store.setToolTipText("What a general store pays for the first one you sell; "
			+ "a shop that already has some pays less");
		grid.add(store);
		if (f.geTradeable)
		{
			grid.add(Cards.statCell("GE", f.gePrice > 0 ? Format.tally(f.gePrice) : "—"));
			grid.add(Cards.statCell("Buy limit", f.geLimit > 0 ? Format.tally(f.geLimit) : "—"));
		}
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		facts.add(grid);

		List<String> flags = new ArrayList<>();
		if (f.members)
		{
			flags.add("Members");
		}
		if (f.tradeable)
		{
			flags.add("Tradeable");
		}
		if (f.stackable)
		{
			flags.add("Stackable");
		}
		if (!flags.isEmpty())
		{
			JLabel line = new JLabel(String.join("  ·  ", flags));
			line.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			line.setAlignmentX(Component.LEFT_ALIGNMENT);
			facts.add(line);
		}
		Cards.refill(factsSlot, facts);

		if (f.equipment != null)
		{
			Cards.refill(equipmentSlot, equipmentCard(f.equipment));
		}
	}

	private static JPanel equipmentCard(ItemEquipmentStats e)
	{
		JPanel card = Cards.card();
		card.add(Cards.section("Equipment"));
		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 0));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.add(Cards.statCell("Slot", slotName(e.getSlot()) + (e.isTwoHanded() ? " (two-handed)" : "")));
		grid.add(Cards.statCell("Speed", e.getAspeed() > 0 ? Format.tally(e.getAspeed()) : "—"));
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		card.add(grid);

		card.add(dim("Attack bonuses"));
		card.add(bonuses(e.getAstab(), e.getAslash(), e.getAcrush(), e.getAmagic(), e.getArange()));
		card.add(dim("Defence bonuses"));
		card.add(bonuses(e.getDstab(), e.getDslash(), e.getDcrush(), e.getDmagic(), e.getDrange()));
		card.add(dim("Other bonuses"));
		// one column: "Ranged strength" and "Magic damage" are too long to share a row
		JPanel other = new JPanel(new GridLayout(0, 1, 8, 0));
		other.setOpaque(false);
		other.setAlignmentX(Component.LEFT_ALIGNMENT);
		other.add(Cards.statCell("Strength", signed(e.getStr())));
		other.add(Cards.statCell("Ranged strength", signed(e.getRstr())));
		other.add(Cards.statCell("Magic damage", signed(Math.round(e.getMdmg())) + "%"));
		other.add(Cards.statCell("Prayer", signed(e.getPrayer())));
		other.setMaximumSize(new Dimension(Integer.MAX_VALUE, other.getPreferredSize().height));
		card.add(other);
		return card;
	}

	// the five styles in the order the game's equipment screen lists them
	private static JPanel bonuses(int stab, int slash, int crush, int magic, int range)
	{
		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 0));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.add(Cards.statCell("Stab", signed(stab)));
		grid.add(Cards.statCell("Magic", signed(magic)));
		grid.add(Cards.statCell("Slash", signed(slash)));
		grid.add(Cards.statCell("Ranged", signed(range)));
		grid.add(Cards.statCell("Crush", signed(crush)));
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		return grid;
	}

	private static JLabel dim(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		return label;
	}

	static String signed(long n)
	{
		return n > 0 ? "+" + n : String.valueOf(n);
	}

	static String weightText(double kg)
	{
		return String.format(Locale.ROOT, "%.1f kg", kg);
	}

	// the equipment screen's slot indexes
	static String slotName(int slot)
	{
		switch (slot)
		{
			case 0:
				return "Head";
			case 1:
				return "Cape";
			case 2:
				return "Neck";
			case 3:
				return "Weapon";
			case 4:
				return "Body";
			case 5:
				return "Shield";
			case 7:
				return "Legs";
			case 9:
				return "Hands";
			case 10:
				return "Feet";
			case 12:
				return "Ring";
			case 13:
				return "Ammo";
			default:
				return "Slot " + slot;
		}
	}

	private JPanel buildShopBox(ShopEntry entry, Consumer<ShopData> onShopSelected)
	{
		ShopData shop = entry.getShop();
		ShopItem line = entry.getItem();

		// the same full-width card as the Facts box above it
		JPanel box = Cards.card();

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
		// a synthetic chest/node id is negative but real, so only the lookup decides ownership
		SourceData owner = data.sourceById(npcId);

		// the same full-width card as the Facts box above it
		JPanel box = Cards.card();

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
		box.add(subLabel(qualifier(owner) + rate));

		if (!known)
		{
			return box;
		}

		// compact by default: an item like Coins has thousands of sources, so a card carries just
		// the scanning stats until the player opens it for the confidence bands
		double p = src.getRarity();
		box.add(statRow("Expected", expected(DropStats.expectedKills(p))));
		// the dry streak counts kills of a monster, or attempts at a chest, an activity, or a
		// pocket, which the Loot Tracker reports one at a time
		if (owner != null)
		{
			boolean killable = !owner.getNpcIds().isEmpty();
			JLabel sinceValue = new JLabel(count(store.getDry(npcId, itemId)));
			box.add(statRow(killable ? "Kills since drop" : "Attempts since drop", sinceValue));
			dryRows.add(new DryRow(npcId, itemId, sinceValue));
		}
		addExpander(box, itemId, npcId, p, store);
		return box;
	}

	/**
	 * The show/hide control for one card's confidence bands.
	 *
	 * <p>The bands are what make a card expensive to build, and an item like Coins has thousands
	 * of cards, so they are created the first time the card is opened and not before. The control
	 * carries its own mouse listener, which keeps the press away from the card's navigate-to-source
	 * handler — Swing delivers the event to the innermost component that is listening.
	 */
	private void addExpander(JPanel box, int itemId, int sourceId, double p, TrackerStore store)
	{
		List<Component> bands = new ArrayList<>();
		JLabel toggle = new JLabel(MORE);
		toggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		toggle.setFont(toggle.getFont().deriveFont(toggle.getFont().getSize2D() - 1f));
		toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				setBandsShown(box, bands, toggle, p, bands.isEmpty());
				store.setExpanded(itemId, sourceId, !bands.isEmpty());
			}
		});
		// the toggle sits above its bands, so it is added before they can be
		box.add(toggle);
		if (store.isExpanded(itemId, sourceId))
		{
			setBandsShown(box, bands, toggle, p, true);
		}
	}

	/**
	 * Adds or removes the band rows outright rather than hiding them, so a collapsed card carries
	 * only the toggle. With thousands of cards on an item like Coins, a hidden-but-present panel
	 * per card is a cost worth avoiding.
	 */
	private static void setBandsShown(JPanel box, List<Component> bands, JLabel toggle,
		double p, boolean show)
	{
		if (show)
		{
			for (double c : new double[]{0.10, 0.50, 0.90})
			{
				JPanel row = statRow(Math.round(c * 100) + "% chance",
					count(DropStats.killsForConfidence(p, c)));
				bands.add(row);
				box.add(row);
			}
		}
		else
		{
			bands.forEach(box::remove);
			bands.clear();
		}
		toggle.setText(show ? LESS : MORE);
		box.revalidate();
		box.repaint();
	}

	/** What precedes the rate on a source card: a combat level for kills, the type otherwise. */
	private static String qualifier(SourceData owner)
	{
		if (owner == null)
		{
			return "";
		}
		switch (owner.getType())
		{
			case REWARD:
				return "Reward  ·  ";
			case PICKPOCKET:
				return "Pickpocket  ·  ";
			case GATHER:
				return "Gather  ·  ";
			case ACTIVITY:
				return "Activity  ·  ";
			case PROCESS:
				return "Process  ·  ";
			default:
				return "Lvl " + owner.getCombatLevel() + "  ·  ";
		}
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
