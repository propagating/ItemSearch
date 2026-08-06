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
		return new SourceResultView(npc, store, mock(SpriteManager.class), mock(ItemManager.class),
			new OkHttpClient(), mock(SpawnMapMarker.class), mock(ClientThread.class),
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
			Collections.singletonList(id), combat, SourceType.KILL, null, null, null);
	}

	private static CombatStats combat(List<String> attackTypes)
	{
		List<Attack> attacks = new ArrayList<>();
		for (String t : attackTypes)
		{
			attacks.add(new Attack(t, 5, 5, 4, 4, null, null));
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
			Collections.singletonList(new Attack("stab", 1, 3, 4, 6, null, null)));
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
			new Attack("magic", 15, 15, 4, 4, "ranged", null)));
		SourceResultView view = build(npc(9020, "Hybrid", 90, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		// the protection icon reflects the PRAY style (ranged), not the accuracy style
		assertEquals(1, countTooltips(view, "Protect from Missiles"));
		assertEquals(0, countTooltips(view, "Protect from Magic"));
		assertEquals(1, countTooltips(view, "Magic accuracy roll — prayed against as Ranged"));
	}

	@Test
	public void conditionalMaxHitsRenderBestFirstWithLegend()
	{
		Attack a = new Attack("magic", 15, 15, 4, 4, null, Collections.singletonList(
			new MaxHitVariant("Insulated boots", "GEAR", 8, 8)));
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(a));
		SourceResultView view = build(npc(9021, "Killerwatt", 55, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		List<String> text = labelTexts(view);
		assertTrue("headline shows best-case with base in parens", text.contains("8 (15)"));
		assertTrue("variant legend row shown", text.stream()
			.anyMatch(t -> t != null && t.contains("8 with Insulated boots")));
		assertTrue("unprotected base in legend", text.stream()
			.anyMatch(t -> t != null && t.contains("15 unprotected")));
	}

	@Test
	public void conditionalMaxHitNumberExplainsDifferenceInTooltip()
	{
		Attack a = new Attack("magic", 15, 15, 4, 4, null, Arrays.asList(
			new MaxHitVariant("Insulated boots", "GEAR", 8, 8),
			new MaxHitVariant("Antifire", "CONSUMABLE", 12, 12)));
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

	@Test
	public void unconditionalMaxHitNumberHasNoTooltip()
	{
		CombatStats c = new CombatStats(1, 1, 1, 1, 1, 5, Collections.singletonList(
			new Attack("stab", 7, 7, 4, 4, null, null)));
		SourceResultView view = build(npc(9023, "Plain", 20, c),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertEquals(0, countTooltips(view, "<html>7 unprotected</html>"));
	}

}
