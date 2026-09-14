# Item Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An item's page shows its examine text, a Facts card (value, low and high alch, general store price, Exchange price and limit, weight, flags), and an Equipment card for wearable items.

**Architecture:** The plugin reads value, alch, flags, weight, Exchange price and equipment bonuses from the client's `ItemManager` on the client thread and fills two cards built in the same style as the monster page. Examine text is the one wiki fact, gathered by the generator's existing item fetch into a top-level `examines` map in the bundled file.

**Tech Stack:** Java 11, Swing, RuneLite client API (`ItemManager`, `ItemComposition`, `ItemStats`, `ItemEquipmentStats`), JUnit 4 + Mockito; generator Gson.

**Spec:** `docs/superpowers/specs/2026-09-12-item-details-design.md`

## Global Constraints

- Java 11. Plugin uses tabs; generator uses 4 spaces. Allman braces.
- Never block the EDT on the client: composition reads go through `ClientThread.invoke`.
- No commits unless the user asks. Tests: `cmd /c ".\gradlew.bat test --offline -q"` in each repo.
- After plugin changes, `gradlew sideload` so the user's client can pick them up.

---

### Task 1: `ItemFacts` and the two cards (plugin)

**Files:**
- Create: `src/main/java/com/itemsearch/ui/ItemFacts.java`
- Modify: `src/main/java/com/itemsearch/ui/ItemResultView.java` (new overload with `ClientThread`; cards)
- Modify: `src/main/java/com/itemsearch/ItemSearchPanel.java:610` (pass `clientThread`)
- Test: `src/test/java/com/itemsearch/ui/ItemResultViewTest.java`

**Interfaces:**
- Produces: `ItemFacts.read(ItemManager, int itemId)` → `ItemFacts` with `int value, lowAlch, highAlch, gePrice, geLimit; boolean geTradeable, members, tradeable, stackable; double weight; ItemEquipmentStats equipment (null when not equipable)`; `ItemResultView(item, data, store, itemManager, clientThread, onBack, onNpcSelected, onShopSelected)`; the old 7-arg constructor delegates with `null` and then shows no cards.

- [ ] **Step 1: Failing tests** (append to `ItemResultViewTest`; `import net.runelite.api.ItemComposition; import net.runelite.client.game.ItemStats; import net.runelite.client.game.ItemEquipmentStats; import net.runelite.client.callback.ClientThread; import static org.mockito.ArgumentMatchers.any; import static org.mockito.Mockito.doAnswer;`)

