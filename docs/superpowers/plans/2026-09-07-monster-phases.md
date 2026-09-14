# Monster Phases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A merged source carries one phase per wiki version (label, image, full combat block), and the monster page switches image, name, stats, and attacks between them.

**Architecture:** The generator reads per-version images from the local monster text snapshot, builds a per-version combat block with the same code path the source's own combat uses, dedupes identical phases, and writes `phases` on the source. The plugin adds a `Phase` model, reads it through the existing Gson path, and gives `SourceResultView` a switcher that swaps the image and rebuilds the Stats and Attacks cards inside fixed slots.

**Tech Stack:** Java 11, JUnit 4, Gson, Swing. Generator repo `E:\Projects\Python\ItemSearchDataGenerator`, plugin repo `E:\Projects\JAVA\ItemSearch`.

**Spec:** `docs/superpowers/specs/2026-09-07-monster-phases-design.md`

## Global Constraints

- Java 11 source level in both repos.
- Generator style: 4-space indent, Allman braces. Plugin style: tabs, Allman braces.
- No commits without the user asking. Tests are the gate for each task.
- Wiki image URL shape: `https://oldschool.runescape.wiki/images/<File name, spaces as underscores, URL-encoded>`.
- Run generator tests with `cmd /c ".\gradlew.bat test --offline -q"` from the generator repo; plugin tests the same way from the plugin repo.

---

### Task 1: Per-version image URLs from the infobox text (generator)

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/VersionImages.java`
- Test: `src/test/java/com/itemsearch/datagen/VersionImagesTest.java`

**Interfaces:**
- Produces: `public static List<String> urlsFor(String infoboxBody)` — one URL per numbered image, or null entries where the version has none; `public static String imageUrl(String fileLink)` — `[[File:X.png|150px]]` or bare `X.png` → URL, null when blank; `public static Map<String, List<String>> fromTexts(Map<String, WikiPageTextJson> texts)` — page title → per-version URLs (empty list when the page has no numbered images; the plain `image` is at index 0 in that case).

- [ ] **Step 1: Write the failing tests**

```java
package com.itemsearch.datagen;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.itemsearch.datagen.source.WikiPageTextJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VersionImagesTest
{
    @Test
    public void aFileLinkBecomesTheWikisDirectImageUrl()
    {
        assertEquals("https://oldschool.runescape.wiki/images/Killerwatt_%28Passive%29.png",
            VersionImages.imageUrl("[[File:Killerwatt (Passive).png|250px]]"));
        assertEquals("https://oldschool.runescape.wiki/images/Zulrah.png",
            VersionImages.imageUrl("Zulrah.png"));
        assertNull(VersionImages.imageUrl(""));
        assertNull(VersionImages.imageUrl(null));
    }

    @Test
    public void numberedImagesLineUpWithVersionsAndFallBackToThePlainOne()
    {
        String infobox = "{{Infobox Monster\n|version1 = Idle\n|version2 = Attacking\n"
            + "|version3 = Dead\n|image1 = [[File:A.png|250px]]\n|image2 = [[File:B.png|250px]]\n"
            + "|image = [[File:Main.png]]\n|combat = 55\n}}";
        List<String> urls = VersionImages.urlsFor(infobox);
        assertEquals(Arrays.asList(
            "https://oldschool.runescape.wiki/images/A.png",
            "https://oldschool.runescape.wiki/images/B.png",
            "https://oldschool.runescape.wiki/images/Main.png"), urls);
    }

    @Test
    public void aPageWithOnlyAPlainImageGivesItToEveryVersion()
    {
        String infobox = "{{Infobox Monster\n|version1 = Level 75\n|version2 = Level 82\n"
            + "|image = [[File:Ankou.png|110px]]\n}}";
        assertEquals(Arrays.asList(
            "https://oldschool.runescape.wiki/images/Ankou.png",
            "https://oldschool.runescape.wiki/images/Ankou.png"), VersionImages.urlsFor(infobox));
    }

    @Test
    public void textsMapToUrlsByPageTitle()
    {
        Map<String, WikiPageTextJson> texts = new HashMap<>();
        WikiPageTextJson t = new WikiPageTextJson();
        t.infobox = "{{Infobox Monster\n|version1 = A\n|version2 = B\n|image1 = [[File:A.png]]\n"
            + "|image2 = [[File:B.png]]\n}}";
        texts.put("Thing", t);
        texts.put("Empty", new WikiPageTextJson());
        Map<String, List<String>> out = VersionImages.fromTexts(texts);
        assertEquals(2, out.get("Thing").size());
        assertEquals(0, out.get("Empty").size());
    }
}
```

- [ ] **Step 2: Run to verify it fails** — compile error, `VersionImages` missing.

- [ ] **Step 3: Implement**

```java
package com.itemsearch.datagen;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.WikiPageTextJson;

