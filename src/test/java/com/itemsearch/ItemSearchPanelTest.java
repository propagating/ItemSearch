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
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import okhttp3.OkHttpClient;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.data.WikiRefreshService;
import com.itemsearch.map.SpawnMapMarker;
import com.itemsearch.tracker.TrackerStore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ItemSearchPanelTest
{
	private ItemSearchPanel newPanel(BundledDataSource ds)
	{
		return new ItemSearchPanel(
			ds,
			mock(TrackerStore.class),
			mock(ItemManager.class),
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
		assertEquals("PICKPOCKET", badgeForResultNamed(results, "Master Farmer"));
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
