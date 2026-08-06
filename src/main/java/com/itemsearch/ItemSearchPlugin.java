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
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.data.WikiRefreshService;
import com.itemsearch.map.SpawnMapMarker;
import com.itemsearch.tracker.KillTracker;
import com.itemsearch.tracker.TrackerStore;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Item Search",
	description = "Search NPCs/items, view loot tables and personal drop-rate stats",
	tags = {"drop", "loot", "search", "rate"}
)
public class ItemSearchPlugin extends Plugin
{
	private static final String DATA_RESOURCE = "/com/itemsearch/sources.json";
	private static final String SHOPS_RESOURCE = "/com/itemsearch/shops.json";

	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Gson gson;
	@Inject
	private OkHttpClient okHttpClient;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private WorldMapPointManager worldMapPointManager;
	@Inject
	private ItemSearchConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private SpriteManager spriteManager;

	private BundledDataSource dataService;
	private TrackerStore trackerStore;
	private KillTracker killTracker;
	private WikiRefreshService wikiRefreshService;
	private SpawnMapMarker spawnMapMarker;
	private ItemSearchPanel panel;
	private NavigationButton navButton;

	@Provides
	ItemSearchConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ItemSearchConfig.class);
	}

	@Override
	protected void startUp()
	{
		dataService = new BundledDataSource(gson, DATA_RESOURCE, SHOPS_RESOURCE);

		wikiRefreshService = new WikiRefreshService(okHttpClient, gson, dataService, executor);

		trackerStore = new TrackerStore(configManager, gson);
		trackerStore.load();
		killTracker = new KillTracker(dataService, trackerStore, itemManager);

		BufferedImage markerIcon;
		try
		{
			markerIcon = ImageUtil.loadImageResource(getClass(), "map_marker.png");
		}
		catch (Exception e)
		{
			log.warn("itemsearch: failed to load map marker icon", e);
			markerIcon = null;
		}
		spawnMapMarker = new SpawnMapMarker(client, clientThread, worldMapPointManager, markerIcon);

		panel = new ItemSearchPanel(dataService, trackerStore, itemManager, okHttpClient,
			wikiRefreshService, spawnMapMarker, clientThread, config, spriteManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Item Search")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// parse the (now large) bundled data off the EDT; unblock the panel when ready.
		// A kill whose loot arrives during this brief load window finds an empty lookup and
		// is skipped (see KillTracker) — negligible in practice since the plugin is enabled
		// before combat and the parse is short.
		executor.execute(() ->
		{
			dataService.load();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.onDataReady();
				}
			});
		});
	}

	@Override
	protected void shutDown()
	{
		if (spawnMapMarker != null)
		{
			spawnMapMarker.clear();
		}
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (killTracker != null)
		{
			killTracker.onNpcLootReceived(event);
		}
		// the store was just updated on this thread; push the new dry counts into the open
		// item view (if any) on the EDT
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.refreshTracking();
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (trackerStore != null
			&& ItemSearchConfig.GROUP.equals(event.getGroup())
			&& "resetAll".equals(event.getKey())
			&& Boolean.parseBoolean(event.getNewValue()))
		{
			trackerStore.clear(); // empties in-memory counters and persists the reset
			configManager.setConfiguration(ItemSearchConfig.GROUP, "resetAll", false);
		}
	}

}
