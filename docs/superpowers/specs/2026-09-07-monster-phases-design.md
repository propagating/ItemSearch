# Monster phases: per-version image, stats, and attacks

**Date:** 2026-09-07
**Repos:** generator (`E:\Projects\Python\ItemSearchDataGenerator`) and plugin (this repo)
**Status:** approved in chat; awaiting implementation plan

## Problem

Since the version-split rule changed to combat level only, a source such as
Killerwatt, Zulrah, or Phantom Muspah stands for several wiki versions at
once. The wiki gives each version its own image, and often its own stats and
max hits. The monster page today shows one image (the page's main picture,
fetched live) and one folded attack table, so a player looking at Zulrah
cannot see what the magma form does or what it looks like.

## What the player sees

On a monster page whose source carries phases:

- The image card shows the selected phase's picture, with a previous arrow,
  the phase's name, and a next arrow beneath it. The arrows wrap.
- The Stats card and the Attacks card describe the selected phase: its six
  levels, its weakness lines, and its attack table.
- The first phase is selected when the page opens. Switching changes the
  image, the name, the stats, and the attacks together.
- A page without phases renders exactly as today, including the live image
  refresh from the wiki's page image.

## Data

### Generator

A `phase` is one wiki version of a merged source. A source built from two or
more versions (a merged level group, or a whole page whose versions all
share a level) gains `phases`, a list in the wiki's version order:

```json
"phases": [
  {
    "label": "Serpentine",
    "image": "https://oldschool.runescape.wiki/images/Zulrah_(serpentine).png",
    "combat": { "attackLevel": 1, ..., "attacks": [...], "weakTo": [...],
                "elementalWeakness": null, "elementalWeaknessPercent": null }
  }
]
```

- `label` is the version's `versionN` text.
- `image` comes from the infobox's `imageN` for that version, falling back
  to the plain `image` field. The infobox text is read from the local
  `wiki-monster-text.json` snapshot, which covers every versioned monster
  page. The URL is the wiki's direct file path: the `File:` name with spaces
  as underscores, percent-encoded. A version with no image at all has
  `image` null.
- `combat` is built the same way the source's own combat is, but for that one
  version: levels from the monster dump under the version's own npc ids,
  attacks from the version's own numbered wiki fields, the curator's overlay
  for the version's id when one is curated, poison seeded the same way,
  metallic dragonfire relabelled the same way, and weakness from the
  version's own numbered defence fields (a new per-version reading in
  `Weakness`, mirroring the per-version reading in `WikiCombat`).
- Phases identical in image and combat collapse into one, keeping the
  first's label. If one phase remains, the source carries no `phases` field.
  Killerwatt keeps Idle and Attacking; Zulrah keeps three. The twenty Canifis
  werewolves each have their own picture, so they stay twenty phases: a
  distinct picture is exactly what the switcher is for.
- The source's own `combat` is unchanged: the folded table, as now. It is
  what item pages and any consumer without phase awareness keep using.

### Plugin

`SourceData` gains `List<Phase> phases` (null when absent). `Phase` is a
new `@Value` model: `String label`, `String image`, `CombatStats combat`.
Gson maps it straight from the bundled file. Every `SourceData` constructor
call site carries it through.

## Plugin UI

`SourceResultView`:

- `buildImageBox` adds a switcher row beneath the image when the source has
  two or more phases: a `◀` button, a label with the phase name, a `▶`
  button. The row is absent otherwise.
- The Stats and Attacks cards are built from a `CombatStats`, as now. With
  phases, the view keeps a reference to each card's container and rebuilds
  the card's contents from the selected phase's `CombatStats` on switch,
  then revalidates. The cards' positions in the body do not move.
- The selected phase's image is loaded with `WikiImageLoader.loadInto`,
  which already caches by URL, so switching back is free.
- `updateImage` (the live page-image refresh) is ignored while phases are
  present, so the wiki's page picture does not overwrite a phase's.
- Killable/type checks are unchanged: phases exist only on KILL sources.

## Testing

Generator:

- Version images: `imageN` per version, plain `image` fallback, none.
- Phases emitted for a merged source with distinct images; none for a
  merged source whose versions are identical; none for a lone version.
- A phase's combat carries its own levels, attacks, weakness, and curated
  overlay.
- Werewolf-shaped page: level 88 group collapses to one phase (no field),
  level 93 pair collapses too.

Plugin:

- `BundledDataSource` reads phases from JSON and leaves them null when absent.
- `SourceResultView`: no switcher without phases; switcher with phases; next
  and previous change the label, the stats values, and the attack columns;
  wrapping at both ends; `updateImage` does nothing while phases are shown.

## Out of scope

- Per-phase drops. Drops are a page-level table and stay shared.
- Per-phase spawns. Spawn tiles stay pooled under the source.
- Item pages. They keep showing the source's folded combat.
