# One page for an NPC who sells and can be robbed

**Date:** 2026-09-12
**Repos:** generator and plugin
**Status:** approved in chat

## Problem

Martin the Master Gardener runs a shop and can be pickpocketed. Today those
are two unrelated pages: a shop page with stock, and a "(pickpocket)"
source with loot. A player looking at the NPC wants both, and wants to know
the thieving level before trying.

## What the player sees

- Opening the shop, or the pickpocket entry, of such an NPC lands on one
  page: the monster page layout with the NPC's picture and name card
  ("Pickpocketing · Shopkeeper"), an Actions card, then:
  - **Purchasable**: the shop's stock rows, exactly as the shop page draws
    them, with the shop's location and currency line above them.
  - **Stealable @ 38 Thieving · 43 xp**: the pickpocket loot rows with the
    usual Got and Dry counters, grouped as today.
- Every pickpocket page, shop or not, gets the "Stealable @ level · xp"
  heading when the data knows the level.
- A shop whose owner cannot be pickpocketed keeps today's shop page.
- Browse and search rows are unchanged. The shop row and the pickpocket row
  both open the merged page.
- The Actions card's Map button marks the NPC's spawn tiles when it has
  them, else the shop's location when it has one.

## Data

### Generator

- Shop pages: the `{{Infobox Shop}}` `owner` parameter, links stripped, is
  carried as `owner` on each shop record. Needs one `--refresh-shops` run.
- Thieving: a new fetch of the wiki's Thieving page reads the Pickpocketing
  table (columns Name, Thieving level, XP, Ardougne cloak level, Stun
  damage, Notes). A row's name cell may name several NPCs
  (`{{plinkt|Man|pic=...}}/[[Woman]]/[[Citizen (...)|Citizen]]`); every
  name in it gets the row's level and xp. Saved as `wiki-thieving.json`,
  refreshed with `--refresh-thieving`.
- Pickpocket sources gain `thievingLevel` (int) and `thievingXp` (double),
  matched by page title, case-insensitively. Unmatched sources carry none.

### Plugin

- `ShopData.owner`; `SourceData.thievingLevel`, `thievingXp` (both nullable).
- `DropDataService.shopOwnedBy(String npcName)` returns the shop whose owner
  matches, case-insensitively, or null.
- The panel's shop route checks for a pickpocket source named
  `<owner> (pickpocket)`; its source route checks for a shop owned by the
  source's wiki title. Either match renders `SourceResultView` with the
  shop attached.

## Testing

- Generator: owner parses from an infobox; the Thieving table parses names,
  levels, xp, including multi-name cells; pickpocket sources carry the
  numbers and unmatched ones do not.
- Plugin: shop route with a pickpocketable owner opens the merged view;
  pickpocket route with an owning shop opens the same; a plain shop opens
  the shop page; the merged view shows a Purchasable section and the
  Stealable heading with level and xp; a pickpocket source without a level
  shows a plain loot list.

## Out of scope

Shops with several owners keep only the first. Selling to the shop.