```java
	private static ClientThread immediate()
	{
		ClientThread ct = mock(ClientThread.class);
		doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(ct).invoke(any(Runnable.class));
		return ct;
	}

	private static ItemManager dagger(boolean equipable)
	{
		ItemManager im = mock(ItemManager.class);
		ItemComposition c = mock(ItemComposition.class);
		when(c.getPrice()).thenReturn(10);
		when(c.getHaPrice()).thenReturn(6);
		when(c.isMembers()).thenReturn(false);
		when(c.isTradeable()).thenReturn(true);
		when(c.isGeTradeable()).thenReturn(true);
		when(c.isStackable()).thenReturn(false);
		when(im.getItemComposition(BRONZE_DAGGER)).thenReturn(c);
		when(im.getItemPrice(BRONZE_DAGGER)).thenReturn(37);
		ItemEquipmentStats eq = ItemEquipmentStats.builder().slot(3).astab(4).aslash(2).acrush(-4)
			.amagic(1).arange(0).dstab(0).dslash(1).dcrush(0).dmagic(1).drange(0).str(3).rstr(0)
			.mdmg(0).prayer(0).aspeed(4).isTwoHanded(false).build();
		when(im.getItemStats(BRONZE_DAGGER)).thenReturn(new ItemStats(equipable, 0.453, 125,
			equipable ? eq : null));
		return im;
	}

	private ItemResultView factsView(ItemManager im)
	{
		return new ItemResultView(new ItemMatch(BRONZE_DAGGER, "Bronze dagger"), loadedTestData(),
			new TrackerStore(mock(ConfigManager.class), new Gson()), im, immediate(),
			() ->
			{
			}, s ->
			{
			}, s ->
			{
			});
	}

	@Test
	public void theFactsCardShowsWhatTheItemIsWorth()
	{
		List<String> text = labelTexts(factsView(dagger(true)));
		assertTrue(text.contains("Value") && text.contains("10"));
		assertTrue(text.contains("Low alch") && text.contains("4"));
		assertTrue(text.contains("High alch") && text.contains("6"));
		assertTrue(text.contains("General store pays") && text.contains("4"));
		assertTrue(text.contains("Grand Exchange") && text.contains("37"));
		assertTrue(text.contains("Buy limit") && text.contains("125"));
		assertTrue(text.contains("Weight") && text.contains("0.5 kg"));
		assertTrue(text.contains("Tradeable"));
		assertFalse(text.contains("Members"));
	}

	@Test
	public void theEquipmentCardAppearsOnlyForWearableItems()
	{
		List<String> worn = labelTexts(factsView(dagger(true)));
		assertTrue(worn.contains("EQUIPMENT"));
		assertTrue(worn.contains("Weapon") && worn.contains("Stab") && worn.contains("+4"));
		assertTrue(worn.contains("Crush") && worn.contains("-4"));
		assertTrue(worn.contains("Speed") && worn.contains("4"));
		assertFalse(labelTexts(factsView(dagger(false))).contains("EQUIPMENT"));
	}

	@Test
	public void anExchangeUntradeableItemShowsNoExchangeRows()
	{
		ItemManager im = dagger(false);
		when(im.getItemComposition(BRONZE_DAGGER).isGeTradeable()).thenReturn(false);
		List<String> text = labelTexts(factsView(im));
		assertFalse(text.contains("Grand Exchange"));
		assertFalse(text.contains("Buy limit"));
	}

	@Test
	public void aClientThreadThatNeverRunsLeavesThePageUsable()
	{
		ItemResultView view = new ItemResultView(new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			loadedTestData(), new TrackerStore(mock(ConfigManager.class), new Gson()), dagger(true),
			mock(ClientThread.class), () ->
			{
			}, s ->
			{
			}, s ->
			{
			});
		assertFalse(labelTexts(view).contains("Value"));
		assertTrue(labelTexts(view).contains("Bronze dagger"));
	}
```

Add a `labelTexts(Container)` helper mirroring `SourceResultViewTest`'s if the class lacks one (it has `collect(root, JLabel.class)`; map to `getText()`).

- [ ] **Step 2: Implement `ItemFacts`**

```java
package com.itemsearch.ui;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** What the client knows about an item, gathered in one read so the page can show it at once. */
final class ItemFacts
{
	final int value;
	final int lowAlch;
	final int highAlch;
	final boolean members;
	final boolean tradeable;
	final boolean stackable;
	final boolean geTradeable;
	final int gePrice;
	final int geLimit;
	final double weight;
	// null when the item cannot be worn
	final ItemEquipmentStats equipment;

	private ItemFacts(ItemComposition c, ItemStats s, int gePrice)
	{
		value = c.getPrice();
		lowAlch = value * 2 / 5; // 40%, which is also the first price a general store pays
		highAlch = c.getHaPrice();
		members = c.isMembers();
		tradeable = c.isTradeable();
		stackable = c.isStackable();
		geTradeable = c.isGeTradeable();
		this.gePrice = gePrice;
		geLimit = s == null ? 0 : s.getGeLimit();
		weight = s == null ? 0 : s.getWeight();
		equipment = s != null && s.isEquipable() ? s.getEquipment() : null;
	}

	/** Must run on the client thread: the composition comes from the game's cache. */
	static ItemFacts read(ItemManager itemManager, int itemId)
	{
		ItemComposition c = itemManager.getItemComposition(itemId);
		if (c == null)
		{
			return null;
		}
		return new ItemFacts(c, itemManager.getItemStats(itemId), itemManager.getItemPrice(itemId));
	}
}
```

