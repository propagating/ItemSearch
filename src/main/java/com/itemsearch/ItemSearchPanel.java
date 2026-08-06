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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import okhttp3.OkHttpClient;
import com.itemsearch.data.DropDataService;
import com.itemsearch.data.WikiRefreshService;
import com.itemsearch.map.SpawnMapMarker;
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.SourceData;
import com.itemsearch.model.SourceType;
import com.itemsearch.model.ShopData;
import com.itemsearch.tracker.TrackerStore;
import com.itemsearch.ui.ItemResultView;
import com.itemsearch.ui.SourceResultView;
import com.itemsearch.ui.ResultRow;
import com.itemsearch.ui.ShopResultView;
import net.runelite.client.ui.PluginPanel;

class ItemSearchPanel extends PluginPanel
{
	private final DropDataService dataService;
	private final TrackerStore trackerStore;
	private final ItemManager itemManager;
	private final WikiRefreshService wikiRefreshService;
	private final SpawnMapMarker spawnMapMarker;
	private final ClientThread clientThread;
	private final ItemSearchConfig config;
	private final SpriteManager spriteManager;
	private final OkHttpClient okHttpClient;

	private final JTextField search = new JTextField();
	private final JPanel results = new JPanel();
	private final JScrollPane resultsScroll;
	private final JPanel content = new JPanel(new BorderLayout());
	private final JLabel hintLabel = new JLabel("Search for an NPC or item above.");
	private final Timer debounce;
	// the item view currently on screen, if any, so its dry counters can update live on kills
	private ItemResultView currentItemView;
	// the npc view currently on screen, if any, so its per-drop stats update live on kills
	private SourceResultView currentNpcView;

