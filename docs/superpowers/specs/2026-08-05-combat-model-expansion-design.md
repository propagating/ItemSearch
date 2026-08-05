# Combat Model Expansion — Design

Date: 2026-08-05
Status: Draft — awaiting user review
Sequenced AFTER the universal-drop-sources rework (its combat join carries
these structures through unchanged).

## Goal

Three user requests (2026-08-05):
1. Hybrid attack types — magic-ranged, magic-melee, ranged-melee,
   ranged-magic, melee-magic, melee-ranged — where the FIRST style is the
   accuracy roll and the SECOND is what it counts as defensively (magic-ranged
   rolls magic accuracy but is prayed against with ranged prayers).
2. Conditional max hits — an attack's max hit can depend on equipment,
   prayers, or potions/consumables (Killerwatt: 15, or 8 with Insulated
   boots; dragonfire: several tiers by shield/prayer/potion combination).
3. Curator polish — rename it for Item Search, label which field is max hit
   vs attack speed, and support editing the above.

## 1. Data model (plugin `model/`, generator `output/` mirrors)

`Attack` (today: style, max-hit range, speed range) becomes:

```java
@Value
public class Attack
{
	String style;        // accuracy-roll style: Melee/Stab/Slash/Crush/Magic/Ranged/
	                     // Dragonfire/... (existing values unchanged)
	String prayStyle;    // what it counts as defensively; null = same as style.
	                     // "magic ranged" parses to style=Magic, prayStyle=Ranged.
	Integer maxHitMin;   // unmitigated base (existing range support unchanged)
	Integer maxHitMax;
	Integer speedMin;
	Integer speedMax;
	List<MaxHitVariant> variants; // empty = unconditional max hit
}

@Value
public class MaxHitVariant
{
	String label;        // "Insulated boots", "Antifire + shield", "Protect from Magic"
	String kind;         // GEAR | PRAYER | CONSUMABLE | COMBO
	Integer maxHitMin;   // the mitigated hit under this condition
	Integer maxHitMax;
}
```

- Protection prayer icons key off `prayStyle` (fall back to `style`), so
  magic-ranged shows the ranged protection prayer while displaying a Magic
  accuracy roll.
- Hybrid parsing: the wiki writes "Magical ranged" / "Magic ranged" /
  "Ranged magical" etc. in `attack style`; `AttackStyles`/`CombatParser`
  gain a hybrid table mapping each of the six pairs (both word orders as
  they appear, normalized) to (style, prayStyle). Unrecognized combos keep
  today's behavior (single style, gap → curator stub).

## 2. Display (source view combat card)

- Attack line: `Magic (pray Ranged) · max 15 · speed 4` for hybrids; plain
  styles unchanged.
- Conditional max hit: headline shows **best-case first, base in
  parentheses** — `8 (15)` — where best-case = the lowest variant max hit.
  Below the headline, one dim sub-row per variant: `15 unprotected`,
  `8 with Insulated boots`, `12 with Antifire`, ordered descending by hit.
  (Chosen over "8/15": parentheses scale to 3-4 dragonfire tiers by listing
  rows instead of packing the headline.)
- Existing dragonfire protection-item/prayer icon row stays; variants make
  the numeric tiers explicit alongside it.

## 3. Curator (`curate_gui.py` / `curate_combat.py`)

- Rename: window title and headers become "Item Search — combat curator".
- Field labels: "Max hit" and "Attack speed (game ticks, 0.6s)" explicitly,
  instead of unlabeled adjacent inputs.
- Attack style dropdown gains the six hybrid pairs.
- Per-attack "Conditional max hits" table: add/remove rows of
  (label, kind dropdown, max hit); saved into `combat-overlay.json` as the
  `variants` array (overlay schema `CombatOverlayJson` gains the same
  fields; overlay entries with variants replace wiki-joined values wholesale
  as today).
- `wiki-combat.json` parsing may seed variants automatically where the
  infobox versions encode them (e.g. `max hit1/max hit2` with parenthetical
  conditions); anything unparseable stays a curator job — same
  stub-and-review flow as today.

## 4. Testing

- Parser: hybrid table (all six pairs, both word orders), variant seeding
  fixture, unchanged plain styles.
- Client: prayStyle drives the protection icon; variant rows render
  descending; headline shows `best (base)`; no variants → today's rendering
  byte-for-byte.
- Curator: manual (Tkinter), but overlay round-trip (save → generator run →
  sources.json) gets a generator test with a variants overlay fixture.

## Out of scope

Automatic best-case selection from the player's actual worn equipment
(requires live gear introspection); DPS math on variants.
