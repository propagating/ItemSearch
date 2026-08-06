# Source View Refinements — Design

Date: 2026-08-06
Status: Approved (2026-08-06). Delivered in two passes; Pass 2 starts only after
Pass 1 is confirmed in-game.

Follows the universal-drop-sources rework
(`2026-08-05-universal-drop-sources.md`), whose data this builds on.

## Origin

Seven requests from the user (2026-08-06), plus two regressions found while
investigating a client freeze they hit during Task 11 verification.

One request needed no work: the dry counter already counts *instances*, not
quantity. `KillTracker` collects dropped items into a `Set<Integer>` and
`TrackerStore.recordKill` does `merge(key, 1, sum)`, so a drop of 50 runite
bolts increments "Got" by exactly 1. Left alone.

## Resolved: the freeze

The user's client froze (whole machine lagging, force quit) after clicking
`'No fingers' (pickpocket)`. It was never reproduced directly — headless, that
view builds in 287 ms from 2 drops and 1 spawn point, and no exception appears
in the client log, consistent with a hang rather than a crash.

The uncapped spawn points (item 1) are the most likely cause: with auto-mark
enabled, opening `Rat` or another high-spawn monster adds hundreds of world-map
markers, and those accumulate across clicks. **The user confirmed the freeze is
gone after Pass 1** (2026-08-06), which fits that explanation.

---

## Pass 1 — regressions and UI

No network. Verifiable in one client launch.

### 1. Restore the spawn cap

`MonsterTransform` capped spawn points at `MAX_SPAWNS_PER_NPC = 25`.
`SourceTransform.collectSpawns` was written without that cap, so `Rat` now
carries 731 spawn points, `Maniacal monkey` 366, `Spider` 294 — 16154 across
all sources. With auto-mark enabled, opening one of those adds hundreds of
`WorldMapPoint`s in a loop, which is a plausible client freeze.

- `SourceTransform` applies the same cap of 25, taking the first N by npc id
  order so the choice is deterministic across regenerations.
- `SpawnMapMarker.showSpawns` refuses to add more than a fixed ceiling of
  markers regardless of what the data says, and logs when it truncates. Data
  should not be able to hang the client — the cap belongs in both places.
- Regenerate `sources.json` (transform only, no network).

### 2. Expand-on-click item cards

`Coins` currently renders 2143 source cards — 18681 Swing components — and the
real `ItemManager` fires an async image load per card. The 10%/50%/90% bands
the user wants back would make it worse if added to every card.

- A source card collapses to what it shows today (name, qualifier, Expected,
  and the kill counter where it applies).
- A disclosure control on the card expands it in place to reveal the percentile
  bands. It must be its own control, **not** the card body: pressing the card
  already navigates to that source, and that behaviour stays.
- Expansion state persists across sessions, keyed by `itemId:sourceId`, stored
  with the existing tracker config data rather than a new file.
- Percentile bands appear only here. The source (NPC) view stays compact —
  that was the point of the original compaction.

### 3. Delete the respawn subsystem

Removes `respawn/` (`RespawnTracker`, `RespawnOverlay`, `RespawnTimeStore`,
`RespawnEntry`, `WatchedNpc`) and its two tests, the overlay registration in
`ItemSearchPlugin`, the `showRespawnTiles` config item, and the
`RespawnTracker` threading through `ItemSearchPanel` into `SourceResultView`.
The Map button is then alone on that row.

Orphaned config values and any stored respawn timings are left where they are;
they are inert once nothing reads them.

### 4. Badge and heading legibility

- `PICKPOCKET` becomes `STEAL`.
- Badges get a fixed-width column so a long badge stops squeezing the name
  beside it, and every row's name starts at the same x.
- Drop-table group headings in the source view render at full width so long
  names ("Rare drop table") are not clipped.

---

## Pass 2 — generator

Network. Regenerates the bundled data, so it lands only once Pass 1 is
confirmed working.

### 5. Resolve transcluded drop tables

149 of 2375 enumerated pages (6.3%) produce no drop table because their table
lives in a template the page transcludes — `{{Revenants}}`, the Deadman and elf
variants. The fetch reads raw wikitext, which does not expand transclusions, so
every revenant is missing.

- After parsing a page, find its transcluded templates; for any that itself
  contains drop lines, fetch that template's wikitext once (cached by name —
  many pages share one table) and splice it in before extraction.
- One level of expansion. If a spliced template transcludes another, leave it
  and count it, rather than recursing without bound.

### 6. Drop-table provenance and grouping

Splicing `{{RareDropTable}}` tells us precisely that those lines *are* the rare
drop table — provenance the section heading only approximates.

- `DropLineJson`/`OutputDrop`/`DropSource` gain `dropTable`, the canonical
  shared-table name, distinct from the existing `category` (the page's wiki
  subsection).
- A config item chooses how the source view groups drops: by wiki section
  (today's behaviour, the default) or by drop table.
- A drop with no shared table falls back to its category under either setting.

### 7. Import hybrid attack styles

`wiki-combat.json` has 18 pages reading `[[Magical melee]]` and more reading
`[[Magical ranged]]`. These are true hybrids — one attack rolling magic
accuracy, defended as melee/ranged — and map exactly onto the curator's
`prayStyle`. `CombatParser` does not recognise them, so they sit in the review
queue.

- `CombatParser` parses the six hybrid pairs into one attack carrying `style` +
  `prayStyle`, clearing those pages from the queue.
- Multi-style monsters (`[[Crush]], [[Ranged]], [[Magic]]`) are **not** hybrids
  and stay curator work: the wiki does not say which max hit belongs to which
  style, which is exactly the ambiguity `gap` marks.

### 8. Regenerate

Re-run the generator to bake in the user's curation and re-seed the 16 stubs
the still-open curator GUI overwrote. Apply the sanity gates in the generator
README against the new baseline.

## Testing

Pass 1: the spawn cap gets a transform test and a `SpawnMapMarker` truncation
test; card expansion and its persistence get view tests; badge width and
heading rendering get assertions in the existing panel and source-view suites;
deleting the respawn package must leave the suite green with no dangling refs.

Pass 2: transclusion splicing gets wikitext fixtures (including a revenant-shaped
page); `dropTable` tagging gets a `{{RareDropTable}}` fixture; the hybrid table
gets a parser test per pair; `BundledSourceDataTest` gains a revenant assertion
once the data lands.

## Out of scope

`{{DropsLineClue}}` (468 pages, no `name=` param — the item derives from
`type=elite`), and the ~5% of drops that resolve to no item id because
`WikiItemFetch` does not follow wiki redirects (`Prayer potion(4)` →
`Prayer potion`). Both are quantified in the generator README.
