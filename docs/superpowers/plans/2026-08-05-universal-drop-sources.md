# Universal Drop Sources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the frozen osrsbox monster database with a live OSRS Wiki sweep so every item shows every source: kills, reward chests/raids, pickpocketing, gathering nodes, and shops.

**Architecture:** Two repos. The generator (`E:\Projects\Python\ItemSearchDataGenerator`) enumerates every wiki page embedding `{{DropsLine}}` or `{{DropsLineReward}}`, parses drop tables with source-type inference, and joins item ids/values, NPC spawns, and combat data into a `sources.json` written to the plugin repo (`E:\Projects\JAVA\ItemSearch`), replacing `drops.json`. The plugin renames `NpcData` → `SourceData` (+ `SourceType`, map coords), badges search results by type, and adapts the views. Spec: `docs/superpowers/specs/2026-08-05-universal-drop-sources-design.md`.

**Tech Stack:** Java, Gson, Swing, JUnit 4 + Mockito, Gradle. No new dependencies.

## Global Constraints

- Two repos: generator work in `E:\Projects\Python\ItemSearchDataGenerator` (commit prefix `datagen:`, 4-space indent, Java 11, JUnit 4, Gson only), plugin work in `E:\Projects\JAVA\ItemSearch` (commit prefix `sources:`, **tabs**, braces on own line, Lombok `@Value`, JUnit 4 + Mockito). Run each task's commands from its repo root (`./gradlew.bat test`). Never commit to the local runelite fork.
- Every new `.java` file — **including test files** — gets the BSD license header copied verbatim from a sibling (copyright line: `Copyright (c) 2026, propagating <propagating@protonmail.com>`).
- Semantics fixed by the spec: `SourceType` ∈ {KILL, REWARD, PICKPOCKET, GATHER}; `DropsLineReward` rows → REWARD; `DropsLine` on an `Infobox Monster`/`Infobox NPC` page → KILL unless the table's `sectionHeading` OR `topHeading` contains "pickpocket" (case-insensitive) → PICKPOCKET; other infoboxes (or none) → GATHER; `source-type-overlay.json` (page title → type string) overrides inference. Rarity: fraction "a/b" (b may be fractional) → a/b; "Always" → 1.0; anything else → rarityKnown=false. Effective rarity uses the existing `MonsterTransform.effectiveRarity(Double, int rolls)`; quantity uses existing `MonsterTransform.parseQuantityMax(String)`; "(noted)" in the quantity string → noted=true. Synthetic ids for non-NPC sources are negative and assigned sequentially by sorted source name starting at -1000 (documented as non-stable across regens; nothing tracks them).
- The wikitext parser MUST handle nested templates (`{{Refn|...}}` inside `rarity`/`raritynotes` values) via brace-balanced scanning, and MUST strip HTML comments (`<!--...-->`) before param parsing — real pages (Zulrah) contain both.
- `wikiImageUrl` is null in the new data; the client's `WikiRefreshService` already fetches live images by name.
- Existing helpers to reuse, not duplicate: `WikiHttp` (request/sleep/snippet), `WikiShopFetch.fetchTitlesEmbedding` (make it accessible), `WikiFetch`-style numbered fields for `id`/`id1..idN`, `MonsterTransform.parseQuantityMax`/`.effectiveRarity`/`.parseSpawns`/`.parseOverlay`/`.parseCombatOverlay`/`.applyCombatOverlay`/`.relabelMetallicDragonfire`, `WikiItemFetch.fetch`, map-coord patterns from `WikiShopFetch` (`MAP_COORDS`/`MAP_PLANE` — extract to a shared helper rather than copying).
- Regen sanity gates (Task 10): total sources ≥ 3000; "Lunar Chest" present as REWARD with "Blood moon helm"; "Zulrah", "Vorkath", "Nex" present as KILL with npcIds; "Master Farmer" present as PICKPOCKET; unresolved-item rate < 5%. A systematic parsing gap → fix the parser with a regression test before committing data.

## File Structure

