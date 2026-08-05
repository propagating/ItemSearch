# Universal Drop Sources — Design

Date: 2026-08-05
Status: Draft — awaiting user review

## Goal

Every obtainable item shows every way to get it: NPC kill drops (current),
shop purchases (current), reward chests and raids, pickpocketing, and
skilling/gathering nodes. The frozen osrsbox monster database (archived
~2021, kills only) is replaced by a live OSRS Wiki sweep, which also fixes
missing post-2021 content (e.g. Moons of Peril / blood moon gear) and stale
drop rates.

## Decisions (from scoping questions, 2026-08-05)

- **Full wiki sweep** replacing osrsbox entirely — one pipeline, everything
  current, snapshot-pinned for reproducibility like the shop data.
- **All source types in scope**: kills, reward chests/raids, pickpocketing,
  skilling/gathering nodes (plus existing shops).

## 1. Data model

`NpcData` generalizes to `SourceData` (same file rename in `model/`):

```java
@Value
public class SourceData
{
	int id;                  // canonical id: NPC id for NPC sources, else a synthetic
	                         // negative id assigned by the generator (stable per name)
	String name;
	SourceType type;         // KILL, REWARD, PICKPOCKET, GATHER
	int combatLevel;         // 0 for non-combat sources
	String wikiImageUrl;
	List<DropSource> drops;
	List<SpawnPoint> spawnPoints;  // NPC spawns; single location point for chests/nodes
	List<Integer> npcIds;    // empty for non-NPC sources
	CombatStats combat;      // null for non-KILL sources
	Integer x;               // wiki {{Map}} location for chests/nodes; null otherwise
	Integer y;
	Integer plane;
}
```

- `DropSource` unchanged (itemId, itemName, rarity, quantity, noted,
  rarityKnown).
- `drops.json` becomes `sources.json` (schema version break is fine — the
  plugin is unreleased). Search indexes and kill tracking key off `npcIds`
  exactly as today; non-NPC sources simply never match a kill event.

## 2. Generator pipeline (replaces osrsbox input)

1. **Enumerate** pages embedding `Template:DropsLine` and
   `Template:DropsLineReward` (same `embeddedin` pattern as shops; both
   template queries unioned).
2. **Fetch** wikitext (batch 50, 200 ms politeness), snapshot to
   `wiki-drops.json` (raw parsed tables, strings preserved).
3. **Parse per page**:
   - Drop tables: `{{DropsTableHead}}` … `{{DropsLine|…}}` /
     `{{DropsLineReward|…}}` rows (name, quantity, rarity, rolls).
   - **Source type inference**: `DropsLineReward` rows → REWARD. `DropsLine`
     under an `Infobox Monster` page → KILL, unless the table's nearest
     preceding section heading contains "pickpocket" → PICKPOCKET. Non-NPC
     infobox (Scenery etc.) → GATHER. A committed `source-type-overlay.json`
     corrects misclassifications by page title (same curated-overlay pattern
     as combat data).
   - `Infobox Monster |id=` list → npcIds; `|combat=` → combatLevel;
     `{{Map}}` coords for chest/node pages (same parser as shops).
   - **Rarity**: fractions ("1/128", "3/128", "1/127.5") → probability;
     "Always" → 1.0; textual ("Common", "Varies", "Unknown") →
     rarityKnown=false. Multi-roll tables multiply by `rolls` when present.
4. **Join**: item ids + values from the (extended) `wiki-items.json` pass
   covering every drop name; spawns from the existing mejrs `npc-spawns.json`
   keyed by npcId; combat from `wiki-combat.json` (keyed by title, unchanged);
   `combat-overlay.json` continues to apply.
5. **Output** `sources.json` to the plugin repo. `monsters-complete.json`
   and its README section are retired; the curator scripts re-point at the
   new snapshot.

Scale: a few thousand pages ≈ 60–100 batched requests — minutes, not hours.

## 3. Client changes

- `BundledDataSource` loads `sources.json`; existing name/level/id indexes
  unchanged. `searchNpcs` becomes `searchSources` (badge per type below).
- **Search badges**: NPC, CHEST, PICKPOCKET, NODE (and existing SHOP, ITEM).
- **Item view order**: Buy from → everything else grouped under one
  "Obtained from" header, cards sorted by source name; each card's sub-label
  carries the type when it isn't a kill ("Reward · Lunar Chest",
  "Pickpocket · Master Farmer"). Compact card format (Expected / Kills since
  drop) applies to KILL and PICKPOCKET (tracked via npcIds); REWARD/GATHER
  cards show Expected only (no kill tracking).
- **Source view** (today's NpcResultView): combat/kill sections render only
  for KILL sources; chests/nodes show their map button (coords) instead of
  spawn markers; pickpocket sources show the drop list without combat stats.
- Kill tracker, respawn tracker, dry streaks: unchanged, inherently scoped
  to NPC ids.

## 4. Error handling

- Unparseable rarity → rarityKnown=false (never dropped).
- Pages with drop tables but no resolvable name/type → skipped with a
  generator warning and counted in the run summary.
- Item names unresolvable to ids → itemId -1, catalog-visible, not
  item-searchable (same rule as shops).

## 5. Testing

- Generator: wikitext fixtures per template variant (DropsLine,
  DropsLineReward, pickpocket section, multi-roll, textual rarities); type
  inference incl. overlay override; rarity parser table-driven test.
- Client: sources fixture with one of each type; badge rendering; item-view
  grouping/order; non-KILL cards hide kill stats; kill tracking still works
  for KILL sources.
- Regen sanity gates: total sources ≥ 3000; Lunar Chest present with blood
  moon items; Zulrah/Vorkath/Nex present; unresolved-item rate < 5%.

## Out of scope

Live drop-rate refresh in the client, GE prices, quest/achievement item
sources, per-region league filtering.
