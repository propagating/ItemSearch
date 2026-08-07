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
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import com.itemsearch.combat.AttackStyles;
import com.itemsearch.combat.Protection;
import com.itemsearch.map.SpawnMapMarker;
import com.itemsearch.model.Attack;
import com.itemsearch.model.CombatStats;
import com.itemsearch.model.DropSource;
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.MaxHitVariant;
import com.itemsearch.model.SourceData;
import com.itemsearch.model.SourceType;
import com.itemsearch.tracker.TrackerStore;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

public class SourceResultView extends JPanel
{
	private final JLabel image = new JLabel();
	private final OkHttpClient okHttpClient;
	// per-drop stat lines ("Got: N · Dry: X%"), captured so kills can refresh them in place
	private final List<DropRow> dropRows = new ArrayList<>();
	private final int npcId;
	// whether anything can be killed here: chests and gathering nodes carry no npc ids, so
	// kill counts and dry streaks are meaningless for them
	private final boolean killable;
	// the kill-count value label, captured so it updates live on kills without a rebuild.
	// null for sources that are not killable, where no such row is rendered.
	private JLabel killsValue;

	public SourceResultView(SourceData npc, TrackerStore store, SpriteManager spriteManager, ItemManager itemManager,
		OkHttpClient okHttpClient, SpawnMapMarker mapMarker,
		ClientThread clientThread, boolean groupByDropTable,
		Runnable onBack, Consumer<ItemMatch> onItemSelected)
	{
		this.okHttpClient = okHttpClient;
		this.npcId = npc.getId();
		this.killable = !npc.getNpcIds().isEmpty();
		setLayout(new BorderLayout(0, 6));

		JButton back = new JButton("< Back");
		back.addActionListener(e -> onBack.run());
		add(back, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		body.add(buildImageBox(npc));
		body.add(buildNameBox(npc, store));

		// only something you fight has stats to fight against; a chest that happens to carry
		// stale combat data must not render a prayer table
		CombatStats c = npc.getCombat();
		if (c != null && npc.getType() == SourceType.KILL)
		{
			body.add(buildStatsBox(c));
			body.add(buildAttacksBox(c, spriteManager, itemManager));
		}

		body.add(buildActionsBox(npc, store, mapMarker));

		// wiki drop tables are split into meaningful subsections ("Rare drop table", "100%");
		// keep those groupings, with anything uncategorised leading as before
		for (Map.Entry<String, List<DropSource>> group
			: groupDrops(npc.getDrops(), groupByDropTable).entrySet())
		{
			if (group.getKey() != null)
			{
				body.add(groupHeading(group.getKey()));
			}
			for (DropSource d : group.getValue())
			{
				body.add(buildDropRow(npc.getId(), d, store, itemManager, onItemSelected));
			}
		}

		add(body, BorderLayout.CENTER);
	}

	/**
	 * A drop-group heading sized to span the sidebar.
	 *
	 * <p>The label cannot be added to the body directly: a Y_AXIS BoxLayout offsets any child
	 * whose alignmentX differs from its siblings, so a LEFT_ALIGNMENT label among the
	 * CENTER_ALIGNMENT rows gets pushed to the right and long names run off the edge. Wrapping it
	 * in a full-width row that matches the rows' alignment and horizontal inset keeps the text at
	 * the left edge with the whole sidebar to spread into.
	 */
	private static JPanel groupHeading(String title)
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
		row.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
		row.add(section(title), BorderLayout.WEST);
		return row;
	}

	/**
	 * Drops grouped for display, uncategorised first, then each group in the order it first
	 * appears. Each group keeps the display sort: known rarities first, commonest first.
	 *
	 * @param byDropTable group by the shared drop table a drop came from rather than by the wiki
	 *                    section it is listed under
	 */
	private static Map<String, List<DropSource>> groupDrops(List<DropSource> drops,
		boolean byDropTable)
	{
		List<DropSource> sorted = new ArrayList<>(drops);
		sorted.sort(Comparator
			.comparing(DropSource::isRarityKnown).reversed()
			.thenComparing(Comparator.comparingDouble(DropSource::getRarity).reversed()));

		Map<String, List<DropSource>> grouped = new LinkedHashMap<>();
		grouped.put(null, new ArrayList<>()); // reserve first position for the uncategorised group
		for (DropSource d : sorted)
		{
			// Each mode falls back to the other rather than collapsing into one unnamed group:
			// a drop outside any shared table has no table name, and a shared table pulled in
			// without a heading introducing it has no wiki section.
			String key = byDropTable
				? (d.getDropTable() != null ? d.getDropTable() : d.getCategory())
				: (d.getCategory() != null ? d.getCategory() : d.getDropTable());
			grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
		}
		grouped.values().removeIf(List::isEmpty);
		return grouped;
	}

