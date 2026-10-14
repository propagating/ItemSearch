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
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.RuneLite;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.data.WikiRefreshService;
import com.itemsearch.model.SourceData;
import com.itemsearch.map.SpawnMapMarker;
import com.itemsearch.map.SpawnRecorder;
import com.itemsearch.menu.ShowDropsMenu;
import com.itemsearch.tracker.DropConditions;
import com.itemsearch.tracker.KillTracker;
import com.itemsearch.tracker.SpawnExport;
import com.itemsearch.tracker.SpawnStore;
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
	private SpawnStore spawnStore;
	private SpawnRecorder spawnRecorder;
	private ShowDropsMenu showDropsMenu;
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

		trackerStore = new TrackerStore(configManager, gson, executor);
		trackerStore.load();
		killTracker = new KillTracker(dataService, trackerStore, itemManager,
			new DropConditions(client));

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
		spawnStore = new SpawnStore(configManager, gson);
		spawnStore.load();
		spawnRecorder = new SpawnRecorder(client, clientThread, spawnStore);

		panel = new ItemSearchPanel(dataService, trackerStore, itemManager, okHttpClient,
			wikiRefreshService, spawnMapMarker, clientThread, config, spriteManager,
			spawnStore, spawnRecorder);

		showDropsMenu = new ShowDropsMenu(client, dataService, itemManager,
			source -> openPanelTo(p -> p.showNpc(source)),
			item -> openPanelTo(p -> p.showItem(item)));
		showDropsMenu.setEnabled(config.showDropsMenuEntry());

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Item Search")
			.icon(icon)
			.priority(99)
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
		if (trackerStore != null)
		{
			trackerStore.flush(); // a kill from the last second is still waiting to be written
		}
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
		showDropsMenu = null;
	}

	// the menu click arrives on the client thread and everything it leads to touches Swing, so
	// the whole handler crosses to the EDT once here rather than each callee guarding itself
	private void openPanelTo(Consumer<ItemSearchPanel> page)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && navButton != null)
			{
				clientToolbar.openPanel(navButton);
				page.accept(panel);
			}
		});
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (killTracker != null && killTracker.onNpcLootReceived(event))
		{
			refreshTrackingViews();
		}
	}

	/** Loot the server reports directly, for monsters whose drops never touch the ground. */
	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (event.getComposition() != null)
		{
			log.debug("itemsearch: server loot from {} ({}): {} item(s)",
				event.getComposition().getName(), event.getComposition().getId(),
				event.getItems().size());
		}
		if (killTracker != null && killTracker.onServerNpcLoot(event))
		{
			refreshTrackingViews();
		}
	}

	/**
	 * Loot the Loot Tracker plugin attributed to a chest, an activity, or a pickpocket. Only
	 * arrives while that plugin is on, which it is by default.
	 */
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (killTracker != null && killTracker.onLootReceived(event))
		{
			refreshTrackingViews();
		}
	}

	/**
	 * Game messages that carry a running total, such as how many golems the player has finished.
	 * Every line of chat passes through here, so the sidebar is only redrawn for the rare one that
	 * moved a counter.
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (killTracker != null && killTracker.onChatMessage(event))
		{
			refreshTrackingViews();
		}
	}

	// the store was just updated on this thread; push the new dry counts into the open
	// item view (if any) on the EDT
	private void refreshTrackingViews()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.refreshTracking();
			}
		});
	}

	/** Writes the recorded spawns out where the curator can pick them up. Off the EDT. */
	private void exportRecordedSpawns()
	{
		executor.execute(() ->
		{
			File out = new File(new File(RuneLite.RUNELITE_DIR, "itemsearch"),
				SpawnExport.FILE_NAME);
			try
			{
				// names make a contributed file reviewable by eye rather than by cross-reference
				Map<Integer, String> names = new HashMap<>();
				for (Integer npcId : spawnStore.editedNpcIds())
				{
					SourceData source = dataService.sourceById(npcId);
					if (source != null)
					{
						names.put(npcId, source.getName());
					}
				}
				int monsters = SpawnExport.write(gson, spawnStore, out, names);
				log.info("itemsearch: exported spawns for {} monster(s) to {}", monsters, out);
			}
			catch (Exception e)
			{
				log.warn("itemsearch: could not export recorded spawns to {}", out, e);
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (spawnMapMarker != null)
		{
			// the entrance marker sits above the map's own dungeon icon, and how far above
			// depends on the zoom, which the player can change at any time
			spawnMapMarker.liftEntranceMarker();
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (showDropsMenu != null)
		{
			showDropsMenu.onMenuOpened(event);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ItemSearchConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		boolean switchedOn = Boolean.parseBoolean(event.getNewValue());
		switch (event.getKey())
		{
			case "showDropsMenuEntry":
				if (showDropsMenu != null)
				{
					showDropsMenu.setEnabled(switchedOn);
				}
				break;
			case "exportSpawns":
				if (spawnStore != null && switchedOn)
				{
					exportRecordedSpawns();
					// a one-shot action rather than a setting: leave it on and it would fire again
					// on every restart, so it puts itself back
					configManager.setConfiguration(ItemSearchConfig.GROUP, "exportSpawns", false);
				}
				break;
			case "resetAll":
				if (trackerStore != null && switchedOn)
				{
					trackerStore.clear(); // empties in-memory counters and persists the reset
					configManager.setConfiguration(ItemSearchConfig.GROUP, "resetAll", false);
				}
				break;
			case "data":
				// the counters changed under us: another client on this profile, or the profile
				// synced down from the RuneLite account. Our own saves announce themselves the
				// same way and are recognised as ours.
				if (trackerStore != null && trackerStore.absorbExternalChanges())
				{
					refreshTrackingViews();
				}
				break;
			default:
				break;
		}
	}

}
