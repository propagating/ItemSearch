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
import com.itemsearch.model.SpawnPoint;
import com.itemsearch.map.SpawnRecorder;
import com.itemsearch.tracker.SpawnStore;
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
		ClientThread clientThread, SpawnStore spawnStore, SpawnRecorder spawnRecorder,
		boolean allowSpawnEditing,
		Runnable onBack, Consumer<ItemMatch> onItemSelected)
	{
		this.spawnStore = spawnStore;
		this.spawnRecorder = spawnRecorder;
		this.allowSpawnEditing = allowSpawnEditing;
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
			: groupDrops(npc.getDrops()).entrySet())
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
	 */
	static Map<String, List<DropSource>> groupDrops(List<DropSource> drops)
	{
		List<DropSource> sorted = new ArrayList<>(mergeByItem(drops));
		sorted.sort(Comparator
			.comparing(DropSource::isRarityKnown).reversed()
			.thenComparing(Comparator.comparingDouble(DropSource::getRarity).reversed()));

		Map<String, List<DropSource>> grouped = new LinkedHashMap<>();
		grouped.put(null, new ArrayList<>()); // reserve first position for the uncategorised group
		for (DropSource d : sorted)
		{
			// the wiki's own section, falling back to the shared table a drop was transcluded from
			// when it arrived without a heading to introduce it
			String key = d.getCategory() != null ? d.getCategory() : d.getDropTable();
			grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
		}
		grouped.values().removeIf(List::isEmpty);
		return grouped;
	}

	/**
	 * Drop lines collapsed so that each item appears once.
	 *
	 * <p>A drop table lists coins five times over at five stack sizes, and the loot event hands
	 * over a stack without saying which line produced it. Drops are therefore tracked per item,
	 * which left five rows reading one counter: any coin drop at all made every coin row claim it.
	 * One row per item is the only shape the tracking underneath can honestly describe.
	 *
	 * <p>Lines on one table are mutually exclusive — a roll picks a line — so the chance of the
	 * item at all is the sum of theirs, capped at certain. A merged row that swallowed a rarity
	 * nobody knows reports itself unknown rather than quietly understating.
	 */
	static List<DropSource> mergeByItem(List<DropSource> drops)
	{
		// first appearance decides position and group, so the merged row lands where the reader
		// already expects to find the item
		Map<Integer, List<DropSource>> byItem = new LinkedHashMap<>();
		for (DropSource d : drops)
		{
			byItem.computeIfAbsent(d.getItemId(), k -> new ArrayList<>()).add(d);
		}

		List<DropSource> merged = new ArrayList<>();
		for (List<DropSource> lines : byItem.values())
		{
			if (lines.size() == 1)
			{
				merged.add(lines.get(0));
				continue;
			}
			DropSource first = lines.get(0);
			double rarity = 0;
			boolean rarityKnown = true;
			int low = Integer.MAX_VALUE;
			int high = 0;
			// one unconditional line makes the row always live: the item can arrive without the
			// condition, so gating the merged row on it would stop counting kills that do count
			String condition = first.getCondition();
			for (DropSource d : lines)
			{
				rarity += d.getRarity();
				rarityKnown &= d.isRarityKnown();
				low = Math.min(low, d.getQuantity());
				high = Math.max(high, Math.max(d.getQuantity(), d.getQuantityMax()));
				if (d.getCondition() == null)
				{
					condition = null;
				}
			}
			merged.add(new DropSource(first.getItemId(), first.getItemName(),
				Math.min(rarity, 1.0), low, high, first.isNoted(), rarityKnown,
				first.getCategory(), first.getDropTable(), lines.size(), condition));
		}
		return merged;
	}

	/**
	 * The row's name, carrying the stack range when it stands for several lines: "Coins (44-440)".
	 *
	 * <p>Only merged rows say a quantity. An ordinary line's stack size is not shown anywhere, and
	 * a merged one has to explain why five coin entries became one.
	 */
	static String dropLabel(DropSource d)
	{
		if (d.getMergedLines() <= 0 || d.getQuantityMax() <= d.getQuantity())
		{
			return d.getItemName();
		}
		return d.getItemName() + " (" + Format.tally(d.getQuantity())
			+ "-" + Format.tally(d.getQuantityMax()) + ")";
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
		JLabel name = new JLabel(dropLabel(d));
		if (d.getMergedLines() > 0)
		{
			// the row is not what the wiki printed, so it says so rather than looking like a
			// single table line whose rarity happens to be generous
			name.setToolTipText(d.getMergedLines() + " drop-table lines combined — the game does "
				+ "not say which one produced a stack");
		}
		textCol.add(name);

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

	/**
	 * Buttons for writing down where a monster actually is.
	 *
	 * <p>The shipped data cannot place everything — its spawn dump stops at npc id 11151, and the
	 * wiki has no map of a good many places. Someone standing in front of the monster knows better
	 * than either, so this lets them say so. What they record layers over the shipped tiles.
	 */
	private JPanel buildRecordingBox(SourceData npc, SpawnMapMarker mapMarker)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setOpaque(false);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		recordedLabel = new JLabel();
		recordedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		recordedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(recordedLabel);
		refreshRecorded();

		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);

		grid.add(recordButton("+ Spawn here", "Record where you are standing as a spawn tile",
			done -> spawnRecorder.recordSpawn(npcId, done), npc, mapMarker));
		grid.add(recordButton("+ Entrance", "Record where you are standing as the way into this "
				+ "monster's dungeon — stand at the surface entrance",
			done -> spawnRecorder.recordEntrance(npcId, done), npc, mapMarker));
		grid.add(recordButton("− Last spawn", "Remove the tile you recorded most recently",
			done ->
			{
				spawnStore.removeLastSpawn(npcId);
				done.accept("Removed the last tile");
			}, npc, mapMarker));
		grid.add(recordButton("− All spawns", "Remove every tile you recorded for this monster; "
				+ "the shipped ones are untouched",
			done ->
			{
				spawnStore.clearSpawns(npcId);
				spawnStore.setEntrance(npcId, null);
				done.accept("Removed your tiles");
			}, npc, mapMarker));

		box.add(grid);
		return box;
	}

	private JButton recordButton(String text, String tooltip, Consumer<Consumer<String>> action,
		SourceData npc, SpawnMapMarker mapMarker)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setToolTipText(tooltip);
		button.addActionListener(e -> action.accept(message ->
		{
			// back on the EDT by now: say what happened, and redraw the markers so the change
			// is visible on the map rather than only on the next reopen
			refreshRecorded();
			recordedLabel.setText(message);
			mapMarker.showSpawns(withRecorded(npc, spawnStore));
		}));
		return button;
	}

	private void refreshRecorded()
	{
		if (recordedLabel == null)
		{
			return;
		}
		int tiles = spawnStore == null ? 0 : spawnStore.spawnsFor(npcId).size();
		boolean entrance = spawnStore != null && spawnStore.entranceFor(npcId) != null;
		if (tiles == 0 && !entrance)
		{
			recordedLabel.setText("Nothing recorded by you yet");
			return;
		}
		recordedLabel.setText("You recorded " + tiles + " tile" + (tiles == 1 ? "" : "s")
			+ (entrance ? " and an entrance" : ""));
	}

	/**
	 * The source as the map should show it: shipped tiles plus whatever the player recorded, and
	 * their entrance in preference to the one worked out for them.
	 */
	public static SourceData withRecorded(SourceData npc, SpawnStore store)
	{
		if (store == null || !store.hasAnything(npc.getId()))
		{
			return npc;
		}
		List<SpawnPoint> spawns = new ArrayList<>(npc.getSpawnPoints());
		spawns.addAll(store.spawnsFor(npc.getId()));
		SpawnPoint entrance = store.entranceFor(npc.getId());
		return new SourceData(npc.getId(), npc.getName(), npc.getCombatLevel(),
			npc.getWikiImageUrl(), npc.getDrops(), spawns, npc.getNpcIds(), npc.getCombat(),
			npc.getType(), npc.getX(), npc.getY(), npc.getPlane(), npc.getTables(),
			npc.getWikiPage(), entrance != null ? entrance : npc.getEntrance(),
			npc.getLocationName());
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

		// what to bring, which is the question these stats are usually being read to answer
		for (String line : weaknessLines(c))
		{
			JLabel label = new JLabel(line);
			label.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			label.setToolTipText("Lowest defence bonus, from the wiki's own numbers");
			box.add(label);
		}
		return box;
	}

	/**
	 * The weakness as lines to render — {@code ["Weak to: Stab", "+50% from Earth spells"]} — or
	 * empty when there is nothing useful to say.
	 *
	 * <p>The wiki publishes defence bonuses rather than weaknesses, so the styles named here are
	 * the ones it defends worst against; a monster that defends everything alike names none.
	 *
	 * <p>A style weakness and an elemental one get a line each. Joined onto one line they overran
	 * the sidebar and were cut off mid-word: a JLabel does not wrap, and the card cannot widen
	 * beyond the panel, so nothing downstream would have saved the text.
	 */
	static List<String> weaknessLines(CombatStats c)
	{
		List<String> lines = new ArrayList<>();
		if (c.getWeakTo() != null && !c.getWeakTo().isEmpty())
		{
			List<String> styles = new ArrayList<>();
			for (String style : c.getWeakTo())
			{
				styles.add(capitalize(style.toLowerCase(Locale.ROOT)));
			}
			lines.add(String.join(", ", styles));
		}
		if (c.getElementalWeakness() != null && !c.getElementalWeakness().trim().isEmpty())
		{
			Integer percent = c.getElementalWeaknessPercent();
			lines.add((percent != null ? "+" + percent + "% from " : "")
				+ c.getElementalWeakness().trim() + " spells");
		}
		if (lines.isEmpty())
		{
			return lines;
		}
		// the lead-in belongs to the first line whatever that line turns out to be, so a monster
		// with only an elemental weakness still reads as a weakness rather than a stray number
		lines.set(0, "Weak to: " + lines.get(0));
		return lines;
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
			// onto the icons themselves, not just their container: Swing shows the tooltip of the
			// deepest component under the pointer, so a note set on the panel behind the icons was
			// never the one displayed — hovering an icon always found the icon's own text instead
			applyHover(icon, iconTooltip(attack));
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

	/**
	 * Adds hover text to a cell and to every icon inside it, keeping what each already said.
	 *
	 * <p>The icons carry their own tooltip ("Protect from Melee"), and Swing shows the innermost
	 * one under the pointer — so anything set on the cell alone is unreachable.
	 */
	private static void applyHover(JComponent cell, String extra)
	{
		if (extra == null)
		{
			return;
		}
		String body = extra.replace("<html>", "").replace("</html>", "");
		cell.setToolTipText("<html>" + body + "</html>");
		for (Component child : cell.getComponents())
		{
			if (!(child instanceof JComponent))
			{
				continue;
			}
			JComponent inner = (JComponent) child;
			String own = inner.getToolTipText();
			inner.setToolTipText(own == null || own.isEmpty()
				? "<html>" + body + "</html>"
				: "<html>" + own.replace("<html>", "").replace("</html>", "") + "<br>" + body + "</html>");
		}
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

	private final SpawnStore spawnStore;
	private final SpawnRecorder spawnRecorder;
	private final boolean allowSpawnEditing;
	// redrawn in place as tiles are added and taken back, so the count stays honest
	private JLabel recordedLabel;

	private JPanel buildActionsBox(SourceData npc, TrackerStore store, SpawnMapMarker mapMarker)
	{
		JPanel box = card();
		box.add(section("Actions"));

		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean mappable = !npc.getSpawnPoints().isEmpty()
			|| (spawnStore != null && spawnStore.hasAnything(npcId));
		if (mappable)
		{
			// local shown-state: SpawnMapMarker is shared, so the button tracks its own toggle
			grid.add(MapToggle.create(
				() -> mapMarker.showSpawns(withRecorded(npc, spawnStore)),
				mapMarker::clear));
		}
		else if (npc.getLocationName() != null && !npc.getLocationName().trim().isEmpty())
		{
			// No tiles the map can use — the wiki has no map of this place, so its coordinates are
			// positions in a drawing. Naming the place is the honest thing left to offer.
			JLabel where = new JLabel("Found in: " + npc.getLocationName().trim());
			where.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			where.setAlignmentX(Component.LEFT_ALIGNMENT);
			where.setToolTipText("The wiki has no map of this place, so it cannot be marked");
			box.add(where);
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
		if (allowSpawnEditing && spawnRecorder != null)
		{
			box.add(buildRecordingBox(npc, mapMarker));
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
		MapToggle.setStateBorder(button, ColorScheme.MEDIUM_GRAY_COLOR);
		button.addActionListener(e -> action.run());
		return button;
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
