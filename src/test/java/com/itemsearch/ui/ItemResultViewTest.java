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
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.SourceData;
import com.itemsearch.tracker.TrackerStore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;

public class ItemResultViewTest
{
	private static final int BRONZE_DAGGER = 1205; // dropped by Goblin and Zulrah in the test data
	private static final int GOBLIN = 3031;

	private BundledDataSource loadedTestData()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		ds.load();
		return ds;
	}

	private BundledDataSource loadedTestDataWithShops()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json",
			"/com/itemsearch/shops-test.json");
		ds.load();
		return ds;
	}

	private ItemResultView build(BundledDataSource ds, TrackerStore store, AtomicReference<SourceData> clicked)
	{
		return new ItemResultView(
			new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			ds, store, mock(ItemManager.class),
			() ->
			{
			},
			clicked::set,
			s ->
			{
			});
	}

	private static <T> List<T> collect(Container root, Class<T> type)
	{
		List<T> out = new ArrayList<>();
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				out.add(type.cast(c));
			}
			if (c instanceof Container)
			{
				out.addAll(collect((Container) c, type));
			}
		}
		return out;
	}

	@Test
	public void npcNamesRenderInAlphabeticalOrder()
	{
		ItemResultView view = build(loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), new AtomicReference<>());

		// wrapLabel (JTextArea) is used only for NPC names; traversal order == render order
		List<String> names = new ArrayList<>();
		for (JTextArea a : collect(view, JTextArea.class))
		{
			names.add(a.getText());
		}
		assertEquals(Arrays.asList("Goblin", "Zulrah"), names);
	}

	@Test
	public void clickingNpcNameFiresCallbackWithThatNpc()
	{
		AtomicReference<SourceData> clicked = new AtomicReference<>();
		ItemResultView view = build(loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), clicked);

		JTextArea goblin = null;
		for (JTextArea a : collect(view, JTextArea.class))
		{
			if ("Goblin".equals(a.getText()))
			{
				goblin = a;
			}
		}
		assertNotNull("Goblin name label must be present", goblin);

		press(goblin);
		assertNotNull("clicking an NPC must fire onNpcSelected", clicked.get());
		assertEquals(GOBLIN, clicked.get().getId());
	}

	// The item view is a scannable list (an item like uncut sapphire has dozens of
	// sources); each card shows only Expected and Kills since drop — the full stats
	// live in the NPC view behind the click-through.
	@Test
	public void sourceCardsShowOnlyExpectedAndKillsSinceDrop()
	{
		ItemResultView view = build(loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), new AtomicReference<>());

		List<JLabel> labels = collect(view, JLabel.class);
		assertTrue("an 'Expected' row must render",
			labels.stream().anyMatch(l -> "Expected".equals(l.getText())));
		assertTrue("a 'Kills since drop' row must render",
			labels.stream().anyMatch(l -> "Kills since drop".equals(l.getText())));
		for (String removed : Arrays.asList("Got", "10% chance", "50% chance", "90% chance",
			"Still this dry", "KILLS TO OBTAIN", "YOUR DRY STREAK"))
		{
			assertFalse("compact card must not render '" + removed + "'",
				labels.stream().anyMatch(l -> removed.equals(l.getText())));
		}
		assertTrue("compact card must not render the reset button",
			collect(view, javax.swing.JButton.class).stream()
				.noneMatch(b -> "Reset dry counter".equals(b.getText())));
	}

	@Test
	public void killsSinceDropRefreshesInPlace()
	{
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		ItemResultView view = build(loadedTestData(), store, new AtomicReference<>());

		store.recordKill(GOBLIN, Collections.emptySet(), Collections.singletonList(BRONZE_DAGGER));
		store.recordKill(GOBLIN, Collections.emptySet(), Collections.singletonList(BRONZE_DAGGER));
		store.recordKill(GOBLIN, Collections.emptySet(), Collections.singletonList(BRONZE_DAGGER));
		view.refresh(store);

		assertEquals("3", killsSinceDropValue(view));
	}

	// the value JLabel sharing a statRow panel with the "Kills since drop" name label
	private static String killsSinceDropValue(ItemResultView view)
	{
		for (Container panel : collect(view, JPanel.class))
		{
			List<JLabel> inRow = collect(panel, JLabel.class);
			if (inRow.size() == 2 && "Kills since drop".equals(inRow.get(0).getText()))
			{
				return inRow.get(1).getText();
			}
		}
		return null;
	}

	@Test
	public void pressingAnywhereOnASourceCardFiresNpcCallback()
	{
		AtomicReference<SourceData> clicked = new AtomicReference<>();
		ItemResultView view = build(loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), clicked);

		JPanel card = cardWithTextArea(view, "Goblin");
		assertNotNull(card);
		assertTrue("the whole source card must carry a mouse listener",
			card.getMouseListeners().length > 0);
		press(card);
		assertEquals(GOBLIN, clicked.get().getId());
	}

	@Test
	public void pressingAnywhereOnAShopCardFiresShopCallback()
	{
		AtomicReference<com.itemsearch.model.ShopData> clicked = new AtomicReference<>();
		ItemResultView view = new ItemResultView(
			new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			loadedTestDataWithShops(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			clicked::set);

		JPanel card = cardWithTextArea(view, "Zaff's Superior Staffs");
		assertNotNull(card);
		assertTrue("the whole shop card must carry a mouse listener",
			card.getMouseListeners().length > 0);
		press(card);
		assertEquals("Zaff's Superior Staffs", clicked.get().getName());
	}

	// the card panel whose DIRECT children include a wrapLabel text area with the given text
	private static JPanel cardWithTextArea(Container root, String text)
	{
		for (JPanel p : collect(root, JPanel.class))
		{
			for (Component c : p.getComponents())
			{
				if (c instanceof JTextArea && text.equals(((JTextArea) c).getText()))
				{
					return p;
				}
			}
		}
		return null;
	}

	private static void press(Component c)
	{
		MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false);
		for (MouseListener l : c.getMouseListeners())
		{
			l.mousePressed(e);
		}
	}

	@Test
	public void buyFromSectionListsShopsCheapestFirstBeforeMonsters()
	{
		ItemResultView view = new ItemResultView(
			new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			loadedTestDataWithShops(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			shop ->
			{
			});
		// wrapLabel text areas render shops first (sorted by price, 30 < 50), then monsters
		List<String> names = new ArrayList<>();
		for (JTextArea a : collect(view, JTextArea.class))
		{
			names.add(a.getText());
		}
		assertEquals(Arrays.asList("Zaff's Superior Staffs", "Aaron's Archery Appendages",
			"Goblin", "Zulrah"), names);
	}

	@Test
	public void obtainedFromHeaderSeparatesSourcesOnlyWhenShopsPresent()
	{
		ItemResultView withShops = new ItemResultView(
			new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			loadedTestDataWithShops(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			shop ->
			{
			});
		assertTrue("expected OBTAINED FROM header when shops precede other sources",
			hasLabel(withShops, "OBTAINED FROM"));

		// without shop data the view keeps its original headerless monster list
		ItemResultView withoutShops = build(loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), new AtomicReference<>());
		assertFalse("no OBTAINED FROM header expected without a buy-from section",
			hasLabel(withoutShops, "OBTAINED FROM"));
	}

	private static boolean hasLabel(ItemResultView view, String text)
	{
		for (JLabel l : collect(view, JLabel.class))
		{
			if (text.equals(l.getText()))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void clickingShopNameFiresCallback()
	{
		AtomicReference<com.itemsearch.model.ShopData> clicked =
			new AtomicReference<>();
		ItemResultView view = new ItemResultView(
			new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			loadedTestDataWithShops(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			clicked::set);
		// JTextArea has Swing-installed default MouseListeners (caret, handler) ahead of ours,
		// so (as with clickingNpcNameFiresCallbackWithThatNpc above) fire all of them rather
		// than assuming index 0 is ours.
		for (JTextArea a : collect(view, JTextArea.class))
		{
			if ("Zaff's Superior Staffs".equals(a.getText()))
			{
				press(a);
			}
		}
		assertEquals("Zaff's Superior Staffs", clicked.get().getName());
	}

	@Test
	public void itemSoldOnlyWithZeroStockGetsNoBuyFromSection()
	{
		// Bronze arrow: only shop entry has stock 0 -> not purchasable -> no section at all
		ItemResultView view = new ItemResultView(
			new ItemMatch(882, "Bronze arrow"),
			loadedTestDataWithShops(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			shop ->
			{
			});
		for (JLabel l : collect(view, JLabel.class))
		{
			assertFalse("no BUY FROM header expected",
				"BUY FROM".equals(l.getText()));
		}
	}

	private static List<String> allText(Container root)
	{
		List<String> out = new ArrayList<>();
		for (JLabel l : collect(root, JLabel.class))
		{
			out.add(l.getText());
		}
		for (JTextArea t : collect(root, JTextArea.class))
		{
			out.add(t.getText());
		}
		return out;
	}

	@Test
	public void rewardSourceIsLabelledByTypeAndHasNoKillCounter()
	{
		// Eclipse atlatl comes only from the Lunar Chest (REWARD, 1/224) in the fixture
		ItemResultView view = new ItemResultView(
			new ItemMatch(29028, "Eclipse atlatl"),
			loadedTestData(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			shop ->
			{
			});
		List<String> text = allText(view);
		assertTrue("reward card is labelled by type instead of a combat level",
			text.contains("Reward  ·  1/224"));
		assertTrue("expected-attempts stat still applies to a chest", text.contains("Expected"));
		assertFalse("nothing is killed for a chest reward",
			text.contains("Kills since drop"));
	}

	@Test
	public void sourceSectionHeaderCoversEveryTypeNotJustDrops()
	{
		// Bronze dagger is both sold and dropped, so the disambiguating header renders
		ItemResultView view = build(loadedTestDataWithShops(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), new AtomicReference<>());
		assertTrue("header must not claim everything is a drop",
			allText(view).contains("OBTAINED FROM"));
	}

	// The bands are what makes a card expensive, and an item like Coins has thousands of cards.
	// They render only for cards the user has opened.
	@Test
	public void percentileBandsAreHiddenUntilACardIsExpanded()
	{
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		ItemResultView view = build(loadedTestData(), store, new AtomicReference<>());
		List<String> text = allText(view);
		assertFalse("collapsed cards must not render bands", text.contains("10% chance"));
		assertFalse(text.contains("50% chance"));
		assertFalse(text.contains("90% chance"));
		assertTrue("the expander must be offered", text.stream()
			.anyMatch(t -> t != null && t.contains("more")));
	}

	@Test
	public void expandingACardRevealsItsBandsAndRemembersTheChoice()
	{
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		ItemResultView view = build(loadedTestData(), store, new AtomicReference<>());

		JLabel toggle = expanderIn(cardWithTextArea(view, "Goblin"));
		assertNotNull("each source card needs its own expander", toggle);
		press(toggle);

		List<String> text = allText(view);
		assertTrue("expanding reveals the bands", text.contains("10% chance"));
		assertTrue(text.contains("50% chance"));
		assertTrue(text.contains("90% chance"));
		assertTrue("the choice is recorded for next time", store.isExpanded(BRONZE_DAGGER, GOBLIN));

		// a freshly built view for the same item restores the expansion
		ItemResultView reopened = build(loadedTestData(), store, new AtomicReference<>());
		assertTrue("expansion must survive rebuilding the view",
			allText(reopened).contains("10% chance"));
	}

	@Test
	public void expandingOneCardDoesNotNavigateAway()
	{
		AtomicReference<SourceData> clicked = new AtomicReference<>();
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		ItemResultView view = build(loadedTestData(), store, clicked);

		press(expanderIn(cardWithTextArea(view, "Goblin")));
		assertNull("pressing the expander must not open the source view", clicked.get());
	}

	// the expander control inside one source card
	private static JLabel expanderIn(Container card)
	{
		for (JLabel l : collect(card, JLabel.class))
		{
			if (l.getText() != null && (l.getText().contains("more") || l.getText().contains("less")))
			{
				return l;
			}
		}
		return null;
	}

	@Test
	public void killSourceKeepsLevelAndKillCounter()
	{
		ItemResultView view = build(loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), new AtomicReference<>());
		List<String> text = allText(view);
		assertTrue("kill sources still show their combat level",
			text.stream().anyMatch(t -> t != null && t.startsWith("Lvl 2  ·  ")));
		assertTrue("kill sources keep the dry counter", text.contains("Kills since drop"));
	}
}
