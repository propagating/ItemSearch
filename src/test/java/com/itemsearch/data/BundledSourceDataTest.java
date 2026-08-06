/*
 * Copyright (c) 2026, propagating <propagating@protonmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.itemsearch.data;

import com.google.gson.Gson;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.itemsearch.model.SourceData;
import com.itemsearch.model.SourceType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Smoke tests against the real bundled {@code sources.json}, not a fixture. These are the
 * generator's sanity gates enforced on the shipped file, so a regeneration that loses a whole
 * source type or stops resolving item ids fails the build instead of reaching a user.
 */
public class BundledSourceDataTest
{
    private static BundledDataSource ds;

    @BeforeClass
    public static void loadBundledData()
    {
        ds = new BundledDataSource(new Gson(), "/com/itemsearch/sources.json",
            "/com/itemsearch/shops.json");
        ds.load();
    }

    private static SourceData named(String name)
    {
        for (SourceData s : ds.searchSources(name))
        {
            if (name.equalsIgnoreCase(s.getName()))
            {
                return s;
            }
        }
        return null;
    }

    @Test
    public void bundledDataLoads()
    {
        assertTrue("the shipped data file must parse", ds.isLoaded());
        assertFalse("a search for a common term must return sources",
            ds.searchSources("dragon").isEmpty());
    }

    @Test
    public void everySourceTypeIsRepresented()
    {
        Map<SourceType, Integer> counts = new EnumMap<>(SourceType.class);
        for (SourceType t : SourceType.values())
        {
            counts.put(t, 0);
        }
        // "e" matches nearly every source name; enough to sample the whole file cheaply
        for (SourceData s : ds.searchSources("e"))
        {
            counts.merge(s.getType(), 1, Integer::sum);
        }
        for (SourceType t : SourceType.values())
        {
            assertTrue("no " + t + " sources survived the last regeneration", counts.get(t) > 0);
        }
    }

    @Test
    public void knownSourcesOfEachTypeArePresent()
    {
        SourceData chest = named("Lunar Chest");
        assertNotNull("reward chests must be in the data", chest);
        assertEquals(SourceType.REWARD, chest.getType());
        assertTrue("the chest must carry its rewards",
            chest.getDrops().stream().anyMatch(d -> "Blood moon helm".equals(d.getItemName())));

        for (String boss : new String[]{"Zulrah", "Vorkath", "Nex"})
        {
            SourceData s = named(boss);
            assertNotNull(boss + " must be in the data", s);
            assertEquals(boss + " is killed", SourceType.KILL, s.getType());
            assertFalse(boss + " must have npc ids to track kills", s.getNpcIds().isEmpty());
        }

        SourceData farmer = named("Master Farmer (pickpocket)");
        assertNotNull("pickpocketing must be in the data", farmer);
        assertEquals(SourceType.PICKPOCKET, farmer.getType());
    }

    @Test
    public void placeholderDropsNeverReachTheClient()
    {
        for (SourceData s : ds.searchSources("e"))
        {
            for (com.itemsearch.model.DropSource d : s.getDrops())
            {
                assertFalse("\"Nothing\" is a wiki placeholder, not a drop",
                    "Nothing".equalsIgnoreCase(d.getItemName()));
                assertFalse("item names must not keep a page-section fragment: " + d.getItemName(),
                    d.getItemName().contains("#"));
            }
        }
    }

    @Test
    public void mostDropsResolveToAnItemId()
    {
        int total = 0;
        int unresolved = 0;
        for (SourceData s : ds.searchSources("e"))
        {
            for (com.itemsearch.model.DropSource d : s.getDrops())
            {
                total++;
                if (d.getItemId() < 0)
                {
                    unresolved++;
                }
            }
        }
        assertTrue("expected a substantial sample", total > 1000);
        // Unresolved names stay catalog-visible but are not item-searchable. This sample tracks
        // the whole corpus closely (4.99% vs 5.0% at the 2026-08-05 sweep), so the bound is the
        // generator's 5% gate plus a point of headroom for normal wiki drift.
        double rate = 100.0 * unresolved / total;
        assertTrue("unresolved item rate regressed to " + String.format("%.1f%%", rate),
            rate < 6.0);
    }

    @Test
    public void nonNpcSourcesCarryNoKillableIds()
    {
        List<SourceData> matches = ds.searchSources("chest");
        assertFalse(matches.isEmpty());
        for (SourceData s : matches)
        {
            if (s.getType() == SourceType.REWARD && s.getId() < 0)
            {
                assertTrue("a synthetic-id source must not look killable: " + s.getName(),
                    s.getNpcIds().isEmpty());
            }
        }
    }
}