Generator repo (`src/main/java/com/itemsearch/datagen/`):
- `source/DropPageJson.java`, `source/DropTableJson.java`, `source/DropLineJson.java` — raw parse DTOs (snapshot format, raw strings)
- `WikiText.java` — shared wikitext helpers (brace-balanced template scan, comment strip, param split, link strip, map coords) extracted from `WikiShopFetch`
- `WikiDropsFetch.java` — enumeration, fetch, drop-table parsing
- `Rarity.java` — rarity string → probability
- `SourceTransform.java` — snapshots → `OutputSource` list (type inference, joins)
- `SourceDataGenerator.java` — main; writes `sources.json`
- `output/OutputSource.java` (renamed from `OutputNpc` + new fields)
- Committed: `wiki-drops.json` (snapshot), `source-type-overlay.json` (curated)

Plugin repo (`src/main/java/com/itemsearch/`):
- `model/SourceData.java` (renamed from `NpcData` + type/x/y/plane), `model/SourceType.java`
- `data/DropDataService.java` + `data/BundledDataSource.java` (rename methods, load `sources.json`)
- `ItemSearchPanel.java`, `ui/NpcResultView.java`→`ui/SourceResultView.java`, `ui/ItemResultView.java`, `ui/SourceOrder.java`, `map/SpawnMapMarker.java`, `tracker/KillTracker.java`, `respawn/*` — mechanical rename + per-type behavior
- `src/main/resources/com/itemsearch/sources.json` (replaces `drops.json`); test fixture `sources-test.json` (replaces `drops-test.json` + `drops-multiid-test.json` contents, extended with one source of each type)

---

### Task 1: Shared wikitext helpers (`WikiText`)

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/WikiText.java`
- Modify: `src/main/java/com/itemsearch/datagen/WikiShopFetch.java` (delegate `stripLinks`/`params`/map-coord patterns to `WikiText`; delete private copies)
- Test: `src/test/java/com/itemsearch/datagen/WikiTextTest.java`

**Interfaces:**
- Produces (all static on `WikiText`):
  - `String stripComments(String s)` — removes `<!--...-->` spans (non-greedy, multiple)
  - `String stripLinks(String s)` — existing behavior moved verbatim
  - `Map<String, String> params(String templateBody)` — splits on `|` not inside `[[...]]` **or `{{...}}`** (upgrade: the current lookahead only guards links; implement by scanning characters with bracket/brace depth counters instead of regex split)
  - `List<String> templates(String wikitext, String... names)` — every `{{Name|...}}` template body (content between the opening `{{Name` and its **brace-balanced** closing `}}`), scanning depth on `{{`/`}}` pairs; returns bodies in document order, tagged: return type is `List<Found>` where `Found {String name; String body; int start;}`
  - `String field(String infoboxBody, String name)` and `List<String> numberedFields(String infoboxBody, String name)` — moved from `WikiFetch` (delegate `WikiFetch` to these)
  - `int[] mapCoords(String body)` → `{x, y, plane}` or null (move `MAP_COORDS`/`MAP_PLANE` logic from `WikiShopFetch`; plane defaults 0)
- `WikiShopFetch` and `WikiFetch` behavior unchanged — existing suites are the regression net.

- [ ] **Step 1: Write failing tests** — brace-balanced scanning is the new behavior; test it directly:

```java
@Test
public void templatesAreBraceBalanced()
{
    String wikitext = "{{DropsLine|name=Flax|quantity=1000 (noted)|rarity=10/249"
        + "|raritynotes={{Refn|group=d|Exact rarity is 10/249 * 5244/5264}}}}\n"
        + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}";
    List<WikiText.Found> found = WikiText.templates(wikitext, "DropsLine");
    assertEquals(2, found.size());
    assertTrue(found.get(0).body.contains("{{Refn|group=d"));
    assertEquals("DropsLine", found.get(1).name);
}

@Test
public void paramsIgnorePipesInsideNestedTemplatesAndLinks()
{
    Map<String, String> p = WikiText.params(
        "name=[[Al Kharid|Flax]]|rarity=10/249|raritynotes={{Refn|group=d|note}}|rolls=2");
    assertEquals("[[Al Kharid|Flax]]", p.get("name"));
    assertEquals("10/249", p.get("rarity"));
    assertEquals("{{Refn|group=d|note}}", p.get("raritynotes"));
    assertEquals("2", p.get("rolls"));
}