- [ ] **Step 3: Implement in `ItemResultView`**: add fields `private final JPanel factsSlot = slot(); private final JPanel equipmentSlot = slot();` (copy `slot()` from `SourceResultView`, or move it to a small `Cards` helper both use); new constructor with `ClientThread clientThread` that, after the header, adds the examine label when `data.examineFor(id)` is non-null (Task 2 adds the method; until then skip), then `body.add(factsSlot); body.add(equipmentSlot);` and, when `clientThread != null`, `clientThread.invoke(() -> { ItemFacts f = ItemFacts.read(itemManager, id); SwingUtilities.invokeLater(() -> showFacts(f)); })`. `showFacts` fills `factsSlot` with a card headed FACTS holding a 2-column grid of `statCell(label, text)` rows, and `equipmentSlot` with a card headed EQUIPMENT when `f.equipment != null`. Reuse the `card()`/`section()`/`statCell` style from `SourceResultView` by moving those three into a package-private `Cards` utility class in `ui`. Slot names: 0 Head, 1 Cape, 2 Neck, 3 Weapon, 4 Body, 5 Shield, 7 Legs, 9 Hands, 10 Feet, 12 Ring, 13 Ammo; append " (two-handed)" when so. Bonuses are printed with a sign (`+4`, `-4`, `0`). Magic damage as `+N%` from `Math.round(mdmg)`.
  The old 7-arg constructor delegates with `clientThread = null`.
- [ ] **Step 4: Run plugin tests, expect pass.**

### Task 2: Examine text (generator + plugin)

**Files:**
- Modify generator: `source/ItemInfoJson.java` (+ `public String examine;`), `WikiItemFetch.java` (read `examine` / `examineN`), `output/OutputData.java` (+ `public Map<Integer, String> examines;`), `SourceDataGenerator.java` (fill it from `itemInfo` for item ids present in the output's drops and shops)
- Modify plugin: `data/DropDataService.java` (+ `String examineFor(int itemId)`), `data/BundledDataSource.java` (`BundledData.examines`, the method), `ui/ItemResultView.java` (examine label under the header, grey, wrapped)
- Tests: `WikiItemFetchTest` (examine parses, versioned examine per version), `SourceDataGeneratorTest` or a new `ExaminesTest` for the map; plugin `PhaseLoadingTest`-style JSON test for `examineFor`; `ItemResultViewTest` for the label via a mocked `DropDataService`.

- [ ] **Step 1: Generator failing tests**

```java
    @Test
    public void examineTextIsReadPerVersionAndForThePage()
    {
        String text = "{{Infobox Item\n|name = Bronze arrow\n|id = 882\n|value = 1\n"
            + "|examine = Arrows with bronze heads.\n}}";
        assertEquals("Arrows with bronze heads.", WikiItemFetch.extractInfobox(text).examine);
        String versioned = "{{Infobox Item\n|name1 = Prayer potion(4)\n|id1 = 2434\n"
            + "|examine1 = 4 doses of prayer restore potion.\n|name2 = Prayer potion(3)\n|id2 = 139\n"
            + "|examine2 = 3 doses of prayer restore potion.\n|examine = Prayer restore potion.\n}}";
        Map<String, ItemInfoJson> v = WikiItemFetch.extractVersions(versioned);
        assertEquals("3 doses of prayer restore potion.", v.get("Prayer potion(3)").examine);
    }
```

- [ ] **Step 2: Implement** — `info.examine = clean(WikiText.field(body, "examine"))` in `extractInfobox`; in `extractVersions`, `examine` + n falling back to plain. `clean` strips `[[...]]` link markup the way `stripLinks` does elsewhere and trims. In `SourceDataGenerator`, after `DropTableIndex.build(sources)`, collect every item id in `data.tables` rows and the shops file's items, then `data.examines = map id -> examine` for those with text, and print the count. Note in the run summary that `--refresh-items` is needed to populate the new field.
- [ ] **Step 3: Plugin** — `BundledData` gains `Map<Integer, String> examines`; `examineFor` returns `examines.get(id)` or null; `ItemResultView` adds the wrapped grey label under the header when non-null. Test with the `loadFrom(Reader)` JSON path.
- [ ] **Step 4: Run both test suites; run `--refresh-items` then `runSources`; sideload.**

### Task 3: Verify

- [ ] Open Bronze dagger, Abyssal whip, and Coins in the client: facts card on all, equipment card on the two weapons, examine text under the name, no Exchange rows on Coins.
