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
package com.itemsearch.tracker;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import static org.junit.Assert.assertEquals;
import com.itemsearch.data.BundledDataSource;
import com.itemsearch.data.DropDataService;
import com.itemsearch.model.SourceData;
import com.itemsearch.model.SourceType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KillTrackerTest
{
	@Mock
	private TrackerStore store;

	@Mock
	private ItemManager itemManager;

	@Mock
	private DropConditions conditions;

	private KillTracker tracker;

	@Before
	public void setUp()
	{
		// canonicalize is identity by default; individual tests override for noted ids
		lenient().when(itemManager.canonicalize(anyInt()))
			.thenAnswer(inv -> inv.getArgument(0));
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		ds.load();
		tracker = new KillTracker(ds, store, itemManager, conditions);
	}

	/**
	 * A brimstone key is on the table only while Konar assigned the task, so an off-task kill was
	 * never a chance at one and must not count against its dry streak. Everything else the monster
	 * drops still counts — the gate is per drop, not per kill.
	 */
	@Test
	public void anOffTaskKillIsNotDryForAConditionalDrop()
	{
		when(conditions.isMet("KONAR_TASK")).thenReturn(false);

		tracker.onKill(2, new HashSet<>(Collections.singletonList(526)));

		verify(store).recordKill(2, new HashSet<>(Collections.singletonList(526)),
			new HashSet<>(Collections.singletonList(526)));
	}

	/** On task, the key is in the pool and a kill without it is a genuine dry kill. */
	@Test
	public void anOnTaskKillIsDryForAConditionalDrop()
	{
		when(conditions.isMet("KONAR_TASK")).thenReturn(true);

		tracker.onKill(2, new HashSet<>(Collections.singletonList(526)));

		verify(store).recordKill(2, new HashSet<>(Collections.singletonList(526)),
			new HashSet<>(Arrays.asList(526, 23083)));
	}

	/**
	 * The drop landing is better evidence than the varp it was judged by: if a key arrives while we
	 * believe the player is off task, the belief is what was wrong, and the drop still counts.
	 */
	@Test
	public void aConditionalDropThatLandsCountsWhateverWeBelieved()
	{
		// lenient: the point of the test is that a landed drop short-circuits before the condition
		// is ever consulted, so this stub is expected to go unused
		lenient().when(conditions.isMet("KONAR_TASK")).thenReturn(false);

		tracker.onKill(2, new HashSet<>(Arrays.asList(526, 23083)));

		verify(store).recordKill(2, new HashSet<>(Arrays.asList(526, 23083)),
			new HashSet<>(Arrays.asList(526, 23083)));
	}

	@Test
	public void killLooksUpPossibleDropsAndRecords()
	{
		// Goblin (3031) can drop 995 and 1205; this kill dropped only 1205
		tracker.onKill(3031, new HashSet<>(Collections.singletonList(1205)));
		verify(store).recordKill(3031,
			new HashSet<>(Collections.singletonList(1205)),
			new HashSet<>(Arrays.asList(995, 1205)));
		verify(store).save();
	}

	@Test
	public void killUnderVariantIdRecordsUnderCanonicalId()
	{
		// Test Beast has npcIds [7001, 7002]; a kill under the variant id 7002 must accumulate
		// under the canonical id 7001 so all variants share one dry counter
		BundledDataSource multi = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		multi.load();
		KillTracker t = new KillTracker(multi, store, itemManager, conditions);

		t.onKill(7002, new HashSet<>(Collections.singletonList(995)));

		verify(store).recordKill(7001,
			new HashSet<>(Collections.singletonList(995)),
			new HashSet<>(Collections.singletonList(995)));
	}

	@Test
	public void killResolvedByNameAndLevelWhenIdNotInData()
	{
		// Test Beast is id 7001 in data; a live kill reports an id our data never listed (88888)
		// but the same name + level, so it must still resolve and record under canonical 7001
		BundledDataSource multi = new BundledDataSource(new Gson(),
			"/com/itemsearch/sources-test.json");
		multi.load();
		KillTracker t = new KillTracker(multi, store, itemManager, conditions);

		NPC beast = org.mockito.Mockito.mock(NPC.class);
		when(beast.getId()).thenReturn(88888);
		when(beast.getName()).thenReturn("Test Beast");
		when(beast.getCombatLevel()).thenReturn(100);

		t.onNpcLootReceived(new NpcLootReceived(beast,
			Collections.singletonList(new ItemStack(995, 1))));

		verify(store).recordKill(7001,
			new HashSet<>(Collections.singletonList(995)),
			new HashSet<>(Collections.singletonList(995)));
	}

	private static ServerNpcLoot serverLoot(int npcId, String name, int level, int... itemIds)
	{
		NPCComposition npc = org.mockito.Mockito.mock(NPCComposition.class);
		lenient().when(npc.getId()).thenReturn(npcId);
		lenient().when(npc.getName()).thenReturn(name);
		lenient().when(npc.getCombatLevel()).thenReturn(level);
		java.util.List<ItemStack> items = new java.util.ArrayList<>();
		for (int id : itemIds)
		{
			items.add(new ItemStack(id, 1));
		}
		return new ServerNpcLoot(npc, items);
	}

	/**
	 * An albatross is never seen to die: it turns into a defeated bird that the player harvests,
	 * and the loot goes straight to the ship's hold. The only word of it is the server's own
	 * loot report, which must count as a kill like any other.
	 */
	@Test
	public void serverReportedLootCountsAsAKillUnderTheCanonicalId()
	{
		tracker.onServerNpcLoot(serverLoot(7002, "Test Beast", 100, 995));

		verify(store).recordKill(7001,
			new HashSet<>(Collections.singletonList(995)),
			new HashSet<>(Collections.singletonList(995)));
		verify(store).save();
	}

	@Test
	public void serverReportedLootResolvesByNameAndLevelWhenTheIdIsUnknown()
	{
		// the defeated form has an id of its own that the data never listed
		tracker.onServerNpcLoot(serverLoot(88888, "Test Beast", 100, 995));

		verify(store).recordKill(7001,
			new HashSet<>(Collections.singletonList(995)),
			new HashSet<>(Collections.singletonList(995)));
	}

	/**
	 * The same channel reports pickpocket loot, and a pickpocket is not a kill. An NPC that can
	 * be pickpocketed cannot be told apart, so its server reports are left uncounted as before.
	 */
	@Test
	public void serverReportedLootFromAPickpocketableNpcIsNotAKill()
	{
		DropDataService data = org.mockito.Mockito.mock(DropDataService.class);
		SourceData guard = new SourceData(9, "Guard", 21, null, Collections.emptyList(),
			Collections.emptyList(), Collections.singletonList(9), null, SourceType.KILL,
			null, null, null, null, null, null, null, null);
		when(data.sourceById(9)).thenReturn(guard);
		when(data.canBePickpocketed(9, "Guard")).thenReturn(true);
		KillTracker t = new KillTracker(data, store, itemManager, conditions);

		t.onServerNpcLoot(serverLoot(9, "Guard", 21, 995));

		verify(store, org.mockito.Mockito.never()).recordKill(
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void serverReportedLootFromAPickpocketOnlySourceIsNotAKill()
	{
		// Master Farmer exists only as a pickpocket source
		tracker.onServerNpcLoot(serverLoot(3257, "Master Farmer", 0, 995));

		verify(store, org.mockito.Mockito.never()).recordKill(
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	/**
	 * Should the client ever report one kill both ways, it is still one kill. But two kills in
	 * quick succession with the same loot, as a burst of barraged monsters produces, arrive by
	 * the same channel and are both counted.
	 */
	@Test
	public void oneKillReportedByBothChannelsCountsOnce()
	{
		long[] now = {1000L};
		BundledDataSource ds = new BundledDataSource(new Gson(), "/com/itemsearch/sources-test.json");
		ds.load();
		KillTracker t = new KillTracker(ds, store, itemManager, conditions, () -> now[0]);
		NPC beast = org.mockito.Mockito.mock(NPC.class);
		when(beast.getId()).thenReturn(7001);

		t.onNpcLootReceived(new NpcLootReceived(beast, Collections.singletonList(new ItemStack(995, 1))));
		now[0] += 600;
		t.onServerNpcLoot(serverLoot(7001, "Test Beast", 100, 995));
		verify(store, org.mockito.Mockito.times(1)).recordKill(
			org.mockito.ArgumentMatchers.eq(7001),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());

		t.onNpcLootReceived(new NpcLootReceived(beast, Collections.singletonList(new ItemStack(995, 1))));
		t.onNpcLootReceived(new NpcLootReceived(beast, Collections.singletonList(new ItemStack(995, 1))));
		verify(store, org.mockito.Mockito.times(3)).recordKill(
			org.mockito.ArgumentMatchers.eq(7001),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());

		// long after, the server's report is a kill of its own again
		now[0] += 10_000;
		t.onServerNpcLoot(serverLoot(7001, "Test Beast", 100, 995));
		verify(store, org.mockito.Mockito.times(4)).recordKill(
			org.mockito.ArgumentMatchers.eq(7001),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	private static LootReceived lootEvent(String name, LootRecordType type, int... itemIds)
	{
		java.util.List<ItemStack> items = new java.util.ArrayList<>();
		for (int id : itemIds)
		{
			items.add(new ItemStack(id, 1));
		}
		return new LootReceived(name, -1, type, items, 1, null);
	}

	/**
	 * A chest opened or an activity rewarded is one attempt at that source, reported by the Loot
	 * Tracker under the source's name. Golem crafting had a dry counter that never moved because
	 * nothing is killed for it.
	 */
	@Test
	public void lootTrackerEventsCountAsAttemptsAtTheSourceTheyName()
	{
		tracker.onLootReceived(lootEvent("Golem Crafting", LootRecordType.EVENT, 995));
		verify(store).recordKill(org.mockito.ArgumentMatchers.eq(-1001),
			org.mockito.ArgumentMatchers.eq(new HashSet<>(Collections.singletonList(995))),
			org.mockito.ArgumentMatchers.any());

		tracker.onLootReceived(lootEvent("Lunar Chest", LootRecordType.EVENT, 995));
		verify(store).recordKill(org.mockito.ArgumentMatchers.eq(-1000),
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void aPickpocketReportedByTheLootTrackerIsAnAttemptAtThePickpocketSource()
	{
		tracker.onLootReceived(lootEvent("Master Farmer", LootRecordType.PICKPOCKET, 995));
		verify(store).recordKill(org.mockito.ArgumentMatchers.eq(3257),
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	/** Monster loot arrives by its own event; counting it here too would double every kill. */
	@Test
	public void monsterLootOnTheLootTrackerChannelIsLeftAlone()
	{
		tracker.onLootReceived(lootEvent("Goblin", LootRecordType.NPC, 995));
		tracker.onLootReceived(lootEvent("Goblin", LootRecordType.PLAYER, 995));
		tracker.onLootReceived(lootEvent("Nobody", LootRecordType.EVENT, 995));
		verify(store, org.mockito.Mockito.never()).recordKill(
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	/** The Loot Tracker says "Barrows"; the wiki, and so this plugin, says "Chest (Barrows)". */
	@Test
	public void lootTrackerNamesAreMappedToTheWikisWhereTheyDiffer()
	{
		DropDataService data = org.mockito.Mockito.mock(DropDataService.class);
		SourceData chest = new SourceData(-7, "Chest (Barrows)", 0, null, Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), null, SourceType.REWARD,
			null, null, null, null, null, null, null, null);
		when(data.sourceByNameAndLevel("Chest (Barrows)", 0)).thenReturn(chest);
		KillTracker t = new KillTracker(data, store, itemManager, conditions);

		t.onLootReceived(lootEvent("Barrows", LootRecordType.EVENT, 995));

		verify(store).recordKill(org.mockito.ArgumentMatchers.eq(-7),
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertEquals("Chest (Barrows)", LootEventNames.sourceNameFor("Barrows"));
		assertEquals("Seed pack", LootEventNames.sourceNameFor("Seed pack"));
	}

	/**
	 * The game announces the player's lifetime golem total as each one is finished. That covers
	 * golems made before the plugin was installed, so the attempt count is raised to match it.
	 */
	@Test
	public void theGamesGolemTotalRaisesTheAttemptCount()
	{
		tracker.onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"You have crafted 1,234 golems on Wyrmscraig.", "", 0));
		verify(store).raiseCountTo(-1001, 1234);

		tracker.onChatMessage(new ChatMessage(null, ChatMessageType.PUBLICCHAT, "someone",
			"You have crafted 9,999 golems on Wyrmscraig.", "", 0));
		tracker.onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"You finish crafting the golem from this angle.", "", 0));
		verify(store, org.mockito.Mockito.times(1)).raiseCountTo(
			org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	public void unknownNpcIsIgnored()
	{
		tracker.onKill(999999, Collections.emptySet());
		verify(store, org.mockito.Mockito.never()).recordKill(
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void notedDropIsCanonicalizedToBaseItemId()
	{
		// a noted Bronze dagger drops as id 1206; canonicalize maps it back to 1205
		// so the drop is detected instead of incrementing the dry counter
		when(itemManager.canonicalize(1206)).thenReturn(1205);
		NPC goblin = org.mockito.Mockito.mock(NPC.class);
		when(goblin.getId()).thenReturn(3031);

		tracker.onNpcLootReceived(new NpcLootReceived(goblin,
			Collections.singletonList(new ItemStack(1206, 1))));

		verify(store).recordKill(3031,
			new HashSet<>(Collections.singletonList(1205)),
			new HashSet<>(Arrays.asList(995, 1205)));
	}
}
