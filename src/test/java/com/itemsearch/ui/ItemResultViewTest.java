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
import javax.swing.JTextArea;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.NpcData;
import com.itemsearch.tracker.TrackerStore;
import static org.junit.Assert.assertEquals;
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

	private ItemResultView build(BundledDataSource ds, TrackerStore store, AtomicReference<NpcData> clicked)
	{
		return new ItemResultView(
			new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			ds, store, mock(ItemManager.class),
			() ->
			{
			},
			clicked::set);
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

	@Test
	public void obtainedCountRendersAndRefreshes()
	{
		TrackerStore store = new TrackerStore(mock(ConfigManager.class), new Gson());
		store.recordKill(GOBLIN, Collections.singleton(BRONZE_DAGGER),
			Collections.singletonList(BRONZE_DAGGER));
		store.recordKill(GOBLIN, Collections.singleton(BRONZE_DAGGER),
			Collections.singletonList(BRONZE_DAGGER));

		ItemResultView view = build(loadedTestData(), store, new AtomicReference<>());
		view.refresh(store);

		List<JLabel> labels = collect(view, JLabel.class);
		boolean hasGotRow = labels.stream().anyMatch(l -> "Got".equals(l.getText()));
		boolean hasCountTwo = labels.stream().anyMatch(l -> "2".equals(l.getText()));
		assertTrue("a 'Got' row must render", hasGotRow);
		assertTrue("obtained count of 2 must render", hasCountTwo);
	}
}
