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
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
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
import com.itemsearch.ui.WikiImageLoader;
import com.itemsearch.ui.ShopResultView;
import net.runelite.client.ui.ColorScheme;
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
	// rendering thousands of rows at once stalls the sidebar, so the list grows on demand
	private static final int PAGE = 100;
	private final Timer debounce;
	// the item view currently on screen, if any, so its dry counters can update live on kills
	private ItemResultView currentItemView;
	// the npc view currently on screen, if any, so its per-drop stats update live on kills
	private SourceResultView currentNpcView;
	// pages behind the one on screen, most recent first. Back retraces the path taken rather
	// than jumping to search, so stepping npc -> drop -> npc returns where you came from.
	private final java.util.Deque<Runnable> history = new java.util.ArrayDeque<>();
	// how to redraw whatever is on screen, so it can be pushed onto the history when leaving
	private Runnable currentPage;
	// how many rows the list shows; "Show more" extends it, a new query resets it
	private int shownLimit = PAGE;
	private String lastQuery = "";
	// what the browse list is narrowed to when nothing has been typed
	private final JPanel filterBar = new JPanel(new java.awt.GridLayout(0, 3, 4, 4));
	private BrowseFilter browseFilter = BrowseFilter.ALL;

	/**
	 * The kinds of thing the browse list can be narrowed to. Browsing 2758 sources and every
	 * dropped item in one undifferentiated list is not browsing, it is scrolling.
	 */
	private enum BrowseFilter
	{
		ALL("All", null),
		NPC("NPCs", SourceType.KILL),
		CHEST("Chests", SourceType.REWARD),
		STEAL("Steal", SourceType.PICKPOCKET),
		NODE("Nodes", SourceType.GATHER),
		ITEM("Items", null);

		private final String label;
		private final SourceType type;

		BrowseFilter(String label, SourceType type)
		{
			this.label = label;
			this.type = type;
		}
	}

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
		// rows ask for their picture as they are scrolled to, not all at once when rendered
		resultsScroll.getVerticalScrollBar().addAdjustmentListener(e -> loadVisibleRowImages());

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
		buildFilterBar();
		// search and filters both stay put while the list scrolls beneath them — a filter you
		// have to scroll back up to reach is not much of a filter
		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.add(search, BorderLayout.NORTH);
		header.add(filterBar, BorderLayout.CENTER);

		JPanel container = new JPanel(new BorderLayout(0, 8));
		container.setBorder(new EmptyBorder(10, 8, 8, 8));
		container.add(header, BorderLayout.NORTH);
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

	private void buildFilterBar()
	{
		filterBar.setBorder(new EmptyBorder(0, 0, 0, 0));
		for (BrowseFilter filter : BrowseFilter.values())
		{
			JButton button = new JButton(filter.label);
			button.setFocusPainted(false);
			button.setMargin(new Insets(2, 2, 2, 2));
			button.addActionListener(e ->
			{
				browseFilter = filter;
				shownLimit = PAGE;   // a different slice starts from its own beginning
				paintFilterBar();
				rebuildResults();
				scrollToTop();
			});
			filterBar.add(button);
		}
		paintFilterBar();
	}

	// the selected filter is the one drawn in the brand colour; the rest are plain
	private void paintFilterBar()
	{
		int i = 0;
		for (BrowseFilter filter : BrowseFilter.values())
		{
			Component c = filterBar.getComponent(i++);
			c.setForeground(filter == browseFilter
				? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		}
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
		// typing is a reset: it returns to the search page and drops the trail behind it
		history.clear();
		currentPage = null;

		// settled before rendering: the filter bar's visibility depends on whether this is a
		// search or a browse, and renderResults reads that
		String q = search.getText() == null ? "" : search.getText().trim();
		boolean changed = !q.equals(lastQuery);
		lastQuery = q;
		renderResults();
		if (changed)
		{
			shownLimit = PAGE; // a different query starts from the top again
			scrollToTop();     // ...including the scrollbar, which "Show more" must not move
		}

		results.removeAll();
		List<ResultRow> rows = q.isEmpty() ? browseRows() : searchRows(q);
		if (rows.isEmpty())
		{
			results.add(hintLabel); // nothing matched, or the data is still loading
		}
		int shown = Math.min(shownLimit, rows.size());
		for (ResultRow row : rows.subList(0, shown))
		{
			results.add(row);
		}
		if (shown < rows.size())
		{
			results.add(new ResultRow("MORE", "Show more  (" + shown + " of " + rows.size() + ")",
				() ->
				{
					shownLimit += PAGE;
					rebuildResults();
				}));
		}
		results.revalidate();
		results.repaint();
		// deferred: rows have no bounds until this layout pass runs, and without bounds nothing
		// can be judged visible
		SwingUtilities.invokeLater(this::loadVisibleRowImages);
	}

	/**
	 * Rows for a query, every match, in the order they are offered.
	 *
	 * <p>They are built rather than added straight to the panel because a one-letter query
	 * matches thousands of sources and items; rendering all of them froze the client, and going
	 * Back froze it again re-laying out the same panel. Only the first {@value #PAGE} are shown.
	 */
	private List<ResultRow> searchRows(String q)
	{
		List<ResultRow> rows = new ArrayList<>();
		for (SourceData npc : dataService.searchSources(q))
		{
			rows.add(new ResultRow(badgeFor(npc.getType()), sourceLabel(npc), () -> showNpc(npc)));
		}
		for (ShopData shop : dataService.searchShops(q))
		{
			rows.add(new ResultRow("SHOP", shop.getName(), () -> showShop(shop)));
		}
		for (ItemMatch item : dataService.searchItems(q))
		{
			// no icon here: search results mix items with sources, and only some rows having one
			// would leave their names starting at different x
			rows.add(new ResultRow("ITEM", item.getItemName(), () -> showItem(item)));
		}
		return rows;
	}

	/**
	 * The page with nothing typed: a list worth scrolling rather than an instruction to search,
	 * narrowed to whichever kind of thing the filter bar has selected.
	 */
	private List<ResultRow> browseRows()
	{
		List<ResultRow> rows = new ArrayList<>();
		if (browseFilter == BrowseFilter.ITEM)
		{
			for (ItemMatch item : dataService.allItems())
			{
				rows.add(itemRow(item));
			}
			return rows;
		}
		for (SourceData npc : dataService.allSources())
		{
			if (browseFilter.type == null || browseFilter.type == npc.getType())
			{
				ResultRow row = new ResultRow(badgeFor(npc.getType()), sourceLabel(npc),
					() -> showNpc(npc), config.showBrowseImages() ? placeholderIcon() : null);
				row.putClientProperty(WIKI_TITLE, npc.wikiTitle());
				rows.add(row);
			}
		}
		return rows;
	}

	// marks a row as wanting a wiki image, and remembers which page to ask about
	private static final String WIKI_TITLE = "itemsearch.wikiTitle";

	// a transparent square so the row reserves its icon column before the image arrives, rather
	// than every name jumping left as each one loads
	private AsyncBufferedImage placeholderIcon()
	{
		return new AsyncBufferedImage(clientThread, 1, 1, BufferedImage.TYPE_INT_ARGB);
	}

	/**
	 * Asks the wiki for the images of rows the player can actually see, and no others.
	 *
	 * <p>A page holds a hundred rows and a sidebar shows about fifteen. Fetching all hundred would
	 * mean a hundred requests to a third-party server to draw one screen, so only what is on
	 * screen is requested, in one batched call, and {@link WikiImageLoader} caches each image to
	 * disk so it is never asked for twice.
	 */
	private void loadVisibleRowImages()
	{
		if (!config.showBrowseImages())
		{
			return;
		}
		Rectangle view = resultsScroll.getViewport().getViewRect();
		List<String> wanted = new ArrayList<>();
		List<JComponent> targets = new ArrayList<>();
		for (Component c : results.getComponents())
		{
			if (!(c instanceof JComponent))
			{
				continue;
			}
			JComponent row = (JComponent) c;
			String title = (String) row.getClientProperty(WIKI_TITLE);
			if (title == null || Boolean.TRUE.equals(row.getClientProperty(IMAGE_ASKED)))
			{
				continue;
			}
			Rectangle bounds = SwingUtilities.convertRectangle(results, row.getBounds(), results);
			if (!bounds.intersects(view))
			{
				continue;
			}
			row.putClientProperty(IMAGE_ASKED, Boolean.TRUE);
			wanted.add(title);
			targets.add(row);
		}
		if (wanted.isEmpty())
		{
			return;
		}
		wikiRefreshService.imageUrlsFor(wanted, urls -> SwingUtilities.invokeLater(() ->
		{
			for (int i = 0; i < targets.size(); i++)
			{
				String url = urls.get(wanted.get(i));
				JLabel icon = iconLabelOf(targets.get(i));
				if (url != null && icon != null)
				{
					WikiImageLoader.loadInto(okHttpClient, icon, url, BROWSE_ICON_SIZE);
				}
			}
		}));
	}

	private static final String IMAGE_ASKED = "itemsearch.imageAsked";
	private static final int BROWSE_ICON_SIZE = 32;

	// the row's icon label is the first label it holds with no text
	private static JLabel iconLabelOf(Container row)
	{
		for (Component c : row.getComponents())
		{
			if (c instanceof JLabel && ((JLabel) c).getText().isEmpty())
			{
				return (JLabel) c;
			}
			if (c instanceof Container)
			{
				JLabel found = iconLabelOf((Container) c);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * A row for an item, carrying the item's own in-game icon. The sprite comes from the client's
	 * cache rather than the network, so a hundred of them cost nothing to show.
	 */
	private ResultRow itemRow(ItemMatch item)
	{
		return new ResultRow("ITEM", item.getItemName(), () -> showItem(item),
			itemManager.getImage(item.getItemId()));
	}

	// a chest or gathering node has no combat level; "(lvl 0)" would be noise
	private static String sourceLabel(SourceData npc)
	{
		return npc.getCombatLevel() > 0
			? npc.getName() + " (lvl " + npc.getCombatLevel() + ")"
			: npc.getName();
	}


	/**
	 * Opens a page, remembering the one being left so Back can return to it.
	 *
	 * <p>Pages used to carry their own "where back goes" callback, which meant anything opened
	 * from outside a chain — the in-game menu entry, a search result — could only go back to the
	 * search. Keeping the trail here lets Back retrace whatever path was actually taken.
	 */
	private void go(Runnable page)
	{
		if (currentPage != null)
		{
			history.push(currentPage);
		}
		currentPage = page;
		page.run();
		scrollToTop();
	}

	private void back()
	{
		if (history.isEmpty())
		{
			showResults();
			return;
		}
		currentPage = history.pop();
		currentPage.run();
		scrollToTop();
	}

	/** Returns to the search page and forgets the trail — a new search starts over. */
	private void showResults()
	{
		history.clear();
		currentPage = null;
		renderResults();
		scrollToTop();
	}

	/**
	 * Puts a newly shown page at its top. Arriving somewhere — forwards or via Back — should not
	 * inherit how far down the previous page happened to be scrolled. Only which cards are
	 * expanded survives a move, because that is a choice the player made about the content.
	 *
	 * <p>Deliberately not called from {@link #rebuildResults()}: "Show more" re-renders the same
	 * page and must leave the player where they were reading.
	 */
	void scrollToTop()
	{
		for (Component c : content.getComponents())
		{
			if (c instanceof JScrollPane)
			{
				JScrollPane pane = (JScrollPane) c;
				// deferred: the viewport clamps the value against a content size that this
				// layout pass has not settled yet, so setting it now can be undone
				SwingUtilities.invokeLater(() -> pane.getVerticalScrollBar().setValue(0));
			}
		}
	}

	private void renderResults()
	{
		currentItemView = null;
		currentNpcView = null;
		spawnMapMarker.clear();
		// the filters narrow the browse list, so they belong to it and to nothing else
		filterBar.setVisible(lastQuery.isEmpty());
		content.removeAll();
		content.add(resultsScroll, BorderLayout.CENTER);
		content.revalidate();
		content.repaint();
	}

	/** Opens a source's page from outside the panel — the in-game "Show drops" menu entry. EDT only. */
	void showSource(SourceData source)
	{
		showNpc(source);
	}

	/** Opens an item's page. Package-visible so tests can walk the navigation. */
	void showItemForTest(ItemMatch item)
	{
		showItem(item);
	}

	private void showNpc(SourceData npc)
	{
		go(() -> renderNpc(npc));
	}

	private void renderNpc(SourceData npc)
	{
		currentItemView = null;
		SourceResultView view = new SourceResultView(npc, trackerStore, spriteManager, itemManager, okHttpClient,
			spawnMapMarker, clientThread, config.groupDropsByTable(), this::back, this::showItem);
		currentNpcView = view;
		showDetail(view);
		if (config.autoMarkSpawns())
		{
			spawnMapMarker.showSpawns(npc);
		}
		// fetch a fresher wiki image and push it into the view we just opened. By page title,
		// not display name — "Killerwatt (Attacking)" is a name we made up when splitting
		// versions, and the wiki answers such a title with a page that does not exist.
		wikiRefreshService.refreshNpcImage(npc.getId(), npc.wikiTitle(), view::updateImage);
	}

	private void showShop(ShopData shop)
	{
		go(() -> renderShop(shop));
	}

	private void renderShop(ShopData shop)
	{
		currentItemView = null;
		currentNpcView = null;
		ShopResultView view = new ShopResultView(shop, itemManager, this::back, line ->
			showItem(new ItemMatch(line.getItemId(), line.getItemName())),
			spawnMapMarker::showShop);
		showDetail(view);
	}

	private void showItem(ItemMatch item)
	{
		go(() -> renderItem(item));
	}

	private void renderItem(ItemMatch item)
	{
		currentNpcView = null;
		currentItemView = new ItemResultView(item, dataService, trackerStore, itemManager, this::back,
			this::showNpc, this::showShop);
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
		filterBar.setVisible(false);
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