	ItemSearchPanel(DropDataService dataService, TrackerStore trackerStore, ItemManager itemManager,
		OkHttpClient okHttpClient, WikiRefreshService wikiRefreshService, SpawnMapMarker spawnMapMarker,
		ClientThread clientThread, ItemSearchConfig config,
		SpriteManager spriteManager)
	{
		super(false);
		this.okHttpClient = okHttpClient;
		this.dataService = dataService;
		this.trackerStore = trackerStore;
		this.itemManager = itemManager;
		this.wikiRefreshService = wikiRefreshService;
		this.spawnMapMarker = spawnMapMarker;
		this.clientThread = clientThread;
		this.config = config;
		this.spriteManager = spriteManager;

		setLayout(new BorderLayout());
		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));

		// same vertically-scrolling, top-anchored wrapper the detail views use
		resultsScroll = verticalScroll(results);

		debounce = new Timer(300, e -> rebuildResults());
		debounce.setRepeats(false);

		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				debounce.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				debounce.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				debounce.restart();
			}
		});

		content.add(resultsScroll, BorderLayout.CENTER);

		// Top padding pushes the search bar down off the sidebar edge so it does not
		// get lost against the toolbar; the search stays fixed above the scrolling
		// content while detail views swap in and out below it.
		JPanel container = new JPanel(new BorderLayout(0, 8));
		container.setBorder(new EmptyBorder(10, 8, 8, 8));
		container.add(search, BorderLayout.NORTH);
		container.add(content, BorderLayout.CENTER);
		add(container, BorderLayout.CENTER);

		// Search is usable immediately; the bundled data loads in the background
		// (see ItemSearchPlugin) and a search before it finishes simply returns no
		// matches. Do NOT gate the field on the background load callback — if that
		// callback ever failed to fire, search would be permanently dead.
		results.add(hintLabel);
	}

	// Called on the EDT once the background data load completes, to resolve any
	// query the user typed during the brief load window. Search works with or
	// without this callback; it is a best-effort refresh, not a gate.
	void onDataReady()
	{
		rebuildResults();
	}

	// what the player interacts with to get the loot, in their own words
	private static String badgeFor(SourceType type)
	{
		switch (type)
		{
			case REWARD:
				return "CHEST";
			case PICKPOCKET:
				return "STEAL";
			case GATHER:
				return "NODE";
			default:
				return "NPC";
		}
	}

	private void rebuildResults()
	{
		results.removeAll();
		String q = search.getText();
		if (q != null && !q.trim().isEmpty())
		{
			for (SourceData npc : dataService.searchSources(q))
			{
				// a chest or gathering node has no combat level; "(lvl 0)" would be noise
				String label = npc.getCombatLevel() > 0
					? npc.getName() + " (lvl " + npc.getCombatLevel() + ")"
					: npc.getName();
				results.add(new ResultRow(badgeFor(npc.getType()), label, () -> showNpc(npc)));
			}
			for (ShopData shop : dataService.searchShops(q))
			{
				results.add(new ResultRow("SHOP", shop.getName(), () -> showShop(shop)));
			}
			for (ItemMatch item : dataService.searchItems(q))
			{
				results.add(new ResultRow("ITEM", item.getItemName(), () -> showItem(item)));
			}
		}
		else
		{
			results.add(hintLabel);
		}
		results.revalidate();
		results.repaint();
	}

	private void showResults()
	{
		currentItemView = null;
		currentNpcView = null;
		spawnMapMarker.clear();
		content.removeAll();
		content.add(resultsScroll, BorderLayout.CENTER);
		content.revalidate();
		content.repaint();
	}

	private void showNpc(SourceData npc)
	{
		currentItemView = null;
		SourceResultView view = new SourceResultView(npc, trackerStore, spriteManager, itemManager, okHttpClient,
			spawnMapMarker, clientThread, config.groupDropsByTable(), this::showResults,
			item -> showItem(item, () -> showNpc(npc)));
		currentNpcView = view;
		showDetail(view);
		if (config.autoMarkSpawns())
		{
			spawnMapMarker.showSpawns(npc);
		}
		// fetch a fresher wiki image and push it into the view we just opened
		wikiRefreshService.refreshNpcImage(npc.getId(), npc.getName(), view::updateImage);
	}

	private void showShop(ShopData shop)
	{
		showShop(shop, this::showResults);
	}

	// onBack lets a shop opened from an item's buy-from list return to that item
	private void showShop(ShopData shop, Runnable onBack)
	{
		currentItemView = null;
		currentNpcView = null;
		ShopResultView view = new ShopResultView(shop, itemManager, onBack, line ->
			showItem(new ItemMatch(line.getItemId(), line.getItemName()),
				() -> showShop(shop, onBack)),
			spawnMapMarker::showShop);
		showDetail(view);
	}

	private void showItem(ItemMatch item)
	{
		showItem(item, this::showResults);
	}

	// onBack lets an item opened from an NPC's loot table return to that NPC instead of search
	private void showItem(ItemMatch item, Runnable onBack)
	{
		currentNpcView = null;
		currentItemView = new ItemResultView(item, dataService, trackerStore, itemManager, onBack,
			this::showNpc, shop -> showShop(shop, () -> showItem(item, onBack)));
		showDetail(currentItemView);
	}

	// Refresh whichever detail view is showing from the store (EDT-only). No-op unless an
	// item or npc view is showing; updates values in place so the scroll position is preserved.
	void refreshTracking()
	{
		if (currentItemView != null)
		{
			currentItemView.refresh(trackerStore);
		}
		if (currentNpcView != null)
		{
			currentNpcView.refresh(trackerStore);
		}
	}

	private void showDetail(JPanel view)
	{
		content.removeAll();
		// detail views can outgrow the sidebar. Anchor the view to a wrapper's NORTH before
		// scrolling it: a view placed directly in a viewport gets stretched to the viewport
		// height and never reports overflow, so no scrollbar appears and the lower rows are
		// clipped. NORTH keeps the view at its preferred height so the scroll pane scrolls.
		content.add(verticalScroll(view), BorderLayout.CENTER);
		content.revalidate();
		content.repaint();
	}

	// Wrap a panel in a vertically-scrolling pane. The wrapper tracks the viewport WIDTH
	// (so content is constrained to the sidebar and wraps instead of being clipped by the
	// no-horizontal-scrollbar policy) but not its HEIGHT (so overflow scrolls vertically).
	private static JScrollPane verticalScroll(JPanel view)
	{
		JPanel wrapper = new ScrollableWrapper();
		wrapper.add(view, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(wrapper,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	// A scroll view that fills the viewport width (forcing children to wrap within the
	// sidebar) while growing to its preferred height (so tall content scrolls vertically).
	private static final class ScrollableWrapper extends JPanel implements Scrollable
	{
		ScrollableWrapper()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return visible.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
