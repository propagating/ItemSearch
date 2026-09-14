# Shop Purchase Sources Implementation Plan

**Goal:** Show which shops sell an item (excluding 0-base-stock entries) in the drop search plugin's item results, and make shops searchable with a full catalog view.

**Architecture:** Shops are a separate entity from monsters, built across two repos. The offline generator (`E:\Projects\Python\ItemSearchDataGenerator`) scrapes OSRS wiki shop pages into committed snapshots and joins them into a bundled `shops.json` written into the plugin repo (`E:\Projects\JAVA\ItemSearch`); the plugin loads it beside `drops.json`, indexes `itemId → shop entries`, and renders a "Buy from" section in item results plus a new shop catalog view. Spec: `docs/superpowers/specs/2026-08-04-shop-sources-design.md`.

**Tech Stack:** Java, Gson, Swing, JUnit 4 + Mockito, Gradle. No new dependencies.

## Global Constraints

- Every new `.java` file gets the standard BSD license header — copy it verbatim from any sibling file in the same directory (copyright line: `Copyright (c) 2026, propagating <propagating@protonmail.com>`).
- The generator repo is a **standalone Gradle build**: Java 11, 4-space indentation, JUnit 4, Gson only. Tests: `./gradlew test` (from its repo root; on Windows `gradlew.bat test`).
- The plugin repo follows the RuneLite Plugin Hub template (see its `AGENTS.md`). Tests: `./gradlew test`. Plugin code uses **tabs**, braces on their own line, Lombok `@Value` for models.
- Two repos: generator work happens in `E:\Projects\Python\ItemSearchDataGenerator` (commit prefix `datagen:`), plugin work in `E:\Projects\JAVA\ItemSearch` (commit prefix `shops:`). Run each task's commands from its repo root. Never commit to the local runelite fork.
- Semantics fixed by the spec: `stock` `-1` = infinite, `null` = unknown (both purchasable), `0` = excluded from item results ("for now" rule — implemented **only** in `BundledDataSource.shopsForItem`); `restockSeconds = round(ticks × 0.6)`; sell price = explicit `sell=` override, else `floor(value × sellmultiplier / 1000)`, else `null`; nulls render as `?`.

## File Structure

Generator repo (`src/main/java/com/itemsearch/datagen/`):
- `source/ShopPageJson.java`, `source/ShopTableJson.java`, `source/ShopLineJson.java` — raw parse DTOs (snapshot format, raw strings)
- `source/ItemInfoJson.java` — `{id, value}` from item infoboxes
- `WikiHttp.java` — shared polite HTTP helper (extracted from `WikiFetch`)
- `WikiShopFetch.java` — shop page enumeration, fetch, wikitext parsing
- `WikiItemFetch.java` — item infobox fetch/parse
- `ShopTransform.java` — snapshots → output DTOs (all numeric parsing/price math)
- `ShopDataGenerator.java` — main; writes `shops.json`
- `output/OutputShop.java`, `output/OutputShopItem.java`

Plugin repo (`src/main/java/com/itemsearch/`):
- `model/ShopData.java`, `model/ShopItem.java`, `model/ShopEntry.java`
- `data/DropDataService.java` (modify: 2 new methods), `data/BundledDataSource.java` (modify: load/index shops)
- `ui/Format.java` (modify: price/stock/restock text helpers)
- `ui/ShopResultView.java` (new), `ui/ItemResultView.java` (modify: Buy-from section)
- `ItemSearchPanel.java` (modify: shop rows + navigation), `ItemSearchPlugin.java` (modify: resource wiring)

---

### Task 1: Parse shop wikitext into snapshot DTOs

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/source/ShopPageJson.java`
- Create: `src/main/java/com/itemsearch/datagen/source/ShopTableJson.java`
- Create: `src/main/java/com/itemsearch/datagen/source/ShopLineJson.java`
- Create: `src/main/java/com/itemsearch/datagen/WikiShopFetch.java`
- Test: `src/test/java/com/itemsearch/datagen/WikiShopFetchTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `WikiShopFetch.extract(String pageTitle, String wikitext) → ShopPageJson` (null when the page has no usable store table); DTO public fields as below. Task 2 reuses `extract`; Task 4 consumes the DTOs.

