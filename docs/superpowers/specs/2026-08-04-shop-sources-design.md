# Shop Sources for Drop Search — Design

Date: 2026-08-04
Status: Approved

## Goal

Show, in the drop search plugin, when an item can be purchased from a shop. Shop
entries whose base stock is 0 do not count as a way to purchase the item (for
now). Shops are also searchable from the NPC search view, where their full
catalog is shown with 0-stock lines greyed out.

## Decisions (from brainstorming)

- **Display scope**: shops appear both in item results ("Buy from" section) and
  in NPC search (shop catalog card).
- **Data source**: OSRS wiki shop pages, fetched and parsed by the offline
  generator following the existing `WikiFetch` pattern; committed snapshots.
- **0-stock rule**: entries stay in the bundled data. The item-search "Buy from"
  list excludes them entirely; the shop catalog view shows them greyed out with
  a "not stocked" note. The rule lives in one client-side filter so it can be
  lifted later without regenerating data.
- **Fields per shop entry** (item view): shop name + location, sell price with
  currency, base stock, restock time.
- **Architecture**: Approach A — shops are a separate entity with their own
  bundled `shops.json`; monster data (`drops.json`) is untouched.
- **Repo layout** (post-migration, 2026-08-04): the plugin lives in
  `E:\Projects\JAVA\ItemSearch` (package `com.itemsearch`, Plugin Hub
  template); the offline generator lives in
  `E:\Projects\Python\ItemSearchDataGenerator` (package
  `com.itemsearch.datagen`) and writes `shops.json`/`drops.json` into the
  plugin repo. All commits go to these two repos — never to the local
  runelite fork (its plugin copy is legacy/testing only).

## 1. Data model & files

Generator side (the ItemSearchDataGenerator repo):

- `wiki-shops.json` — committed snapshot of raw parsed shop pages (analogous to
  `wiki-combat.json`).
- `wiki-items.json` — committed snapshot of item name → `{id, value}` from wiki
  `{{Infobox Item}}` pages, for **all** distinct shop item names. (Originally
  planned as ids-only for unresolvable names, but the wiki research showed shop
  sell prices are usually *derived* — `floor(item value × sellmultiplier /
  1000)` — so the item pass must fetch `value` for every shop item anyway.
  Monster drop data remains an id fallback when an item page has no id.)

Client side: new bundled resource `shops.json` next to `drops.json`:

```json
[{
  "name": "Zaff's Superior Staffs",
  "location": "Varrock",
  "currency": "Coins",
  "items": [{
    "itemId": 1381,
    "itemName": "Staff of air",
    "stock": 5,
    "sellPrice": 1500,
    "restockSeconds": 100
  }]
}]
```

- 0-stock entries are kept in the data.
- Unresolvable item names get `itemId: -1`; they render in the shop catalog
  (no icon) but cannot join to item search.
- `stock` semantics: `-1` = infinite (wiki `∞`/`inf`, always purchasable),
  `null` = unknown (treated as purchasable), `0` = not purchasable.
- `restockSeconds` is converted from the wiki's tick value
  (`round(ticks × 0.6)`); `null` when the wiki says N/A/unknown.
- `sellPrice` and `restockSeconds` may be `null` (rendered "?" in the UI).

New client models in `model/`: `ShopData` (name, location, currency, items) and
`ShopItem` (itemId, itemName, stock, sellPrice, restockSeconds), mirroring
`NpcData`/`DropSource` (`@Value` Lombok classes).

## 2. Generator pipeline

New `WikiShopFetch` following `WikiFetch`'s conventions (batch size 50, 200 ms
sleep between requests, same User-Agent string style):

1. **Enumerate** shop pages via the MediaWiki API `list=embeddedin` on
   `Template:StoreLine` — finds every page embedding a store table without
   guessing category names. Paginate with `continue`.
2. **Fetch** wikitext in batches of 50. Parse per page:
   - `{{Infobox Shop}}`: location.
   - Each `{{StoreTableHead}}` block: `currency` (defaults "Coins"),
     `sellmultiplier`, `namenotes`. A page may contain several store tables;
     each becomes its own shop record (first keeps the page title, later ones
     get a numbered suffix).
   - Each `{{StoreLine|name=…|stock=…|restock=…}}` row until
     `{{StoreTableBottom}}`. Raw values are kept as strings in the snapshot
     (like `wiki-combat.json`); rows with no name are skipped.
3. **Fetch item info**: one wiki pass over all distinct shop item names reads
   `{{Infobox Item}}` `|id=` and `|value=`, snapshotted to `wiki-items.json`.
   Sell price = explicit `sell=` override when present, else
   `floor(value × sellmultiplier / 1000)`, else null. Ids fall back to the
   monster drop data's name → id map; still-unresolved names get `itemId: -1`.
4. **Join** snapshots into `shops.json` via a new `ShopDataGenerator` main
   class (keeps `DropDataGenerator` untouched; it takes the monster input path
   only to build the name → id map), writing to
   `src/main/resources/com/itemsearch/shops.json`.

New source/output DTOs mirror the existing layout: `source/ShopPageJson` (raw
parse) and `output/OutputShop` / `output/OutputShopItem`.

## 3. Client data layer

`DropDataService` gains:

- `List<ShopEntry> shopsForItem(int itemId)` — where `ShopEntry` is a small
  `@Value` pair type in `model/` holding the `ShopData` and the matching
  `ShopItem` line, **filtered to stock > 0**. This single filter implements
  the "for now" rule.
- `List<ShopData> searchShops(String query)` — case-insensitive substring match
  on shop name and location, sorted by name.

`BundledDataSource` loads `shops.json` as a second resource path in `load()`,
tolerating absence/corruption exactly like `drops.json` (log a warning, shop
features silently absent). New indexes: `itemId → List<ShopEntry>` and the
shop list for search. Malformed shop/item entries are skipped with a warning,
not fatal.

## 4. UI

- **Item view** (`ItemResultView`): after the monster source boxes, a
  "BUY FROM" section header (same style as existing section headers), then one
  compact card per shop entry, sorted by sell price ascending: shop name
  (clickable, brand orange → opens shop view), location sub-label, and stat
  rows Price (with currency), Stock, Restock.
- **NPC search** (`ItemSearchPanel`): shop-name matches are appended after
  monster matches in the result list, tagged "Shop"; selecting one opens
  `ShopResultView`.
- **`ShopResultView`** (new, in `ui/`): back button, header (shop name,
  location, currency), then one row per catalog item: item icon (via
  `ItemManager` when itemId ≥ 0), name, price, stock. 0-stock rows are dimmed
  and annotated "not stocked". Rows with a known item id are clickable and
  jump to that item's `ItemResultView`.

## 5. Error handling & testing

Error handling:

- Missing/corrupt `shops.json` → shop features absent, everything else works.
- `itemId: -1` entries: shown in shop catalog, never joined to item search.
- Null price/stock/restock from parsing: rendered as "?" in the UI rather than
  dropped.

Testing:

- Generator: wikitext fixture tests for `{{StoreLine}}`/`{{Infobox Shop}}`
  parsing; id-resolution fallback order; 0-stock entries retained in output.
- Client: `BundledDataSource` shop indexing + stock>0 filter (new
  `shops-test.json` fixture); `ItemResultViewTest` covers buy-from section
  presence, price sort, and 0-stock exclusion; shop search matching/ordering;
  new `ShopResultViewTest` covers greyed 0-stock rows and icon-less `-1` rows.

## Out of scope

World-map markers for shop locations, live in-game stock reading, members-only
filtering, restock simulation, dynamic price changes as stock depletes.