/**
 * The picture of each version of a monster page.
 *
 * <p>An {{Infobox Monster}} with versions carries image1, image2, … beside version1, version2,
 * …, and a page whose versions look alike carries one plain image for all of them. Read from the
 * page text snapshot the curator uses, so no further fetching is needed.
 */
public final class VersionImages
{
    private static final String IMAGE_BASE = "https://oldschool.runescape.wiki/images/";
    private static final Pattern FILE_LINK = Pattern.compile("\\[\\[\\s*File:([^\\]|]+)");

    private VersionImages()
    {
    }

    public static Map<String, List<String>> fromTexts(Map<String, WikiPageTextJson> texts)
    {
        Map<String, List<String>> out = new HashMap<>();
        if (texts == null)
        {
            return out;
        }
        for (Map.Entry<String, WikiPageTextJson> e : texts.entrySet())
        {
            WikiPageTextJson t = e.getValue();
            out.put(e.getKey(), urlsFor(t == null ? null : t.infobox));
        }
        return out;
    }

    /**
     * One url per version, in version order: the version's own numbered image, else the page's
     * plain one, else null. Empty when the page has no versions.
     */
    public static List<String> urlsFor(String infoboxBody)
    {
        List<String> out = new ArrayList<>();
        if (infoboxBody == null)
        {
            return out;
        }
        List<String> versions = WikiText.numberedFields(infoboxBody, "version");
        List<String> images = WikiText.numberedFields(infoboxBody, "image");
        String plain = imageUrl(WikiText.field(infoboxBody, "image"));
        for (int i = 0; i < versions.size(); i++)
        {
            String own = i < images.size() ? imageUrl(images.get(i)) : null;
            out.add(own != null ? own : plain);
        }
        return out;
    }

