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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.data.WikiRefreshService;
import com.itemsearch.map.SpawnMapMarker;
import com.itemsearch.respawn.RespawnTracker;
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
			mock(WikiRefreshService.class),
			mock(SpawnMapMarker.class),
			mock(RespawnTracker.class),
			mock(ClientThread.class),
			mock(ItemSearchConfig.class),
			mock(SpriteManager.class));
	}

	private BundledDataSource loadedTestData()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/drops-test.json");
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
