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
package com.itemsearch.tracker;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Whether a drop that is only sometimes on the table is on it right now.
 *
 * <p>A brimstone key exists only while Konar assigned the task. Counting an off-task kill against
 * its dry streak measures nothing — the drop was never in the pool — so the streak would climb
 * forever on a monster people mostly kill off task.
 *
 * <p>Read on the client thread: every caller is an event-bus subscriber.
 */
public class DropConditions
{
    /** Konar assigned it, which is what a brimstone key needs. */
    public static final String KONAR_TASK = "KONAR_TASK";

    /** Any slayer master assigned it. */
    public static final String SLAYER_TASK = "SLAYER_TASK";

    private final Client client;

    public DropConditions(Client client)
    {
        this.client = client;
    }

    /**
     * True when the condition holds, and for the unconditional drops that are most of them.
     *
     * <p>A condition this build does not recognise reads as unmet. That direction is deliberate:
     * not counting a kill understates a dry streak, while counting one that could never have
     * dropped the item invents a streak outright, and only the second is a wrong number.
     */
    public boolean isMet(String condition)
    {
        if (condition == null || condition.isEmpty())
        {
            return true;
        }
        switch (condition)
        {
            case SLAYER_TASK:
                return onSlayerTask();
            case KONAR_TASK:
                return onKonarTask();
            default:
                return false;
        }
    }

    /** Kills remaining on the current task; zero when there is no task. */
    private boolean onSlayerTask()
    {
        return client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0;
    }

    /**
     * Konar is the only master who assigns a place along with the monster, so a non-zero assigned
     * area is what distinguishes her task from any other master's.
     */
    private boolean onKonarTask()
    {
        return onSlayerTask() && client.getVarpValue(VarPlayerID.SLAYER_AREA) != 0;
    }
}