    /** The wiki's direct path for a file link or bare file name; null when there is none. */
    public static String imageUrl(String fileLink)
    {
        if (fileLink == null || fileLink.trim().isEmpty())
        {
            return null;
        }
        Matcher m = FILE_LINK.matcher(fileLink);
        String name = m.find() ? m.group(1) : fileLink;
        name = name.trim();
        if (name.isEmpty())
        {
            return null;
        }
        try
        {
            return IMAGE_BASE + URLEncoder.encode(name.replace(' ', '_'), "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalStateException(e); // UTF-8 is always present
        }
    }
}
```

- [ ] **Step 4: Run tests, expect pass.**

### Task 2: Per-version weakness (generator)

**Files:**
- Modify: `src/main/java/com/itemsearch/datagen/Weakness.java`
- Test: `src/test/java/com/itemsearch/datagen/WeaknessTest.java`

**Interfaces:**
- Produces: `public static List<String> weakestOf(WikiCombatJson w, int version)` — the numbered defence field at that index when present, else the plain one.

- [ ] **Step 1: Failing test** (append to `WeaknessTest`)

```java
    @Test
    public void aVersionReadsItsOwnNumberedDefenceBonuses()
    {
        WikiCombatJson w = new WikiCombatJson();
        w.defStabs = java.util.Arrays.asList("10", "0");
        w.defSlashes = java.util.Arrays.asList("10", "70");
        w.defCrushes = java.util.Arrays.asList("10", "70");
        w.defMagics = java.util.Arrays.asList("0", "70");
        w.defRangeds = java.util.Arrays.asList("10", "70");
        assertEquals(java.util.Collections.singletonList("MAGIC"), Weakness.weakestOf(w, 0));
        assertEquals(java.util.Collections.singletonList("STAB"), Weakness.weakestOf(w, 1));
        assertEquals("out of range falls back to the plain reading",
            Weakness.weakestOf(w), Weakness.weakestOf(w, 5));
    }
```

- [ ] **Step 2: Implement** — refactor `weakestOf(w)` to call a private `weakest(Integer[] values)`; add:

```java
    /** As {@link #weakestOf(WikiCombatJson)}, read from the version's own numbered fields. */
    public static List<String> weakestOf(WikiCombatJson w, int version)
    {
        if (w == null)
        {
            return new ArrayList<>();
        }
        return weakest(new Integer[]{
            at(w.defStabs, version, w.defStab),
            at(w.defSlashes, version, w.defSlash),
            at(w.defCrushes, version, w.defCrush),
            at(w.defMagics, version, w.defMagic),
            at(w.defRangeds, version, w.defRanged),
        });
    }