- [ ] **Step 1: Write the DTOs** (plain public-field Gson DTOs, matching `WikiCombatJson` style; raw strings — numeric parsing happens in Task 4's transform)

```java
// source/ShopPageJson.java
package com.itemsearch.datagen.source;

import java.util.ArrayList;
import java.util.List;

public class ShopPageJson
{
    public String pageTitle;
    public String location; // from {{Infobox Shop}} |location=, wiki links stripped; may be null
    public List<ShopTableJson> tables = new ArrayList<>();
}
```

```java
// source/ShopTableJson.java
package com.itemsearch.datagen.source;

import java.util.ArrayList;
import java.util.List;

public class ShopTableJson
{
    public String currency;       // {{StoreTableHead}} |currency=, links stripped; null -> "Coins" at transform
    public String sellMultiplier; // raw |sellmultiplier= value
    public String nameNotes;      // raw |namenotes= value, may be null
    public List<ShopLineJson> lines = new ArrayList<>();
}
```

```java
// source/ShopLineJson.java
package com.itemsearch.datagen.source;

public class ShopLineJson
{
    public String name;    // item name, links stripped (required; nameless rows are dropped)
    public String stock;   // raw: "5", "∞", "inf", "N/A", or null
    public String restock; // raw tick count, "N/A", "", or null
    public String sell;    // raw explicit sell override, "N/A", or null
}
```

- [ ] **Step 2: Write the failing parser test**

```java
// WikiShopFetchTest.java
package com.itemsearch.datagen;

import com.itemsearch.datagen.source.ShopPageJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikiShopFetchTest
{
    private static final String ZAFF =
        "{{Infobox Shop\n|name = Zaff's Superior Staffs\n|location = [[Varrock]]\n|members = No\n}}\n"
        + "Some prose.\n"
        + "{{StoreTableHead|sellmultiplier=1000|buymultiplier=550|delta=2}}\n"
        + "{{StoreLine|name=Staff|stock=5|restock=100}}\n"
        + "{{StoreLine|name=Magic staff|stock=5|restock=100|sell=200}}\n"
        + "{{StoreLine|name=Battlestaff|stock=∞|restock=N/A}}\n"
        + "{{StoreLine|name=Mystery item}}\n"
        + "{{StoreLine|stock=9}}\n"
        + "{{StoreTableBottom}}\n";

    @Test
    public void parsesInfoboxLocationAndStoreLines()
    {
        ShopPageJson page = WikiShopFetch.extract("Zaff's Superior Staffs", ZAFF);
        assertEquals("Zaff's Superior Staffs", page.pageTitle);
        assertEquals("Varrock", page.location);
        assertEquals(1, page.tables.size());
        assertEquals("1000", page.tables.get(0).sellMultiplier);
        // nameless row dropped: 4 lines survive
        assertEquals(4, page.tables.get(0).lines.size());
        assertEquals("Staff", page.tables.get(0).lines.get(0).name);
        assertEquals("5", page.tables.get(0).lines.get(0).stock);
        assertEquals("100", page.tables.get(0).lines.get(0).restock);
        assertNull(page.tables.get(0).lines.get(0).sell);
        assertEquals("200", page.tables.get(0).lines.get(1).sell);
        assertEquals("∞", page.tables.get(0).lines.get(2).stock);
        assertNull(page.tables.get(0).lines.get(3).stock);
    }

    @Test
    public void defaultsCurrencyNullAndStripsLinkedCurrency()
    {
        ShopPageJson page = WikiShopFetch.extract("Zaff's Superior Staffs", ZAFF);
        assertNull(page.tables.get(0).currency);

        String tokkul = "{{StoreTableHead|sellmultiplier=1000|currency=[[Tokkul]]}}\n"
            + "{{StoreLine|name=Uncut opal|stock=1|restock=25}}\n{{StoreTableBottom}}";
        assertEquals("Tokkul", WikiShopFetch.extract("Shop", tokkul).tables.get(0).currency);
    }

    @Test
    public void splitsMultipleStoreTablesAndKeepsPipesInsideLinks()
    {
        String two = "{{Infobox Shop\n|location = [[Al Kharid|Al-Kharid]]\n}}\n"
            + "{{StoreTableHead|sellmultiplier=1300|namenotes=standard}}\n"
            + "{{StoreLine|name=Pot|stock=5|restock=10}}\n{{StoreTableBottom}}\n"
            + "{{StoreTableHead|sellmultiplier=1000}}\n"
            + "{{StoreLine|name=Jug|stock=2|restock=10}}\n{{StoreTableBottom}}\n";
        ShopPageJson page = WikiShopFetch.extract("General Store", two);
        assertEquals("Al-Kharid", page.location);
        assertEquals(2, page.tables.size());
        assertEquals("standard", page.tables.get(0).nameNotes);
        assertEquals("Jug", page.tables.get(1).lines.get(0).name);
    }

    @Test
    public void returnsNullWhenNoStoreTable()
    {
        assertNull(WikiShopFetch.extract("Some Page", "Just prose, no shop."));
        assertNull(WikiShopFetch.extract("Some Page", null));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.itemsearch.datagen.WikiShopFetchTest"`
Expected: FAIL — `WikiShopFetch` does not exist (compile error).

- [ ] **Step 4: Implement `WikiShopFetch` parsing** (fetch methods come in Task 2; this task is the pure parser)

```java
package com.itemsearch.datagen;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.ShopLineJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;

/** Fetches and parses {{StoreTableHead}}/{{StoreLine}} shop tables from the live OSRS Wiki. */
public final class WikiShopFetch
{
    private static final Pattern INFOBOX_SHOP =
        Pattern.compile("\\{\\{Infobox Shop(.*?)\\n\\}\\}", Pattern.DOTALL);
    private static final Pattern STORE_TABLE_HEAD =
        Pattern.compile("\\{\\{StoreTableHead\\|(.*?)\\}\\}");
    private static final Pattern STORE_LINE = Pattern.compile("\\{\\{StoreLine\\|(.*?)\\}\\}");

    private WikiShopFetch()
    {
    }

    public static ShopPageJson extract(String pageTitle, String wikitext)
    {
        if (wikitext == null)
        {
            return null;
        }
        ShopPageJson page = new ShopPageJson();
        page.pageTitle = pageTitle;
        Matcher infobox = INFOBOX_SHOP.matcher(wikitext);
        if (infobox.find())
        {
            page.location = stripLinks(params(infobox.group(1)).get("location"));
        }
        Matcher head = STORE_TABLE_HEAD.matcher(wikitext);
        while (head.find())
        {
            Map<String, String> headParams = params(head.group(1));
            ShopTableJson table = new ShopTableJson();
            table.currency = stripLinks(headParams.get("currency"));
            table.sellMultiplier = headParams.get("sellmultiplier");
            table.nameNotes = headParams.get("namenotes");
            int end = wikitext.indexOf("{{StoreTableBottom", head.end());
            String body = wikitext.substring(head.end(), end < 0 ? wikitext.length() : end);
            Matcher line = STORE_LINE.matcher(body);
            while (line.find())
            {
                Map<String, String> p = params(line.group(1));
                String name = stripLinks(p.get("name"));
                if (name == null || name.isEmpty())
                {
                    continue; // a store line without an item name is unusable
                }
                ShopLineJson l = new ShopLineJson();
                l.name = name;
                l.stock = emptyToNull(p.get("stock"));
                l.restock = emptyToNull(p.get("restock"));
                l.sell = emptyToNull(p.get("sell"));
                table.lines.add(l);
            }
            if (!table.lines.isEmpty())
            {
                page.tables.add(table);
            }
        }
        return page.tables.isEmpty() ? null : page;
    }

    // splits template params on '|' pipes that are not inside a [[wiki link]]
    private static Map<String, String> params(String body)
    {
        Map<String, String> out = new HashMap<>();
        for (String part : body.split("\\|(?![^\\[]*?\\]\\])"))
        {
            int eq = part.indexOf('=');
            if (eq > 0)
            {
                out.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT),
                    part.substring(eq + 1).trim());
            }
        }
        return out;
    }

    // "[[Al Kharid|Al-Kharid]]" -> "Al-Kharid", "[[Varrock]]" -> "Varrock"
    static String stripLinks(String s)
    {
        return s == null ? null
            : s.replaceAll("\\[\\[(?:[^\\]|]*\\|)?([^\\]]*)\\]\\]", "$1").trim();
    }

    private static String emptyToNull(String s)
    {
        return s == null || s.isEmpty() ? null : s;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.itemsearch.datagen.WikiShopFetchTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 6: Commit**

```bash
git add ItemSearchDataGenerator/src
git commit -m "datagen: parse wiki shop pages into snapshot DTOs"
```

---

### Task 2: Shop page enumeration and fetch (network layer)

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/WikiHttp.java`
- Modify: `src/main/java/com/itemsearch/datagen/WikiFetch.java` (delegate `request`/`sleep`/`snippet` to `WikiHttp`; delete the private copies)
- Modify: `src/main/java/com/itemsearch/datagen/WikiShopFetch.java`
- Test: `src/test/java/com/itemsearch/datagen/WikiShopFetchTest.java` (add `titlesFrom` test)

**Interfaces:**
- Consumes: `WikiShopFetch.extract` (Task 1).
- Produces: `WikiHttp.API` (String constant), `WikiHttp.request(Gson, String url) → JsonObject`, `WikiHttp.sleep()`, `WikiHttp.snippet(JsonObject) → String`; `WikiShopFetch.fetchShopPageTitles(Gson) → List<String>`; `WikiShopFetch.fetchShops(Gson, Collection<String>) → Map<String, ShopPageJson>` (keyed by page title). Task 4's generator calls the two fetch methods.

- [ ] **Step 1: Extract `WikiHttp`** — new package-private final class holding exactly the `request`, `sleep`, and `snippet` bodies currently private in `WikiFetch` (copy them verbatim), plus constants:

```java
package com.itemsearch.datagen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Shared polite HTTP plumbing for OSRS Wiki API requests. */
final class WikiHttp
{
    static final String API = "https://oldschool.runescape.wiki/api.php";
    private static final String UA = "itemsearch-datagen/1.0 (offline data generator)";

    private WikiHttp()
    {
    }

    static JsonObject request(Gson gson, String url) throws IOException
    {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", UA);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (BufferedReader r = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)))
        {
            return gson.fromJson(r, JsonObject.class);
        }
        finally
        {
            conn.disconnect();
        }
    }

    static String snippet(JsonObject response)
    {
        String raw = response.toString();
        return raw.length() > 500 ? raw.substring(0, 500) + "..." : raw;
    }

    static void sleep()
    {
        try
        {
            Thread.sleep(200); // be polite to the wiki
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
```

In `WikiFetch`: delete the private `request`/`sleep`/`snippet` methods and the `API` constant's uses inside `fetch` become `WikiHttp.API` / `WikiHttp.request` / `WikiHttp.sleep` / `WikiHttp.snippet`. Remove now-unused imports.

- [ ] **Step 2: Run the whole tool test suite to verify the refactor broke nothing**

Run: `./gradlew test`
Expected: PASS (all existing tests, including `WikiFetchTest`).

- [ ] **Step 3: Write failing test for `titlesFrom`**

```java
// add to WikiShopFetchTest.java
@Test
public void extractsTitlesFromEmbeddedinResponse()
{
    com.google.gson.JsonObject response = new com.google.gson.Gson().fromJson(
        "{\"query\":{\"embeddedin\":[{\"pageid\":1,\"ns\":0,\"title\":\"Zaff's Superior Staffs\"},"
            + "{\"pageid\":2,\"ns\":0,\"title\":\"Aubury's Rune Shop\"}]}}",
        com.google.gson.JsonObject.class);
    assertEquals(java.util.Arrays.asList("Zaff's Superior Staffs", "Aubury's Rune Shop"),
        WikiShopFetch.titlesFrom(response));
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew test --tests "com.itemsearch.datagen.WikiShopFetchTest"`
Expected: FAIL — `titlesFrom` not defined.

- [ ] **Step 5: Implement the fetch methods in `WikiShopFetch`**

```java
// additional imports: com.google.gson.Gson, com.google.gson.JsonArray, com.google.gson.JsonElement,
// com.google.gson.JsonObject, java.io.IOException, java.net.URLEncoder, java.util.ArrayList,
// java.util.Collection, java.util.List, java.util.TreeMap

private static final int BATCH = 50;

/** All mainspace page titles that embed {{StoreLine}} — i.e. every shop page. */
public static List<String> fetchShopPageTitles(Gson gson) throws IOException
{
    List<String> titles = new ArrayList<>();
    String continueSuffix = "";
    while (true)
    {
        JsonObject response = WikiHttp.request(gson, WikiHttp.API
            + "?action=query&list=embeddedin&eititle=" + URLEncoder.encode("Template:StoreLine", "UTF-8")
            + "&einamespace=0&eilimit=500&format=json" + continueSuffix);
        WikiHttp.sleep();
        titles.addAll(titlesFrom(response));
        JsonObject cont = response.getAsJsonObject("continue");
        if (cont == null)
        {
            break;
        }
        StringBuilder next = new StringBuilder();
        for (Map.Entry<String, JsonElement> e : cont.entrySet())
        {
            next.append('&').append(e.getKey()).append('=')
                .append(URLEncoder.encode(e.getValue().getAsString(), "UTF-8"));
        }
        continueSuffix = next.toString();
    }
    return titles;
}

static List<String> titlesFrom(JsonObject response)
{
    List<String> out = new ArrayList<>();
    JsonObject query = response.getAsJsonObject("query");
    JsonArray list = query == null ? null : query.getAsJsonArray("embeddedin");
    if (list == null)
    {
        return out;
    }
    for (JsonElement e : list)
    {
        out.add(e.getAsJsonObject().get("title").getAsString());
    }
    return out;
}

/** Fetches and parses each page's store tables; pages with no usable table are omitted. */
public static Map<String, ShopPageJson> fetchShops(Gson gson, Collection<String> pageTitles)
    throws IOException
{
    Map<String, ShopPageJson> result = new TreeMap<>();
    List<String> titles = new ArrayList<>(pageTitles);
    for (int i = 0; i < titles.size(); i += BATCH)
    {
        List<String> batch = titles.subList(i, Math.min(i + BATCH, titles.size()));
        String baseBatchUrl = WikiHttp.API
            + "?action=query&prop=revisions&rvslots=main&rvprop=content&format=json"
            + "&titles=" + URLEncoder.encode(String.join("|", batch), "UTF-8");
        String continueSuffix = "";
        while (true)
        {
            JsonObject response = WikiHttp.request(gson, baseBatchUrl + continueSuffix);
            WikiHttp.sleep();
            JsonObject query = response.getAsJsonObject("query");
            JsonObject pages = query == null ? null : query.getAsJsonObject("pages");
            if (pages == null)
            {
                throw new IOException("Unexpected wiki API response for batch ["
                    + String.join(", ", batch) + "]: "
                    + (response.has("error") ? response.get("error").toString()
                        : WikiHttp.snippet(response)));
            }
            for (Map.Entry<String, JsonElement> e : pages.entrySet())
            {
                JsonObject page = e.getValue().getAsJsonObject();
                if (!page.has("revisions"))
                {
                    continue; // missing page
                }
                String title = page.get("title").getAsString();
                String content = page.getAsJsonArray("revisions").get(0).getAsJsonObject()
                    .getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString();
                ShopPageJson shop = extract(title, content);
                if (shop != null)
                {
                    result.put(title, shop);
                }
            }
            JsonObject cont = response.getAsJsonObject("continue");
            if (cont == null)
            {
                break;
            }
            StringBuilder next = new StringBuilder();
            for (Map.Entry<String, JsonElement> e : cont.entrySet())
            {
                next.append('&').append(e.getKey()).append('=')
                    .append(URLEncoder.encode(e.getValue().getAsString(), "UTF-8"));
            }
            continueSuffix = next.toString();
        }
    }
    return result;
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add ItemSearchDataGenerator/src
git commit -m "datagen: fetch shop pages from the wiki (shared WikiHttp helper)"
```

---

### Task 3: Item info fetch (`{{Infobox Item}}` id + value)

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/source/ItemInfoJson.java`
- Create: `src/main/java/com/itemsearch/datagen/WikiItemFetch.java`
- Test: `src/test/java/com/itemsearch/datagen/WikiItemFetchTest.java`

**Interfaces:**
- Consumes: `WikiHttp` (Task 2).
- Produces: `ItemInfoJson {public Integer id; public Integer value;}`; `WikiItemFetch.extractInfobox(String wikitext) → ItemInfoJson` (null when no item infobox); `WikiItemFetch.fetch(Gson, Collection<String> itemNames) → Map<String, ItemInfoJson>` keyed by the **requested** name (redirect/normalization mapped back). Task 4 consumes both.

- [ ] **Step 1: Write the failing test**

```java
package com.itemsearch.datagen;

import com.itemsearch.datagen.source.ItemInfoJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikiItemFetchTest
{
    @Test
    public void extractsIdAndValue()
    {
        ItemInfoJson info = WikiItemFetch.extractInfobox(
            "{{Infobox Item\n|name = Staff of air\n|id = 1381\n|value = 1500\n}}");
        assertEquals(Integer.valueOf(1381), info.id);
        assertEquals(Integer.valueOf(1500), info.value);
    }

    @Test
    public void fallsBackToNumberedIdAndFirstOfCommaList()
    {
        ItemInfoJson info = WikiItemFetch.extractInfobox(
            "{{Infobox Item\n|name = Multi\n|id1 = 11840,24564\n|value = 100\n}}");
        assertEquals(Integer.valueOf(11840), info.id);
    }

    @Test
    public void missingFieldsBecomeNull()
    {
        ItemInfoJson info = WikiItemFetch.extractInfobox(
            "{{Infobox Item\n|name = Weird\n|value = free\n}}");
        assertNull(info.id);
        assertNull(info.value);
    }

    @Test
    public void returnsNullWithoutInfobox()
    {
        assertNull(WikiItemFetch.extractInfobox("no infobox here"));
        assertNull(WikiItemFetch.extractInfobox(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.itemsearch.datagen.WikiItemFetchTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement**

```java
// source/ItemInfoJson.java
package com.itemsearch.datagen.source;

public class ItemInfoJson
{
    public Integer id;    // in-game item id; null when the page doesn't state one
    public Integer value; // shop value in coins; null when unknown/non-numeric
}
```

```java
// WikiItemFetch.java
package com.itemsearch.datagen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.ItemInfoJson;

/** Fetches {{Infobox Item}} id + shop value from the live OSRS Wiki, for shop stock lines. */
public final class WikiItemFetch
{
    private static final Pattern INFOBOX_ITEM =
        Pattern.compile("\\{\\{Infobox Item(.*?)\\n\\}\\}", Pattern.DOTALL);
    private static final int BATCH = 50;

    private WikiItemFetch()
    {
    }

    public static ItemInfoJson extractInfobox(String wikitext)
    {
        if (wikitext == null)
        {
            return null;
        }
        Matcher box = INFOBOX_ITEM.matcher(wikitext);
        if (!box.find())
        {
            return null;
        }
        String body = box.group(1);
        ItemInfoJson info = new ItemInfoJson();
        String id = field(body, "id");
        if (id == null)
        {
            id = field(body, "id1"); // multi-version items number their ids
        }
        info.id = firstInt(id);
        info.value = firstInt(field(body, "value"));
        return info;
    }

    // "|field = value" on its own line, same convention as WikiFetch
    private static String field(String body, String name)
    {
        Matcher m = Pattern.compile("(?m)^\\s*\\|\\s*" + Pattern.quote(name)
            + "\\s*=\\s*(.*?)\\s*$").matcher(body);
        return m.find() ? m.group(1).trim() : null;
    }

    // first integer of a possibly comma-separated value; null on junk
    private static Integer firstInt(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String first = raw.split(",")[0].trim().replace(" ", "");
        try
        {
            return Integer.parseInt(first);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * Fetches item info for each name, keyed by the REQUESTED name: the wiki may
     * normalize or redirect a title, so the response's "normalized"/"redirects"
     * mappings are walked back to the original.
     */
    public static Map<String, ItemInfoJson> fetch(Gson gson, Collection<String> itemNames)
        throws IOException
    {
        Map<String, ItemInfoJson> result = new TreeMap<>();
        List<String> names = new ArrayList<>(itemNames);
        for (int i = 0; i < names.size(); i += BATCH)
        {
            List<String> batch = names.subList(i, Math.min(i + BATCH, names.size()));
            JsonObject response = WikiHttp.request(gson, WikiHttp.API
                + "?action=query&prop=revisions&rvslots=main&rvprop=content&redirects=1&format=json"
                + "&titles=" + URLEncoder.encode(String.join("|", batch), "UTF-8"));
            WikiHttp.sleep();
            JsonObject query = response.getAsJsonObject("query");
            if (query == null)
            {
                throw new IOException("Unexpected wiki API response: " + WikiHttp.snippet(response));
            }
            // final title -> original requested name (walk normalization then redirects)
            Map<String, String> toOriginal = new HashMap<>();
            for (String n : batch)
            {
                toOriginal.put(n, n);
            }
            remap(query.getAsJsonArray("normalized"), toOriginal);
            remap(query.getAsJsonArray("redirects"), toOriginal);
            JsonObject pages = query.getAsJsonObject("pages");
            if (pages == null)
            {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : pages.entrySet())
            {
                JsonObject page = e.getValue().getAsJsonObject();
                if (!page.has("revisions"))
                {
                    continue;
                }
                String title = page.get("title").getAsString();
                String content = page.getAsJsonArray("revisions").get(0).getAsJsonObject()
                    .getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString();
                ItemInfoJson info = extractInfobox(content);
                String original = toOriginal.getOrDefault(title, title);
                if (info != null)
                {
                    result.put(original, info);
                }
            }
        }
        return result;
    }

    // applies a wiki "from"/"to" mapping array so 'to' titles resolve to the original request
    private static void remap(JsonArray mappings, Map<String, String> toOriginal)
    {
        if (mappings == null)
        {
            return;
        }
        for (JsonElement e : mappings)
        {
            JsonObject m = e.getAsJsonObject();
            String from = m.get("from").getAsString();
            String to = m.get("to").getAsString();
            String original = toOriginal.remove(from);
            if (original != null)
            {
                toOriginal.put(to, original);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.itemsearch.datagen.WikiItemFetchTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ItemSearchDataGenerator/src
git commit -m "datagen: fetch item id and shop value from item infoboxes"
```

---

### Task 4: Transform + `ShopDataGenerator` main

**Files:**
- Create: `src/main/java/com/itemsearch/datagen/output/OutputShop.java`
- Create: `src/main/java/com/itemsearch/datagen/output/OutputShopItem.java`
- Create: `src/main/java/com/itemsearch/datagen/ShopTransform.java`
- Create: `src/main/java/com/itemsearch/datagen/ShopDataGenerator.java`
- Modify: `build.gradle.kts` (register `runShops` task)
- Test: `src/test/java/com/itemsearch/datagen/ShopTransformTest.java`

**Interfaces:**
- Consumes: `ShopPageJson`/`ShopTableJson`/`ShopLineJson` (Task 1), `ItemInfoJson` (Task 3), `MonsterJson`/`DropJson` (existing: `public int id; public String name;` per drop).
- Produces: `OutputShop {public String name, location, currency; public List<OutputShopItem> items;}`; `OutputShopItem {public int itemId; public String itemName; public Integer stock, sellPrice, restockSeconds;}`; `ShopTransform.transform(Map<String, ShopPageJson>, Map<String, ItemInfoJson>, Map<String, Integer> fallbackIds) → List<OutputShop>`; `ShopTransform.itemIdsFromMonsters(Map<String, MonsterJson>) → Map<String, Integer>` (lower-cased name → id). The output JSON shape is exactly what the client's `ShopData`/`ShopItem` (Task 5) deserialize.

- [ ] **Step 1: Write the output DTOs**

```java
// output/OutputShop.java
package com.itemsearch.datagen.output;

import java.util.ArrayList;
import java.util.List;

public class OutputShop
{
    public String name;
    public String location;
    public String currency;
    public List<OutputShopItem> items = new ArrayList<>();
}
```

```java
// output/OutputShopItem.java
package com.itemsearch.datagen.output;

public class OutputShopItem
{
    public int itemId;             // -1 when the name could not be resolved to an id
    public String itemName;
    public Integer stock;          // -1 = infinite, null = unknown
    public Integer sellPrice;      // null = unknown
    public Integer restockSeconds; // null = unknown/N-A
}
```

- [ ] **Step 2: Write the failing transform test**

```java
package com.itemsearch.datagen;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.itemsearch.datagen.output.OutputShop;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.ShopLineJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShopTransformTest
{
    private static ShopLineJson line(String name, String stock, String restock, String sell)
    {
        ShopLineJson l = new ShopLineJson();
        l.name = name;
        l.stock = stock;
        l.restock = restock;
        l.sell = sell;
        return l;
    }

    private static ShopPageJson page(String title, String location, ShopTableJson... tables)
    {
        ShopPageJson p = new ShopPageJson();
        p.pageTitle = title;
        p.location = location;
        for (ShopTableJson t : tables)
        {
            p.tables.add(t);
        }
        return p;
    }

    private static ShopTableJson table(String currency, String sellMultiplier, ShopLineJson... lines)
    {
        ShopTableJson t = new ShopTableJson();
        t.currency = currency;
        t.sellMultiplier = sellMultiplier;
        for (ShopLineJson l : lines)
        {
            t.lines.add(l);
        }
        return t;
    }

    private static ItemInfoJson info(Integer id, Integer value)
    {
        ItemInfoJson i = new ItemInfoJson();
        i.id = id;
        i.value = value;
        return i;
    }

    @Test
    public void transformsValuesStockRestockAndDerivedPrice()
    {
        Map<String, ShopPageJson> pages = new TreeMap<>();
        pages.put("Zaff's Superior Staffs", page("Zaff's Superior Staffs", "Varrock",
            table(null, "1300",
                line("Staff", "5", "100", null),        // derived: floor(200*1300/1000)=260
                line("Magic staff", "0", "N/A", "500"), // explicit sell override, 0 stock kept
                line("Battlestaff", "∞", null, null),   // infinite stock, no value -> null price
                line("Unknown thing", "junk", "N/A", "N/A"))));
        Map<String, ItemInfoJson> items = new HashMap<>();
        items.put("Staff", info(1379, 200));
        items.put("Magic staff", info(1389, 300));
        Map<String, Integer> fallback = new HashMap<>();
        fallback.put("battlestaff", 1391);

        List<OutputShop> shops = ShopTransform.transform(pages, items, fallback);
        assertEquals(1, shops.size());
        OutputShop shop = shops.get(0);
        assertEquals("Zaff's Superior Staffs", shop.name);
        assertEquals("Varrock", shop.location);
        assertEquals("Coins", shop.currency); // null currency defaults
        assertEquals(4, shop.items.size());   // 0-stock line KEPT in data

        assertEquals(1379, shop.items.get(0).itemId);
        assertEquals(Integer.valueOf(5), shop.items.get(0).stock);
        assertEquals(Integer.valueOf(60), shop.items.get(0).restockSeconds); // 100 ticks * 0.6
        assertEquals(Integer.valueOf(260), shop.items.get(0).sellPrice);

        assertEquals(Integer.valueOf(500), shop.items.get(1).sellPrice); // override wins
        assertEquals(Integer.valueOf(0), shop.items.get(1).stock);
        assertNull(shop.items.get(1).restockSeconds);

        assertEquals(1391, shop.items.get(2).itemId); // fallback id, case-insensitive
        assertEquals(Integer.valueOf(-1), shop.items.get(2).stock); // ∞ -> -1
        assertNull(shop.items.get(2).sellPrice);

        assertEquals(-1, shop.items.get(3).itemId); // unresolvable
        assertNull(shop.items.get(3).stock);        // junk -> unknown
    }

    @Test
    public void multiTablePagesBecomeNumberedShopsAndSortByName()
    {
        Map<String, ShopPageJson> pages = new TreeMap<>();
        pages.put("Zeta", page("Zeta", null, table(null, null, line("Pot", "1", null, null)),
            table("Tokkul", null, line("Jug", "1", null, null))));
        pages.put("Alpha", page("Alpha", null, table(null, null, line("Pot", "1", null, null))));

        List<OutputShop> shops = ShopTransform.transform(pages,
            Collections.emptyMap(), Collections.emptyMap());
        assertEquals(3, shops.size());
        assertEquals("Alpha", shops.get(0).name);
        assertEquals("Zeta", shops.get(1).name);
        assertEquals("Zeta (2)", shops.get(2).name);
        assertEquals("Tokkul", shops.get(2).currency);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.itemsearch.datagen.ShopTransformTest"`
Expected: FAIL — `ShopTransform` not defined.

- [ ] **Step 4: Implement `ShopTransform`**

```java
package com.itemsearch.datagen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.itemsearch.datagen.output.OutputShop;
import com.itemsearch.datagen.output.OutputShopItem;
import com.itemsearch.datagen.source.DropJson;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.ShopLineJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;

/** Joins the wiki shop + item snapshots into the client's shops.json shape. */
public final class ShopTransform
{
    private ShopTransform()
    {
    }

    public static List<OutputShop> transform(Map<String, ShopPageJson> pages,
        Map<String, ItemInfoJson> itemInfo, Map<String, Integer> fallbackIds)
    {
        List<OutputShop> out = new ArrayList<>();
        for (ShopPageJson page : pages.values())
        {
            for (int t = 0; t < page.tables.size(); t++)
            {
                ShopTableJson table = page.tables.get(t);
                OutputShop shop = new OutputShop();
                shop.name = t == 0 ? page.pageTitle : page.pageTitle + " (" + (t + 1) + ")";
                shop.location = page.location;
                shop.currency = table.currency == null || table.currency.isEmpty()
                    ? "Coins" : table.currency;
                Integer multiplier = parseInt(table.sellMultiplier);
                for (ShopLineJson line : table.lines)
                {
                    OutputShopItem item = new OutputShopItem();
                    item.itemName = line.name;
                    item.itemId = resolveId(line.name, itemInfo, fallbackIds);
                    item.stock = parseStock(line.stock);
                    item.restockSeconds = ticksToSeconds(parseInt(line.restock));
                    item.sellPrice = sellPrice(line.sell, itemInfo.get(line.name), multiplier);
                    shop.items.add(item);
                }
                out.add(shop);
            }
        }
        out.sort(Comparator.comparing(s -> s.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Lower-cased drop item name -> item id, from the monster database (first occurrence wins). */
    public static Map<String, Integer> itemIdsFromMonsters(Map<String, MonsterJson> monsters)
    {
        Map<String, Integer> out = new HashMap<>();
        for (MonsterJson m : monsters.values())
        {
            if (m == null || m.drops == null)
            {
                continue;
            }
            for (DropJson d : m.drops)
            {
                if (d != null && d.name != null)
                {
                    out.putIfAbsent(d.name.toLowerCase(Locale.ROOT), d.id);
                }
            }
        }
        return out;
    }

    static int resolveId(String name, Map<String, ItemInfoJson> itemInfo,
        Map<String, Integer> fallbackIds)
    {
        ItemInfoJson info = itemInfo.get(name);
        if (info != null && info.id != null)
        {
            return info.id;
        }
        Integer fallback = fallbackIds.get(name.toLowerCase(Locale.ROOT));
        return fallback != null ? fallback : -1;
    }

    // -1 = infinite, null = unknown/unparseable
    static Integer parseStock(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("∞") || s.equals("inf"))
        {
            return -1;
        }
        Integer n = parseInt(s);
        return n != null && n >= 0 ? n : null;
    }

    static Integer parseInt(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        try
        {
            return Integer.parseInt(raw.trim().replace(",", ""));
        }
        catch (NumberFormatException e)
        {
            return null; // "N/A", "∞", prose
        }
    }

    // wiki restock values are game ticks (0.6s each); negative means "unknown"
    static Integer ticksToSeconds(Integer ticks)
    {
        if (ticks == null || ticks < 0)
        {
            return null;
        }
        return (int) Math.round(ticks * 0.6);
    }

    static Integer sellPrice(String sellOverride, ItemInfoJson info, Integer sellMultiplier)
    {
        Integer explicit = parseInt(sellOverride);
        if (explicit != null)
        {
            return explicit;
        }
        if (sellOverride != null)
        {
            return null; // an explicit non-numeric override like "N/A" means "no price"
        }
        if (info == null || info.value == null || sellMultiplier == null)
        {
            return null;
        }
        return (int) Math.floor((long) info.value * sellMultiplier / 1000.0);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.itemsearch.datagen.ShopTransformTest"`
Expected: PASS.

- [ ] **Step 6: Write `ShopDataGenerator` main + gradle task** (wiring only — its pieces are all tested above; mirror `DropDataGenerator`'s structure)

```java
package com.itemsearch.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;
import com.itemsearch.datagen.output.OutputShop;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;
import com.itemsearch.datagen.source.ShopLineJson;

public final class ShopDataGenerator
{
    private static final String DEFAULT_MONSTERS = "monsters-complete.json";
    private static final String DEFAULT_SHOPS_SNAPSHOT = "wiki-shops.json";
    private static final String DEFAULT_ITEMS_SNAPSHOT = "wiki-items.json";
    // same sibling-repo layout as DropDataGenerator.DEFAULT_OUTPUT: the ItemSearch
    // plugin checkout lives at ../../JAVA/ItemSearch relative to this repo
    private static final String DEFAULT_OUTPUT =
        "../../JAVA/ItemSearch/src/main/resources/com/itemsearch/shops.json";

    private ShopDataGenerator()
    {
    }

    public static void main(String[] args) throws IOException
    {
        boolean refreshShops = false;
        boolean refreshItems = false;
        List<String> positional = new ArrayList<>();
        for (String a : args)
        {
            if ("--refresh-shops".equals(a))
            {
                refreshShops = true;
            }
            else if ("--refresh-items".equals(a))
            {
                refreshItems = true;
            }
            else
            {
                positional.add(a);
            }
        }
        Path monsters = Paths.get(positional.isEmpty() ? DEFAULT_MONSTERS : positional.get(0));
        Path shopsSnapshot = Paths.get(DEFAULT_SHOPS_SNAPSHOT);
        Path itemsSnapshot = Paths.get(DEFAULT_ITEMS_SNAPSHOT);
        Path output = Paths.get(positional.size() >= 2 ? positional.get(1) : DEFAULT_OUTPUT);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        if (refreshShops)
        {
            List<String> titles = WikiShopFetch.fetchShopPageTitles(gson);
            System.out.println("Fetching " + titles.size() + " shop pages...");
            Map<String, ShopPageJson> shops = WikiShopFetch.fetchShops(gson, titles);
            Files.write(shopsSnapshot,
                (gson.toJson(new TreeMap<>(shops)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + shops.size() + " shop pages to " + shopsSnapshot);
        }

        Map<String, ShopPageJson> pages = readSnapshot(gson, shopsSnapshot,
            new TypeToken<Map<String, ShopPageJson>>()
            {
            }.getType());

        if (refreshItems)
        {
            Set<String> names = new TreeSet<>();
            for (ShopPageJson page : pages.values())
            {
                for (ShopTableJson table : page.tables)
                {
                    for (ShopLineJson line : table.lines)
                    {
                        names.add(line.name);
                    }
                }
            }
            System.out.println("Fetching item info for " + names.size() + " item names...");
            Map<String, ItemInfoJson> items = WikiItemFetch.fetch(gson, names);
            Files.write(itemsSnapshot,
                (gson.toJson(new TreeMap<>(items)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + items.size() + " item records to " + itemsSnapshot);
        }

        Map<String, ItemInfoJson> itemInfo = readSnapshot(gson, itemsSnapshot,
            new TypeToken<Map<String, ItemInfoJson>>()
            {
            }.getType());

        Map<String, Integer> fallback = Collections.emptyMap();
        if (Files.exists(monsters))
        {
            Type monstersType = new TypeToken<Map<String, MonsterJson>>()
            {
            }.getType();
            try (BufferedReader reader = Files.newBufferedReader(monsters, StandardCharsets.UTF_8))
            {
                fallback = ShopTransform.itemIdsFromMonsters(gson.fromJson(reader, monstersType));
            }
        }

        List<OutputShop> shops = ShopTransform.transform(pages, itemInfo, fallback);
        Files.write(output, (gson.toJson(shops) + "\n").getBytes(StandardCharsets.UTF_8));
        long unresolved = shops.stream().flatMap(s -> s.items.stream())
            .filter(i -> i.itemId < 0).count();
        long priceless = shops.stream().flatMap(s -> s.items.stream())
            .filter(i -> i.sellPrice == null).count();
        System.out.println("Wrote " + shops.size() + " shops to " + output);
        System.out.println(unresolved + " items without ids, " + priceless + " without prices");
    }

    private static <T> T readSnapshot(Gson gson, Path path, Type type) throws IOException
    {
        if (!Files.exists(path))
        {
            throw new IOException("Missing snapshot " + path
                + " — run with --refresh-shops / --refresh-items first");
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            return gson.fromJson(reader, type);
        }
    }
}
```

Append to `build.gradle.kts`:

```kotlin
// second entry point: shop data generation (same repo-root working dir as "run")
tasks.register<JavaExec>("runShops") {
    mainClass.set("com.itemsearch.datagen.ShopDataGenerator")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}
```

- [ ] **Step 7: Verify everything compiles and the suite passes**

Run: `./gradlew test`
Expected: PASS. Also run `./gradlew compileJava` to be sure the main wiring compiles.

- [ ] **Step 8: Commit**

```bash
git add ItemSearchDataGenerator
git commit -m "datagen: ShopDataGenerator joins wiki snapshots into shops.json"
```

---

### Task 5: Client models + shop data layer + plugin wiring

**Files:**
- Create: `src/main/java/com/itemsearch/model/ShopData.java`
- Create: `src/main/java/com/itemsearch/model/ShopItem.java`
- Create: `src/main/java/com/itemsearch/model/ShopEntry.java`
- Modify: `src/main/java/com/itemsearch/data/DropDataService.java`
- Modify: `src/main/java/com/itemsearch/data/BundledDataSource.java`
- Modify: `src/main/java/com/itemsearch/ItemSearchPlugin.java` (add `SHOPS_RESOURCE` constant next to `DATA_RESOURCE`, pass to the new 3-arg constructor)
- Create: `src/test/resources/com/itemsearch/shops-test.json`
- Test: `src/test/java/com/itemsearch/data/BundledDataSourceShopTest.java`

**Interfaces:**
- Consumes: `shops.json` shape from Task 4.
- Produces (used by Tasks 6–8):
  - `ShopData` — `@Value`: `String name; String location; String currency; List<ShopItem> items;`
  - `ShopItem` — `@Value`: `int itemId; String itemName; Integer stock; Integer sellPrice; Integer restockSeconds;`
  - `ShopEntry` — `@Value`: `ShopData shop; ShopItem item;` (getters `getShop()`/`getItem()`)
  - `DropDataService.shopsForItem(int itemId) → List<ShopEntry>` (stock-0 excluded; sorted price asc, null prices last, then shop name)
  - `DropDataService.searchShops(String query) → List<ShopData>` (name or location contains, sorted by name)
  - `new BundledDataSource(Gson, String dropsResource, String shopsResource)` — old 2-arg ctor delegates with `null` shops (existing tests unchanged)

- [ ] **Step 1: Write the models** (each with license header, `lombok.Value`, `java.util.List` import as needed — exactly the shapes in Interfaces above, one class per file, mirroring `NpcData.java`)

- [ ] **Step 2: Write the test fixture** `shops-test.json` (note: `drops-test.json`'s Goblin/Zulrah both drop Bronze dagger 1205, used below):

```json
[
  {
    "name": "Aaron's Archery Appendages",
    "location": "Ranging Guild",
    "currency": "Coins",
    "items": [
      { "itemId": 1205, "itemName": "Bronze dagger", "stock": 10, "sellPrice": 50, "restockSeconds": 30 },
      { "itemId": 882, "itemName": "Bronze arrow", "stock": 0, "sellPrice": 2, "restockSeconds": 6 }
    ]
  },
  {
    "name": "Zaff's Superior Staffs",
    "location": "Varrock",
    "currency": "Coins",
    "items": [
      { "itemId": 1205, "itemName": "Bronze dagger", "stock": -1, "sellPrice": 30 },
      { "itemId": 1381, "itemName": "Staff of air", "stock": 5 },
      { "itemId": -1, "itemName": "Mystery box", "stock": 5 }
    ]
  }
]
```

- [ ] **Step 3: Write the failing data-layer test**

```java
package com.itemsearch.data;

import com.google.gson.Gson;
import java.util.List;
import com.itemsearch.model.ItemMatch;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopEntry;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BundledDataSourceShopTest
{
	private static final int BRONZE_DAGGER = 1205;
	private static final int BRONZE_ARROW = 882;
	private static final int STAFF_OF_AIR = 1381;

	private BundledDataSource loaded()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/drops-test.json",
			"/com/itemsearch/shops-test.json");
		ds.load();
		return ds;
	}

	@Test
	public void shopsForItemSortsByPriceAndIncludesInfiniteStock()
	{
		List<ShopEntry> entries = loaded().shopsForItem(BRONZE_DAGGER);
		assertEquals(2, entries.size());
		assertEquals("Zaff's Superior Staffs", entries.get(0).getShop().getName()); // 30 < 50
		assertEquals(Integer.valueOf(-1), entries.get(0).getItem().getStock());
		assertEquals("Aaron's Archery Appendages", entries.get(1).getShop().getName());
	}

	@Test
	public void zeroStockEntriesAreNotAWayToPurchase()
	{
		assertTrue(loaded().shopsForItem(BRONZE_ARROW).isEmpty());
	}

	@Test
	public void unknownPriceSortsLast()
	{
		List<ShopEntry> entries = loaded().shopsForItem(STAFF_OF_AIR);
		assertEquals(1, entries.size());
		assertNull(entries.get(0).getItem().getSellPrice());
	}

	@Test
	public void searchShopsMatchesNameAndLocation()
	{
		BundledDataSource ds = loaded();
		List<ShopData> byName = ds.searchShops("zaff");
		assertEquals(1, byName.size());
		assertEquals("Zaff's Superior Staffs", byName.get(0).getName());

		List<ShopData> byLocation = ds.searchShops("ranging guild");
		assertEquals(1, byLocation.size());
		assertEquals("Aaron's Archery Appendages", byLocation.get(0).getName());
	}

	@Test
	public void shopOnlyItemsBecomeSearchableItems()
	{
		// Staff of air is sold but never dropped; item search should still find it
		List<ItemMatch> matches = loaded().searchItems("staff of air");
		assertEquals(1, matches.size());
		assertEquals(STAFF_OF_AIR, matches.get(0).getItemId());
	}

	@Test
	public void missingShopsResourceLeavesShopFeaturesEmptyButDropsWork()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/drops-test.json",
			"/com/itemsearch/no-such-shops.json");
		ds.load();
		assertTrue(ds.shopsForItem(BRONZE_DAGGER).isEmpty());
		assertTrue(ds.searchShops("zaff").isEmpty());
		assertTrue(ds.searchNpcs("goblin").size() >= 1);
	}
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew test --tests "com.itemsearch.data.BundledDataSourceShopTest"`
Expected: FAIL — models/methods not defined (compile error).

- [ ] **Step 5: Implement**

`DropDataService` — add (with imports for `ShopData`/`ShopEntry`):

```java
	/**
	 * Shops that currently sell the item: entries whose base stock is 0 are excluded
	 * ("not a way to purchase", for now); infinite (-1) and unknown (null) stock are
	 * included. Sorted by sell price ascending, unknown prices last, then shop name.
	 */
	List<ShopEntry> shopsForItem(int itemId);

	List<ShopData> searchShops(String query);
```

`BundledDataSource`:
- Field `private final String shopsResourcePath;` — new 3-arg constructor sets it; existing 2-arg constructor delegates: `this(gson, resourcePath, null);`
- New indexes: `private final Map<Integer, List<ShopEntry>> shopsByItemId = new ConcurrentHashMap<>();` and `private final List<ShopData> allShops = new CopyOnWriteArrayList<>();`
- `load()` calls a new `loadShops()` after the existing drops block (its own try/catch, so a bad shops file cannot take down drop data):

```java
	private void loadShops()
	{
		shopsByItemId.clear();
		allShops.clear();
		if (shopsResourcePath == null)
		{
			return;
		}
		try (InputStream in = getClass().getResourceAsStream(shopsResourcePath))
		{
			if (in == null)
			{
				log.warn("itemsearch: bundled shop data not found at {}", shopsResourcePath);
				return;
			}
			Type listType = new TypeToken<List<ShopData>>()
			{
			}.getType();
			List<ShopData> shops = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), listType);
			indexShops(shops == null ? Collections.emptyList() : shops);
		}
		catch (Exception e)
		{
			log.warn("itemsearch: failed to load bundled shop data", e);
		}
	}

	private void indexShops(List<ShopData> shops)
	{
		for (ShopData shop : shops)
		{
			if (shop == null || shop.getName() == null)
			{
				log.warn("itemsearch: skipping malformed shop entry in bundled data");
				continue;
			}
			List<ShopItem> items = shop.getItems() == null
				? Collections.emptyList()
				: shop.getItems().stream()
					.filter(i -> i != null && i.getItemName() != null)
					.collect(Collectors.toList());
			ShopData normalized = new ShopData(shop.getName(), shop.getLocation(),
				shop.getCurrency(), items);
			allShops.add(normalized);
			for (ShopItem item : items)
			{
				if (item.getItemId() < 0)
				{
					continue; // unresolvable name: shown in the catalog, never joined to items
				}
				shopsByItemId.computeIfAbsent(item.getItemId(), k -> new ArrayList<>())
					.add(new ShopEntry(normalized, item));
				// shop-only items should still be findable by item search
				itemNames.putIfAbsent(item.getItemId(), item.getItemName());
			}
		}
	}

	@Override
	public List<ShopEntry> shopsForItem(int itemId)
	{
		List<ShopEntry> purchasable = new ArrayList<>();
		for (ShopEntry e : shopsByItemId.getOrDefault(itemId, Collections.emptyList()))
		{
			Integer stock = e.getItem().getStock();
			if (stock != null && stock == 0)
			{
				continue; // 0 base stock is not a way to purchase the item (for now)
			}
			purchasable.add(e);
		}
		purchasable.sort(Comparator
			.comparing((ShopEntry e) -> e.getItem().getSellPrice() == null)
			.thenComparingInt(e -> e.getItem().getSellPrice() == null
				? 0 : e.getItem().getSellPrice())
			.thenComparing(e -> e.getShop().getName(), String.CASE_INSENSITIVE_ORDER));
		return purchasable;
	}

	@Override
	public List<ShopData> searchShops(String query)
	{
		String q = query.toLowerCase(Locale.ROOT).trim();
		List<ShopData> out = new ArrayList<>();
		if (q.isEmpty())
		{
			return out;
		}
		for (ShopData shop : allShops)
		{
			if (shop.getName().toLowerCase(Locale.ROOT).contains(q)
				|| (shop.getLocation() != null
					&& shop.getLocation().toLowerCase(Locale.ROOT).contains(q)))
			{
				out.add(shop);
			}
		}
		out.sort(Comparator.comparing(ShopData::getName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}
```

Also clear the two new indexes at the top of `index(List<NpcData>)`? No — they are cleared in `loadShops()`, which runs after `index`. Keep the clears in `loadShops()` as shown.

`ItemSearchPlugin`:

```java
	private static final String SHOPS_RESOURCE = "/com/itemsearch/shops.json";
	...
	dataService = new BundledDataSource(gson, DATA_RESOURCE, SHOPS_RESOURCE);
```

- [ ] **Step 6: Run tests to verify they pass** (plus the whole plugin suite for regressions)

Run: `./gradlew test --tests "com.itemsearch.*"`
Expected: PASS — new shop tests and all existing tests.

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "shops: load and index bundled shop data (stock-0 excluded from item joins)"
```

---

### Task 6: Format helpers + `ShopResultView`

**Files:**
- Modify: `src/main/java/com/itemsearch/ui/Format.java`
- Create: `src/main/java/com/itemsearch/ui/ShopResultView.java`
- Test: `src/test/java/com/itemsearch/ui/FormatTest.java` (add cases)
- Test: `src/test/java/com/itemsearch/ui/ShopResultViewTest.java`

**Interfaces:**
- Consumes: `ShopData`/`ShopItem` (Task 5), `ItemManager`, `ColorScheme`.
- Produces:
  - `Format.priceText(Integer price, String currency) → String` — null → `"?"`; Coins → `"1,500 gp"`; else `"120 Tokkul"`
  - `Format.stockText(Integer stock) → String` — null → `"?"`, negative → `"∞"`, else `tally`
  - `Format.restockText(Integer seconds) → String` — null → `"?"`, `<60` → `"30s"`, else `"1m 40s"`
  - `new ShopResultView(ShopData shop, ItemManager itemManager, Runnable onBack, Consumer<ShopItem> onItemSelected)` — rows for every catalog item; 0-stock rows dimmed (`ColorScheme.MEDIUM_GRAY_COLOR` foreground) with a `"not stocked"` note; rows with `itemId >= 0` are clickable and fire `onItemSelected`. Task 7 links to it via the panel; Task 8 constructs it.

- [ ] **Step 1: Write failing Format tests** (append to the existing `FormatTest`)

```java
	@Test
	public void priceTextFormatsCoinsAndOtherCurrenciesAndUnknown()
	{
		assertEquals("1,500 gp", Format.priceText(1500, "Coins"));
		assertEquals("120 Tokkul", Format.priceText(120, "Tokkul"));
		assertEquals("3 gp", Format.priceText(3, null));
		assertEquals("?", Format.priceText(null, "Coins"));
	}

	@Test
	public void stockTextFormatsCounts()
	{
		assertEquals("5", Format.stockText(5));
		assertEquals("1,000", Format.stockText(1000));
		assertEquals("∞", Format.stockText(-1));
		assertEquals("?", Format.stockText(null));
	}

	@Test
	public void restockTextFormatsSeconds()
	{
		assertEquals("30s", Format.restockText(30));
		assertEquals("1m 40s", Format.restockText(100));
		assertEquals("?", Format.restockText(null));
	}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.itemsearch.ui.FormatTest"`
Expected: FAIL — methods not defined.

- [ ] **Step 3: Implement the three Format helpers**

```java
	/** Formats a shop price with its currency ("1,500 gp", "120 Tokkul"); null price -> "?". */
	static String priceText(Integer price, String currency)
	{
		if (price == null)
		{
			return "?";
		}
		if (currency == null || "Coins".equalsIgnoreCase(currency))
		{
			return tally(price) + " gp";
		}
		return tally(price) + " " + currency;
	}

	/** Formats shop stock: negative = infinite ("∞"), null = unknown ("?"). */
	static String stockText(Integer stock)
	{
		if (stock == null)
		{
			return "?";
		}
		if (stock < 0)
		{
			return "∞";
		}
		return tally(stock);
	}

	/** Formats a restock duration in seconds ("30s", "1m 40s"); null -> "?". */
	static String restockText(Integer seconds)
	{
		if (seconds == null)
		{
			return "?";
		}
		if (seconds < 60)
		{
			return seconds + "s";
		}
		return (seconds / 60) + "m " + (seconds % 60) + "s";
	}
```

- [ ] **Step 4: Run FormatTest to verify pass, then write the failing view test**

```java
package com.itemsearch.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import net.runelite.client.game.ItemManager;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopItem;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ShopResultViewTest
{
	private static ShopData shop()
	{
		return new ShopData("Zaff's Superior Staffs", "Varrock", "Coins", Arrays.asList(
			new ShopItem(1381, "Staff of air", 5, 1500, 60),
			new ShopItem(1383, "Staff of water", 0, 1500, 60),
			new ShopItem(-1, "Mystery box", 5, null, null)));
	}

	private static <T> List<T> collect(Container root, Class<T> type)
	{
		List<T> out = new ArrayList<>();
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				out.add(type.cast(c));
			}
			if (c instanceof Container)
			{
				out.addAll(collect((Container) c, type));
			}
		}
		return out;
	}

	private static JLabel labelWithText(Container root, String text)
	{
		for (JLabel l : collect(root, JLabel.class))
		{
			if (l.getText() != null && l.getText().contains(text))
			{
				return l;
			}
		}
		return null;
	}

	@Test
	public void rendersEveryCatalogRowWithPriceAndStock()
	{
		ShopResultView view = new ShopResultView(shop(), mock(ItemManager.class), () ->
		{
		}, i ->
		{
		});
		assertNotNull(labelWithText(view, "Staff of air"));
		assertNotNull(labelWithText(view, "Mystery box"));
		assertNotNull(labelWithText(view, "1,500 gp"));
	}

	@Test
	public void zeroStockRowsAreDimmedAndAnnotated()
	{
		ShopResultView view = new ShopResultView(shop(), mock(ItemManager.class), () ->
		{
		}, i ->
		{
		});
		JLabel name = labelWithText(view, "Staff of water");
		assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, name.getForeground());
		assertNotNull(labelWithText(view, "not stocked"));
	}

	@Test
	public void clickingAResolvedItemFiresCallbackButUnresolvedDoesNot()
	{
		AtomicReference<ShopItem> clicked = new AtomicReference<>();
		ShopResultView view = new ShopResultView(shop(), mock(ItemManager.class), () ->
		{
		}, clicked::set);

		JLabel air = labelWithText(view, "Staff of air");
		MouseListener[] listeners = air.getMouseListeners();
		assertTrue(listeners.length > 0);
		listeners[0].mouseClicked(new MouseEvent(air, MouseEvent.MOUSE_CLICKED,
			0, 0, 0, 0, 1, false));
		assertEquals(1381, clicked.get().getItemId());

		JLabel mystery = labelWithText(view, "Mystery box");
		assertEquals(0, mystery.getMouseListeners().length);
	}
}
```

Run: `./gradlew test --tests "com.itemsearch.ui.ShopResultViewTest"`
Expected: FAIL — `ShopResultView` not defined.

- [ ] **Step 5: Implement `ShopResultView`**

```java
package com.itemsearch.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import net.runelite.client.game.ItemManager;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopItem;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

public class ShopResultView extends JPanel
{
	public ShopResultView(ShopData shop, ItemManager itemManager, Runnable onBack,
		Consumer<ShopItem> onItemSelected)
	{
		setLayout(new BorderLayout(0, 6));

		JButton back = new JButton("< Back");
		back.addActionListener(e -> onBack.run());
		add(back, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JTextArea name = wrapLabel(shop.getName());
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		body.add(name);

		String location = shop.getLocation();
		String currency = shop.getCurrency() == null ? "Coins" : shop.getCurrency();
		JLabel sub = new JLabel((location != null ? location + "  ·  " : "") + currency);
		sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(sub);
		body.add(Box.createVerticalStrut(6));

		for (ShopItem item : shop.getItems())
		{
			body.add(buildRow(shop, item, itemManager, onItemSelected));
		}

		add(body, BorderLayout.CENTER);
	}

	private JPanel buildRow(ShopData shop, ShopItem item, ItemManager itemManager,
		Consumer<ShopItem> onItemSelected)
	{
		// stock 0 lines stay visible (the shop's full catalog) but are dimmed: they
		// are not currently a way to purchase the item
		boolean stocked = item.getStock() == null || item.getStock() != 0;

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));

		if (item.getItemId() >= 0)
		{
			JLabel icon = new JLabel();
			AsyncBufferedImage img = itemManager.getImage(item.getItemId());
			if (img != null)
			{
				img.addTo(icon);
			}
			row.add(icon, BorderLayout.WEST);
		}

		JLabel name = new JLabel(item.getItemName());
		if (stocked && item.getItemId() >= 0)
		{
			name.setForeground(ColorScheme.BRAND_ORANGE);
		}
		if (item.getItemId() >= 0)
		{
			name.setCursor(new Cursor(Cursor.HAND_CURSOR));
			name.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onItemSelected.accept(item);
				}
			});
		}
		row.add(name, BorderLayout.CENTER);

		String value = Format.priceText(item.getSellPrice(), shop.getCurrency())
			+ "  ·  " + Format.stockText(item.getStock());
		if (!stocked)
		{
			value += "  ·  not stocked";
		}
		JLabel valueLabel = new JLabel(value);
		valueLabel.setForeground(stocked ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		if (!stocked)
		{
			name.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}
		row.add(valueLabel, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	// same re-wrapping label used by the other detail views (see ItemResultView.wrapLabel)
	private static JTextArea wrapLabel(String text)
	{
		JTextArea area = new JTextArea(text);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setBorder(null);
		area.setFont(UIManager.getFont("Label.font"));
		area.setForeground(UIManager.getColor("Label.foreground"));
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		return area;
	}
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "com.itemsearch.ui.*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "shops: shop catalog view with dimmed not-stocked rows"
```

---

### Task 7: "Buy from" section in `ItemResultView`

**Files:**
- Modify: `src/main/java/com/itemsearch/ui/ItemResultView.java`
- Modify: `src/main/java/com/itemsearch/ItemSearchPanel.java` (constructor call only — full navigation comes in Task 8)
- Test: `src/test/java/com/itemsearch/ui/ItemResultViewTest.java`

**Interfaces:**
- Consumes: `DropDataService.shopsForItem` (Task 5), `Format.priceText/stockText/restockText` (Task 6).
- Produces: `ItemResultView` constructor gains a final parameter `Consumer<ShopData> onShopSelected` (after `onNpcSelected`). Existing behavior with no shop data is unchanged. Task 8 passes the real callback.

- [ ] **Step 1: Write the failing tests** (in `ItemResultViewTest`; the existing `build` helper gains a trailing `s -> {}` argument, and a new helper loads shops)

```java
	private BundledDataSource loadedTestDataWithShops()
	{
		BundledDataSource ds = new BundledDataSource(new Gson(),
			"/com/itemsearch/drops-test.json",
			"/com/itemsearch/shops-test.json");
		ds.load();
		return ds;
	}

	@Test
	public void buyFromSectionListsShopsCheapestFirstAfterMonsters()
	{
		ItemResultView view = new ItemResultView(
			new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			loadedTestDataWithShops(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			shop ->
			{
			});
		// wrapLabel text areas render monsters first, then shops sorted by price (30 < 50)
		List<String> names = new ArrayList<>();
		for (JTextArea a : collect(view, JTextArea.class))
		{
			names.add(a.getText());
		}
		assertEquals(Arrays.asList("Goblin", "Zulrah",
			"Zaff's Superior Staffs", "Aaron's Archery Appendages"), names);
	}

	@Test
	public void clickingShopNameFiresCallback()
	{
		AtomicReference<com.itemsearch.model.ShopData> clicked =
			new AtomicReference<>();
		ItemResultView view = new ItemResultView(
			new ItemMatch(BRONZE_DAGGER, "Bronze dagger"),
			loadedTestDataWithShops(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			clicked::set);
		for (JTextArea a : collect(view, JTextArea.class))
		{
			if ("Zaff's Superior Staffs".equals(a.getText()))
			{
				a.getMouseListeners()[0].mouseClicked(new MouseEvent(a,
					MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false));
			}
		}
		assertEquals("Zaff's Superior Staffs", clicked.get().getName());
	}

	@Test
	public void itemSoldOnlyWithZeroStockGetsNoBuyFromSection()
	{
		// Bronze arrow: only shop entry has stock 0 -> not purchasable -> no section at all
		ItemResultView view = new ItemResultView(
			new ItemMatch(882, "Bronze arrow"),
			loadedTestDataWithShops(), new TrackerStore(mock(ConfigManager.class), new Gson()),
			mock(ItemManager.class),
			() ->
			{
			},
			npc ->
			{
			},
			shop ->
			{
			});
		for (JLabel l : collect(view, JLabel.class))
		{
			assertFalse("no BUY FROM header expected",
				"BUY FROM".equals(l.getText()));
		}
	}
```

Also update the existing `build(...)` helper (and any direct constructor calls) to pass `s -> {}` as the new final argument, and add the needed imports (`assertFalse`, `JLabel` is already imported? add if missing).

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.itemsearch.ui.ItemResultViewTest"`
Expected: FAIL — constructor arity (compile error).

- [ ] **Step 3: Implement** — in `ItemResultView`:
  - Add `Consumer<ShopData> onShopSelected` as the last constructor parameter; import `ShopData`, `ShopEntry`.
  - After the `SourceOrder` loop, append:

```java
		List<ShopEntry> shopEntries = data.shopsForItem(item.getItemId());
		if (!shopEntries.isEmpty())
		{
			body.add(Box.createVerticalStrut(6));
			body.add(section("Buy from"));
			for (ShopEntry entry : shopEntries)
			{
				body.add(buildShopBox(entry, onShopSelected));
			}
		}
```

  - New method:

```java
	private JPanel buildShopBox(ShopEntry entry, Consumer<ShopData> onShopSelected)
	{
		ShopData shop = entry.getShop();
		ShopItem line = entry.getItem();

		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JTextArea name = wrapLabel(shop.getName());
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setForeground(ColorScheme.BRAND_ORANGE);
		name.setCursor(new Cursor(Cursor.HAND_CURSOR));
		name.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onShopSelected.accept(shop);
			}
		});
		box.add(name);

		if (shop.getLocation() != null)
		{
			box.add(subLabel(shop.getLocation()));
		}
		box.add(statRow("Price", Format.priceText(line.getSellPrice(), shop.getCurrency())));
		box.add(statRow("Stock", Format.stockText(line.getStock())));
		box.add(statRow("Restock", Format.restockText(line.getRestockSeconds())));
		return box;
	}
```

  - Import `ShopItem` too. In `ItemSearchPanel.showItem`, pass a placeholder `shop -> {}` for now so the client compiles (replaced in Task 8).

- [ ] **Step 4: Run the plugin test suite to verify everything passes**

Run: `./gradlew test --tests "com.itemsearch.*"`
Expected: PASS — new tests plus all pre-existing ones (they load no shop data, so their JTextArea expectations still hold).

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "shops: item view lists purchasable shops cheapest-first"
```

---

### Task 8: Shop search results + navigation wiring

**Files:**
- Modify: `src/main/java/com/itemsearch/ItemSearchPanel.java`
- Test: `src/test/java/com/itemsearch/ItemSearchPanelTest.java`

**Interfaces:**
- Consumes: `searchShops`, `ShopResultView`, `ItemResultView`'s `onShopSelected` (Tasks 5–7).
- Produces: search results ordered NPCs → shops (badge `"SHOP"`) → items; `showShop(ShopData)` / `showShop(ShopData, Runnable onBack)` navigation; shop → item → shop back-chains.

- [ ] **Step 1: Write the failing test** (in `ItemSearchPanelTest`; update its `loadedTestData()` helper to the 3-arg constructor with `"/com/itemsearch/shops-test.json"` — existing tests keep passing since goblin/results assertions are unaffected)

```java
	@Test
	public void searchRendersShopRowsWithShopBadge() throws Exception
	{
		ItemSearchPanel panel = newPanel(loadedTestData());
		JTextField search = field(panel, "search", JTextField.class);
		JPanel results = field(panel, "results", JPanel.class);

		search.setText("zaff");
		invokeRebuild(panel);

		boolean foundBadge = false;
		for (Component row : results.getComponents())
		{
			for (Component c : ((Container) row).getComponents())
			{
				if (c instanceof JLabel && "SHOP".equals(((JLabel) c).getText()))
				{
					foundBadge = true;
				}
			}
		}
		assertTrue("expected a SHOP-badged result row for 'zaff'", foundBadge);
	}
```

(add imports: `java.awt.Component`, `java.awt.Container`, `javax.swing.JLabel`)

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.itemsearch.ItemSearchPanelTest"`
Expected: FAIL — no SHOP row rendered.

- [ ] **Step 3: Implement in `ItemSearchPanel`** (import `ShopData`, `ShopResultView`)
  - In `rebuildResults()`, between the NPC loop and the item loop:

```java
				for (ShopData shop : dataService.searchShops(q))
				{
					results.add(new ResultRow("SHOP", shop.getName(), () -> showShop(shop)));
				}
```

  - New navigation methods (below `showNpc`):

```java
	private void showShop(ShopData shop)
	{
		showShop(shop, this::showResults);
	}

	// onBack lets a shop opened from an item's buy-from list return to that item
	private void showShop(ShopData shop, Runnable onBack)
	{
		currentItemView = null;
		currentNpcView = null;
		ShopResultView view = new ShopResultView(shop, itemManager, onBack, line ->
			showItem(new ItemMatch(line.getItemId(), line.getItemName()),
				() -> showShop(shop, onBack)));
		showDetail(view);
	}
```

  - In `showItem(ItemMatch, Runnable)`, replace the Task 7 placeholder so shops open with a back-link to the item:

```java
		currentItemView = new ItemResultView(item, dataService, trackerStore, itemManager, onBack,
			this::showNpc, shop -> showShop(shop, () -> showItem(item, onBack)));
```

- [ ] **Step 4: Run the full plugin test suite**

Run: `./gradlew test --tests "com.itemsearch.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "shops: shops searchable alongside NPCs with catalog navigation"
```

---

### Task 9: Generate real data, README, final verification

All steps run in the **generator repo** (`E:\Projects\Python\ItemSearchDataGenerator`) except where noted; the generated `shops.json` lands in — and is committed to — the **plugin repo** (`E:\Projects\JAVA\ItemSearch`).

**Files:**
- Modify: `README.md` (generator repo)
- Create (generated): `wiki-shops.json`, `wiki-items.json` (generator repo); `src/main/resources/com/itemsearch/shops.json` (plugin repo)

**Interfaces:**
- Consumes: everything above. Produces the shipped data.

- [ ] **Step 1: Ensure the monster input exists** (used only for fallback item ids; skip the download if the file is already present)

```bash
curl -sL "https://raw.githubusercontent.com/osrsbox/osrsbox-db/dae12e3400add0c71465f07a334f9d8f86bebce8/docs/monsters-complete.json" \
  -o monsters-complete.json
```

- [ ] **Step 2: Run the generator with live fetches** (network; ~300 shop pages + item batches at 200 ms politeness — expect several minutes)

```bash
./gradlew runShops --args="--refresh-shops --refresh-items"
```

Expected output lines: `Wrote N shop pages to ...wiki-shops.json`, `Wrote M item records to ...wiki-items.json`, `Wrote K shops to ...shops.json`, plus the unresolved/priceless counts. Sanity: N roughly 250–400; unresolved items a small fraction of the total.

- [ ] **Step 3: Spot-check the output**

```bash
python -c "
import json
shops = json.load(open('../../JAVA/ItemSearch/src/main/resources/com/itemsearch/shops.json', encoding='utf-8'))
print(len(shops), 'shops')
zaff = [s for s in shops if 'Zaff' in s['name']]
print(json.dumps(zaff, indent=1)[:800])
"
```

Expected: Zaff's shop present with Varrock location, staff items with plausible ids/prices/stock. If a systematic parsing gap appears (e.g. many empty shops), fix the parser (Task 1) with a regression test before proceeding.

- [ ] **Step 4: README** — add a `## Shop data (OSRS Wiki)` section after the combat-data section covering: what `wiki-shops.json` / `wiki-items.json` snapshot (shop tables; item id+value), the `runShops` command above, that snapshots are the reproducibility pin, the derived-price formula (`floor(value × sellmultiplier / 1000)`, explicit `sell=` override wins), restock ticks → seconds, and stock encoding (`-1` infinite / `null` unknown / `0` kept but excluded from item joins by the client).

- [ ] **Step 5: Full verification of both repos**

```bash
./gradlew test                                        # generator repo
cd ../../JAVA/ItemSearch && ./gradlew test            # plugin repo
```

Expected: all PASS.

- [ ] **Step 6: Commit each repo**

```bash
# generator repo: snapshots + README
git add wiki-shops.json wiki-items.json README.md
git commit -m "datagen: shop + item-info wiki snapshots"

# plugin repo: the shipped data
cd ../../JAVA/ItemSearch
git add src/main/resources/com/itemsearch/shops.json
git commit -m "shops: bundle generated shop data"
```
