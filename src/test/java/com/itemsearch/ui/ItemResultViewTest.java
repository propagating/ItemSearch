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
import com.itemsearch.model.NpcData;
import com.itemsearch.tracker.TrackerStore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
			"/com/itemsearch/drops-test.json");
		ds.load();
		return ds;
	}

	private BundledDataSource loadedTestDataWithShops()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/drops-test.json",
			"/com/itemsearch/shops-test.json");
		ds.load();
		return ds;
	}

	private ItemResultView build(BundledDataSource ds, TrackerStore store, AtomicReference<NpcData> clicked)
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
		AtomicReference<NpcData> clicked = new AtomicReference<>();
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

		MouseEvent click = new MouseEvent(goblin, MouseEvent.MOUSE_CLICKED, 0L, 0, 0, 0, 1, false);
		for (MouseListener l : goblin.getMouseListeners())
		{
			l.mouseClicked(click);
		}
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
	public void droppedByHeaderSeparatesMonstersOnlyWhenShopsPresent()
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
		assertTrue("expected DROPPED BY header when shops precede monster sources",
			hasLabel(withShops, "DROPPED BY"));

		// without shop data the view keeps its original headerless monster list
		ItemResultView withoutShops = build(loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), new AtomicReference<>());
		assertFalse("no DROPPED BY header expected without a buy-from section",
			hasLabel(withoutShops, "DROPPED BY"));
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
				MouseEvent click = new MouseEvent(a, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false);
				for (MouseListener l : a.getMouseListeners())
				{
					l.mouseClicked(click);
				}
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
}