    // the numbered field for this version when the page has one, else the plain reading
    private static Integer at(List<String> numbered, int version, String plain)
    {
        if (numbered != null && version >= 0 && version < numbered.size())
        {
            Integer value = parse(numbered.get(version));
            if (value != null)
            {
                return value;
            }
        }
        return firstOf(plain, numbered);
    }
```

- [ ] **Step 3: Run tests, expect pass.**

### Task 3: Phases on the output source (generator)

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/output/OutputPhase.java`
- Modify: `src/main/java/com/itemsearch/datagen/output/OutputSource.java` (add `public List<OutputPhase> phases;`)
- Modify: `src/main/java/com/itemsearch/datagen/SourceTransform.java`
- Modify: `src/main/java/com/itemsearch/datagen/SourceDataGenerator.java` (read the text snapshot, pass version images)
- Test: `src/test/java/com/itemsearch/datagen/SourceTransformTest.java`

**Interfaces:**
- Produces: `OutputPhase { String label; String image; OutputCombat combat; }`; `SourceTransform.transform(pages, itemInfo, spawns, wiki, combatOverlay, typeOverlay, monsters, Map<String, List<String>> versionImages)`; the 7-arg overload passes an empty map.

- [ ] **Step 1: Failing tests** (append to `SourceTransformTest`)

```java
    /**
     * Zulrah is one source with three forms. Each form keeps its own picture and its own way of
     * fighting, so the player can see what the magma form does rather than a table that folds all
     * three together.
     */
    @Test
    public void aMergedSourceCarriesOnePhasePerVersion()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        DropPageJson p = page("Zulrah", "Monster", Arrays.asList("2042", "2043"), "725",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null)));
        p.versions.add(version("Serpentine", "725", "2042"));
        p.versions.add(version("Magma", "725", "2043"));
        pages.put("Zulrah", p);
        WikiCombatJson w = versioned(Arrays.asList("[[Ranged]]", "[[Melee]]"),
            Arrays.asList("41", "30"), Arrays.asList("4", "4"));
        w.defStabs = Arrays.asList("0", "50");
        w.defMagics = Arrays.asList("50", "0");
        Map<Integer, MonsterJson> monsters = new HashMap<>();
        monsters.put(2042, monster(2042, 300, 1, 300));
        monsters.put(2043, monster(2043, 300, 2, 300));
        Map<String, List<String>> images = Collections.singletonMap("Zulrah",
            Arrays.asList("https://x/serpentine.png", "https://x/magma.png"));

        OutputSource zulrah = byName(SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.singletonMap("Zulrah", w), Collections.emptyMap(),
            Collections.emptyMap(), monsters, images), "Zulrah");

        assertEquals(2, zulrah.phases.size());
        OutputPhase magma = zulrah.phases.get(1);
        assertEquals("Magma", magma.label);
        assertEquals("https://x/magma.png", magma.image);
        assertEquals("melee", magma.combat.attacks.get(0).style);
        assertEquals(30, magma.combat.attacks.get(0).maxHit);
        assertEquals(2, magma.combat.strengthLevel);
        assertEquals(Collections.singletonList("MAGIC"), magma.combat.weakTo);
        assertEquals("the source's own table still folds every form",
            2, zulrah.combat.attacks.size());
    }

    /** Twenty werewolves that look and fight alike are one phase, which is to say none. */
    @Test
    public void identicalVersionsCollapseToNoPhases()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        DropPageJson p = page("Werewolf", "Monster", Arrays.asList("1", "2"), "88",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null)));
        p.versions.add(version("Boris", "88", "1"));
        p.versions.add(version("Imre", "88", "2"));
        pages.put("Werewolf", p);
        Map<String, List<String>> images = Collections.singletonMap("Werewolf",
            Arrays.asList("https://x/w.png", "https://x/w.png"));

        OutputSource werewolf = byName(SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), images), "Werewolf");

        assertNull(werewolf.phases);
    }

    /** A curated write-up under a version's id is that phase's table. */
    @Test
    public void aPhaseTakesTheCuratedAttacksOfItsOwnId()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        DropPageJson p = page("Killerwatt", "Monster", Arrays.asList("470", "469"), "55",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null)));
        p.versions.add(version("Idle", "55", "470"));
        p.versions.add(version("Attacking", "55", "469"));
        pages.put("Killerwatt", p);
        Map<Integer, CombatOverlayJson> overlay = new HashMap<>();
        overlay.put(469, curated(new OutputAttack("crush", 6, 2)));
        Map<String, List<String>> images = Collections.singletonMap("Killerwatt",
            Arrays.asList("https://x/idle.png", "https://x/attacking.png"));

        OutputSource k = byName(SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), overlay, Collections.emptyMap(),
            Collections.emptyMap(), images), "Killerwatt");

        assertEquals(2, k.phases.size());
        assertEquals("crush", k.phases.get(1).combat.attacks.get(0).style);
        assertTrue(k.phases.get(0).combat.attacks.isEmpty());
    }

    private static MonsterJson monster(int id, int attack, int strength, int hitpoints)
    {
        MonsterJson m = new MonsterJson();
        m.id = id;
        m.attackLevel = attack;
        m.strengthLevel = strength;
        m.hitpoints = hitpoints;
        return m;
    }
```

Add `import com.itemsearch.datagen.output.OutputPhase;`.

- [ ] **Step 2: Implement**

`OutputPhase`:

```java
package com.itemsearch.datagen.output;

/** One wiki version of a merged source: what it looks like and how it fights. */
public class OutputPhase
{
    public String label;
    public String image;
    public OutputCombat combat;

    public OutputPhase(String label, String image, OutputCombat combat)
    {
        this.label = label;
        this.image = image;
        this.combat = combat;
    }
}
```

`SourceTransform`:
- Bucket gains `List<VersionJson> pageVersions;` set in `splitVersions` (`split.pageVersions = page.versions`) and, for a whole page whose versions all share a level (the `groups.size() < 2` early return), on every KILL bucket: `b.pageVersions = page.versions; b.versions = all indexes with ids`. Do that by computing `groups` first and, when it has one entry, assigning that entry's index list and `page.versions` to each KILL bucket before returning.
- `finish` takes `Map<String, List<String>> versionImages`. Extract the combat-building block into:

```java
    private static OutputCombat combatFor(String pageTitle, List<Integer> npcIds, int syntheticId,
        List<Integer> versions, int weaknessVersion, Map<String, WikiCombatJson> wiki,
        Map<Integer, CombatOverlayJson> combatOverlay, Map<Integer, MonsterJson> monsters,
        boolean[] gapOut)
```

which does exactly what the block does today (parsed attacks for `versions`, levels from `monsterFor(npcIds, monsters)`, overlays under every id in `npcIds` (or `syntheticId` when empty), metallic relabel by `pageTitle`, weakness via `Weakness.weakestOf(record, weaknessVersion)` when `weaknessVersion >= 0` else `Weakness.weakestOf(record)`, element, poison seeding). `gapOut[0]` receives `pc.gap`.

- `monsterFor(Bucket, ...)` becomes `monsterFor(List<Integer> npcIds, ...)`.
- After the source's combat, when `b.versions != null && b.versions.size() >= 2 && b.pageVersions != null`:

```java
            List<OutputPhase> phases = new ArrayList<>();
            List<String> images = versionImages == null ? null : versionImages.get(b.pageTitle);
            for (int i : b.versions)
            {
                VersionJson v = b.pageVersions.get(i);
                List<Integer> ids = parseNpcIds(v.npcIds);
                OutputCombat pc = combatFor(b.pageTitle, ids, b.id, Collections.singletonList(i),
                    i, wiki, combatOverlay, monsters, new boolean[1]);
                String image = images != null && i < images.size() ? images.get(i) : null;
                OutputPhase phase = new OutputPhase(v.label, image, pc);
                if (phases.stream().noneMatch(seen -> phaseKey(seen).equals(phaseKey(phase))))
                {
                    phases.add(phase);
                }
            }
            if (phases.size() >= 2)
            {
                source.phases = phases;
            }
```

with `phaseKey` building a string from image, the six levels, weakTo, element, and each attack's style, prayStyle, maxHit, maxHitMax, speed, speedMax, notes, effects, and variant labels/values.

`SourceDataGenerator`: after reading `wiki`, read the text snapshot and pass the images:

```java
        Map<String, WikiPageTextJson> texts = gson.fromJson(
            readIfPresent(Paths.get(DEFAULT_WIKI_TEXT)),
            new TypeToken<Map<String, WikiPageTextJson>>()
            {
            }.getType());
        Map<String, List<String>> versionImages = VersionImages.fromTexts(texts);
```

(`gson.fromJson(null, type)` returns null; `fromTexts(null)` returns an empty map.)

- [ ] **Step 3: Run generator tests, expect pass.**

### Task 4: `Phase` model and loader (plugin)

**Files:**
- Create: `src/main/java/com/itemsearch/model/Phase.java`
- Modify: `src/main/java/com/itemsearch/model/SourceData.java` (field `List<Phase> phases` last; `withDrops`)
- Modify: `src/main/java/com/itemsearch/data/BundledDataSource.java:279,458` (pass phases)
- Modify: `src/main/java/com/itemsearch/ui/SourceResultView.java:512` (`withRecorded` passes phases)
- Modify tests constructing `SourceData`: `SourceResultViewTest` (6), `SourceDataTest`, `SpawnMapMarkerTest` — append `, null`.
- Test: `src/test/java/com/itemsearch/data/BundledSourceDataTest.java`

- [ ] **Step 1: Failing test** (append to `BundledSourceDataTest`; it already loads a `BundledDataSource` from JSON — follow its existing pattern for building one from a string, or add a JSON string test):

```java
	@Test
	public void phasesAreReadWhenPresentAndNullOtherwise()
	{
		String json = "{\"tables\":[],\"sources\":[{\"id\":2042,\"npcIds\":[2042,2043],"
			+ "\"name\":\"Zulrah\",\"combatLevel\":725,\"type\":\"KILL\",\"drops\":[],"
			+ "\"phases\":[{\"label\":\"Serpentine\",\"image\":\"https://x/s.png\","
			+ "\"combat\":{\"attackLevel\":1,\"strengthLevel\":1,\"defenceLevel\":300,"
			+ "\"magicLevel\":300,\"rangedLevel\":300,\"hitpoints\":500,\"attacks\":[]}},"
			+ "{\"label\":\"Magma\",\"image\":\"https://x/m.png\",\"combat\":{\"attackLevel\":1,"
			+ "\"strengthLevel\":1,\"defenceLevel\":300,\"magicLevel\":300,\"rangedLevel\":300,"
			+ "\"hitpoints\":500,\"attacks\":[]}}]},"
			+ "{\"id\":3031,\"npcIds\":[3031],\"name\":\"Goblin\",\"combatLevel\":2,\"type\":\"KILL\",\"drops\":[]}]}";
		BundledDataSource ds = BundledDataSource.fromJson(new Gson(), json);
		assertEquals(2, ds.getSource(2042).getPhases().size());
		assertEquals("Magma", ds.getSource(2042).getPhases().get(1).getLabel());
		assertNull(ds.getSource(3031).getPhases());
	}
```

Check `BundledSourceDataTest` for the existing way it builds a data source from JSON and use that instead of `fromJson`/`getSource` if those names differ.

- [ ] **Step 2: Implement** `Phase`:

```java
package com.itemsearch.model;

import lombok.Value;

/** One wiki version of a monster that stands for several: what it looks like and how it fights. */
@Value
public class Phase
{
	String label;
	String image;
	CombatStats combat;
}
```

Add `List<Phase> phases;` to `SourceData` after `locationName`, extend `withDrops`, and every constructor call.

- [ ] **Step 3: Run plugin tests, expect pass.**

### Task 5: Phase switcher on the monster page (plugin)

**Files:**
- Modify: `src/main/java/com/itemsearch/ui/SourceResultView.java`
- Test: `src/test/java/com/itemsearch/ui/SourceResultViewTest.java`

- [ ] **Step 1: Failing tests**

```java
	private static SourceData phased(List<Phase> phases)
	{
		return new SourceData(2042, "Zulrah", 725, null, Collections.emptyList(),
			Collections.emptyList(), Arrays.asList(2042, 2043), phases.get(0).getCombat(),
			SourceType.KILL, null, null, null, null, null, null, null, phases);
	}

	private static Phase phase(String label, int strength, String attack)
	{
		return new Phase(label, "https://x/" + label + ".png", new CombatStats(1, strength, 1, 1, 1, 5,
			Collections.singletonList(new Attack(attack, 5, 5, 4, 4, null, null, null, null)),
			null, null, null));
	}

	@Test
	public void aSourceWithoutPhasesHasNoSwitcher()
	{
		SourceResultView view = build(npc(GOBLIN, "Goblin", 2, combat(Collections.singletonList("stab"))),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertNull(button(view, "◀"));
		assertNull(button(view, "▶"));
	}

	@Test
	public void switchingPhasesMovesTheNameStatsAndAttacksTogether()
	{
		SourceResultView view = build(phased(Arrays.asList(
			phase("Serpentine", 10, "ranged"), phase("Magma", 20, "melee"))),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		assertTrue(labelTexts(view).contains("Serpentine"));
		assertTrue(labelTexts(view).contains("10"));
		assertEquals(1, countTooltips(view, "Protect from Missiles"));

		button(view, "▶").doClick();
		assertTrue(labelTexts(view).contains("Magma"));
		assertFalse(labelTexts(view).contains("Serpentine"));
		assertTrue(labelTexts(view).contains("20"));
		assertEquals(0, countTooltips(view, "Protect from Missiles"));
		assertEquals(1, countTooltips(view, "Protect from Melee"));

		// wraps
		button(view, "▶").doClick();
		assertTrue(labelTexts(view).contains("Serpentine"));
		button(view, "◀").doClick();
		assertTrue(labelTexts(view).contains("Magma"));
	}

	@Test
	public void theLiveImageRefreshDoesNotOverrideAPhasesPicture()
	{
		SourceResultView view = build(phased(Arrays.asList(
			phase("Serpentine", 10, "ranged"), phase("Magma", 20, "melee"))),
			new TrackerStore(mock(ConfigManager.class), new Gson()));
		view.updateImage("https://x/page.png");
		assertEquals("https://x/Serpentine.png", view.shownImageUrl());
	}
```

Add `import com.itemsearch.model.Phase;`. The stat labels are rendered through `Format.tally`, so 10 and 20 appear as "10" and "20". `Protect from Missiles` is the ranged prayer's display name (see the existing hybrid test); confirm the melee one with `AttackStyles`.

- [ ] **Step 2: Implement** in `SourceResultView`:
  - Fields: `private final List<Phase> phases; private int phase; private JLabel phaseName; private final JPanel statsSlot = slot(); private final JPanel attacksSlot = slot(); private final SpriteManager spriteManager; private final ItemManager itemManager; private String shownImageUrl;`
  - Constructor: `phases = npc.getPhases() != null && npc.getPhases().size() >= 2 ? npc.getPhases() : null;` `CombatStats c = phases != null ? phases.get(0).getCombat() : npc.getCombat();` Stats/attacks go into the slots: `statsSlot.add(buildStatsBox(c)); body.add(statsSlot); attacksSlot.add(buildAttacksBox(c, ...)); body.add(attacksSlot);` inside the existing `if (c != null && KILL)`.
  - `buildImageBox`: load `phases != null ? phases.get(0).getImage() : npc.getWikiImageUrl()` via a private `showImage(String url)` that records `shownImageUrl` and calls the loader; when `phases != null`, add a row: `JButton prev = arrow("◀")`, `phaseName = new JLabel(phases.get(0).getLabel())`, `JButton next = arrow("▶")`, in a `FlowLayout(CENTER, 6, 0)` panel; `prev` calls `showPhase(phase - 1)`, `next` calls `showPhase(phase + 1)`.
  - `showPhase(int i)`: `phase = Math.floorMod(i, phases.size()); Phase p = phases.get(phase); phaseName.setText(p.getLabel()); showImage(p.getImage()); refill(statsSlot, buildStatsBox(p.getCombat())); refill(attacksSlot, buildAttacksBox(p.getCombat(), spriteManager, itemManager));`
  - `refill(JPanel slot, JPanel card)`: `slot.removeAll(); slot.add(card); slot.revalidate(); slot.repaint();`
  - `slot()`: a `JPanel` with `BoxLayout` Y, non-opaque, `CENTER_ALIGNMENT`, `getMaximumSize` capped to preferred height like `card()`.
  - `updateImage(url)`: return early when `phases != null`.
  - `String shownImageUrl()` package-private for the test.
  - `buildStatsBox` and `buildAttacksBox` are unchanged.

- [ ] **Step 3: Run plugin tests, expect pass.**

### Task 6: Regenerate and check

- [ ] Run `cmd /c ".\gradlew.bat runSources --offline -q"` in the generator.
- [ ] Count sources with phases and print Killerwatt's, Zulrah's, and Phantom Muspah's phases (label, image, attacks) with a short Python check; confirm Werewolf (Level 88) has none.
- [ ] Run plugin tests against the regenerated file.
- [ ] Relaunch the dev client for the user.

## Self-review

- Spec coverage: image per version (T1, T3), per-version combat with levels, attacks, overlay, weakness, poison, dragonfire (T2, T3), dedupe and the no-field rule (T3), model and loader (T4), switcher, slots, default phase, wrap, refresh guard (T5), regeneration (T6). Item pages untouched.
- Types: `OutputPhase(label, image, combat)` matches `Phase(label, image, combat)`; JSON field names `label`, `image`, `combat`, `phases` are the same in both repos.
