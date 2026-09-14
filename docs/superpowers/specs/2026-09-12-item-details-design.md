# Item details: facts, prices, examine, equipment

**Date:** 2026-09-12
**Repos:** plugin (this repo) for the page; generator for the examine text
**Status:** approved in chat

## Problem

An item's page is a header and two lists: where to buy it and what drops it.
A monster's page has a picture, a name card, a stats card, and an attacks
card. The item page should carry the same weight: what the item is worth,
what it does when worn, and what the game says about it.

## What the player sees

Beneath the header (icon and name), before Buy from and Obtained from:

- **Examine line.** The wiki's examine text in grey under the name, wrapped.
  Absent when the data has none for the item.
- **Facts card**, two columns like the monster Stats card:
  - Value (the item's shop value)
  - Low alch, High alch
  - Sells to a general store: the low alch value, with a hover note that a
    shop already holding the item pays less
  - Grand Exchange: the current price, and Buy limit, both only when the
    item is tradeable on the Exchange
  - Weight in kg to one decimal
  - A flags line: Members, Tradeable, Stackable, each shown only when true
- **Equipment card**, only for wearable items, headed "Equipment": Slot
  (with "two-handed" when so), Speed, then Attack bonuses for stab, slash,
  crush, magic, ranged; Defence bonuses for the same five; then Strength,
  Ranged strength, Magic damage (percent), Prayer. Zeros are shown as 0, not
  hidden, so a row is where the player expects it.

## Data

- Value, high alch, members, tradeable, stackable, Exchange tradeable come
  from the client's item composition. Low alch is 40% of value, which is
  also what a general store pays for the first one sold to it.
- Exchange price comes from the client's item price service, buy limit and
  weight and equipment stats from the client's item stats. None of these
  need the data file.
- The composition is read on the client thread. The page builds at once
  with empty cards and fills them when the read completes, so opening an
  item never blocks the sidebar.
- **Examine text** comes from the wiki's item infobox, `examine` (or
  `examineN` for a versioned item), gathered by the generator's existing
  item fetch and written to the bundled file as a top-level `examines`
  map from item id to text, for the items that appear in drops or shops.
  Gathering it needs one `--refresh-items` run.

## Plugin

- `ItemResultView` gains a `ClientThread` and builds the two cards through
  a small `ItemFacts` holder filled on the client thread.
- `DropDataService.examineFor(int itemId)` returns the text or null;
  `BundledDataSource` reads the `examines` map.

## Testing

- Generator: `examine` and `examineN` parse from an infobox; the output
  carries an `examines` map for the item ids in use.
- Plugin: with a mocked item manager, the facts card shows value, both
  alch prices, the general store price, Exchange price and limit only when
  Exchange tradeable, weight, and flags; the equipment card appears only
  for equipable items and shows its rows; the examine line appears only
  when the data has one; a client thread that never runs leaves the page
  usable.

## Out of scope

Release date and other wiki facts. Selling prices to specialty shops.
