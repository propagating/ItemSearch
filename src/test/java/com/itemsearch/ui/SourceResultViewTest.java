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

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import okhttp3.OkHttpClient;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.map.SpawnMapMarker;
import com.itemsearch.model.Attack;
import com.itemsearch.model.CombatStats;
import com.itemsearch.model.MaxHitVariant;
import com.itemsearch.model.SourceData;
import com.itemsearch.model.SourceType;
import com.itemsearch.tracker.TrackerStore;
import net.runelite.client.ui.ColorScheme;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SourceResultViewTest
{
	private static final int GOBLIN = 3031;
	private static final int ZULRAH = 2042;
	private static final int COINS = 995;
	private static final int BRONZE_DAGGER = 1205;

	private BundledDataSource loadedTestData()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		ds.load();
		return ds;
	}

	private SourceResultView build(SourceData npc, TrackerStore store)
	{
		return build(npc, store, false);
	}

	private SourceResultView build(SourceData npc, TrackerStore store, boolean groupByDropTable)
	{
		return new SourceResultView(npc, store, mock(SpriteManager.class), mock(ItemManager.class),
			new OkHttpClient(), mock(SpawnMapMarker.class), mock(ClientThread.class), groupByDropTable,
			() ->
			{
			},
			item ->
			{
			});
	}

	private static SourceData npc(int id, String name, int level, CombatStats combat)
	{
		return new SourceData(id, name, level, null, Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList(id), combat, SourceType.KILL, null, null, null, null);
	}

	private static CombatStats combat(List<String> attackTypes)
	{
		List<Attack> attacks = new ArrayList<>();
		for (String t : attackTypes)
		{
			attacks.add(new Attack(t, 5, 5, 4, 4, null, null, null, null));
		}
		return new CombatStats(1, 1, 1, 1, 1, 5, attacks);
	}

	private static List<JLabel> labels(Container root)
	{
		List<JLabel> out = new ArrayList<>();
		for (Component c : root.getComponents())
		{
			if (c instanceof JLabel)
			{
				out.add((JLabel) c);
			}
			if (c instanceof Container)
			{
				out.addAll(labels((Container) c));
			}
		}
		return out;
	}

	private static List<String> labelTexts(Container root)
	{
		List<String> out = new ArrayList<>();
		for (JLabel l : labels(root))
		{
			out.add(l.getText());
		}
		return out;
	}

	// lay out the whole component tree headless so child widths are computed
	private static void deepLayout(Container c)
	{
		c.doLayout();
		for (Component ch : c.getComponents())
		{
			if (ch instanceof Container)
			{
				deepLayout((Container) ch);
			}
		}
	}

	// the bordered card (its border is the CompoundBorder built by SourceResultView.card())
	// that transitively contains a label with the given text
	private static JComponent cardContaining(Container root, String labelText)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JComponent && ((JComponent) c).getBorder() instanceof CompoundBorder
				&& labelTexts((Container) c).contains(labelText))
			{
				return (JComponent) c;
			}
			if (c instanceof Container)
			{
				JComponent found = cardContaining((Container) c, labelText);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void statBoxesFillTheFullSidebarWidth()
	{
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		int width = 250;
		view.setSize(width, 3000);
		deepLayout(view);

		// the Stats card carries an "Attack" row; it must span the sidebar, not float inward
		JComponent statsCard = cardContaining(view, "Attack");
		assertNotNull("stats card should exist", statsCard);
		assertEquals("stat card must span the full sidebar width", width, statsCard.getWidth());
	}

	private static JButton button(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && text.equals(((JButton) c).getText()))
			{
				return (JButton) c;
			}
			if (c instanceof Container)
			{
				JButton found = button((Container) c, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static int countTooltips(Container root, String tip)
	{
		int n = 0;
		for (Component c : root.getComponents())
		{
			if (c instanceof JComponent && tip.equals(((JComponent) c).getToolTipText()))
			{
				n++;
			}
			if (c instanceof Container)
			{
				n += countTooltips((Container) c, tip);
			}
		}
		return n;
	}

	@Test
	public void knownRarityDropShowsGotAndDryPercent()
	{
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		store.recordKill(GOBLIN, Collections.singleton(BRONZE_DAGGER), Arrays.asList(COINS, BRONZE_DAGGER));
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN), store);
		assertTrue(labels(view).stream().anyMatch(l ->
			l.getText() != null && l.getText().startsWith("Got: 1 · Dry: ") && l.getText().endsWith("%")));
	}

	@Test
	public void unknownRarityDropShowsDashForDry()
	{
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		SourceResultView view = build(loadedTestData().sourceById(ZULRAH), store);
		assertTrue(labels(view).stream().anyMatch(l -> "Got: 0 · Dry: —".equals(l.getText())));
	}

	@Test
	public void combatNpcShowsTwoColumnStatGrid()
	{
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		List<String> text = labelTexts(view);
		for (String stat : Arrays.asList("Attack", "Strength", "Defence", "Magic", "Ranged", "Hitpoints"))
		{
			assertTrue("stat grid must list " + stat, text.contains(stat));
		}
	}

	@Test
	public void attacksBoxShowsPerStyleValuesAndPrayerIcon()
	{
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN), // fixture attack: crush, maxHit 60, speed 6
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		List<String> text = labelTexts(view);
		assertTrue("attacks box has a Speed row", text.contains("Speed"));
		assertTrue("attacks box has a Max hit row", text.contains("Max hit"));
		assertTrue("shows the crush style's own max hit", text.contains("60"));
		assertTrue("shows the crush style's own speed", text.contains("6"));
		assertEquals("one melee column for crush", 1, countTooltips(view, "Protect from Melee"));
	}

	@Test
	public void repeatedPrayerStylesGetRepeatedColumns()
	{
		SourceResultView view = build(npc(9001, "Multi", 100, combat(Arrays.asList("stab", "crush", "magic"))),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals("two melee columns for stab + crush", 2, countTooltips(view, "Protect from Melee"));
		assertEquals("one magic column", 1, countTooltips(view, "Protect from Magic"));
	}

	@Test
	public void unprotectableStyleShowsNoPrayerWarning()
	{
		SourceResultView view = build(npc(9002, "Ghost", 200, combat(Collections.singletonList("typeless"))),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(1, countTooltips(view, "No prayer protects against this attack"));
	}

	@Test
	public void dragonfireShowsPartialMagicPrayerAndDragonfireShield()
	{
		SourceResultView view = build(npc(9003, "Dragon", 200, combat(Collections.singletonList("dragonfire"))),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(1, countTooltips(view, "Protect from Magic (partial)"));
		assertEquals(1, countTooltips(view, "Protect with Dragonfire shield (or antifire potion)"));
		assertEquals(0, countTooltips(view, "No prayer protects against this attack"));
	}

	@Test
	public void icyBreathShowsPartialMagicPrayerAndElementalShield()
	{
		SourceResultView view = build(npc(9004, "Wyvern", 200, combat(Collections.singletonList("icy breath"))),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(1, countTooltips(view, "Protect from Magic (partial)"));
		assertEquals(1, countTooltips(view, "Protect with Elemental shield"));
	}

	@Test
	public void metallicDragonfireShowsShieldWithoutMagicPrayer()
	{
		SourceResultView view = build(
			npc(9005, "Steel dragon", 246, combat(Collections.singletonList("metallic dragonfire"))),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(1, countTooltips(view, "Protect with Dragonfire shield (or antifire potion)"));
		assertEquals(0, countTooltips(view, "Protect from Magic (partial)"));
	}

	@Test
	public void attackRangesRenderAsMinDashMax()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5,
			Collections.singletonList(new Attack("stab", 1, 3, 4, 6, null, null, null, null)));
		SourceResultView view = build(npc(9010, "Dwarf", 10, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		List<String> text = labelTexts(view);
		assertTrue("max hit range shown", text.contains("1-3"));
		assertTrue("speed range shown", text.contains("4-6"));
	}

	@Test
	public void nonCombatNpcShowsKillsButNoStatGrid()
	{
		SourceResultView view = build(loadedTestData().sourceById(ZULRAH), // no combat in fixture
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		List<String> text = labelTexts(view);
		assertTrue("kills line still shown", text.contains("Kills:"));
		assertFalse("no stat grid without combat data", text.contains("Attack"));
	}

	@Test
	public void killCountRendersRefreshesAndGroups()
	{
		ConfigManager cm = mock(ConfigManager.class);
		when(cm.getConfiguration("itemsearch", "data")).thenReturn("{\"npcKills\":{\"3031\":1234}}");
		TrackerStore store = new TrackerStore(cm, new Gson());
		store.load();
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN), store);
		assertTrue("kills render with a thousands separator",
			labels(view).stream().anyMatch(l -> "1,234".equals(l.getText())));

		for (int i = 0; i < 2; i++)
		{
			store.recordKill(9999, Collections.emptySet(), Collections.emptyList()); // unrelated npc
		}
		store.recordKill(GOBLIN, Collections.emptySet(), Collections.emptyList());
		view.refresh(store);
		assertTrue("kills refresh in place",
			labels(view).stream().anyMatch(l -> "1,235".equals(l.getText())));
	}

	@Test
	public void actionsBoxHasAllButtons()
	{
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN), // fixture has spawn points
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertNotNull(button(view, "Map"));
		assertNotNull(button(view, "↺ Kills"));
		assertNotNull(button(view, "↺ Drops"));
	}

	@Test
	public void toggleButtonsBorderReflectsOnOffState()
	{
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN), // fixture has spawn points
			new TrackerStore(mock(ConfigManager.class), new Gson()));

		JButton map = button(view, "Map");
		assertEquals("markers hidden -> orange", ColorScheme.BRAND_ORANGE, borderColor(map));
		map.doClick();
		assertEquals("markers shown -> green", ColorScheme.PROGRESS_COMPLETE_COLOR, borderColor(map));
	}

	// the color of a button's outer LineBorder (set by SourceResultView.setStateBorder)
	private static Color borderColor(JButton button)
	{
		CompoundBorder cb = (CompoundBorder) button.getBorder();
		return ((LineBorder) cb.getOutsideBorder()).getLineColor();
	}

	@Test
	public void resetKillsButtonZeroesKillCount()
	{
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		store.recordKill(GOBLIN, Collections.emptySet(), Collections.emptyList());
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN), store);
		button(view, "↺ Kills").doClick();
		assertEquals(0, store.getNpcKills(GOBLIN));
	}

	@Test
	public void resetDropsButtonZeroesDropCounters()
	{
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		store.recordKill(GOBLIN, Collections.singleton(BRONZE_DAGGER), Arrays.asList(COINS, BRONZE_DAGGER));
		SourceResultView view = build(loadedTestData().sourceById(GOBLIN), store);
		button(view, "↺ Drops").doClick();
		assertEquals(0, store.getObtained(GOBLIN, BRONZE_DAGGER));
		assertEquals(0, store.getDry(GOBLIN, COINS));
	}
	@Test
	public void hybridAttackPraysWithPrayStyleAndExplainsInTooltip()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(
			new Attack("magic", 15, 15, 4, 4, "ranged", null, null, null)));
		SourceResultView view = build(npc(9020, "Hybrid", 90, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		// the protection icon reflects the PRAY style (ranged), not the accuracy style
		assertEquals(1, countTooltips(view, "Protect from Missiles"));
		assertEquals(0, countTooltips(view, "Protect from Magic"));
		assertEquals(1, countTooltips(view,
			"<html>Magic accuracy roll — prayed against as Ranged</html>"));
	}


	@Test
	public void conditionalMaxHitNumberExplainsDifferenceInTooltip()
	{
		Attack a = new Attack("magic", 15, 15, 4, 4, null, Arrays.asList(
			new MaxHitVariant("Insulated boots", "GEAR", 8, 8),
			new MaxHitVariant("Antifire", "CONSUMABLE", 12, 12)), null, null);
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(a));
		SourceResultView view = build(npc(9022, "Killerwatt", 55, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals("max hit number carries a variant breakdown tooltip", 1, countTooltips(view,
			"<html>15 unprotected<br>12 with Antifire<br>8 with Insulated boots</html>"));
	}

	@Test
	public void rewardSourceDropsCombatAndKillTrackingSections()
	{
		SourceResultView view = build(loadedTestData().sourceById(-1000),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		List<String> text = labelTexts(view);

		assertTrue("the type stands in for a combat level", text.contains("Reward chest"));
		assertFalse("a chest has no combat stats", text.contains("Attack"));
		assertFalse("a chest has no attack styles", text.contains("Max hit"));
		assertFalse("nothing is killed at a chest", text.contains("Kills:"));
		assertNull("no kill counter to reset", button(view, "↺ Kills"));
		// the chest's wiki coordinates come through as a spawn point, so it is still mappable
		assertNotNull("a chest must still be locatable on the map", button(view, "Map"));
	}

	@Test
	public void killSourceKeepsCombatAndKillTrackingSections()
	{
		SourceResultView view = build(loadedTestData().sourceById(3031),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		List<String> text = labelTexts(view);
		assertTrue(text.contains("Level 2"));
		assertTrue(text.contains("Kills:"));
		assertTrue(text.contains("Attack"));
		assertNotNull(button(view, "↺ Kills"));
	}

	@Test
	public void dropsGroupUnderTheirWikiSubsectionHeadings()
	{
		SourceResultView view = build(loadedTestData().sourceById(963),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		List<String> text = labelTexts(view);
		assertTrue("category headings render", text.contains("100%"));  // section() upper-cases; unchanged here
		assertTrue("category headings render", text.contains("RARE DROP TABLE"));

		// uncategorised drops lead, then each category in first-appearance order
		int coins = text.indexOf("Coins");
		int hundred = text.indexOf("100%");
		int rare = text.indexOf("RARE DROP TABLE");
		assertTrue("uncategorised drops come before any heading", coins < hundred);
		assertTrue("headings keep first-appearance order", hundred < rare);
		assertTrue("a categorised drop follows its heading", rare < text.indexOf("Loop half of key"));
	}

	// A Y_AXIS BoxLayout offsets a child whose alignmentX differs from its siblings, so a
	// LEFT_ALIGNMENT heading among CENTER_ALIGNMENT rows gets pushed right and clipped.
	@Test
	public void dropGroupHeadingsSpanTheSidebarAndStartAtTheLeftEdge()
	{
		SourceResultView view = build(loadedTestData().sourceById(963),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		int width = 225;
		view.setSize(width, 4000);
		deepLayout(view);

		JLabel heading = labelWithText(view, "RARE DROP TABLE");
		assertNotNull("the group heading must render", heading);
		JLabel dropName = labelWithText(view, "Loop half of key");
		assertNotNull("a drop row must render to compare against", dropName);

		assertTrue("heading is offset right of the drop rows: heading at "
				+ absoluteX(heading, view) + ", rows at " + absoluteX(dropName, view),
			absoluteX(heading, view) <= absoluteX(dropName, view));
		assertTrue("heading must not be clipped: needs " + heading.getPreferredSize().width
				+ "px, got " + heading.getWidth(),
			heading.getWidth() >= heading.getPreferredSize().width);
	}

	private static JLabel labelWithText(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JLabel && text.equals(((JLabel) c).getText()))
			{
				return (JLabel) c;
			}
			if (c instanceof Container)
			{
				JLabel found = labelWithText((Container) c, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static int absoluteX(Component c, Component root)
	{
		int x = 0;
		for (Component cur = c; cur != null && cur != root; cur = cur.getParent())
		{
			x += cur.getX();
		}
		return x;
	}


	@Test
	public void dropsCanGroupByTheirSharedDropTableInstead()
	{
		// default: grouped by the page's wiki section
		List<String> bySection = labelTexts(build(loadedTestData().sourceById(963),
			new TrackerStore(mock(ConfigManager.class), new Gson()), false));
		assertTrue(bySection.contains("HERBS"));
		assertFalse("the shared table is not the grouping by default",
			bySection.contains("HERB DROP TABLE"));

		List<String> byTable = labelTexts(build(loadedTestData().sourceById(963),
			new TrackerStore(mock(ConfigManager.class), new Gson()), true));
		assertTrue("grouping by drop table names the shared table",
			byTable.contains("HERB DROP TABLE"));
		assertFalse(byTable.contains("HERBS"));
		// a drop belonging to no shared table keeps its section heading either way
		assertTrue("uncategorised drops still fall back to their section",
			byTable.contains("RARE DROP TABLE"));
	}

	// Attacks read left to right in the order the prayers sit in the prayer book, whatever order
	// the curator happened to enter them in.
	@Test
	public void attacksRenderInPrayerOrder()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Arrays.asList(
			new Attack("dragonfire", 50, 50, 4, 4, null, null, null, null),
			new Attack("crush", 10, 10, 4, 4, null, null, null, null),
			new Attack("ranged", 20, 20, 4, 4, null, null, null, null),
			new Attack("magic", 30, 30, 4, 4, null, null, null, null)));
		SourceResultView view = build(npc(9030, "Everything", 300, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));

		List<String> text = labelTexts(view);
		int magic = text.indexOf("30");
		int ranged = text.indexOf("20");
		int melee = text.indexOf("10");
		int special = text.indexOf("50");
		assertTrue("magic first, got order " + text, magic < ranged);
		assertTrue("ranged before melee", ranged < melee);
		assertTrue("specials last", melee < special);
	}

	// a hybrid is prayed against as its prayStyle, so it sorts where its icon says it belongs
	@Test
	public void hybridsSortByWhatTheyArePrayedAgainst()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Arrays.asList(
			new Attack("magic", 40, 40, 4, 4, "melee", null, null, null),
			new Attack("magic", 30, 30, 4, 4, null, null, null, null)));
		List<String> text = labelTexts(build(npc(9031, "Bloodveld", 76, c),
			new TrackerStore(mock(ConfigManager.class), new Gson())));
		assertTrue("the pure magic attack comes before the magic-melee one",
			text.indexOf("30") < text.indexOf("40"));
	}

	@Test
	public void conditionalMaxHitsHaveNoLegendRowUnderTheTable()
	{
		Attack a = new Attack("magic", 15, 15, 4, 4, null, Collections.singletonList(
			new MaxHitVariant("Insulated boots", "GEAR", 8, 8)), null, null);
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(a));
		List<String> text = labelTexts(build(npc(9032, "Killerwatt", 55, c),
			new TrackerStore(mock(ConfigManager.class), new Gson())));
		assertTrue("the headline still shows the conditional", text.contains("8 (15)"));
		assertFalse("no legend row below the table", text.stream()
			.anyMatch(t -> t != null && t.contains("unprotected")));
	}

	@Test
	public void curatorNotesShowOnHoverOverTheAttackIcon()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(
			new Attack("magic", 15, 15, 4, 4, null, null, "Only casts below 50% hitpoints", null)));
		SourceResultView view = build(npc(9033, "Noted", 100, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(1, countTooltips(view, "<html>Only casts below 50% hitpoints</html>"));
	}

	/**
	 * A Saradomin wizard poisons with its melee. That belongs beside the damage it deals, not in
	 * a paragraph somewhere else.
	 */
	@Test
	public void attackEffectsAppearOnTheMaxHitTooltip()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(
			new Attack("crush", 6, 6, 4, 4, null, null, null,
				Arrays.asList("POISON", "DRAIN_PRAYER"))));
		SourceResultView view = build(npc(9040, "Saradomin wizard", 60, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(1, countTooltips(view,
			"<html>May inflict poison<br>May drain Prayer</html>"));
	}

	@Test
	public void venomAndDiseaseReadAsThemselves()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Arrays.asList(
			new Attack("magic", 20, 20, 4, 4, null, null, null,
				Collections.singletonList("VENOM")),
			new Attack("ranged", 10, 10, 4, 4, null, null, null,
				Collections.singletonList("DISEASE"))));
		SourceResultView view = build(npc(9041, "Nasty", 100, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(1, countTooltips(view, "<html>May inflict venom</html>"));
		assertEquals(1, countTooltips(view, "<html>May inflict disease</html>"));
	}

	@Test
	public void effectsSitAlongsideTheConditionalBreakdown()
	{
		Attack a = new Attack("magic", 15, 15, 4, 4, null, Collections.singletonList(
			new MaxHitVariant("Insulated boots", "GEAR", 8, 8)), null,
			Collections.singletonList("POISON"));
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(a));
		SourceResultView view = build(npc(9042, "Both", 55, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(1, countTooltips(view,
			"<html>15 unprotected<br>8 with Insulated boots<br>May inflict poison</html>"));
	}

	@Test
	public void unconditionalMaxHitNumberHasNoTooltip()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(
			new Attack("stab", 7, 7, 4, 4, null, null, null, null)));
		SourceResultView view = build(npc(9023, "Plain", 20, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(0, countTooltips(view, "<html>7 unprotected</html>"));
	}

}
