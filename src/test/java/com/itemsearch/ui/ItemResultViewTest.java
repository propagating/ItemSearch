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
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import javax.swing.JButton;
import static org.mockito.Mockito.when;
import com.itemsearch.data.DropDataService;
import com.itemsearch.model.DropSource;

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

	private static List<String> labelTexts(Container root)
	{
		List<String> out = new ArrayList<>();
		for (JLabel l : collect(root, JLabel.class))
		{
			out.add(l.getText());
		}
		return out;
	}

	private static ClientThread immediate()
	{
		ClientThread ct = mock(ClientThread.class);
		doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(ct).invoke(any(Runnable.class));
		return ct;
	}

	// a bronze dagger as the client describes it: value 10, tradeable, 0.453 kg, a weapon
	private static ItemManager dagger(boolean equipable)
	{
		ItemManager im = mock(ItemManager.class);
		ItemComposition c = mock(ItemComposition.class);
		when(c.getPrice()).thenReturn(10);
		when(c.getHaPrice()).thenReturn(6);
		when(c.isMembers()).thenReturn(false);
		when(c.isTradeable()).thenReturn(true);
		when(c.isGeTradeable()).thenReturn(true);
		when(c.isStackable()).thenReturn(false);
		when(im.getItemComposition(BRONZE_DAGGER)).thenReturn(c);
		when(im.getItemPrice(BRONZE_DAGGER)).thenReturn(37);
		ItemEquipmentStats eq = ItemEquipmentStats.builder().slot(3).astab(4).aslash(2).acrush(-4)
			.amagic(1).arange(0).dstab(0).dslash(1).dcrush(0).dmagic(1).drange(0).str(3).rstr(0)
			.mdmg(0).prayer(0).aspeed(4).isTwoHanded(false).build();
		when(im.getItemStats(BRONZE_DAGGER)).thenReturn(new ItemStats(equipable, 0.453, 125,
			equipable ? eq : null));
		return im;
	}

	private ItemResultView factsView(ItemManager im, ClientThread clientThread)
	{
		ItemResultView view = new ItemResultView(new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			loadedTestData(), new TrackerStore(mock(ConfigManager.class), new Gson()), im, clientThread,
			() ->
			{
			}, s ->
			{
			}, s ->
			{
			});
		// the facts are filled in on the event thread once the client thread has read them
		try
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
		return view;
	}

	/**
	 * An item page had a name and two lists. A monster page says what the thing is; the item
	 * page now says what the item is worth, in every sense the game prices it.
	 */
	@Test
	public void theFactsCardShowsWhatTheItemIsWorth()
	{
		List<String> text = labelTexts(factsView(dagger(true), immediate()));
		assertTrue(text.contains("FACTS"));
		assertTrue(text.contains("Value") && text.contains("10"));
		assertTrue(text.contains("Low alch") && text.contains("4"));
		assertTrue(text.contains("High alch") && text.contains("6"));
		assertTrue(text.contains("Vendor"));
		assertTrue(text.contains("GE") && text.contains("37"));
		assertTrue(text.contains("Buy limit") && text.contains("125"));
		assertTrue(text.contains("Weight") && text.contains("0.5 kg"));
		assertTrue(text.contains("Tradeable"));
		assertFalse("the flags line names only what is true", text.contains("Members"));
	}

	@Test
	public void theEquipmentCardAppearsOnlyForWearableItems()
	{
		List<String> worn = labelTexts(factsView(dagger(true), immediate()));
		assertTrue(worn.contains("EQUIPMENT"));
		assertTrue(worn.contains("Weapon"));
		assertTrue(worn.contains("Stab") && worn.contains("+4"));
		assertTrue(worn.contains("Crush") && worn.contains("-4"));
		assertTrue(worn.contains("Speed"));
		assertTrue(worn.contains("Strength") && worn.contains("+3"));
		assertFalse(labelTexts(factsView(dagger(false), immediate())).contains("EQUIPMENT"));
	}

	@Test
	public void anExchangeUntradeableItemShowsNoExchangeRows()
	{
		ItemManager im = dagger(false);
		when(im.getItemComposition(BRONZE_DAGGER).isGeTradeable()).thenReturn(false);
		List<String> text = labelTexts(factsView(im, immediate()));
		assertTrue(text.contains("Value"));
		assertFalse(text.contains("GE"));
		assertFalse(text.contains("Buy limit"));
	}

	@Test
	public void theExamineLineSitsUnderTheNameWhenTheDataHasOne()
	{
		DropDataService data = mock(DropDataService.class);
		when(data.sourcesForItem(BRONZE_DAGGER)).thenReturn(Collections.emptyList());
		when(data.shopsForItem(BRONZE_DAGGER)).thenReturn(Collections.emptyList());
		when(data.examineFor(BRONZE_DAGGER)).thenReturn("A dagger made of bronze.");
		ItemResultView view = new ItemResultView(new ItemMatch(BRONZE_DAGGER, "Bronze dagger"), data,
			new TrackerStore(mock(ConfigManager.class), new Gson()), mock(ItemManager.class),
			() ->
			{
			}, s ->
			{
			}, s ->
			{
			});
		assertTrue(collect(view, JTextArea.class).stream()
			.anyMatch(a -> "A dagger made of bronze.".equals(a.getText())));

		when(data.examineFor(BRONZE_DAGGER)).thenReturn(null);
		ItemResultView plain = new ItemResultView(new ItemMatch(BRONZE_DAGGER, "Bronze dagger"), data,
			new TrackerStore(mock(ConfigManager.class), new Gson()), mock(ItemManager.class),
			() ->
			{
			}, s ->
			{
			}, s ->
			{
			});
		assertTrue(collect(plain, JTextArea.class).isEmpty());
	}

	/** The read happens on the client thread; until it runs the page is complete without it. */
	@Test
	public void aClientThreadThatNeverRunsLeavesThePageUsable()
	{
		List<String> text = labelTexts(factsView(dagger(true), mock(ClientThread.class)));
		assertFalse(text.contains("FACTS"));
		assertTrue(text.contains("Bronze dagger"));
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
		assertTrue("but each opening the Loot Tracker reports is an attempt",
			text.contains("Attempts since drop"));
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

	/**
	 * Coins has thousands of sources. Building them all is slow, and tearing them all down when
	 * the player opens one of them is slower still: AWT recomputes clip shapes for every sibling of
	 * every removed component beside the game canvas, which is quadratic in the sibling count and
	 * froze the sidebar for seconds. A page at a time keeps both bounded.
	 */
	@Test
	public void aLongSourceListIsShownAPageAtATime()
	{
		DropDataService data = mock(DropDataService.class);
		List<DropSource> many = new ArrayList<>();
		for (int i = 0; i < 100; i++)
		{
			many.add(new DropSource(BRONZE_DAGGER, "Bronze dagger", 0.5, 1, 0, false, true, null, null, 0, null));
		}
		when(data.sourcesForItem(BRONZE_DAGGER)).thenReturn(many);
		when(data.shopsForItem(BRONZE_DAGGER)).thenReturn(Collections.emptyList());
		ItemResultView view = new ItemResultView(new ItemMatch(BRONZE_DAGGER, "Bronze dagger"), data,
			new TrackerStore(mock(ConfigManager.class), new Gson()), mock(ItemManager.class),
			() ->
			{
			},
			s ->
			{
			},
			s ->
			{
			});

		assertEquals(40, cards(view));
		JButton more = showMore(view);
		assertEquals("Show 40 more", more.getText());
		more.doClick();
		assertEquals(80, cards(view));
		assertEquals("Show 20 more", more.getText());
		more.doClick();
		assertEquals(100, cards(view));
		assertNull("nothing left to show", showMore(view));
	}

	@Test
	public void aShortSourceListHasNoPaging()
	{
		ItemResultView view = build(loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), new AtomicReference<>());
		assertNull(showMore(view));
	}

	// every source card carries exactly one text area, its name
	private static int cards(ItemResultView view)
	{
		return collect(view, JTextArea.class).size();
	}

	private static JButton showMore(ItemResultView view)
	{
		return collect(view, JButton.class).stream()
			.filter(b -> b.getText().startsWith("Show ")).findFirst().orElse(null);
	}
}
