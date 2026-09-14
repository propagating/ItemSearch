# Shopkeeper + Pickpocket Page Implementation Plan

**Goal:** An NPC who runs a shop and can be pickpocketed has one page with a Purchasable section and a "Stealable @ level · xp" section; every pickpocket page shows its thieving level.

**Architecture:** The generator adds the shop owner from the shop infobox and thieving level/xp from the wiki's Thieving table. The plugin indexes shops by owner, routes both the shop and the pickpocket entry to `SourceResultView` with the shop attached, and that view draws the shop rows above the loot.

**Tech Stack:** Java 11, Swing, Gson, JUnit 4 + Mockito. Generator repo `E:\Projects\Python\ItemSearchDataGenerator`.

**Spec:** `docs/superpowers/specs/2026-09-12-shopkeeper-pickpocket-design.md`

## Global Constraints

- Java 11; plugin tabs, generator 4 spaces; Allman braces.
- No commits unless the user asks. Refreshes that touch the wiki (`--refresh-shops`, `--refresh-thieving`) are part of the generator's normal flow.
- `gradlew sideload` after plugin changes.

---

### Task 1: Shop owner (generator)

**Files:** `source/ShopPageJson.java` (+ `public String owner;`), `WikiShopFetch.java:80` (`page.owner = stripLinks(WikiText.params(body).get("owner"));`), `output/OutputShop.java` (+ `public String owner;`), `ShopTransform.java:62` (`shop.owner = page.owner;`), tests in `WikiShopFetchTest` and `ShopTransformTest`.

- [ ] Test: an infobox with `|owner = [[Aubury]]` yields `owner == "Aubury"`; a page without one yields null; the transform carries it onto every table's shop.
- [ ] Implement; run generator tests.

### Task 2: Thieving table (generator)

**Files:** Create `WikiThievingFetch.java` and `source/ThievingJson.java` (`public Integer level; public Double xp;`); modify `SourceDataGenerator.java` (flag `--refresh-thieving`, snapshot `wiki-thieving.json`, pass the map into `SourceTransform.transform`), `output/OutputSource.java` (+ `public Integer thievingLevel; public Double thievingXp;`), `SourceTransform.java` (set both on PICKPOCKET buckets by page title, case-insensitive); tests `WikiThievingFetchTest`, `SourceTransformTest`.

**Interfaces:** `WikiThievingFetch.parse(String wikitext)` → `Map<String, ThievingJson>` keyed by NPC name; `WikiThievingFetch.fetch(Gson)` downloads the Thieving page's wikitext with `WikiHttp` and parses it.

- [ ] Test the parser on this excerpt:

```
{| class="wikitable sortable align-center-1"
!colspan="2"| Name
!{{SCP|Thieving}} Level
![[Experience|XP]]
!{{plinkp|Ardougne cloak 3}} 100% success lvl
![[Stun (status)|Stun]] damage 
!Notes
|-
|{{plinkt|Man|pic=Thief Man}}/[[Woman]]/[[Citizen (Civitas illa Fortis)|Citizen]]
| 1 || 8 || 85 || 1
| Found all around [[Gielinor]].
|-
|{{plinkt|Master Farmer|pic=Thief Master farmer}}
|38 || 43 || 94 || 3
| Notes
|}
```

expecting Man, Woman, Citizen → level 1, xp 8; Master Farmer → 38, 43. Parsing: split on `|-`; the first cell after the row start holds names, taken from every `{{plinkt|NAME|...}}` and `[[NAME]]` / `[[TARGET|NAME]]` in it (display name for piped links); the next line's `||`-separated cells give level (int) and xp (double).

- [ ] Implement; wire the flag and snapshot in `SourceDataGenerator` next to the other refreshes; in `SourceTransform.finish`, for `"PICKPOCKET".equals(b.type)` look up `thieving.get(pageTitle.toLowerCase())` (build the lookup map lower-cased) and set the two fields. Test: a pickpocket bucket for "Master Farmer" gets 38 and 43.0; one with no row gets nulls.
- [ ] Run generator tests; run `runShops --refresh-shops`, then `runSources --refresh-thieving`.

### Task 3: Plugin model and routing

**Files:** `model/ShopData.java` (+ `String owner;` and the constructor call in `BundledDataSource.indexShops`), `model/SourceData.java` (+ `Integer thievingLevel; Double thievingXp;` and all constructor call sites: `SourceData.withDrops`, `BundledDataSource` ×2, `SourceResultView.withRecorded`, tests ×9), `data/DropDataService.java` (+ `ShopData shopOwnedBy(String npcName)`), `data/BundledDataSource.java` (owner index), `ItemSearchPanel.java` (`renderShop` and `renderNpc` routing), tests `ItemSearchPanelTest`, a shops fixture entry with an owner.

- [ ] Add to `shops-test.json` a shop `{"name": "Martin's Seeds", "owner": "Master Farmer", "currency": "Coins", "items": [...]}`; test `shopOwnedBy("master farmer")` finds it and `shopOwnedBy("Goblin")` is null.
- [ ] Panel tests: opening that shop (call the private `showShop` via reflection as other tests do) leaves `currentNpcView` non-null and shows "Purchasable"; opening the "Master Farmer (pickpocket)" source shows "Purchasable" too; opening the other fixture shop shows a `ShopResultView`.
- [ ] Implement routing: in `renderShop`, `SourceData thief = shop.getOwner() == null ? null : dataService.sourceByNameAndLevel(shop.getOwner() + " (pickpocket)", 0); if (thief != null && thief.getType() == SourceType.PICKPOCKET) { renderNpc(thief, shop); return; }`. In `renderNpc(npc)`: `ShopData shop = npc.getType() == SourceType.PICKPOCKET ? dataService.shopOwnedBy(npc.wikiTitle()) : null;` and pass it to the view.

### Task 4: The merged view

**Files:** `ui/SourceResultView.java` (new constructor param `ShopData shop`, nullable; old signature delegates with null), `ui/ShopResultView.java` (make `buildRow` a package-private static `ShopRows.row(shop, item, itemManager, onItemSelected)` in a new `ui/ShopRows.java` used by both views), tests `SourceResultViewTest`.

- [ ] Tests: a pickpocket source with `thievingLevel 38, thievingXp 43.0` shows a label "STEALABLE @ 38 THIEVING · 43 XP" (section labels are upper-cased); one without shows no such label; with a shop attached the view shows "PURCHASABLE", the shop's location/currency line, and a row per shop item; the name card shows "Pickpocketing · Shopkeeper".
- [ ] Implement: after the Actions card, when `shop != null`: `body.add(section("Purchasable"))` inside a card with the location · currency line and `ShopRows.row(...)` per item (items open via `onItemSelected` with `new ItemMatch(id, name)`). Then, when the source is PICKPOCKET and `thievingLevel != null`, `body.add(groupHeading("Stealable @ " + level + " Thieving" + (xp != null ? " · " + Format.tally(xp) + " xp" : "")))` before the drop groups; the drop groups follow as today. In `buildNameBox`, the type label becomes `"Pickpocketing · Shopkeeper"` when a shop is attached. In `buildActionsBox`, when the source has no map tiles and `shop != null && shop.getX() != null`, add `MapToggle.create(() -> mapMarker.showShop(shop), mapMarker::clear)`.
- [ ] Run plugin tests; sideload.

### Task 5: Verify in-game

- [ ] Search "Martin the Master Gardener": the shop row and the pickpocket row both open one page with Purchasable and Stealable @ 38 Thieving · 43 xp. Open Master Farmer: Stealable heading, no shop. Open Aubury's Rune Shop: the ordinary shop page.