	private JPanel buildDropRow(int ownerId, DropSource d, TrackerStore store, ItemManager itemManager,
		Consumer<ItemMatch> onItemSelected)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setCursor(new Cursor(Cursor.HAND_CURSOR));

		JLabel icon = new JLabel();
		AsyncBufferedImage img = itemManager.getImage(d.getItemId());
		if (img != null)
		{
			img.addTo(icon);
		}
		row.add(icon, BorderLayout.WEST);

		JPanel textCol = new JPanel();
		textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
		textCol.setOpaque(false);
		textCol.add(new JLabel(d.getItemName()));

		JLabel stat = new JLabel(statLine(ownerId, d, store));
		stat.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		stat.setFont(stat.getFont().deriveFont(stat.getFont().getSize2D() - 1f));
		textCol.add(stat);
		row.add(textCol, BorderLayout.CENTER);

		row.add(new JLabel(formatRarity(d)), BorderLayout.EAST);
		dropRows.add(new DropRow(ownerId, d, stat));

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onItemSelected.accept(new ItemMatch(d.getItemId(), d.getItemName()));
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
		return row;
	}

	// A full-width bordered card (matching the rest of the plugin's card styling). The getMaximumSize
	// override lets the parent Y_AXIS BoxLayout stretch the card across the whole sidebar width — a
	// BoxLayout panel otherwise reports only its (narrower) children's max width, so BoxLayout leaves
	// the card at preferred width and its alignmentX floats it inward. Height stays capped to the
	// natural height (so it never stretches vertically and still grows when the portrait loads).
	private static JPanel card()
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
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Match the drop rows' alignmentX (0.5). In a Y_AXIS BoxLayout a child whose alignmentX
		// differs from its siblings is offset and shrunk rather than filled; keeping every body
		// child at CENTER_ALIGNMENT lets the MAX-width cards span the full sidebar like the rows.
		box.setAlignmentX(Component.CENTER_ALIGNMENT);
		return box;
	}

	private JPanel buildImageBox(SourceData npc)
	{
		JPanel box = card();
		WikiImageLoader.loadInto(okHttpClient, image, npc.getWikiImageUrl());
		JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		center.setOpaque(false);
		center.setAlignmentX(Component.LEFT_ALIGNMENT);
		center.add(image);
		box.add(center);
		return box;
	}

	private JPanel buildNameBox(SourceData npc, TrackerStore store)
	{
		JPanel box = card();

		JLabel name = new JLabel(npc.getName());
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(name);

		// a chest or node has no combat level; name what it is instead
		JLabel level = new JLabel(npc.getType() == SourceType.KILL
			? "Level " + npc.getCombatLevel() : typeLabel(npc.getType()));
		level.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		level.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(level);

		if (killable)
		{
			JPanel killsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			killsRow.setOpaque(false);
			killsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel killsLabel = new JLabel("Kills:");
			killsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			killsValue = new JLabel(Format.tally(store.getNpcKills(npcId)));
			killsRow.add(killsLabel);
			killsRow.add(new JLabel(" "));
			killsRow.add(killsValue);
			box.add(killsRow);
		}
		return box;
	}

	private static String typeLabel(SourceType type)
	{
		switch (type)
		{
			case REWARD:
				return "Reward chest";
			case PICKPOCKET:
				return "Pickpocketing";
			case GATHER:
				return "Gathering node";
			default:
				return "";
		}
	}

	private JPanel buildStatsBox(CombatStats c)
	{
		JPanel box = card();
		box.add(section("Stats"));

		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 0));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		// GridLayout fills row-by-row, giving left column ATK/STR/DEF and right column Magic/Range/HP
		grid.add(statCell("Attack", c.getAttackLevel()));
		grid.add(statCell("Magic", c.getMagicLevel()));
		grid.add(statCell("Strength", c.getStrengthLevel()));
		grid.add(statCell("Ranged", c.getRangedLevel()));
		grid.add(statCell("Defence", c.getDefenceLevel()));
		grid.add(statCell("Hitpoints", c.getHitpoints()));
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		box.add(grid);
		return box;
	}

	private JPanel buildAttacksBox(CombatStats c, SpriteManager spriteManager, ItemManager itemManager)
	{
		JPanel box = card();
		box.add(section("Attacks"));

		List<Attack> attacks = c.getAttacks();
		if (attacks == null || attacks.isEmpty())
		{
			JLabel none = new JLabel("No attack data");
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.add(none);
			return box;
		}

		JPanel table = new JPanel(new GridBagLayout());
		table.setOpaque(false);
		table.setAlignmentX(Component.LEFT_ALIGNMENT);
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(1, 3, 1, 3);

		// column 0: row labels. Nothing sits above the icons — a spacer label there rendered as a
		// stray mark, and the column is sized by "Speed"/"Max hit" without one.
		gc.gridx = 0;
		gc.anchor = GridBagConstraints.WEST;
		gc.gridy = 1;
		table.add(dim("Speed"), gc);
		gc.gridy = 2;
		table.add(dim("Max hit"), gc);

		// one column per Attack, each with its own prayer icon, speed, and max hit.
		int col = 1;
		for (Attack attack : inPrayerOrder(attacks))
		{
			if (attack == null)
			{
				continue;
			}
			gc.gridx = col++;
			gc.anchor = GridBagConstraints.CENTER;
			gc.gridy = 0;
			// hybrids ("magic ranged") roll accuracy with style but are countered by
			// prayStyle — the icon must show what to PRAY, with the roll explained on hover
			String prayToken = attack.getPrayStyle() != null ? attack.getPrayStyle() : attack.getStyle();
			JComponent icon = protectionCell(prayToken, spriteManager, itemManager);
			String hover = iconTooltip(attack);
			if (hover != null)
			{
				icon.setToolTipText(hover);
			}
			table.add(icon, gc);
			gc.gridy = 1;
			table.add(new JLabel(range(attack.getSpeed(), attack.getSpeedMax())), gc);
			gc.gridy = 2;
			JLabel maxHit = new JLabel(maxHitText(attack));
			maxHit.setToolTipText(maxHitTooltip(attack));
			table.add(maxHit, gc);
		}
		box.add(table);

		return box;
	}

	/**
	 * Attacks in the order the prayers sit in the prayer book — Magic, Ranged, Melee, then
	 * anything with no prayer of its own — so the icon row always reads the same way whatever
	 * order the curator entered them in. Ordering is by what the attack is prayed against, which
	 * is what its icon shows: a magic-melee hybrid sorts with melee.
	 */
	private static List<Attack> inPrayerOrder(List<Attack> attacks)
	{
		List<Attack> ordered = new ArrayList<>();
		for (Attack a : attacks)
		{
			if (a != null)
			{
				ordered.add(a);
			}
		}
		// stable, so the curator's own order still breaks ties within one prayer
		ordered.sort(Comparator.comparingInt(SourceResultView::prayerRank));
		return ordered;
	}

	private static int prayerRank(Attack attack)
	{
		String token = attack.getPrayStyle() != null ? attack.getPrayStyle() : attack.getStyle();
		String style = token == null ? "" : token.toLowerCase(Locale.ROOT);
		if (style.contains("magic") || style.contains("mage"))
		{
			return 0;
		}
		if (style.contains("ranged") || style.contains("range"))
		{
			return 1;
		}
		if (style.contains("melee") || style.contains("stab")
			|| style.contains("slash") || style.contains("crush"))
		{
			return 2;
		}
		return 3; // dragonfire, icy breath, typeless: no prayer of their own
	}

	/**
	 * Hover text for an attack's prayer icon: the curator's notes when it has any, and the
	 * accuracy/defence split for a hybrid. Notes used to occupy a row of their own beneath the
	 * table, which crowded a card that is mostly numbers.
	 */
	private static String iconTooltip(Attack attack)
	{
		List<String> lines = new ArrayList<>();
		if (attack.getPrayStyle() != null)
		{
			lines.add(capitalize(attack.getStyle()) + " accuracy roll — prayed against as "
				+ capitalize(attack.getPrayStyle()));
		}
		if (attack.getNotes() != null && !attack.getNotes().trim().isEmpty())
		{
			lines.add(attack.getNotes().trim());
		}
		return lines.isEmpty() ? null : "<html>" + String.join("<br>", lines) + "</html>";
	}

	/**
	 * Headline max hit: the worst case, starred when gear or a potion can lower it.
	 *
	 * <p>A steel dragon's dragonfire hits 50 through nothing and 0 through insulated boots and an
	 * antifire, with rungs in between. Listing every rung on the label ("0/5/35/50") crowds the
	 * column as soon as there are more than two, so the label states the number you take if you
	 * bring nothing and the star says it moves; the hover has the ladder.
	 */
	private static String maxHitText(Attack attack)
	{
		String base = range(attack.getMaxHit(), attack.getMaxHitMax());
		if (attack.getVariants() == null || attack.getVariants().isEmpty())
		{
			return base;
		}
		// a combo variant can peak above the unprotected number, and the worst case is the point
		int worst = Math.max(attack.getMaxHit(), attack.getMaxHitMax());
		String text = base;
		for (MaxHitVariant variant : attack.getVariants())
		{
			int peak = Math.max(variant.getMaxHit(), variant.getMaxHitMax());
			if (peak > worst)
			{
				worst = peak;
				text = range(variant.getMaxHit(), variant.getMaxHitMax());
			}
		}
		return text + "*";
	}

	/**
	 * Hover text for a max hit: what mitigates it, and what it does besides damage.
	 *
	 * <p>Both belong at the number. A Saradomin wizard poisons with its melee, which matters
	 * exactly when you are reading how hard that melee hits.
	 */
	private static String maxHitTooltip(Attack attack)
	{
		List<String> lines = new ArrayList<>();
		if (attack.getVariants() != null && !attack.getVariants().isEmpty())
		{
			// lowest first, so the lines read in the same order as the slashes on the label
			attack.getVariants().stream()
				.sorted((a, b) -> Integer.compare(
					Math.max(a.getMaxHit(), a.getMaxHitMax()),
					Math.max(b.getMaxHit(), b.getMaxHitMax())))
				.forEach(v -> lines.add(range(v.getMaxHit(), v.getMaxHitMax())
					+ " with " + v.getLabel()));
			lines.add(range(attack.getMaxHit(), attack.getMaxHitMax()) + " unprotected");
		}
		if (attack.getEffects() != null)
		{
			for (String effect : attack.getEffects())
			{
				String phrase = effectPhrase(effect);
				if (phrase != null)
				{
					lines.add(phrase);
				}
			}
		}
		return lines.isEmpty() ? null : "<html>" + String.join("<br>", lines) + "</html>";
	}

	/** Reads as a warning rather than a data field: "May inflict poison", "May drain Prayer". */
	private static String effectPhrase(String effect)
	{
		if (effect == null)
		{
			return null;
		}
		switch (effect.toUpperCase(Locale.ROOT))
		{
			case "POISON":
				return "May inflict poison";
			case "VENOM":
				return "May inflict venom";
			case "DISEASE":
				return "May inflict disease";
			case "DRAIN_PRAYER":
				return "May drain Prayer";
			case "DRAIN_STATS":
				return "May drain stats";
			default:
				return null; // an effect this build does not know how to word
		}
	}

	private static String capitalize(String s)
	{
		return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// The icons telling the player how to counter this attack style: each protection prayer or
	// protecting item as its own icon (dragonfire/icy breath show a partial Protect from Magic plus
	// the shield that blocks them), or a "✕" when nothing counters it.
	private static JComponent protectionCell(String style, SpriteManager spriteManager, ItemManager itemManager)
	{
		List<Protection> protections = AttackStyles.protectionsFor(style);
		if (protections.isEmpty())
		{
			JLabel none = new JLabel("✕");
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			none.setToolTipText("No prayer protects against this attack");
			return none;
		}

		JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
		cell.setOpaque(false);
		for (Protection p : protections)
		{
			JLabel icon = new JLabel();
			icon.setToolTipText(p.getDisplayName());
			if (p.isPrayer())
			{
				// getSpriteAsync's callback runs on the client thread -> marshal setIcon to the EDT
				spriteManager.getSpriteAsync(p.getPrayer().getSpriteId(), 0,
					img -> SwingUtilities.invokeLater(() -> icon.setIcon(new ImageIcon(img))));
			}
			else
			{
				AsyncBufferedImage img = itemManager.getImage(p.getItem().getItemId());
				if (img != null)
				{
					img.addTo(icon);
				}
			}
			cell.add(icon);
		}
		return cell;
	}

	private JPanel buildActionsBox(SourceData npc, TrackerStore store, SpawnMapMarker mapMarker)
	{
		JPanel box = card();
		box.add(section("Actions"));

		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (!npc.getSpawnPoints().isEmpty())
		{
			JButton map = new JButton("Map");
			map.setFocusPainted(false);
			// local shown-state: SpawnMapMarker is shared, so the button tracks its own toggle
			boolean[] shown = {false};
			applyToggle(map, false, "Hide spawn markers", "Show spawn locations on the map");
			map.addActionListener(e ->
			{
				boolean now = !shown[0];
				shown[0] = now;
				applyToggle(map, now, "Hide spawn markers", "Show spawn locations on the map");
				if (now)
				{
					mapMarker.showSpawns(npc);
				}
				else
				{
					mapMarker.clear();
				}
			});
			grid.add(map);
		}

		if (killable)
		{
			grid.add(resetButton("↺ Kills", "Reset kill count", () ->
			{
				store.resetNpcKills(npcId);
				store.save();
				refresh(store);
			}));
		}
		grid.add(resetButton("↺ Drops", "Reset drop counters", () ->
		{
			store.resetNpcDrops(npcId);
			store.save();
			refresh(store);
		}));

		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		box.add(grid);
		return box;
	}

	/**
	 * Refreshes the kill count and every drop's "Got / Dry" line from the store in place, on the
	 * EDT after a kill, so counters move without rebuilding the view (which would reset the scroll).
	 */
	public void refresh(TrackerStore store)
	{
		if (killsValue != null)
		{
			killsValue.setText(Format.tally(store.getNpcKills(npcId)));
		}
		for (DropRow row : dropRows)
		{
			row.stat.setText(statLine(row.npcId, row.drop, store));
		}
	}

	private static String statLine(int npcId, DropSource d, TrackerStore store)
	{
		int got = store.getObtained(npcId, d.getItemId());
		String dry = (d.isRarityKnown() && d.getRarity() > 0)
			? Format.dryPercent(d.getRarity(), store.getDry(npcId, d.getItemId()))
			: "—";
		return "Got: " + Format.tally(got) + " · Dry: " + dry;
	}

	private static JPanel statCell(String label, int value)
	{
		JPanel cell = new JPanel(new BorderLayout());
		cell.setOpaque(false);
		JLabel name = new JLabel(label);
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		cell.add(name, BorderLayout.WEST);
		cell.add(new JLabel(Format.tally(value)), BorderLayout.EAST);
		return cell;
	}

	private static JLabel dim(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	// "N", "low-high", or "—" when unknown. Reads the high via max(low, high) so single-value
	// attacks (and older data where the high is absent/0) render as one number.
	private static String range(int low, int high)
	{
		int hi = Math.max(low, high);
		if (hi <= 0)
		{
			return "—";
		}
		if (low <= 0 || low == hi)
		{
			return Format.tally(hi);
		}
		return Format.tally(low) + "-" + Format.tally(hi);
	}

	private static JLabel section(String title)
	{
		JLabel label = new JLabel(title.toUpperCase(Locale.ROOT));
		label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1f));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * Loads a freshly fetched wiki image into this view. Safe to call from any thread
	 * (the underlying loader marshals to the EDT).
	 */
	public void updateImage(String url)
	{
		WikiImageLoader.loadInto(okHttpClient, image, url);
	}

	static String formatRarity(DropSource d)
	{
		if (!d.isRarityKnown() || d.getRarity() <= 0)
		{
			return "—";
		}
		return Format.oneIn(d.getRarity());
	}

	// An action button (reset kills/drops): reset glyph + word, neutral border, no state.
	private static JButton resetButton(String text, String tooltip, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setToolTipText(tooltip);
		setStateBorder(button, ColorScheme.MEDIUM_GRAY_COLOR);
		button.addActionListener(e -> action.run());
		return button;
	}

	// A toggle button's on/off state, shown as a green (on) or orange (off) border + tooltip.
	private static void applyToggle(JButton button, boolean on, String onTip, String offTip)
	{
		setStateBorder(button, on ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
		button.setToolTipText(on ? onTip : offTip);
	}

	private static void setStateBorder(JButton button, Color color)
	{
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(color),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)));
	}

	// captures one drop's stat label so kills can refresh it in place
	private static final class DropRow
	{
		private final int npcId;
		private final DropSource drop;
		private final JLabel stat;

		private DropRow(int npcId, DropSource drop, JLabel stat)
		{
			this.npcId = npcId;
			this.drop = drop;
			this.stat = stat;
		}
	}
}