@Test
public void stripCommentsRemovesHtmlComments()
{
    assertEquals("100-299", WikiText.stripComments("100-299<!--note: excluded-->"));
}
```

Plus one test each asserting `stripLinks`, `field`, `numberedFields`, and `mapCoords` behave identically to their current homes (copy an assertion from `WikiShopFetchTest`/`WikiFetchTest` fixtures).

- [ ] **Step 2: Run to verify RED** (`./gradlew.bat test --tests "com.itemsearch.datagen.WikiTextTest"` — compile error expected)
- [ ] **Step 3: Implement `WikiText`** — character-scanning implementations (no regex for `templates`/`params`): track `{{`/`}}` depth and `[[`/`]]` depth; `templates` finds `{{<name>` case-sensitively at depth 0 and captures until depth returns to 0. Then delegate `WikiShopFetch`/`WikiFetch` to it, deleting their private copies (keep their public behavior byte-identical).
- [ ] **Step 4: Run the full generator suite** — `WikiShopFetchTest`/`WikiFetchTest`/`ShopTransformTest` green proves the migration.
- [ ] **Step 5: Commit** — `datagen: shared brace-balanced wikitext helpers (WikiText)`

---

### Task 2: Drop-page parsing (`WikiDropsFetch.extract`)

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/source/DropPageJson.java`, `DropTableJson.java`, `DropLineJson.java`
- Create: `src/main/java/com/itemsearch/datagen/WikiDropsFetch.java` (pure parsing this task)
- Test: `src/test/java/com/itemsearch/datagen/WikiDropsFetchTest.java`

**Interfaces:**
- DTOs (public fields, raw strings):
  - `DropPageJson { String pageTitle; String infobox; /* "Monster","NPC","Scenery",... or null */ List<String> npcIds; /* raw values of id + id1..idN from the infobox */ String combatLevel; Integer mapX, mapY, mapPlane; List<DropTableJson> tables; }`
  - `DropTableJson { String sectionHeading; /* nearest preceding heading at ANY level (===Rare drop table===), stripped, or null */ String topHeading; /* nearest preceding top-level ==heading== (==Drops==), stripped, or null */ List<DropLineJson> lines; }`
  - `DropLineJson { boolean reward; /* true for DropsLineReward */ String name, quantity, rarity, rolls; }`
- `WikiDropsFetch.extract(String pageTitle, String wikitext) → DropPageJson` — null when the page has no drop lines. Consumes `WikiText` throughout. Infobox name captured from the first `{{Infobox <word>` occurrence; ids via `field`/`numberedFields` on its body; map coords via `WikiText.mapCoords`. Comments stripped from the whole page first. Lines with no `name` are dropped; tables with no surviving lines are dropped. Section headings: `sectionHeading` = the last heading at ANY level (equal-count `=` fences, 2-6) before the table's first line offset; `topHeading` = the last exactly-double-`==` heading before that offset. A `===Rare drop table===` under `==Drops==` yields sectionHeading "Rare drop table", topHeading "Drops".

- [ ] **Step 1: Write failing tests** — fixtures as inline strings:

```java
private static final String ZULRAH_LIKE =
    "{{Infobox Monster\n|name = Zulrah\n|combat = 725\n|id1 = 2042\n|id2 = 2043\n|id3 = 2044\n}}\n"
    + "==Drops==\n"
    + "{{DropsTableHead}}\n"
    + "{{DropsLine|name=Tanzanite fang|quantity=1|rarity=1/1024|rolls=2}}\n"
    + "{{DropsLine|name=Zulrah's scales|quantity=100-299<!--separate drop excluded-->|rarity=Always}}\n"
    + "{{DropsLine|name=Flax|quantity=1000 (noted)|rarity=10/249|raritynotes={{Refn|group=d|note}}}}\n"
    + "{{DropsTableBottom}}\n";

@Test
public void parsesMonsterPageWithNumberedIdsAndNestedTemplates()
{
    DropPageJson page = WikiDropsFetch.extract("Zulrah", ZULRAH_LIKE);
    assertEquals("Monster", page.infobox);
    assertEquals(Arrays.asList("2042", "2043", "2044"), page.npcIds);
    assertEquals("725", page.combatLevel);
    assertEquals(1, page.tables.size());
    assertEquals("Drops", page.tables.get(0).sectionHeading);
    assertEquals("Drops", page.tables.get(0).topHeading);
    List<DropLineJson> lines = page.tables.get(0).lines;
    assertEquals(3, lines.size());
    assertEquals("2", lines.get(0).rolls);
    assertEquals("100-299", lines.get(1).quantity); // comment stripped
    assertEquals("10/249", lines.get(2).rarity);    // nested Refn did not break the split
    assertFalse(lines.get(0).reward);
}

@Test
public void rewardChestPageParsesRewardLinesUnderSceneryInfobox()
{
    String chest = "{{Infobox Scenery\n|name = Lunar Chest\n}}\n"
        + "==Rewards==\n{{DropsTableHead}}\n"
        + "{{DropsLineReward|name=Blood moon helm|quantity=1|rarity=1/224}}\n"
        + "{{DropsTableBottom}}\n";
    DropPageJson page = WikiDropsFetch.extract("Lunar Chest", chest);
    assertEquals("Scenery", page.infobox);
    assertTrue(page.npcIds.isEmpty());
    assertTrue(page.tables.get(0).lines.get(0).reward);
    assertEquals("Rewards", page.tables.get(0).sectionHeading);
}

@Test
public void pickpocketSectionHeadingIsCaptured()
{
    String farmer = "{{Infobox NPC\n|name = Master Farmer\n|id = 3257\n}}\n"
        + "==Pickpocketing loot==\n{{DropsTableHead}}\n"
        + "{{DropsLine|name=Potato seed|quantity=1-4|rarity=1/2.2}}\n{{DropsTableBottom}}\n";
    DropPageJson page = WikiDropsFetch.extract("Master Farmer", farmer);
    assertEquals("NPC", page.infobox);
    assertEquals(Collections.singletonList("3257"), page.npcIds);
    assertEquals("Pickpocketing loot", page.tables.get(0).sectionHeading);
}

@Test
public void subsectionHeadingsBecomeCategoriesWithTopLevelSectionKept()
{
    String kalphite = "{{Infobox Monster
|name = Kalphite Queen
|id = 963
}}
"
        + "==Drops==
===100%===
{{DropsTableHead}}
"
        + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}
{{DropsTableBottom}}
"
        + "===Rare drop table===
{{DropsTableHead}}
"
        + "{{DropsLine|name=Loop half of key|quantity=1|rarity=1/128}}
{{DropsTableBottom}}
";
    DropPageJson page = WikiDropsFetch.extract("Kalphite Queen", kalphite);
    assertEquals(2, page.tables.size());
    assertEquals("100%", page.tables.get(0).sectionHeading);
    assertEquals("Drops", page.tables.get(0).topHeading);
    assertEquals("Rare drop table", page.tables.get(1).sectionHeading);
    assertEquals("Drops", page.tables.get(1).topHeading);
}

@Test
public void pagesWithoutDropLinesReturnNull()
{
    assertNull(WikiDropsFetch.extract("Prose", "Just text."));
    assertNull(WikiDropsFetch.extract("Prose", null));
}
```

- [ ] **Step 2: RED** → **Step 3: Implement** (use `WikiText.templates(wikitext, "DropsLine", "DropsLineReward")` for lines with document offsets; group lines into tables by intervening `{{DropsTableHead` occurrences — a new head starts a new table; heading = last `(?m)^==+\s*(.*?)\s*=+=$` match before the table's first line offset) → **Step 4: GREEN + full suite** → **Step 5: Commit** — `datagen: parse wiki drop pages into snapshot DTOs`

---

### Task 3: Enumeration + fetch → `wiki-drops.json`

**Files:**
- Modify: `src/main/java/com/itemsearch/datagen/WikiDropsFetch.java` (add fetch methods), `WikiShopFetch.java` (widen `fetchTitlesEmbedding` to package-static if it is private)
- Test: `src/test/java/com/itemsearch/datagen/WikiDropsFetchTest.java` (pure parts only)

**Interfaces:**
- `WikiDropsFetch.fetchDropPageTitles(Gson) → List<String>` — union (TreeSet) of `embeddedin` on `Template:DropsLine` and `Template:DropsLineReward`, namespace 0, reusing the existing embeddedin pagination helper.
- `WikiDropsFetch.fetchDropPages(Gson, Collection<String>) → Map<String, DropPageJson>` — batched 50 with continue-handling, exactly the `WikiShopFetch.fetchShops` structure, calling `extract`; null pages omitted; TreeMap keyed by title.

Steps: failing test for any newly-extracted pure helper (if the embeddedin helper moves, port its existing test); implement; full suite; commit — `datagen: fetch drop pages from the wiki`.

---

### Task 4: Rarity parser

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/Rarity.java`
- Test: `src/test/java/com/itemsearch/datagen/RarityTest.java`

**Interfaces:**
- `Rarity.parse(String raw) → Double` — null = unknown. Rules (strip links/comments/whitespace first; case-insensitive): `"Always"` → 1.0; `"a/b"` where a and b parse as doubles (commas stripped; b may be fractional like `127.5`) → a/b; `"~a/b"` treated as `a/b`; multiplication suffixes and anything else ("Common", "Varies", "Unknown", empty, null) → null. Guard: result must be in (0, 1] else null.

- [ ] **Step 1: Failing table-driven test:**

```java
@Test
public void parsesKnownForms()
{
    assertEquals(1.0, Rarity.parse("Always"), 1e-12);
    assertEquals(1.0 / 1024, Rarity.parse("1/1024"), 1e-12);
    assertEquals(10.0 / 249, Rarity.parse("10/249"), 1e-12);
    assertEquals(1.0 / 127.5, Rarity.parse("1/127.5"), 1e-12);
    assertEquals(3.0 / 128, Rarity.parse("~3/128"), 1e-12);
    assertEquals(1.0 / 5000, Rarity.parse("1/5,000"), 1e-12);
}

@Test
public void unknownFormsReturnNull()
{
    assertNull(Rarity.parse("Common"));
    assertNull(Rarity.parse("Varies"));
    assertNull(Rarity.parse(null));
    assertNull(Rarity.parse(""));
    assertNull(Rarity.parse("2 x 1/128"));
    assertNull(Rarity.parse("5/4")); // > 1 is nonsense
    assertNull(Rarity.parse("0/128"));
}
```

Steps: RED → implement → GREEN → commit — `datagen: rarity string parser`.

---

### Task 5: Source transform (`SourceTransform` + `OutputSource`)

**Files:**
- Modify: `output/OutputDrop.java` gains `public String category;` (nullable; assign after construction where MonsterTransform builds drops — null there)
- Rename: `output/OutputNpc.java` → `output/OutputSource.java` (git mv; class rename; add `public String type; public Integer x, y, plane;`; keep the existing constructor signature plus the new fields assigned after construction, or extend the constructor — extend it: append `String type, Integer x, Integer y, Integer plane` parameters and update `MonsterTransform`'s construction sites to pass `"KILL", null, null, null`)
- Create: `src/main/java/com/itemsearch/datagen/SourceTransform.java`
- Create: `source-type-overlay.json` (committed; initially `{}`)
- Test: `src/test/java/com/itemsearch/datagen/SourceTransformTest.java`

**Interfaces:**
- `SourceTransform.transform(Map<String, DropPageJson> pages, Map<String, ItemInfoJson> itemInfo, Map<Integer, List<OutputSpawn>> spawns, Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay, Map<String, String> typeOverlay) → List<OutputSource>`
- Per page: one `OutputSource` per **type bucket** (a Master-Farmer-like page with both kill drops and pickpocket tables yields two sources, name suffixed ` (pickpocket)` for the PICKPOCKET one). Type per table via the Global Constraints inference + `typeOverlay` (title → type wins over inference, applied per page: an overlaid page forces ALL its tables to that type).
- npcIds: parsed ints of the page's raw id list (bad values skipped); canonical `id` = first npcId, else the next synthetic negative id (sequence starting at -1000, assigned in sorted-name order after all NPC-backed sources are numbered).
- Drops: `category` = the table's `sectionHeading`, null when it equals (case-insensitive) "Drops", "Rewards", or "Loot" or is null — generic section names are not categories; `itemId` via `itemInfo` id else -1; `rarityKnown` = `Rarity.parse(...) != null`; `rarity` = `MonsterTransform.effectiveRarity(parsed, rolls)` (rolls int, default 1); `quantity` = `MonsterTransform.parseQuantityMax(raw)`; `noted` = raw quantity contains "(noted)".
- combatLevel parsed int else 0; KILL sources join `wiki`/`combatOverlay` combat exactly as `DropDataGenerator.generate` does today (including `relabelMetallicDragonfire`); non-KILL sources get null combat. Spawns joined by npcId for NPC sources; chest/node pages with map coords get a single synthetic spawn point at (x, y, plane) so the existing spawn-marker flow works, plus x/y/plane set on the source itself.
- Output sorted by name (case-insensitive), then type.

- [ ] **Step 1: Failing tests** covering: KILL page with ids/combat level; REWARD chest (Scenery + reward lines + coords → synthetic negative id, single spawn point, type REWARD); PICKPOCKET section split into its own source with ` (pickpocket)` suffix; overlay forcing a title to GATHER; rarity known/unknown paths; noted quantity; itemId fallback to -1. Construct DTOs by hand exactly as `ShopTransformTest` does (builder helpers at the top of the test).
- [ ] **Step 2: RED** → **Step 3: Implement** → **Step 4: full suite GREEN** (includes `MonsterTransformTest`/`DropDataGeneratorTest` still passing with the extended `OutputSource` constructor) → **Step 5: Commit** — `datagen: transform wiki drop pages into typed sources`

---

### Task 6: `SourceDataGenerator` main + retire osrsbox path

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/SourceDataGenerator.java`
- Modify: `build.gradle.kts` (register `runSources` JavaExec, workingDir = projectDir), `README.md` (rewrite: wiki sweep replaces osrsbox; document snapshots, overlay, gates), `curate_combat.py` (`DROPS` path → `.../com/itemsearch/sources.json`)
- Delete: nothing yet (`DropDataGenerator`/`MonsterTransform` inputs stay until Task 10 confirms the new data; note the retirement in README)

**Interfaces:**
- Flags: `--refresh-drops` (titles + pages → `wiki-drops.json`), `--refresh-items` (all drop names + shop names → shared `wiki-items.json`), `--refresh-wiki` (combat snapshot, reusing `WikiFetch.fetch` keyed off drop-page titles that are Monster pages). Then always: transform → `../../JAVA/ItemSearch/src/main/resources/com/itemsearch/sources.json` (positional override supported). Prints: page/source/type counts, unresolved-item and unknown-rarity rates, combat gaps.
- Missing snapshot → IOException naming the flag to run (same UX as `ShopDataGenerator`).

Steps: wire (structure mirrors `ShopDataGenerator.main` — no new logic beyond flag handling and the joins already tested in Task 5), `compileJava` + full suite, commit — `datagen: SourceDataGenerator joins wiki snapshots into sources.json`.

---

### Task 7: Plugin model + data layer rename

**Files (plugin repo):**
- Modify: `model/DropSource.java` gains `String category;` as the last @Value field (nullable; fixtures may omit it)
- Rename: `model/NpcData.java` → `model/SourceData.java` (add `SourceType type; Integer x, y, plane;` after `combat`); Create: `model/SourceType.java` (`public enum SourceType { KILL, REWARD, PICKPOCKET, GATHER }`)
- Modify: `data/DropDataService.java` (`npcById`→`sourceById`, `npcByNameAndLevel`→`sourceByNameAndLevel`, `searchNpcs`→`searchSources`, types `NpcData`→`SourceData`), `data/BundledDataSource.java` (resource `sources.json`; normalize null `type` in older data to `KILL`), `ItemSearchPlugin.java` (`DATA_RESOURCE = "/com/itemsearch/sources.json"`), and every `NpcData` reference (`ItemSearchPanel`, `ui/*`, `map/SpawnMapMarker`, `tracker/KillTracker`, `respawn/*`) — mechanical rename via `sed` (`NpcData`→`SourceData`, `npcById`→`sourceById`, `npcByNameAndLevel`→`sourceByNameAndLevel`, `searchNpcs`→`searchSources`) then hand-fix compile fallout.
- Test fixture: create `sources-test.json` = `drops-test.json` entries (each gains `"type": "KILL"`) + `drops-multiid-test.json` entries + two new entries: `{"id": -1000, "name": "Lunar Chest", "type": "REWARD", "combatLevel": 0, "x": 1512, "y": 9581, "plane": 0, "spawnPoints": [{"x": 1512, "y": 9581, "plane": 0}], "npcIds": [], "drops": [{"itemId": 29028, "itemName": "Eclipse atlatl", "rarity": 0.004464285714285714, "quantity": 1, "noted": false, "rarityKnown": true}]}` and `{"id": 3257, "name": "Master Farmer (pickpocket)", "type": "PICKPOCKET", "combatLevel": 0, "npcIds": [3257], "spawnPoints": [], "drops": [{"itemId": 5318, "itemName": "Potato seed", "rarity": 0.45, "quantity": 4, "noted": false, "rarityKnown": true}]}`. Update every test that loads `drops-test.json`/`drops-multiid-test.json` to the new fixture path; keep the old json files deleted.

**Interfaces (later tasks rely on):** `SourceData.getType()` never null after load; `searchSources(String)` returns all types; everything else keeps today's contracts.

Steps: failing test first — extend `BundledDataSourceTest` (renamed expectations) with `typedSourcesLoadAndDefaultToKill` asserting the Lunar Chest entry has `SourceType.REWARD` and a legacy entry without `type` loads as `KILL`; RED (compile) → rename + implement → full suite GREEN → commit — `sources: SourceData model with typed, mappable sources`.

---

### Task 8: Search badges + item-view grouping

**Files:**
- Modify: `ItemSearchPanel.java` (badge by `source.getType()`: KILL → "NPC", REWARD → "CHEST", PICKPOCKET → "PICKPOCKET", GATHER → "NODE"), `ui/ItemResultView.java` ("Dropped by" header → "Obtained from"; card sub-label prefixes non-KILL types: `"Reward · "`, `"Pickpocket · "`, `"Gather · "` before the rate text; `Kills since drop` row renders only for sources with non-empty npcIds — REWARD/GATHER show Expected only), `ui/SourceOrder.java` (owner resolution unchanged — verify only)
- Tests: `ItemSearchPanelTest` (badge per type from the fixture), `ItemResultViewTest` (Lunar Chest card shows "Reward · 1/224", has Expected, lacks "Kills since drop")

Steps: failing tests → implement → full suite → commit — `sources: typed badges and per-type item cards`.

---

### Task 9: Source view per-type behavior

**Files:**
- Rename: `ui/NpcResultView.java` → `ui/SourceResultView.java` (+ test rename)
- Modify: the drop list renders grouped by `DropSource.getCategory()` in first-appearance order — a `section(category)` header precedes each group; null categories render first without a header (test: a fixture source with "100%" and "Rare drop table" categories shows both headers, uncategorized rows on top). Combat/attack sections render only when `type == KILL && combat != null`; kill-count/respawn sections only when `npcIds` non-empty; a "Show on map" button (same pattern as `ShopResultView`) when x/y set; header sub-label shows the type for non-KILL sources.

Steps: failing tests (REWARD source: no combat section, map button present and firing `SpawnMapMarker`-bound callback; KILL source unchanged assertions keep passing) → implement → full suite → commit — `sources: per-type source view (chests get map, kills keep combat)`.

---

### Task 10: Generate real data + gates + retire old data

Run in the generator repo: `./gradlew.bat runSources --args="--refresh-drops --refresh-items"` (expect a few minutes; `--refresh-wiki` optional this run — the committed combat snapshot still joins by title). Apply the Global Constraints sanity gates; investigate/fix parser gaps with regression tests before committing (the shop rollout found real ones — expect the same). Spot-check and record in the report: Lunar Chest REWARD lines, Zulrah npcIds `[2042, 2043, 2044]`-style list, a pickpocket source, unknown-rarity rate. Then:
- generator: commit snapshot + overlay + README — `datagen: generate typed source data from wiki sweep`
- plugin: delete `drops.json`, add `sources.json`, full suite, commit — `sources: bundle wiki-swept source data`
- Delete now-dead osrsbox code if nothing references it (`MonsterJson`-driven paths in `DropDataGenerator`/`MonsterTransform.transformAll`): only if the full suite stays green after removal; otherwise leave with a README deprecation note and a ledger entry.

---

### Task 11: Final verification

Both repos' full suites; run the plugin repo's `./gradlew.bat run` smoke launch is NOT possible for agents (user verifies in-game per AGENTS.md — report what to test: search "blood moon helm" → CHEST source with map button; "uncut sapphire" → compact typed list; "master farmer" → PICKPOCKET badge). Sync to the runelite fork is the controller's post-plan step, not a task.
