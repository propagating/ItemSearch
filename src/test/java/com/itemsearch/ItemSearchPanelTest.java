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
package com.itemsearch;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import okhttp3.OkHttpClient;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.data.WikiRefreshService;
import com.itemsearch.map.SpawnMapMarker;
import com.itemsearch.tracker.TrackerStore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ItemSearchPanelTest
{
	private ItemSearchPanel newPanel(BundledDataSource ds)
	{
		return newPanel(ds, mock(ItemManager.class));
	}

	private ItemSearchPanel newPanel(BundledDataSource ds, ItemManager itemManager)
	{
		return new ItemSearchPanel(
			ds,
			mock(TrackerStore.class),
			itemManager,
			new OkHttpClient(),
			mock(WikiRefreshService.class),
			mock(SpawnMapMarker.class),
			mock(ClientThread.class),
			mock(ItemSearchConfig.class),
			mock(SpriteManager.class));
	}

	private BundledDataSource loadedTestData()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json", "/com/itemsearch/shops-test.json");
		ds.load();
		return ds;
	}

	private <T> T field(ItemSearchPanel panel, String name, Class<T> type) throws Exception
	{
		Field f = ItemSearchPanel.class.getDeclaredField(name);
		f.setAccessible(true);
		return type.cast(f.get(panel));
	}

	private void invokeRebuild(ItemSearchPanel panel) throws Exception
	{
		Method m = ItemSearchPanel.class.getDeclaredMethod("rebuildResults");
		m.setAccessible(true);
		m.invoke(panel);
	}

	// Regression: search must be usable immediately after construction, without
	// waiting on the background onDataReady() callback. A disabled-until-callback
	// gate leaves search permanently dead if that callback never takes effect.
	@Test
	public void searchIsUsableWithoutWaitingForCallback() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		assertTrue("search field must be enabled without onDataReady()", search.isEnabled());
	}

	@Test
	public void searchRendersResultRowsForAMatch() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		JPanel results = field(panel, "results", JPanel.class);

		search.setText("goblin");
		invokeRebuild(panel);
		assertTrue("expected at least one result row for 'goblin'", results.getComponentCount() >= 1);
	}

	// Regression: the results list must live inside a vertically-scrolling pane so
	// long result sets aren't clipped below the sidebar (which reads as "missing").
	@Test
	public void resultsListScrollsVertically() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JPanel results = field(panel, "results", JPanel.class);

		JScrollPane scroll = enclosingScrollPane(results);
		assertNotNull("results must be inside a JScrollPane", scroll);
		assertEquals(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, scroll.getVerticalScrollBarPolicy());
		assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
	}

	@Test
	public void searchRendersShopRowsWithShopBadge() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		JPanel results = field(panel, "results", JPanel.class);

		search.setText("zaff");
		invokeRebuild(panel);

		boolean foundBadge = false;
		for (Component row : results.getComponents())
		{
			for (Component c : ((Container) row).getComponents())
			{
				if (c instanceof JLabel && "SHOP".equals(((JLabel) c).getText()))
				{
					foundBadge = true;
				}
			}
		}
		assertTrue("expected a SHOP-badged result row for 'zaff'", foundBadge);
	}

	// the badge text of the first result row whose label text contains the needle
	private String badgeForResultNamed(JPanel results, String needle)
	{
		for (Component row : results.getComponents())
		{
			if (!(row instanceof Container))
			{
				continue;
			}
			String badge = null;
			boolean matched = false;
			for (Component c : ((Container) row).getComponents())
			{
				if (c instanceof JLabel)
				{
					String text = ((JLabel) c).getText();
					if (badge == null)
					{
						badge = text;
					}
					else if (text != null && text.contains(needle))
					{
						matched = true;
					}
				}
			}
			if (matched)
			{
				return badge;
			}
		}
		return null;
	}

	@Test
	public void resultBadgesReflectTheSourceType() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		JPanel results = field(panel, "results", JPanel.class);

		search.setText("goblin");
		invokeRebuild(panel);
		assertEquals("NPC", badgeForResultNamed(results, "Goblin"));

		search.setText("lunar chest");
		invokeRebuild(panel);
		assertEquals("CHEST", badgeForResultNamed(results, "Lunar Chest"));
		// a chest has no combat level; "(lvl 0)" would be noise
		assertEquals("Lunar Chest", labelTextContaining(results, "Lunar Chest"));

		search.setText("master farmer");
		invokeRebuild(panel);
		assertEquals("STEAL", badgeForResultNamed(results, "Master Farmer"));
	}

	// A long badge used to eat the width of the name beside it, clipping the source name.
	// Reserving one badge column keeps every name starting at the same x.
	@Test
	public void badgeColumnIsFixedWidthSoNamesAreNotClipped() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		JPanel results = field(panel, "results", JPanel.class);

		search.setText("a"); // matches every source type at once
		invokeRebuild(panel);

		java.util.Set<Integer> widths = new java.util.HashSet<>();
		java.util.Set<String> badges = new java.util.HashSet<>();
		for (Component row : results.getComponents())
		{
			if (!(row instanceof Container))
			{
				continue;
			}
			for (Component c : ((Container) row).getComponents())
			{
				if (c instanceof JLabel && ((java.awt.BorderLayout) ((Container) row).getLayout())
					.getConstraints(c) == java.awt.BorderLayout.WEST)
				{
					widths.add(c.getPreferredSize().width);
					badges.add(((JLabel) c).getText());
				}
			}
		}
		assertTrue("expected several badge kinds in this result set", badges.size() > 1);
		assertEquals("every badge must reserve the same width, got " + badges,
			1, widths.size());
	}

	private String labelTextContaining(Container root, String needle)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JLabel && ((JLabel) c).getText() != null
				&& ((JLabel) c).getText().contains(needle))
			{
				return ((JLabel) c).getText();
			}
			if (c instanceof Container)
			{
				String found = labelTextContaining((Container) c, needle);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static JButton backButton(Container root)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && "< Back".equals(((JButton) c).getText()))
			{
				return (JButton) c;
			}
			if (c instanceof Container)
			{
				JButton found = backButton((Container) c);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static boolean showing(Container root, String text)
	{
		return collectText(root).stream().anyMatch(t -> t != null && t.contains(text));
	}

	private static java.util.List<String> collectText(Container root)
	{
		java.util.List<String> out = new java.util.ArrayList<>();
		for (Component c : root.getComponents())
		{
			if (c instanceof javax.swing.JTextArea)
			{
				out.add(((javax.swing.JTextArea) c).getText());
			}
			if (c instanceof JLabel)
			{
				out.add(((JLabel) c).getText());
			}
			if (c instanceof Container)
			{
				out.addAll(collectText((Container) c));
			}
		}
		return out;
	}

	/** Back should retrace the path taken, not jump to search from wherever you are. */
	@Test
	public void backReturnsToThePreviousPageNotTheSearch() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		panel.showSource(loadedTestData().sourceById(3031)); // Goblin
		assertTrue("the source page is showing", showing(panel, "Goblin"));

		// step into one of its drops, then back out again
		panel.showItemForTest(new com.itemsearch.model.ItemMatch(1205, "Bronze dagger"));
		assertTrue("the item page is showing", showing(panel, "Bronze dagger"));

		backButton(panel).doClick();
		assertTrue("back must land on the source page again", showing(panel, "Goblin"));

		backButton(panel).doClick();
		assertNull("a second back leaves the detail views entirely", backButton(panel));
	}

	@Test
	public void typingASearchResetsToTheResultsPage() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		panel.showSource(loadedTestData().sourceById(3031));
		assertNotNull("a detail page is open", backButton(panel));

		search.setText("zulrah");
		invokeRebuild(panel);

		assertNull("a new search clears the back button", backButton(panel));
		assertTrue("and shows the results", showing(panel, "Zulrah"));
	}

	/**
	 * A one-letter query matches thousands of sources and items. Building a row for every one
	 * froze the client, and Back froze it again by re-laying out the same panel.
	 */
	@Test
	public void aBroadSearchIsCappedRatherThanBuildingEveryRow() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		JPanel results = field(panel, "results", JPanel.class);

		search.setText("o"); // matches most of the fixture
		invokeRebuild(panel);

		int cap = 100;
		assertTrue("a broad search must not render every match, got "
				+ results.getComponentCount(),
			results.getComponentCount() <= cap + 1);
	}

	@Test
	public void narrowingTheSearchStartsFromTheTopAgain() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		JPanel results = field(panel, "results", JPanel.class);

		search.setText("o");
		invokeRebuild(panel);
		search.setText("goblin");
		invokeRebuild(panel);

		assertTrue("a new query shows its own matches", showing(results, "Goblin"));
		assertTrue("and only those", results.getComponentCount() < 10);
	}

	/** The empty search page is worth browsing rather than a bare instruction. */
	@Test
	public void theDefaultPageListsSourcesToBrowse() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JPanel results = field(panel, "results", JPanel.class);
		invokeRebuild(panel);

		assertTrue("the empty search browses the data", results.getComponentCount() > 1);
		assertTrue("and names something real", showing(results, "Goblin"));
	}

	// clicks the browse filter button with the given label
	private void clickFilter(ItemSearchPanel panel, String label) throws Exception
	{
		JPanel bar = field(panel, "filterBar", JPanel.class);
		for (Component c : bar.getComponents())
		{
			if (c instanceof JButton && label.equals(((JButton) c).getText()))
			{
				((JButton) c).doClick();
				return;
			}
		}
		throw new AssertionError("no filter button labelled " + label);
	}

	/**
	 * One undifferentiated list of every source and item is not browsable. Picking a kind narrows
	 * it to that kind and nothing else.
	 */
	@Test
	public void aBrowseFilterNarrowsTheListToOneKind() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JPanel results = field(panel, "results", JPanel.class);
		invokeRebuild(panel);
		assertTrue("unfiltered browsing shows a chest", showing(results, "Lunar Chest"));

		clickFilter(panel, "NPCs");
		assertTrue("the NPC filter keeps monsters", showing(results, "Goblin"));
		assertFalse("and drops everything that is not one", showing(results, "Lunar Chest"));

		clickFilter(panel, "Chests");
		assertTrue(showing(results, "Lunar Chest"));
		assertFalse(showing(results, "Goblin"));

		clickFilter(panel, "All");
		assertTrue(showing(results, "Goblin"));
		assertTrue(showing(results, "Lunar Chest"));
	}

	/** The Items filter browses what drops rather than what drops it, and shows each item's icon. */
	@Test
	public void theItemsFilterListsItemsWithTheirIcons() throws Exception
	{
		ItemManager itemManager = mock(ItemManager.class);
		when(itemManager.getImage(anyInt()))
			.thenReturn(new AsyncBufferedImage(mock(ClientThread.class), 16, 16, BufferedImage.TYPE_INT_ARGB));
		ItemSearchPanel panel = newPanel(loadedTestData(), itemManager);
		JPanel results = field(panel, "results", JPanel.class);
		clickFilter(panel, "Items");

		assertTrue("items are listed by name", showing(results, "Bronze dagger"));
		assertFalse("and sources are not", showing(results, "Goblin (lvl"));

		int withIcons = 0;
		for (Component row : results.getComponents())
		{
			for (JLabel l : allLabels((Container) row))
			{
				if (l.getIcon() != null)
				{
					withIcons++;
				}
			}
		}
		assertTrue("every item row carries its in-game icon, got " + withIcons,
			withIcons >= results.getComponentCount() - 1);
	}

	/** The filters narrow the browse list, so they have no business on a search or a detail page. */
	@Test
	public void theFilterBarBelongsToBrowsingOnly() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JPanel bar = field(panel, "filterBar", JPanel.class);
		invokeRebuild(panel);
		assertTrue("shown while browsing", bar.isVisible());

		JTextField search = field(panel, "search", JTextField.class);
		search.setText("goblin");
		invokeRebuild(panel);
		assertFalse("hidden while searching", bar.isVisible());

		search.setText("");
		invokeRebuild(panel);
		assertTrue("and back when the search is cleared", bar.isVisible());
	}

	private static List<JLabel> allLabels(Container root)
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
				out.addAll(allLabels((Container) c));
			}
		}
		return out;
	}

	private JScrollPane enclosingScrollPane(Component c)
	{
		Container p = c.getParent();
		while (p != null && !(p instanceof JScrollPane))
		{
			p = p.getParent();
		}
		return (JScrollPane) p;
	}
}
